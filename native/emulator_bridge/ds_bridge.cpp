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
 * mutex/semáforo, log...) y expone las funciones nativas que Kotlin declara
 * en NativeBridge.kt.
 */

#include <jni.h>

#include <android/log.h>

#include <chrono>
#include <condition_variable>
#include <cstdarg>
#include <cstdio>
#include <ctime>
#include <memory>
#include <mutex>
#include <thread>
#include <unistd.h>

#include <NDS.h>
#include <NDSCart.h>
#include <GPU.h>
#include <Platform.h>

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

struct Emulator {
    std::unique_ptr<melonDS::NDS> nds;
    bool loaded = false;
};

Emulator* FromHandle(jlong handle) {
    return reinterpret_cast<Emulator*>(static_cast<intptr_t>(handle));
}

melonDS::NDSArgs MakeArgs() {
    // Sin argumentos explícitos, NDSArgs ya trae FreeBIOS (ARM9BIOS/ARM7BIOS
    // por defecto) y firmware generado (Firmware{0}): arranca sin que el
    // usuario tenga que aportar BIOS ni firmware reales. Los archivos de
    // sistema de verdad, si hacen falta, llegan en la fase 4.5.
    melonDS::NDSArgs args{};
    args.JIT = std::nullopt;  // ENABLE_JIT=OFF en esta compilación.
    args.GDB = std::nullopt;  // ENABLE_GDBSTUB=OFF: sin socket de depuración.
    return args;
}

/*
 * melonDS entrega cada píxel como r | (g<<8) | (b<<16), con el byte alto en
 * 0xFF: en memoria son cuatro bytes R,G,B,A. Android espera un entero
 * 0xAARRGGBB (el mismo formato que produce android.graphics.Color). Sin este
 * canje el rojo y el azul saldrían intercambiados en pantalla.
 */
inline uint32_t ToAndroidArgb(uint32_t pixel) {
    uint32_t r = pixel & 0xFFu;
    uint32_t g = (pixel >> 8) & 0xFFu;
    uint32_t b = (pixel >> 16) & 0xFFu;
    return 0xFF000000u | (r << 16) | (g << 8) | b;
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
// este proyecto y quedan como no-operación; la partida de batería y el
// micrófono se conectan de verdad en la fase 4.3.
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

void WriteNDSSave(const u8*, u32, u32, u32, void*) {
    // La partida de batería se conecta de verdad en la fase 4.3.
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

int Mic_ReadInput(s16*, int, void*) {
    // El botón de soplar se conecta de verdad en la fase 4.3.
    return 0;
}

AACDecoder* AAC_Init() { return nullptr; }
void AAC_DeInit(AACDecoder*) {}
bool AAC_Configure(AACDecoder*, int, int) { return false; }
bool AAC_DecodeFrame(AACDecoder*, const void*, int, void*, int) { return false; }

bool Addon_KeyDown(KeyType, void*) { return false; }
void Addon_RumbleStart(u32, void*) {}
void Addon_RumbleStop(void*) {}
float Addon_MotionQuery(MotionQueryType, void*) { return 0.0f; }

}  // namespace melonDS::Platform

// ---------------------------------------------------------------------------
// Funciones nativas: la frontera con NativeBridge.kt
// ---------------------------------------------------------------------------
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCreate(JNIEnv*, jobject) {
    auto* emu = new Emulator();
    return reinterpret_cast<jlong>(emu);
}

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jbyteArray romArray) {
    Emulator* emu = FromHandle(handle);
    if (!emu || !romArray) return kLoadFailed;

    jsize romLen = env->GetArrayLength(romArray);
    if (romLen <= 0) return kLoadInvalidRom;

    jbyte* romBytes = env->GetByteArrayElements(romArray, nullptr);
    if (!romBytes) return kLoadFailed;

    // ParseROM copia el contenido a su propio búfer: los bytes de Java se
    // pueden liberar en cuanto vuelve, sin que el core se quede con un
    // puntero a memoria que la JVM podría mover o recolectar.
    auto cart = melonDS::NDSCart::ParseROM(
        reinterpret_cast<const melonDS::u8*>(romBytes), static_cast<melonDS::u32>(romLen));
    env->ReleaseByteArrayElements(romArray, romBytes, JNI_ABORT);

    if (!cart) return kLoadInvalidRom;

    emu->nds = std::make_unique<melonDS::NDS>(MakeArgs());
    emu->nds->NDSCartSlot.SetCart(std::move(cart));
    emu->nds->Reset();

    if (emu->nds->NeedsDirectBoot()) {
        emu->nds->SetupDirectBoot();
    }

    emu->loaded = true;
    return kLoadOk;
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
    if (emu && emu->loaded) {
        emu->nds->RunFrame();
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

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetKeys(
    JNIEnv*, jobject, jlong handle, jint mask) {
    // La correspondencia real de bits (qué botón es cuál) se investiga y se
    // conecta en la fase 4.3; por ahora la entrada no llega al core.
    (void) handle;
    (void) mask;
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetTouch(
    JNIEnv*, jobject, jlong handle, jint x, jint y, jboolean down) {
    // El cableado real de la pantalla táctil llega en la fase 4.3.
    (void) handle;
    (void) x;
    (void) y;
    (void) down;
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetMicNoise(
    JNIEnv*, jobject, jlong handle, jboolean active) {
    // El ruido de soplido llega en la fase 4.3.
    (void) handle;
    (void) active;
}

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeStateSize(
    JNIEnv*, jobject, jlong handle) {
    // Los save states contra el core real llegan en la fase 4.4.
    (void) handle;
    return 0;
}

JNIEXPORT jbyteArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSerializeState(
    JNIEnv*, jobject, jlong handle) {
    (void) handle;
    return nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeDeserializeState(
    JNIEnv*, jobject, jlong handle, jbyteArray state) {
    (void) handle;
    (void) state;
    return JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeReadBattery(
    JNIEnv*, jobject, jlong handle) {
    // La partida de batería contra el core real llega en la fase 4.3.
    (void) handle;
    return nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeConsumeBatteryDirty(
    JNIEnv*, jobject, jlong handle) {
    (void) handle;
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCoreVersion(
    JNIEnv* env, jobject) {
    return env->NewStringUTF(BRIDGE_CORE_VERSION);
}

}  // extern "C"
