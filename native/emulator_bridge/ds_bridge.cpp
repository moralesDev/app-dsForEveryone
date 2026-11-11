/*
 * Puente JNI entre EmulatorCore (Kotlin) y ds_core.
 *
 * Copyright (c) 2026 DsForEveryone.
 *
 * melonDS se consume sin modificar desde third_party/melonDS y conserva su
 * propia licencia (GPL-3.0); ver docs/integracion-core.md.
 *
 * Desde la fase iOS-1 (docs/ios.md) este archivo sólo traduce JNI: cada
 * función nativa de NativeBridge.kt convierte sus argumentos y llama a la
 * misma función de ../ds_core/ds_core.h, que es donde vive todo lo que
 * toca a melonDS y que comparte la app de iOS. El audio de Android está en
 * audio_aaudio.cpp.
 *
 * Todas las llamadas llegan desde el hilo de emulación (ver el contrato de
 * hilos documentado en EmulatorCore.kt y en ds_core.h).
 */

#include <jni.h>

#include <android/log.h>

#include <cerrno>
#include <cstring>
#include <memory>
#include <string>
#include <sys/mman.h>
#include <unistd.h>
#include <vector>

#include "audio_aaudio.h"
#include "ds_core.h"

#define TAG "ds-bridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

namespace {

DsEmulator* FromHandle(jlong handle) {
    return reinterpret_cast<DsEmulator*>(static_cast<intptr_t>(handle));
}

// La partida de batería, de `jbyteArray` (o null) a la llamada de ds_load.
jint LoadWithSave(JNIEnv* env, DsEmulator* emu, const uint8_t* rom, uint32_t romLen,
                  jbyteArray saveArray) {
    jbyte* saveBytes = nullptr;
    jsize saveLen = 0;
    if (saveArray) {
        saveLen = env->GetArrayLength(saveArray);
        if (saveLen > 0) saveBytes = env->GetByteArrayElements(saveArray, nullptr);
    }
    jint result = ds_load(emu, rom, romLen, reinterpret_cast<const uint8_t*>(saveBytes),
                          saveBytes ? static_cast<uint32_t>(saveLen) : 0u);
    if (saveBytes) env->ReleaseByteArrayElements(saveArray, saveBytes, JNI_ABORT);
    return result;
}

// Destino de DsByteSink: copia los bytes a un `jbyteArray` nuevo.
struct ByteArraySink {
    JNIEnv* env;
    jbyteArray result = nullptr;
};

void SinkToByteArray(const uint8_t* data, size_t length, void* context) {
    auto* sink = static_cast<ByteArraySink*>(context);
    jsize len = static_cast<jsize>(length);
    sink->result = sink->env->NewByteArray(len);
    if (sink->result) {
        sink->env->SetByteArrayRegion(sink->result, 0, len, reinterpret_cast<const jbyte*>(data));
    }
}

}  // namespace

// ---------------------------------------------------------------------------
// Funciones nativas: la frontera con NativeBridge.kt
// ---------------------------------------------------------------------------
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCreate(JNIEnv*, jobject) {
    DsEmulator* emu = ds_create();
    OpenAAudio(emu);
    return reinterpret_cast<jlong>(emu);
}

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeLoad(
    JNIEnv* env, jobject, jlong handle, jbyteArray romArray, jbyteArray saveArray) {
    DsEmulator* emu = FromHandle(handle);
    if (!emu || !romArray) return DS_LOAD_FAILED;

    jsize romLen = env->GetArrayLength(romArray);
    if (romLen <= 0) return DS_LOAD_INVALID_ROM;

    jbyte* romBytes = env->GetByteArrayElements(romArray, nullptr);
    if (!romBytes) return DS_LOAD_FAILED;

    jint result = LoadWithSave(env, emu, reinterpret_cast<const uint8_t*>(romBytes),
                               static_cast<uint32_t>(romLen), saveArray);
    env->ReleaseByteArrayElements(romArray, romBytes, JNI_ABORT);
    return result;
}

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeLoadFd(
    JNIEnv* env, jobject, jlong handle, jint fd, jlong length, jbyteArray saveArray) {
    DsEmulator* emu = FromHandle(handle);
    // Este puente es el único dueño de `fd` desde aquí: Kotlin lo entrega con
    // ParcelFileDescriptor.detachFd() precisamente para no cerrarlo dos veces.
    // Cualquier salida de esta función tiene que cerrarlo.
    if (!emu || fd < 0 || length <= 0) {
        if (fd >= 0) close(fd);
        return DS_LOAD_FAILED;
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
        return DS_LOAD_FAILED;
    }

    jint result = LoadWithSave(env, emu, static_cast<const uint8_t*>(mapped),
                               static_cast<uint32_t>(length), saveArray);

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
    ds_unload(FromHandle(handle));
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeRunFrame(
    JNIEnv*, jobject, jlong handle) {
    ds_run_frame(FromHandle(handle));
}

JNIEXPORT jintArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCaptureFrame(
    JNIEnv* env, jobject, jlong handle) {
    // No es zero-copy, y reserva en cada llamada: sólo para miniaturas y
    // capturas sueltas. El camino de cada fotograma es nativeCaptureFrameInto.
    std::unique_ptr<uint32_t[]> pixels(new uint32_t[DS_FRAME_PIXELS]);
    if (!ds_copy_frame_argb(FromHandle(handle), pixels.get(), DS_FRAME_PIXELS)) return nullptr;

    jintArray result = env->NewIntArray(DS_FRAME_PIXELS);
    if (!result) return nullptr;
    env->SetIntArrayRegion(result, 0, DS_FRAME_PIXELS, reinterpret_cast<const jint*>(pixels.get()));
    return result;
}

// Camino caliente: una llamada por fotograma desde EmulatorController, en el
// hilo de emulación. A diferencia de nativeCaptureFrame, no reserva nada: el
// `ByteBuffer` directo lo asigna Kotlin una sola vez (ver
// EmulatorCore.captureFrameInto) y ds_copy_frame_rgba escribe dentro, con el
// color ya convertido en el mismo recorrido. Usar nativeCaptureFrame en cada
// fotograma eran ~90 MB/s de basura en el heap de Java, y candidato serio a
// ser la causa real del OutOfMemoryError de la fase 4.C (ver
// docs/estabilizacion-core.md).
JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCaptureFrameInto(
    JNIEnv* env, jobject, jlong handle, jobject buffer) {
    auto* out = static_cast<uint8_t*>(env->GetDirectBufferAddress(buffer));
    jlong capacity = env->GetDirectBufferCapacity(buffer);
    if (!out || capacity < 0) return JNI_FALSE;
    return ds_copy_frame_rgba(FromHandle(handle), out, static_cast<size_t>(capacity)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetKeys(
    JNIEnv*, jobject, jlong handle, jint mask) {
    ds_set_keys(FromHandle(handle), static_cast<uint32_t>(mask));
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetTouch(
    JNIEnv*, jobject, jlong handle, jint x, jint y, jboolean down) {
    ds_set_touch(FromHandle(handle), x, y, down == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetMicNoise(
    JNIEnv*, jobject, jlong handle, jboolean active) {
    ds_set_mic_noise(FromHandle(handle), active == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetLidClosed(
    JNIEnv*, jobject, jlong handle, jboolean closed) {
    ds_set_lid_closed(FromHandle(handle), closed == JNI_TRUE);
}

JNIEXPORT void JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSetAudioMuted(
    JNIEnv*, jobject, jlong handle, jboolean muted) {
    ds_set_audio_muted(FromHandle(handle), muted == JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeStateSize(
    JNIEnv*, jobject, jlong handle) {
    return static_cast<jint>(ds_state_size(FromHandle(handle)));
}

JNIEXPORT jbyteArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeSerializeState(
    JNIEnv* env, jobject, jlong handle) {
    ByteArraySink sink{env};
    if (!ds_serialize_state(FromHandle(handle), SinkToByteArray, &sink)) return nullptr;
    return sink.result;
}

JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeDeserializeState(
    JNIEnv* env, jobject, jlong handle, jbyteArray stateArray) {
    if (!stateArray) return JNI_FALSE;
    jsize len = env->GetArrayLength(stateArray);
    if (len <= 0) return JNI_FALSE;

    jbyte* bytes = env->GetByteArrayElements(stateArray, nullptr);
    if (!bytes) return JNI_FALSE;
    bool ok = ds_deserialize_state(FromHandle(handle), reinterpret_cast<const uint8_t*>(bytes),
                                   static_cast<size_t>(len));
    env->ReleaseByteArrayElements(stateArray, bytes, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeReadBattery(
    JNIEnv* env, jobject, jlong handle) {
    ByteArraySink sink{env};
    if (!ds_read_battery(FromHandle(handle), SinkToByteArray, &sink)) return nullptr;
    return sink.result;
}

JNIEXPORT jboolean JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeConsumeBatteryDirty(
    JNIEnv*, jobject, jlong handle) {
    return ds_consume_battery_dirty(FromHandle(handle)) ? JNI_TRUE : JNI_FALSE;
}

// Devuelve un String por truco: "" si se aceptó, o "L<n>" con la línea
// (desde 0) que no tenía la forma "XXXXXXXX YYYYYYYY". Es el formato que
// espera CheatsManager.
JNIEXPORT jobjectArray JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeApplyCheats(
    JNIEnv* env, jobject, jlong handle, jobjectArray linesArray, jbooleanArray enabledArray) {
    DsEmulator* emu = FromHandle(handle);
    if (!emu) return nullptr;

    jsize count = env->GetArrayLength(linesArray);
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(count, stringClass, env->NewStringUTF(""));
    if (!result) return nullptr;

    std::vector<std::string> codes(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto lineObj = static_cast<jstring>(env->GetObjectArrayElement(linesArray, i));
        const char* utf = env->GetStringUTFChars(lineObj, nullptr);
        codes[static_cast<size_t>(i)] = utf;
        env->ReleaseStringUTFChars(lineObj, utf);
        env->DeleteLocalRef(lineObj);
    }
    std::vector<const char*> codePointers(codes.size());
    for (size_t i = 0; i < codes.size(); ++i) codePointers[i] = codes[i].c_str();

    jboolean* enabledElems = env->GetBooleanArrayElements(enabledArray, nullptr);
    // std::vector<bool> no da un bool* contiguo: va en un array de verdad.
    std::unique_ptr<bool[]> enabled(new bool[codes.size()]);
    for (size_t i = 0; i < codes.size(); ++i) enabled[i] = enabledElems[i] == JNI_TRUE;
    env->ReleaseBooleanArrayElements(enabledArray, enabledElems, JNI_ABORT);

    std::vector<int32_t> rejected(codes.size(), -1);
    ds_apply_cheats(emu, codePointers.data(), enabled.get(), codes.size(), rejected.data());

    for (jsize i = 0; i < count; ++i) {
        int32_t line = rejected[static_cast<size_t>(i)];
        if (line >= 0) {
            std::string rejection = "L" + std::to_string(line);
            env->SetObjectArrayElement(result, i, env->NewStringUTF(rejection.c_str()));
        }
    }
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_moralesDev_dsForEveryone_emulator_core_NativeBridge_nativeCoreVersion(
    JNIEnv* env, jobject) {
    return env->NewStringUTF(ds_core_version());
}

}  // extern "C"
