/*
 * Salida de audio de Android (AAudio) para ds_core.
 *
 * Copyright (c) 2026 DsForEveryone. GPL-3.0, como melonDS (ver LICENSE).
 *
 * El anillo y su lógica —silencio al silenciar, relleno con silencio si la
 * emulación no llega— viven en ds_core (ds_audio_read), y son los mismos en
 * todas las plataformas. Aquí sólo queda lo propio de Android: abrir AAudio
 * y llamar a ds_audio_read desde su callback.
 */
#include "audio_aaudio.h"

#include <aaudio/AAudio.h>
#include <android/log.h>

#define TAG "ds-bridge"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)

namespace {

aaudio_data_callback_result_t AudioCallback(
    AAudioStream*, void* userData, void* audioData, int32_t numFrames) {
    if (numFrames > 0) {
        ds_audio_read(static_cast<DsEmulator*>(userData), static_cast<int16_t*>(audioData),
                      static_cast<uint32_t>(numFrames));
    }
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}

}  // namespace

// Se abre una sola vez, al crear el manejador: la frecuencia real que
// devuelve AAudio hace falta ANTES de construir el NDS (se le pasa como
// OutputSampleRate en NDSArgs), y el manejador vive lo que vive el proceso,
// así que el stream no se cierra nunca.
void OpenAAudio(DsEmulator* emu) {
    AAudioStreamBuilder* builder = nullptr;
    if (AAudio_createStreamBuilder(&builder) != AAUDIO_OK || !builder) {
        LOGE("No se pudo crear el constructor de AAudio");
        return;
    }
    AAudioStreamBuilder_setFormat(builder, AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder, 2);
    AAudioStreamBuilder_setSampleRate(builder, DS_AUDIO_SAMPLE_RATE_HINT);
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

    double rate = static_cast<double>(AAudioStream_getSampleRate(stream));
    ds_set_output_sample_rate(emu, rate);
    if (AAudioStream_requestStart(stream) != AAUDIO_OK) {
        LOGE("No se pudo arrancar AAudio");
        AAudioStream_close(stream);
        return;
    }
    LOGI("Audio a %.0f Hz", rate);
}
