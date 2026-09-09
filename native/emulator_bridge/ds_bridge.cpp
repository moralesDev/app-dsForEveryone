/*
 * Puente JNI entre EmulatorCore (Kotlin) y melonDS.
 *
 * Copyright (c) 2026 DsForEveryone.
 *
 * melonDS se consume sin modificar desde third_party/melonDS y conserva su
 * propia licencia (GPL-3.0); ver docs/integracion-core.md.
 *
 * El puente sólo traduce: no hay lógica de interfaz en C++. Todas las
 * llamadas al core llegan desde el hilo de emulación (ver el contrato de
 * hilos documentado en EmulatorCore.kt).
 *
 * Este archivo cumple dos papeles a la vez: implementa la interfaz
 * melonDS::Platform que el core necesita para enlazar (archivos, hilos,
 * mutex/semáforo, log, mic...) y expone las funciones nativas que Kotlin
 * declara en NativeBridge.kt.
 *
 * El audio (AAudio, 32768 Hz estéreo) usa el mismo anillo sin cerrojos que
 * `app-emulador`: el hilo de emulación empuja en nativeRunFrame, el callback
 * de AAudio saca. Sigue vigente la misma limitación heredada: sin
 * sincronización por audio, el ritmo lo marca el reloj de la emulación.
 */

#include <jni.h>

#include <aaudio/AAudio.h>
#include <android/log.h>

#include <atomic>
#include <cerrno>
#include <chrono>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <ctime>
#include <dlfcn.h>
#include <memory>
#include <mutex>
#include <sys/mman.h>
#include <thread>
#include <unistd.h>

#include <NDS.h>
#include <NDSCart.h>
#include <GPU.h>
#include <GPU3D_Soft.h>
#include <Platform.h>
#include <SPU.h>
#include <Savestate.h>
#include <AREngine.h>
#include <ARCodeFile.h>

#define TAG "ds-bridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

/*
 * Versión del core empaquetado en third_party/melonDS, fijada a mano.
 *
 * melonDS 1.1 no tiene el problema de app-emulador con mGBA (version.cmake
 * escapándose a un `.git` ajeno: ver docs/integracion-core.md, sección 6),
 * pero el principio se mantiene igual: lo que se graba en cada save state es
 * esta constante, nunca un valor generado por el sistema de compilación.
 */
static const char* const BRIDGE_CORE_VERSION = "1.1";

namespace {

constexpr int kScreenWidth = 256;
constexpr int kScreenHeight = 192;
constexpr int kScreenPixels = kScreenWidth * kScreenHeight;

constexpr jint kLoadOk = 0;
constexpr jint kLoadInvalidRom = 1;
constexpr jint kLoadFailed = 2;

// Anillo de audio sin cerrojos, mismo diseño que app-emulador (mgba_bridge.c):
// un productor (el hilo de emulación, en nativeRunFrame) y un consumidor (el
// callback de AAudio), potencia de dos para recortar el índice con una
// máscara. 16384 fotogramas estéreo son ~0,5 s a 32768 Hz: de sobra para
// absorber que el hilo de emulación entregue a ráfagas de un fotograma.
constexpr unsigned kAudioRingFrames = 16384u;
constexpr unsigned kAudioRingMask = kAudioRingFrames - 1u;
constexpr int32_t kAudioSampleRateHint = 32768;  // frecuencia nativa de la DS.
constexpr int kAudioDrainMax = 4096;

struct Emulator {
    std::unique_ptr<melonDS::NDS> nds;
    bool loaded = false;

    std::atomic<bool> micNoiseActive{false};
    std::atomic<bool> batteryDirty{false};

    std::unique_ptr<int16_t[]> ring{new int16_t[kAudioRingFrames * 2]};
    std::atomic<unsigned> ringRead{0};
    std::atomic<unsigned> ringWrite{0};
    AAudioStream* audioStream = nullptr;
    double audioSampleRate = kAudioSampleRateHint;
};

Emulator* FromHandle(jlong handle) {
    return reinterpret_cast<Emulator*>(static_cast<intptr_t>(handle));
}

melonDS::NDSArgs MakeArgs(double outputSampleRate) {
    // Sin argumentos explícitos, NDSArgs ya trae FreeBIOS (ARM9BIOS/ARM7BIOS
    // por defecto) y firmware generado (Firmware{0}): arranca sin que el
    // usuario tenga que aportar BIOS ni firmware reales. Los archivos de
    // sistema de verdad, si hacen falta, llegan en la fase 4.5.
    melonDS::NDSArgs args{};
    // JITArgs por defecto (src/Args.h): bloques de hasta 32 instrucciones,
    // optimización de literales y de saltos activas, y FastMemory activa (usa
    // manejadores de SIGSEGV para las lecturas/escrituras de memoria emulada
    // en vez de comprobar límites en cada acceso). Si FastMemory choca con los
    // manejadores de señales de ART en algún dispositivo, es el primer valor a
    // poner en `false` antes de renunciar al JIT entero.
    args.JIT = melonDS::JITArgs();
    args.GDB = std::nullopt;  // ENABLE_GDBSTUB=OFF: sin socket de depuración.
    // El SPU resamplea internamente de 32768 Hz a esta frecuencia; hay que
    // decirle la que de verdad entrega AAudio (puede no ser la pedida), no la
    // nominal de la DS.
    args.OutputSampleRate = outputSampleRate;
    return args;
}

// Productor: el hilo de emulación, una vez por fotograma (ver nativeRunFrame).
void RingPush(Emulator* emu, const int16_t* frames, unsigned count) {
    const unsigned write = emu->ringWrite.load(std::memory_order_relaxed);
    const unsigned read = emu->ringRead.load(std::memory_order_acquire);
    unsigned freeSlots = kAudioRingFrames - (write - read);
    if (count > freeSlots) count = freeSlots;
    for (unsigned i = 0; i < count; ++i) {
        unsigned slot = ((write + i) & kAudioRingMask) * 2u;
        emu->ring[slot] = frames[i * 2];
        emu->ring[slot + 1] = frames[i * 2 + 1];
    }
    emu->ringWrite.store(write + count, std::memory_order_release);
}

// Consumidor: el hilo de AAudio (ver AudioCallback).
unsigned RingPop(Emulator* emu, int16_t* out, unsigned count) {
    const unsigned read = emu->ringRead.load(std::memory_order_relaxed);
    const unsigned write = emu->ringWrite.load(std::memory_order_acquire);
    unsigned available = write - read;
    if (available > count) available = count;
    for (unsigned i = 0; i < available; ++i) {
        unsigned slot = ((read + i) & kAudioRingMask) * 2u;
        out[i * 2] = emu->ring[slot];
        out[i * 2 + 1] = emu->ring[slot + 1];
    }
    emu->ringRead.store(read + available, std::memory_order_release);
    return available;
}

aaudio_data_callback_result_t AudioCallback(
    AAudioStream*, void* userData, void* audioData, int32_t numFrames) {
    auto* emu = static_cast<Emulator*>(userData);
    auto* out = static_cast<int16_t*>(audioData);
    unsigned got = RingPop(emu, out, static_cast<unsigned>(numFrames));
    if (got < static_cast<unsigned>(numFrames)) {
        // El anillo se vacía si la emulación no ha entregado nada todavía o
        // no llega a tiempo: se completa con silencio en vez de repetir la
        // última muestra o dejar memoria sin inicializar.
        memset(out + static_cast<size_t>(got) * 2, 0,
               (static_cast<size_t>(numFrames) - got) * 2 * sizeof(int16_t));
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

// Abre la salida de audio una sola vez, al crear el manejador: la frecuencia
// real que devuelve AAudio hace falta ANTES de construir el NDS (se le pasa
// como OutputSampleRate en NDSArgs), y el manejador vive lo que vive el
// proceso. Que falle no es motivo para no jugar: sólo silencio, con el
// mensaje en logcat.
void OpenAudio(Emulator* emu) {
    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || !builder) {
        LOGE("No se pudo crear el constructor de AAudio");
        return;
    }
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, kAudioSampleRateHint);
    AAudioStreamBuilder_setPerformanceMode(builder, AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setSharingMode(builder, AAUDIO_SHARING_MODE_SHARED);
    AAudioStreamBuilder_setDataCallback(builder, AudioCallback, emu);

    AAudioStream* stream = nullptr;
    aaudio_result_t opened = AAudioStreamBuilder_openStream(builder, &stream);
    AAudioStreamBuilder_delete(builder);
    if (opened != AAUDIO_OK || !stream) {
        LOGE("No se pudo abrir AAudio: %s", AAudio_convertResultToText(opened));
        return;
    }

    emu->audioSampleRate = static_cast<double>(AAudioStream_getSampleRate(stream));
    if (AAudioStream_requestStart(stream) != AAUDIO_OK) {
        LOGE("No se pudo arrancar AAudio");
        AAudioStream_close(stream);
        return;
    }
    emu->audioStream = stream;
    LOGI("Audio a %.0f Hz", emu->audioSampleRate);
}

/*
 * En memoria, cada píxel del framebuffer de melonDS es B,G,R,A (byte 0 =
 * azul), no R,G,B,A: se comprobó al revés en la fase 4.2 —con la ROM
 * sintética, sin contenido real, nada lo delataba— y quedó así hasta que un
 * juego con colores de verdad (rojo y azul bien diferenciados) lo hizo
 * evidente: Ho-Oh, que es rojo, salía azul. Android espera un entero
 * 0xAARRGGBB (el mismo formato que produce android.graphics.Color); sin este
 * canje R↔B, rojo y azul salen intercambiados en toda la pantalla.
 */
inline uint32_t ToAndroidArgb(uint32_t pixel) {
    uint32_t b = pixel & 0xFFu;
    uint32_t g = (pixel >> 8) & 0xFFu;
    uint32_t r = (pixel >> 16) & 0xFFu;
    return 0xFF000000u | (r << 16) | (g << 8) | b;
}

// Mismo canje de canales que ToAndroidArgb, pero escrito directamente como
// cuatro bytes R,G,B,A: el orden que GLES espera en un GL_RGBA/
// GL_UNSIGNED_BYTE (ver DsGLRenderer.uploadFrame), sin pasar por un entero
// empaquetado que habría que desempaquetar otra vez del lado de Kotlin.
inline void WriteRgbaBytes(uint32_t pixel, uint8_t* out) {
    out[0] = static_cast<uint8_t>((pixel >> 16) & 0xFFu);  // R
    out[1] = static_cast<uint8_t>((pixel >> 8) & 0xFFu);   // G
    out[2] = static_cast<uint8_t>(pixel & 0xFFu);          // B
    out[3] = 0xFF;                                         // A
}

}  // namespace

// ---------------------------------------------------------------------------
// melonDS::Platform
//
// Interfaz que el core necesita para enlazar. La lista exacta de funciones
// realmente referenciadas por `core` con las banderas de compilación de este
// proyecto (JIT/OGL/GDB/red apagados) se comprobó con `llvm-nm --undefined-
// only` sobre libcore.a; sólo se implementa esa lista. Multijugador, cámara,
// AAC (DSi) y los addons (Guitar Grip, Rumble Pak, Motion Pak) no se usan en
// este proyecto y quedan como no-operación.
// ---------------------------------------------------------------------------
namespace melonDS::Platform {

void SignalStop(StopReason reason, void*) {
    LOGI("SignalStop: %d", static_cast<int>(reason));
}

struct FileHandle {
    FILE* file;
};

FileHandle* OpenFile(const std::string& path, FileMode mode) {
    bool write = mode & Write;
    bool read = mode & Read;
    bool preserve = mode & Preserve;
    bool noCreate = mode & NoCreate;
    bool text = mode & Text;
    bool append = mode & Append;

    FILE* f = nullptr;
    if (append) {
        f = fopen(path.c_str(), text ? "a" : "ab");
    } else if (write && read) {
        f = fopen(path.c_str(), text ? "r+" : "r+b");
        if (!f && !noCreate) f = fopen(path.c_str(), text ? "w+" : "w+b");
    } else if (write) {
        if (preserve) {
            f = fopen(path.c_str(), text ? "r+" : "r+b");
            if (!f && !noCreate) f = fopen(path.c_str(), text ? "w" : "wb");
        } else {
            f = fopen(path.c_str(), text ? "w" : "wb");
        }
    } else {
        f = fopen(path.c_str(), text ? "r" : "rb");
    }

    if (!f) return nullptr;
    return new FileHandle{f};
}

FileHandle* OpenLocalFile(const std::string& path, FileMode mode) {
    return OpenFile(path, mode);
}

bool LocalFileExists(const std::string& name) {
    FILE* f = fopen(name.c_str(), "rb");
    if (!f) return false;
    fclose(f);
    return true;
}

bool CloseFile(FileHandle* file) {
    if (!file) return false;
    bool ok = fclose(file->file) == 0;
    delete file;
    return ok;
}

bool IsEndOfFile(FileHandle* file) {
    return file && feof(file->file) != 0;
}

bool FileReadLine(char* str, int count, FileHandle* file) {
    return file && fgets(str, count, file->file) != nullptr;
}

u64 FilePosition(FileHandle* file) {
    return file ? static_cast<u64>(ftell(file->file)) : 0;
}

bool FileSeek(FileHandle* file, s64 offset, FileSeekOrigin origin) {
    if (!file) return false;
    int whence = origin == FileSeekOrigin::Start
        ? SEEK_SET
        : (origin == FileSeekOrigin::Current ? SEEK_CUR : SEEK_END);
    return fseek(file->file, static_cast<long>(offset), whence) == 0;
}

void FileRewind(FileHandle* file) {
    if (file) rewind(file->file);
}

u64 FileRead(void* data, u64 size, u64 count, FileHandle* file) {
    return file ? fread(data, size, count, file->file) : 0;
}

bool FileFlush(FileHandle* file) {
    return file && fflush(file->file) == 0;
}

u64 FileWrite(const void* data, u64 size, u64 count, FileHandle* file) {
    return file ? fwrite(data, size, count, file->file) : 0;
}

u64 FileWriteFormatted(FileHandle* file, const char* fmt, ...) {
    if (!file) return 0;
    va_list args;
    va_start(args, fmt);
    int written = vfprintf(file->file, fmt, args);
    va_end(args);
    return written < 0 ? 0 : static_cast<u64>(written);
}

u64 FileLength(FileHandle* file) {
    if (!file) return 0;
    long pos = ftell(file->file);
    fseek(file->file, 0, SEEK_END);
    long len = ftell(file->file);
    fseek(file->file, pos, SEEK_SET);
    return len < 0 ? 0 : static_cast<u64>(len);
}

void Log(LogLevel level, const char* fmt, ...) {
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case Debug: prio = ANDROID_LOG_DEBUG; break;
        case Info: prio = ANDROID_LOG_INFO; break;
        case Warn: prio = ANDROID_LOG_WARN; break;
        case Error: prio = ANDROID_LOG_ERROR; break;
    }
    va_list args;
    va_start(args, fmt);
    __android_log_vprint(prio, "melonDS", fmt, args);
    va_end(args);
}

struct Thread {
    std::thread t;
};

Thread* Thread_Create(std::function<void()> func) {
    return new Thread{std::thread(std::move(func))};
}

void Thread_Free(Thread* thread) {
    if (!thread) return;
    if (thread->t.joinable()) thread->t.join();
    delete thread;
}

void Thread_Wait(Thread* thread) {
    if (thread && thread->t.joinable()) thread->t.join();
}

struct Semaphore {
    std::mutex m;
    std::condition_variable cv;
    int count = 0;
};

Semaphore* Semaphore_Create() {
    return new Semaphore();
}

void Semaphore_Free(Semaphore* sema) {
    delete sema;
}

void Semaphore_Reset(Semaphore* sema) {
    std::lock_guard<std::mutex> lock(sema->m);
    sema->count = 0;
}

void Semaphore_Wait(Semaphore* sema) {
    std::unique_lock<std::mutex> lock(sema->m);
    sema->cv.wait(lock, [sema] { return sema->count > 0; });
    sema->count--;
}

bool Semaphore_TryWait(Semaphore* sema, int timeout_ms) {
    std::unique_lock<std::mutex> lock(sema->m);
    if (timeout_ms <= 0) {
        if (sema->count <= 0) return false;
        sema->count--;
        return true;
    }
    bool signaled = sema->cv.wait_for(
        lock, std::chrono::milliseconds(timeout_ms), [sema] { return sema->count > 0; });
    if (signaled) sema->count--;
    return signaled;
}

void Semaphore_Post(Semaphore* sema, int count) {
    std::lock_guard<std::mutex> lock(sema->m);
    sema->count += count;
    sema->cv.notify_all();
}

struct Mutex {
    std::mutex m;
};

Mutex* Mutex_Create() {
    return new Mutex();
}

void Mutex_Free(Mutex* mutex) {
    delete mutex;
}

void Mutex_Lock(Mutex* mutex) {
    mutex->m.lock();
}

void Mutex_Unlock(Mutex* mutex) {
    mutex->m.unlock();
}

bool Mutex_TryLock(Mutex* mutex) {
    return mutex->m.try_lock();
}

void Sleep(u64 usecs) {
    usleep(static_cast<useconds_t>(usecs));
}

u64 GetMSCount() {
    struct timespec ts {};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<u64>(ts.tv_sec) * 1000u + static_cast<u64>(ts.tv_nsec) / 1000000u;
}

u64 GetUSCount() {
    struct timespec ts {};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return static_cast<u64>(ts.tv_sec) * 1000000u + static_cast<u64>(ts.tv_nsec) / 1000u;
}

void WriteNDSSave(const u8*, u32, u32, u32, void* userdata) {
    // `userdata` es el mismo Emulator* que se pasó a NDSCart::ParseROM: sólo
    // marca la bandera. El contenido en sí se lee siempre en vivo desde
    // NDS::GetNDSSave() (ver nativeReadBattery), nunca se copia aquí.
    if (auto* emu = static_cast<::Emulator*>(userdata)) {
        emu->batteryDirty.store(true, std::memory_order_relaxed);
    }
}

void WriteGBASave(const u8*, u32, u32, u32, void*) {
    // No hay ranura de GBA en este proyecto.
}

void WriteFirmware(const Firmware&, u32, u32, void*) {
    // El firmware es generado (NDSArgs por defecto); nada que persistir aquí.
}

void WriteDateTime(int, int, int, int, int, int, void*) {}

void MP_Begin(void*) {}
void MP_End(void*) {}
int MP_SendPacket(u8*, int, u64, void*) { return 0; }
int MP_RecvPacket(u8*, u64*, void*) { return 0; }
int MP_SendCmd(u8*, int, u64, void*) { return 0; }
int MP_SendReply(u8*, int, u64, u16, void*) { return 0; }
int MP_SendAck(u8*, int, u64, void*) { return 0; }
int MP_RecvHostPacket(u8*, u64*, void*) { return 0; }
u16 MP_RecvReplies(u8*, u64, u16, void*) { return 0; }

int Net_SendPacket(u8*, int, void*) { return 0; }
int Net_RecvPacket(u8*, void*) { return 0; }

void Camera_Start(int, void*) {}
void Camera_Stop(int, void*) {}
void Camera_CaptureFrame(int, u32*, int, int, bool, void*) {}

void Mic_Start(void*) {}
void Mic_Stop(void*) {}

int Mic_ReadInput(s16* data, int maxlength, void* userdata) {
    auto* emu = static_cast<::Emulator*>(userdata);
    if (!emu || !emu->micNoiseActive.load(std::memory_order_relaxed)) return 0;
    // Ruido blanco a (casi) plena escala: los juegos que esperan un soplido
    // comprueban un umbral de nivel, y un ruido tibio no siempre lo cruza.
    for (int i = 0; i < maxlength; ++i) {
        data[i] = static_cast<s16>((rand() % 65536) - 32768);
    }
    return maxlength;
}

AACDecoder* AAC_Init() { return nullptr; }
void AAC_DeInit(AACDecoder*) {}
bool AAC_Configure(AACDecoder*, int, int) { return false; }
bool AAC_DecodeFrame(AACDecoder*, const void*, int, void*, int) { return false; }

bool Addon_KeyDown(KeyType, void*) { return false; }
void Addon_RumbleStart(u32, void*) {}
void Addon_RumbleStop(void*) {}
float Addon_MotionQuery(MotionQueryType, void*) { return 0.0f; }

// Con ENABLE_JIT=ON, ARMJIT_Memory usa esto en Android para resolver
// ASharedMemory_create de libandroid.so (memoria compartida con la que monta
// el mapeo doble que hace posible FastMemory). dlopen/dlsym son las
// funciones reales de bionic, no un stub: sin ellas el enlazado falla porque
// libcore.a las referencia incondicionalmente en cuanto el JIT está
// compilado.
DynamicLibrary* DynamicLibrary_Load(const char* lib) {
    return reinterpret_cast<DynamicLibrary*>(dlopen(lib, RTLD_NOW));
}

void DynamicLibrary_Unload(DynamicLibrary* lib) {
    if (lib) dlclose(reinterpret_cast<void*>(lib));
}

void* DynamicLibrary_LoadFunction(DynamicLibrary* lib, const char* name) {
    if (!lib) return nullptr;
    return dlsym(reinterpret_cast<void*>(lib), name);
}

}  // namespace melonDS::Platform

// Traduce una clave ya unida por '\n' a su forma para AREngine::Cheats:
// cada línea son dos palabras de 8 dígitos hexadecimales -mismo formato que
// usa el propio melonDS en ARCodeFile.cpp para su archivo de proyecto,
// "%08X %08X"-, concatenadas todas dentro de un único std::vector<u32>. Deja
// `rejection` vacío si todo fue bien, o "L<n>" con la línea (0-based) que no
// tuvo esa forma; en ese caso `code` queda a medio construir y quien llama no
// debe usarlo.
void ParseArCodeLines(const std::string& joinedLines, std::vector<melonDS::u32>* code,
                       std::string* rejection) {
    size_t lineIndex = 0;
    size_t start = 0;
    while (start <= joinedLines.size()) {
        size_t newline = joinedLines.find('\n', start);
        std::string line = (newline == std::string::npos)
                                ? joinedLines.substr(start)
                                : joinedLines.substr(start, newline - start);
        if (!line.empty()) {
            unsigned int c0, c1;
            if (std::sscanf(line.c_str(), "%8x %8x", &c0, &c1) != 2) {
                *rejection = "L" + std::to_string(lineIndex);
                return;
            }
            code->push_back(static_cast<melonDS::u32>(c0));
            code->push_back(static_cast<melonDS::u32>(c1));
        }
        if (newline == std::string::npos) break;
        start = newline + 1;
        ++lineIndex;
    }
    if (code->empty()) *rejection = "L0";  // sin ninguna línea con contenido: nada que aplicar
}

// ---------------------------------------------------------------------------
// Funciones nativas: la frontera con NativeBridge.kt
// ---------------------------------------------------------------------------
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCreate(JNIEnv*, jobject) {
    auto* emu = new Emulator();
    OpenAudio(emu);
    return reinterpret_cast<jlong>(emu);
}

namespace {

/*
 * Todo lo que hace falta para dejar un cartucho jugando, a partir de un
 * puntero a la ROM ya en memoria (venga de un `jbyteArray` copiado o de un
 * `mmap`) y de la partida de batería opcional. Comparten esto `nativeLoad`
 * (ROM pequeña o dentro de un `.zip`, entregada como `ByteArray`) y
 * `nativeLoadFd` (ROM suelta y grande, mapeada en memoria sin copiarla a
 * Java) — la única diferencia entre las dos es cómo se consigue `romData`.
 */
jint FinishLoad(
    Emulator* emu, const melonDS::u8* romData, melonDS::u32 romLen, JNIEnv* env,
    jbyteArray saveArray) {
    // Si hay partida de batería previa, se entrega en memoria al construir el
    // cartucho: el core la lee ya con el tamaño que él mismo detecte como el
    // real (ver docs/integracion-core.md, sección 7 — "algunos juegos no
    // declaran bien su tipo" es un problema del propio formato, no de este
    // puente).
    melonDS::NDSCart::NDSCartArgs cartArgs{};
    if (saveArray) {
        jsize saveLen = env->GetArrayLength(saveArray);
        if (saveLen > 0) {
            jbyte* saveBytes = env->GetByteArrayElements(saveArray, nullptr);
            if (saveBytes) {
                cartArgs.SRAM = std::make_unique<melonDS::u8[]>(static_cast<size_t>(saveLen));
                memcpy(cartArgs.SRAM.get(), saveBytes, static_cast<size_t>(saveLen));
                cartArgs.SRAMLength = static_cast<melonDS::u32>(saveLen);
                env->ReleaseByteArrayElements(saveArray, saveBytes, JNI_ABORT);
            }
        }
    }

    // ParseROM copia el contenido de la ROM a su propio búfer: `romData` se
    // puede liberar (o desmapear) en cuanto vuelve. `emu` viaja como
    // userdata: WriteNDSSave lo recibirá tal cual (ver esa función más
    // arriba).
    auto cart = melonDS::NDSCart::ParseROM(romData, romLen, emu, std::move(cartArgs));
    if (!cart) return kLoadInvalidRom;

    // `emu` también viaja como userdata del propio NDS (mismo puntero que el
    // del cartucho, igual que hace el frontend de referencia): es lo que
    // Mic_ReadInput recibe para saber si el soplido está activo.
    emu->nds = std::make_unique<melonDS::NDS>(MakeArgs(emu->audioSampleRate), emu);
    emu->nds->NDSCartSlot.SetCart(std::move(cart));
    emu->nds->Reset();

    if (emu->nds->NeedsDirectBoot()) {
        // La sobrecarga sin argumentos SOLO prepara la CPU (registros,
        // memoria, copia del logo); es NDSCartSlot::SetupDirectBoot() —que
        // esta sobrecarga con nombre de ROM también invoca— quien pasa el
        // cartucho a modo de cifrado KEY2. Sin eso, cualquier lectura de ROM
        // después de la cabecera inicial (overlays, gráficos, sonido) vuelve
        // con datos basura: el juego se queda literalmente a ciegas después
        // de arrancar. El nombre en sí sólo importa para el modo SD de
        // cartuchos homebrew (vía FAT virtual); aquí no hay ninguno, así que
        // cualquier literal sirve.
        emu->nds->SetupDirectBoot("rom.nds");
    }

    // Sin esto, NDS::Running se queda en el valor indeterminado con el que
    // se construye el objeto (no tiene inicializador de miembro, y Reset()
    // tampoco lo toca): sólo NDS::Start() lo pone a true. RunFrame() entero
    // es un `while (Running) { ... }` — con Running en false ese bucle no
    // ejecuta ni un ciclo de CPU, GPU::FinishFrame() no se llama nunca (así
    // que FrontBuffer no alterna y el framebuffer se queda en el blanco de
    // GPU::Reset()), y aun así NumFrames++ sigue contando fuera del bucle
    // (el propio melonDS lo hace a propósito, para TAS) y RunFrame()
    // devuelve 263. El resultado es una emulación que desde fuera parece
    // sana —60 fps constantes, sin caídas— y que en realidad no ejecuta
    // nada. Orden correcto, el del frontend de referencia: Reset() ->
    // SetupDirectBoot() -> Start().
    emu->nds->Start();

    // El renderizador 3D por software arranca en modo de un solo hilo
    // (GPU3D_Soft.h). NDSArgs::Renderer3D ya trae un SoftRenderer por
    // defecto (Args.h), así que basta con pedirle el hilo aparte: el propio
    // SoftRenderer crea y destruye ese hilo con Platform::Thread_Create/
    // Thread_Free, que en este puente hacen join — se detiene solo, sin
    // trabajo adicional en nativeUnload, cuando el unique_ptr de NDS/GPU se
    // destruye.
    static_cast<melonDS::SoftRenderer&>(emu->nds->GPU.GetRenderer3D())
        .SetThreaded(true, emu->nds->GPU);

    emu->batteryDirty.store(false, std::memory_order_relaxed);
    emu->loaded = true;
    return kLoadOk;
}

}  // namespace

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jbyteArray romArray, jbyteArray saveArray) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !romArray) return kLoadFailed;

    jsize romLen = env->GetArrayLength(romArray);
    if (romLen <= 0) return kLoadInvalidRom;

    jbyte* romBytes = env->GetByteArrayElements(romArray, nullptr);
    if (!romBytes) return kLoadFailed;

    jint result = FinishLoad(
        emu, reinterpret_cast<const melonDS::u8*>(romBytes), static_cast<melonDS::u32>(romLen),
        env, saveArray);
    env->ReleaseByteArrayElements(romArray, romBytes, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeLoadFd(
    JNIEnv* env, jobject, jlong handle, jint fd, jlong length, jbyteArray saveArray) {
    Emulator* emu = FromHandle(handle);
    // Este puente es el único dueño de `fd` desde aquí: Kotlin lo entrega con
    // ParcelFileDescriptor.detachFd() precisamente para no cerrarlo dos veces.
    // Cualquier salida de esta función tiene que cerrarlo.
    if (!emu || fd < 0 || length <= 0) {
        if (fd >= 0) close(fd);
        return kLoadFailed;
    }

    // MAP_PRIVATE: copy-on-write de sólo lectura. No se escribe nunca en este
    // mapeo (ParseROM sólo lee), así que no hace falta pensar en qué pasaría
    // si el archivo cambiara por debajo durante la partida.
    void* mapped = mmap(
        nullptr, static_cast<size_t>(length), PROT_READ, MAP_PRIVATE, fd, 0);
    if (mapped == MAP_FAILED) {
        LOGE("mmap falló para la ROM (%lld bytes): %s", static_cast<long long>(length),
             strerror(errno));
        close(fd);
        return kLoadFailed;
    }

    jint result = FinishLoad(
        emu, static_cast<const melonDS::u8*>(mapped), static_cast<melonDS::u32>(length), env,
        saveArray);

    // ParseROM ya copió lo que necesitaba a su propio búfer (sección 7 de
    // integracion-core.md): el mapeo no hace falta conservarlo más allá de
    // esta llamada.
    munmap(mapped, static_cast<size_t>(length));
    close(fd);
    return result;
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeUnload(
    JNIEnv*, jobject, jlong handle) {
    Emulator* emu = FromHandle(handle);
    if (emu) {
        emu->nds.reset();
        emu->loaded = false;
    }
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeRunFrame(
    JNIEnv*, jobject, jlong handle) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return;

    emu->nds->RunFrame();

    // Vaciar el SPU aquí, en el hilo de emulación, y nunca desde el callback
    // de AAudio: su búfer de salida no es thread-safe frente a un lector
    // concurrente (son índices simples, no atómicos). El callback sólo toca
    // el anillo propio del puente.
    melonDS::SPU& spu = emu->nds->SPU;
    int available = spu.GetOutputSize();
    if (available > 0) {
        if (available > kAudioDrainMax) available = kAudioDrainMax;
        int16_t buffer[kAudioDrainMax * 2];
        int got = spu.ReadOutput(buffer, available);
        if (got > 0) RingPush(emu, buffer, static_cast<unsigned>(got));
    }
}

JNIEXPORT jintArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCaptureFrame(
    JNIEnv* env, jobject, jlong handle) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return nullptr;

    melonDS::GPU& gpu = emu->nds->GPU;
    int front = gpu.FrontBuffer;
    const melonDS::u32* top = gpu.Framebuffer[front][0].get();
    const melonDS::u32* bottom = gpu.Framebuffer[front][1].get();
    if (!top || !bottom) return nullptr;

    // 256×384: la pantalla superior en las filas 0-191, la inferior en las
    // 192-383 (ver EmulatorCore.captureFrame). No es zero-copy: melonDS es
    // dueño de su propio framebuffer de doble búfer y aquí sólo se copia.
    jintArray result = env->NewIntArray(kScreenPixels * 2);
    if (!result) return nullptr;

    std::unique_ptr<jint[]> pixels(new jint[kScreenPixels * 2]);
    for (int i = 0; i < kScreenPixels; ++i) {
        pixels[i] = static_cast<jint>(ToAndroidArgb(top[i]));
        pixels[kScreenPixels + i] = static_cast<jint>(ToAndroidArgb(bottom[i]));
    }
    env->SetIntArrayRegion(result, 0, kScreenPixels * 2, pixels.get());
    return result;
}

// Camino caliente: una llamada por fotograma desde EmulatorController, en el
// hilo de emulación. A diferencia de nativeCaptureFrame, no reserva nada: el
// `ByteBuffer` directo lo asigna Kotlin una sola vez (ver
// EmulatorCore.captureFrameInto) y aquí sólo se escribe dentro, con el color
// ya convertido en el mismo recorrido. El nativeCaptureFrame de arriba
// hacía, 60 veces por segundo, un NewIntArray más una copia intermedia a un
// unique_ptr: ~90 MB/s de basura en el heap de Java, y candidato serio a ser
// la causa real del OutOfMemoryError de la fase 4.C (ver
// docs/estabilizacion-core.md).
JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCaptureFrameInto(
    JNIEnv* env, jobject, jlong handle, jobject buffer) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return JNI_FALSE;

    melonDS::GPU& gpu = emu->nds->GPU;
    int front = gpu.FrontBuffer;
    const melonDS::u32* top = gpu.Framebuffer[front][0].get();
    const melonDS::u32* bottom = gpu.Framebuffer[front][1].get();
    if (!top || !bottom) return JNI_FALSE;

    auto* out = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (!out || capacity < static_cast<jlong>(kScreenPixels) * 2 * 4) return JNI_FALSE;

    for (int i = 0; i < kScreenPixels; ++i) {
        WriteRgbaBytes(top[i], out + i * 4);
        WriteRgbaBytes(bottom[i], out + (kScreenPixels + i) * 4);
    }
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetKeys(
    JNIEnv*, jobject, jlong handle, jint mask) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return;
    // DsButton.bit ya está en el orden que espera NDS::SetKeyMask (copiado de
    // EmuInstance::buttonNames, ver DsButton.kt); aquí sólo cambia la
    // polaridad: en Kotlin 1 = pulsado, pero KEYINPUT/EXTKEYIN son activos a
    // nivel bajo (0 = pulsado).
    melonDS::u32 coreMask = (~static_cast<melonDS::u32>(mask)) & 0xFFFu;
    emu->nds->SetKeyMask(coreMask);
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetTouch(
    JNIEnv*, jobject, jlong handle, jint x, jint y, jboolean down) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return;
    if (down) {
        emu->nds->TouchScreen(static_cast<melonDS::u16>(x), static_cast<melonDS::u16>(y));
    } else {
        emu->nds->ReleaseScreen();
    }
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetMicNoise(
    JNIEnv*, jobject, jlong handle, jboolean active) {
    Emulator* emu = FromHandle(handle);
    if (emu) emu->micNoiseActive.store(active == JNI_TRUE, std::memory_order_relaxed);
}

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeStateSize(
    JNIEnv*, jobject, jlong handle) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return 0;

    // melonDS no tiene un tamaño de estado fijo por versión de core (a
    // diferencia de mGBA): Savestate crece según lo que de verdad haga falta
    // serializar. La única forma honesta de responder «cuánto ocupa ahora
    // mismo» es serializar de verdad y medir; no es gratis, pero se llama una
    // vez al cargar la ROM (ver StateIdentity en GameViewModel), no cada
    // fotograma.
    melonDS::Savestate state(melonDS::Savestate::DEFAULT_SIZE);
    if (state.Error) return 0;
    if (!emu->nds->DoSavestate(&state) || state.Error) return 0;
    return static_cast<jint>(state.Length());
}

JNIEXPORT jbyteArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSerializeState(
    JNIEnv* env, jobject, jlong handle) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return nullptr;

    melonDS::Savestate state(melonDS::Savestate::DEFAULT_SIZE);
    if (state.Error) return nullptr;
    if (!emu->nds->DoSavestate(&state) || state.Error) return nullptr;

    jsize len = static_cast<jsize>(state.Length());
    jbyteArray result = env->NewByteArray(len);
    if (!result) return nullptr;
    env->SetByteArrayRegion(result, 0, len, reinterpret_cast<const jbyte*>(state.Buffer()));
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeDeserializeState(
    JNIEnv* env, jobject, jlong handle, jbyteArray stateArray) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded || !stateArray) return JNI_FALSE;

    jsize len = env->GetArrayLength(stateArray);
    if (len <= 0) return JNI_FALSE;

    jbyte* bytes = env->GetByteArrayElements(stateArray, nullptr);
    if (!bytes) return JNI_FALSE;

    // El constructor (buffer, size, false) envuelve estos bytes sin copiarlos
    // ni quedarse con la propiedad (buffer_owned=false en Savestate.cpp): el
    // puntero sólo tiene que vivir durante DoSavestate, que es exactamente lo
    // que dura este bloque.
    melonDS::Savestate state(bytes, static_cast<melonDS::u32>(len), false);
    bool ok = !state.Error && emu->nds->DoSavestate(&state) && !state.Error;
    env->ReleaseByteArrayElements(stateArray, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeReadBattery(
    JNIEnv* env, jobject, jlong handle) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !emu->loaded) return nullptr;

    // Siempre en vivo desde el búfer que posee el cartucho: no se mantiene
    // copia propia en el puente, así que no hay dos versiones que puedan
    // desincronizarse.
    const melonDS::u8* save = emu->nds->GetNDSSave();
    melonDS::u32 len = emu->nds->GetNDSSaveLength();
    if (!save || len == 0) return nullptr;

    jbyteArray result = env->NewByteArray(static_cast<jsize>(len));
    if (!result) return nullptr;
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(len),
                             reinterpret_cast<const jbyte*>(save));
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeConsumeBatteryDirty(
    JNIEnv*, jobject, jlong handle) {
    Emulator* emu = FromHandle(handle);
    if (!emu) return JNI_FALSE;
    return emu->batteryDirty.exchange(false, std::memory_order_relaxed) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jobjectArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeApplyCheats(
    JNIEnv* env, jobject, jlong handle, jobjectArray linesArray, jbooleanArray enabledArray) {
    Emulator* emu = FromHandle(handle);
    if (!emu) return nullptr;

    jsize count = env->GetArrayLength(linesArray);
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(count, stringClass, env->NewStringUTF(""));
    if (!result) return nullptr;

    jboolean* enabledElems = env->GetBooleanArrayElements(enabledArray, nullptr);

    // Se reconstruye entero cada vez -nunca una actualización incremental de
    // AREngine.Cheats-, igual que hace el propio EmuInstance de melonDS al
    // cargar un archivo de claves nuevo.
    std::vector<melonDS::ARCode> newCheats;
    newCheats.reserve(static_cast<size_t>(count));

    for (jsize i = 0; i < count; ++i) {
        auto lineObj = static_cast<jstring>(env->GetObjectArrayElement(linesArray, i));
        const char* utf = env->GetStringUTFChars(lineObj, nullptr);
        std::string joinedLines(utf);
        env->ReleaseStringUTFChars(lineObj, utf);
        env->DeleteLocalRef(lineObj);

        std::vector<melonDS::u32> code;
        std::string rejection;
        ParseArCodeLines(joinedLines, &code, &rejection);

        if (rejection.empty()) {
            melonDS::ARCode arCode{};
            arCode.Enabled = enabledElems[i] == JNI_TRUE;
            arCode.Code = std::move(code);
            newCheats.push_back(std::move(arCode));
        } else {
            env->SetObjectArrayElement(result, i, env->NewStringUTF(rejection.c_str()));
        }
    }

    env->ReleaseBooleanArrayElements(enabledArray, enabledElems, JNI_ABORT);

    if (emu->loaded) emu->nds->AREngine.Cheats = std::move(newCheats);

    return result;
}

JNIEXPORT jstring JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCoreVersion(
    JNIEnv* env, jobject) {
    return env->NewStringUTF(BRIDGE_CORE_VERSION);
}

}  // extern "C"
