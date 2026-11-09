/*
 * ds_core: melonDS detrás de la API en C de ds_core.h.
 *
 * Copyright (c) 2026 DsForEveryone.
 *
 * melonDS se consume sin modificar desde third_party/melonDS y conserva su
 * propia licencia (GPL-3.0); ver docs/integracion-core.md.
 *
 * Aquí no hay nada de ninguna plataforma: ni JNI, ni AAudio, ni UIKit. Era la
 * mitad de native/emulator_bridge/ds_bridge.cpp, separada en la fase iOS-1
 * (docs/ios.md) para que Android e iOS compartan exactamente el mismo
 * puente con el core. Dos papeles:
 *
 * - Implementa la interfaz melonDS::Platform que el core necesita para
 *   enlazar (archivos, hilos, mutex/semáforo, log, micrófono...).
 * - Expone ds_core.h: cargar, correr fotogramas, entrada, audio, save
 *   states, batería y trucos.
 *
 * El audio usa el mismo anillo sin cerrojos que `app-emulador`: el hilo de
 * emulación empuja en ds_run_frame y el hilo de audio de la plataforma saca
 * con ds_audio_read. Sigue vigente la misma limitación heredada: sin
 * sincronización por audio, el ritmo lo marca el reloj de la emulación.
 */

#include "ds_core.h"

#ifdef __ANDROID__
#include <android/log.h>
#endif

#include <atomic>
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
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

#include <NDS.h>
#include <NDSCart.h>
#include <GPU.h>
#include <GPU3D_Soft.h>
#include <Platform.h>
#include <SPU.h>
#include <Savestate.h>
#include <AREngine.h>
#include <ARCodeFile.h>
// La grabación de un soplido real que usa el frontend de referencia de
// melonDS (frontend/, mismo árbol GPL). Ver Mic_ReadInput.
#include <frontend/mic_blow.h>

#ifdef __ANDROID__
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "ds-core", __VA_ARGS__)
#else
#define LOGI(...) (fprintf(stderr, "[ds-core] " __VA_ARGS__), fputc('\n', stderr))
#endif

/*
 * Versión del core empaquetado en third_party/melonDS, fijada a mano.
 *
 * melonDS 1.1 no tiene el problema de app-emulador con mGBA (version.cmake
 * escapándose a un `.git` ajeno: ver docs/integracion-core.md, sección 6),
 * pero el principio se mantiene igual: lo que se graba en cada save state es
 * esta constante, nunca un valor generado por el sistema de compilación.
 * La tarea verifyCoreVersion de app/build.gradle.kts la busca en el objeto
 * compilado de este archivo.
 */
static const char* const BRIDGE_CORE_VERSION = "1.1";

namespace {

constexpr int kScreenPixels = DS_SCREEN_WIDTH * DS_SCREEN_HEIGHT;

// Anillo de audio sin cerrojos, mismo diseño que app-emulador (mgba_bridge.c):
// un productor (el hilo de emulación, en ds_run_frame) y un consumidor (el
// hilo de audio de la plataforma, en ds_audio_read), potencia de dos para
// recortar el índice con una máscara. 16384 fotogramas estéreo son ~0,5 s a
// 32768 Hz: de sobra para absorber que el hilo de emulación entregue a
// ráfagas de un fotograma.
constexpr unsigned kAudioRingFrames = 16384u;
constexpr unsigned kAudioRingMask = kAudioRingFrames - 1u;
constexpr int kAudioDrainMax = 4096;

}  // namespace

struct DsEmulator {
    std::unique_ptr<melonDS::NDS> nds;
    bool loaded = false;

    std::atomic<bool> micNoiseActive{false};
    // Por dónde va la grabación del soplido. Sólo la toca Mic_ReadInput, que
    // corre dentro de RunFrame en el hilo de emulación: no necesita ser atómico.
    size_t micBlowReadPos = 0;
    std::atomic<bool> batteryDirty{false};

    // Avance rápido: a 1,5x o 2x el SPU produce muestras más deprisa de lo
    // que la salida de audio las consume, así que se siguen vaciando del SPU
    // pero no se empujan al anillo. Ver ds_run_frame y
    // EmulatorCore.setAudioMuted.
    std::atomic<bool> audioMuted{false};

    std::unique_ptr<int16_t[]> ring{new int16_t[kAudioRingFrames * 2]};
    std::atomic<unsigned> ringRead{0};
    std::atomic<unsigned> ringWrite{0};
    double audioSampleRate = DS_AUDIO_SAMPLE_RATE_HINT;
};

namespace {

melonDS::NDSArgs MakeArgs(double outputSampleRate) {
    // Sin argumentos explícitos, NDSArgs ya trae FreeBIOS (ARM9BIOS/ARM7BIOS
    // por defecto) y firmware generado (Firmware{0}): arranca sin que el
    // usuario tenga que aportar BIOS ni firmware reales. Los archivos de
    // sistema de verdad, si hacen falta, llegan en la fase 4.5.
    melonDS::NDSArgs args{};
    // JITArgs por defecto (src/Args.h). En las compilaciones sin JIT —iOS, que
    // no lo permite— melonDS ignora este campo (Args.h).
    //
    // JITArgs por defecto (src/Args.h): bloques de hasta 32 instrucciones,
    // optimización de literales y de saltos activas, y FastMemory activa (usa
    // manejadores de SIGSEGV para las lecturas/escrituras de memoria emulada
    // en vez de comprobar límites en cada acceso). Si FastMemory choca con los
    // manejadores de señales de ART en algún dispositivo, es el primer valor a
    // poner en `false` antes de renunciar al JIT entero.
    args.JIT = melonDS::JITArgs();
    args.GDB = std::nullopt;  // ENABLE_GDBSTUB=OFF: sin socket de depuración.
    // El SPU resamplea internamente de 32768 Hz a esta frecuencia; hay que
    // decirle la que de verdad entrega la salida de audio (puede no ser la pedida), no la
    // nominal de la DS.
    args.OutputSampleRate = outputSampleRate;
    return args;
}

// Productor: el hilo de emulación, una vez por fotograma (ver ds_run_frame).
void RingPush(DsEmulator* emu, const int16_t* frames, unsigned count) {
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

// Consumidor: el hilo de la salida de audio (ver ds_audio_read).
unsigned RingPop(DsEmulator* emu, int16_t* out, unsigned count) {
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

/**
 * Descarta lo que quede en el anillo. Lo llama SIEMPRE el consumidor (el
 * hilo de audio, en ds_audio_read), que es el dueño de ringRead: adelantar
 * ese índice desde el hilo de emulación sería una carrera con el propio
 * consumidor.
 */
void RingDiscard(DsEmulator* emu) {
    const unsigned write = emu->ringWrite.load(std::memory_order_acquire);
    emu->ringRead.store(write, std::memory_order_release);
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
inline uint32_t ToArgb(uint32_t pixel) {
    uint32_t b = pixel & 0xFFu;
    uint32_t g = (pixel >> 8) & 0xFFu;
    uint32_t r = (pixel >> 16) & 0xFFu;
    return 0xFF000000u | (r << 16) | (g << 8) | b;
}

// Mismo canje de canales que ToArgb, pero escrito directamente como
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
    va_list args;
    va_start(args, fmt);
#ifdef __ANDROID__
    int prio = ANDROID_LOG_INFO;
    switch (level) {
        case Debug: prio = ANDROID_LOG_DEBUG; break;
        case Info: prio = ANDROID_LOG_INFO; break;
        case Warn: prio = ANDROID_LOG_WARN; break;
        case Error: prio = ANDROID_LOG_ERROR; break;
    }
    __android_log_vprint(prio, "melonDS", fmt, args);
#else
    // Fuera de Android, a stderr: Xcode lo enseña en su consola.
    if (level != Debug) {
        fputs("[melonDS] ", stderr);
        vfprintf(stderr, fmt, args);
    }
#endif
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
    // `userdata` es el mismo DsEmulator* que se pasó a NDSCart::ParseROM: sólo
    // marca la bandera. El contenido en sí se lee siempre en vivo desde
    // NDS::GetNDSSave() (ver ds_read_battery), nunca se copia aquí.
    if (auto* emu = static_cast<::DsEmulator*>(userdata)) {
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
    auto* emu = static_cast<::DsEmulator*>(userdata);
    if (maxlength <= 0) return 0;

    // Sin soplido se entrega silencio, no «nada». Devolver 0 deja el búfer
    // del micrófono del core sin rellenar y su última muestra congelada: si
    // era de un soplido, el juego seguiría oyéndolo después de soltar.
    if (!emu || !emu->micNoiseActive.load(std::memory_order_relaxed)) {
        if (emu) emu->micBlowReadPos = 0;
        memset(data, 0, static_cast<size_t>(maxlength) * sizeof(s16));
        return maxlength;
    }

    // Con soplido, la grabación de un soplido de verdad en bucle, igual que
    // el frontend de referencia. Ruido blanco aleatorio no vale: hay juegos
    // —Zelda: Phantom Hourglass y sus velas, por ejemplo— que no lo
    // reconocen como soplido, y éste sí.
    constexpr size_t kBlowLength = sizeof(mic_blow) / sizeof(mic_blow[0]);
    for (int i = 0; i < maxlength; ++i) {
        data[i] = mic_blow[emu->micBlowReadPos];
        if (++emu->micBlowReadPos >= kBlowLength) emu->micBlowReadPos = 0;
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
// `rejection` en -1 si todo fue bien, o con la línea (0-based) que no
// tuvo esa forma; en ese caso `code` queda a medio construir y quien llama no
// debe usarlo.
static void ParseArCodeLines(const std::string& joinedLines, std::vector<melonDS::u32>* code,
                       int32_t* rejection) {
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
                *rejection = static_cast<int32_t>(lineIndex);
                return;
            }
            code->push_back(static_cast<melonDS::u32>(c0));
            code->push_back(static_cast<melonDS::u32>(c1));
        }
        if (newline == std::string::npos) break;
        start = newline + 1;
        ++lineIndex;
    }
    if (code->empty()) *rejection = 0;  // sin ninguna línea con contenido: nada que aplicar
}

// ---------------------------------------------------------------------------
// ds_core.h
// ---------------------------------------------------------------------------
extern "C" {

const char* ds_core_version(void) {
    return BRIDGE_CORE_VERSION;
}

DsEmulator* ds_create(void) {
    return new DsEmulator();
}

void ds_destroy(DsEmulator* emu) {
    delete emu;
}

void ds_set_output_sample_rate(DsEmulator* emu, double hz) {
    if (emu && hz > 0) emu->audioSampleRate = hz;
}

int ds_load(DsEmulator* emu, const uint8_t* romData, uint32_t romLen,
            const uint8_t* save, uint32_t saveLen) {
    if (!emu || !romData) return DS_LOAD_FAILED;
    if (romLen == 0) return DS_LOAD_INVALID_ROM;

    // Si hay partida de batería previa, se entrega en memoria al construir el
    // cartucho: el core la lee ya con el tamaño que él mismo detecte como el
    // real (ver docs/integracion-core.md, sección 7 — "algunos juegos no
    // declaran bien su tipo" es un problema del propio formato, no de este
    // puente).
    melonDS::NDSCart::NDSCartArgs cartArgs{};
    if (save && saveLen > 0) {
        cartArgs.SRAM = std::make_unique<melonDS::u8[]>(saveLen);
        memcpy(cartArgs.SRAM.get(), save, saveLen);
        cartArgs.SRAMLength = saveLen;
    }

    // ParseROM copia el contenido de la ROM a su propio búfer: `romData` se
    // puede liberar (o desmapear) en cuanto vuelve. `emu` viaja como
    // userdata: WriteNDSSave lo recibirá tal cual (ver esa función más
    // arriba).
    auto cart = melonDS::NDSCart::ParseROM(romData, romLen, emu, std::move(cartArgs));
    if (!cart) return DS_LOAD_INVALID_ROM;

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
    // trabajo adicional en ds_unload, cuando el unique_ptr de NDS/GPU se
    // destruye.
    static_cast<melonDS::SoftRenderer&>(emu->nds->GPU.GetRenderer3D())
        .SetThreaded(true, emu->nds->GPU);

    emu->batteryDirty.store(false, std::memory_order_relaxed);
    emu->loaded = true;
    return DS_LOAD_OK;
}

void ds_unload(DsEmulator* emu) {
    if (emu) {
        emu->nds.reset();
        emu->loaded = false;
    }
}

void ds_run_frame(DsEmulator* emu) {
    if (!emu || !emu->loaded) return;

    emu->nds->RunFrame();

    // Vaciar el SPU aquí, en el hilo de emulación, y nunca desde el hilo de
    // audio: su búfer de salida no es thread-safe frente a un lector
    // concurrente (son índices simples, no atómicos). ds_audio_read sólo toca
    // el anillo propio de este archivo.
    melonDS::SPU& spu = emu->nds->SPU;
    int available = spu.GetOutputSize();
    if (available > 0) {
        if (available > kAudioDrainMax) available = kAudioDrainMax;
        int16_t buffer[kAudioDrainMax * 2];
        int got = spu.ReadOutput(buffer, available);
        // Silenciado (avance rápido): se vacía el SPU igual —su búfer de
        // salida hay que consumirlo, o se llena y descarta él por su cuenta—
        // pero no se entrega nada al anillo. El resultado es silencio limpio,
        // no audio picado.
        if (got > 0 && !emu->audioMuted.load(std::memory_order_relaxed)) {
            RingPush(emu, buffer, static_cast<unsigned>(got));
        }
    }
}

// Camino caliente: una vez por fotograma, en el hilo de emulación, sin
// reservar nada. melonDS es dueño de su propio framebuffer de doble búfer y
// aquí sólo se copia, con el color ya convertido en el mismo recorrido.
bool ds_copy_frame_rgba(DsEmulator* emu, uint8_t* out, size_t capacity) {
    if (!emu || !emu->loaded || !out) return false;
    if (capacity < static_cast<size_t>(kScreenPixels) * 2 * 4) return false;

    melonDS::GPU& gpu = emu->nds->GPU;
    int front = gpu.FrontBuffer;
    const melonDS::u32* top = gpu.Framebuffer[front][0].get();
    const melonDS::u32* bottom = gpu.Framebuffer[front][1].get();
    if (!top || !bottom) return false;

    for (int i = 0; i < kScreenPixels; ++i) {
        WriteRgbaBytes(top[i], out + i * 4);
        WriteRgbaBytes(bottom[i], out + (kScreenPixels + i) * 4);
    }
    return true;
}

bool ds_copy_frame_argb(DsEmulator* emu, uint32_t* out, size_t count) {
    if (!emu || !emu->loaded || !out) return false;
    if (count < static_cast<size_t>(kScreenPixels) * 2) return false;

    melonDS::GPU& gpu = emu->nds->GPU;
    int front = gpu.FrontBuffer;
    const melonDS::u32* top = gpu.Framebuffer[front][0].get();
    const melonDS::u32* bottom = gpu.Framebuffer[front][1].get();
    if (!top || !bottom) return false;

    // 256×384: la pantalla superior en las filas 0-191, la inferior en las
    // 192-383 (ver EmulatorCore.captureFrame).
    for (int i = 0; i < kScreenPixels; ++i) {
        out[i] = ToArgb(top[i]);
        out[kScreenPixels + i] = ToArgb(bottom[i]);
    }
    return true;
}

void ds_set_keys(DsEmulator* emu, uint32_t mask) {
    if (!emu || !emu->loaded) return;
    // DsButton.bit ya está en el orden que espera NDS::SetKeyMask (copiado de
    // EmuInstance::buttonNames, ver DsButton.kt); aquí sólo cambia la
    // polaridad: arriba 1 = pulsado, pero KEYINPUT/EXTKEYIN son activos a
    // nivel bajo (0 = pulsado).
    melonDS::u32 coreMask = (~static_cast<melonDS::u32>(mask)) & 0xFFFu;
    emu->nds->SetKeyMask(coreMask);
}

void ds_set_touch(DsEmulator* emu, int x, int y, bool down) {
    if (!emu || !emu->loaded) return;
    if (down) {
        emu->nds->TouchScreen(static_cast<melonDS::u16>(x), static_cast<melonDS::u16>(y));
    } else {
        emu->nds->ReleaseScreen();
    }
}

void ds_set_mic_noise(DsEmulator* emu, bool active) {
    if (emu) emu->micNoiseActive.store(active, std::memory_order_relaxed);
}

void ds_set_lid_closed(DsEmulator* emu, bool closed) {
    if (!emu || !emu->loaded) return;

    // Ojo con llamar a esto de más: SetLidClosed(false) no sólo baja el bit,
    // dispara IRQ_LidOpen cada vez (NDS.cpp). Quien llama —el hilo de
    // emulación— sólo lo hace cuando el estado cambia de verdad.
    emu->nds->SetLidClosed(closed);
}

void ds_set_audio_muted(DsEmulator* emu, bool muted) {
    if (emu) emu->audioMuted.store(muted, std::memory_order_relaxed);
}

uint32_t ds_audio_read(DsEmulator* emu, int16_t* out, uint32_t frames) {
    if (!out || frames == 0) return 0;
    if (!emu) {
        memset(out, 0, static_cast<size_t>(frames) * 2 * sizeof(int16_t));
        return 0;
    }

    // Silenciado (avance rápido, o partida en pausa): silencio inmediato, y
    // se tira lo que quedaba pendiente. Sin esto sonaría el medio segundo
    // que ya estaba en el anillo —al salir de la aplicación, la música
    // seguiría un instante— y ese resto volvería a sonar, viejo, al reanudar.
    if (emu->audioMuted.load(std::memory_order_relaxed)) {
        RingDiscard(emu);
        memset(out, 0, static_cast<size_t>(frames) * 2 * sizeof(int16_t));
        return 0;
    }

    unsigned got = RingPop(emu, out, frames);
    if (got < frames) {
        // El anillo se vacía si la emulación no ha entregado nada todavía o
        // no llega a tiempo: se completa con silencio en vez de repetir la
        // última muestra o dejar memoria sin inicializar.
        memset(out + static_cast<size_t>(got) * 2, 0,
               (static_cast<size_t>(frames) - got) * 2 * sizeof(int16_t));
    }
    return got;
}

uint32_t ds_state_size(DsEmulator* emu) {
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
    return state.Length();
}

bool ds_serialize_state(DsEmulator* emu, DsByteSink sink, void* context) {
    if (!emu || !emu->loaded || !sink) return false;

    melonDS::Savestate state(melonDS::Savestate::DEFAULT_SIZE);
    if (state.Error) return false;
    if (!emu->nds->DoSavestate(&state) || state.Error) return false;

    sink(static_cast<const uint8_t*>(state.Buffer()), state.Length(), context);
    return true;
}

bool ds_deserialize_state(DsEmulator* emu, const uint8_t* bytes, size_t length) {
    if (!emu || !emu->loaded || !bytes || length == 0) return false;

    // El constructor (buffer, size, false) envuelve estos bytes sin copiarlos
    // ni quedarse con la propiedad (buffer_owned=false en Savestate.cpp): el
    // puntero sólo tiene que vivir durante DoSavestate, que es exactamente lo
    // que dura esta llamada. melonDS pide un puntero no constante, pero al
    // cargar sólo lee.
    melonDS::Savestate state(const_cast<uint8_t*>(bytes), static_cast<melonDS::u32>(length), false);
    return !state.Error && emu->nds->DoSavestate(&state) && !state.Error;
}

bool ds_read_battery(DsEmulator* emu, DsByteSink sink, void* context) {
    if (!emu || !emu->loaded || !sink) return false;

    // Siempre en vivo desde el búfer que posee el cartucho: no se mantiene
    // copia propia en el puente, así que no hay dos versiones que puedan
    // desincronizarse.
    const melonDS::u8* save = emu->nds->GetNDSSave();
    melonDS::u32 len = emu->nds->GetNDSSaveLength();
    if (!save || len == 0) return false;

    sink(save, len, context);
    return true;
}

bool ds_consume_battery_dirty(DsEmulator* emu) {
    if (!emu) return false;
    return emu->batteryDirty.exchange(false, std::memory_order_relaxed);
}

void ds_apply_cheats(DsEmulator* emu, const char* const* codes, const bool* enabled,
                     size_t count, int32_t* rejectedLine) {
    if (!emu) return;

    // Se reconstruye entero cada vez -nunca una actualización incremental de
    // AREngine.Cheats-, igual que hace el propio EmuInstance de melonDS al
    // cargar un archivo de claves nuevo.
    std::vector<melonDS::ARCode> newCheats;
    newCheats.reserve(count);

    for (size_t i = 0; i < count; ++i) {
        std::vector<melonDS::u32> code;
        int32_t rejection = -1;
        ParseArCodeLines(codes[i] ? std::string(codes[i]) : std::string(), &code, &rejection);
        rejectedLine[i] = rejection;

        if (rejection < 0) {
            melonDS::ARCode arCode{};
            arCode.Enabled = enabled[i];
            arCode.Code = std::move(code);
            newCheats.push_back(std::move(arCode));
        }
    }

    if (emu->loaded) emu->nds->AREngine.Cheats = std::move(newCheats);
}

}  // extern "C"
