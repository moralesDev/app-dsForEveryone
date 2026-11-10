/*
 * Salida de audio de iOS para ds_core (ver ds_audio_ios.h).
 *
 * Copyright (c) 2026 DsForEveryone. GPL-3.0, como melonDS (ver LICENSE).
 *
 * Igual que audio_aaudio.cpp en Android: el anillo y su lógica —silencio al
 * silenciar, relleno con silencio si la emulación no llega— viven en ds_core
 * (ds_audio_read), y son los mismos en las dos plataformas. Aquí sólo queda
 * lo de iOS: una AudioUnit RemoteIO a 48 kHz, estéreo, enteros de 16 bits
 * entrelazados, cuyo callback llama a ds_audio_read.
 */
#include "ds_audio_ios.h"

#include <AudioToolbox/AudioToolbox.h>
#include <stdio.h>

static const double kSampleRate = 48000.0;

static OSStatus Render(void* refCon, AudioUnitRenderActionFlags* flags, const AudioTimeStamp* timeStamp,
                       UInt32 bus, UInt32 frames, AudioBufferList* data) {
    (void)flags;
    (void)timeStamp;
    (void)bus;
    ds_audio_read((DsEmulator*)refCon, (int16_t*)data->mBuffers[0].mData, frames);
    return noErr;
}

bool ds_audio_ios_start(DsEmulator* emu) {
    AudioComponentDescription description = {
        .componentType = kAudioUnitType_Output,
        .componentSubType = kAudioUnitSubType_RemoteIO,
        .componentManufacturer = kAudioUnitManufacturer_Apple,
    };
    AudioComponent component = AudioComponentFindNext(NULL, &description);
    AudioUnit unit = NULL;
    if (!component || AudioComponentInstanceNew(component, &unit) != noErr) {
        fprintf(stderr, "[ds-audio] No hay AudioUnit RemoteIO\n");
        return false;
    }

    AudioStreamBasicDescription format = {
        .mSampleRate = kSampleRate,
        .mFormatID = kAudioFormatLinearPCM,
        .mFormatFlags = kLinearPCMFormatFlagIsSignedInteger | kLinearPCMFormatFlagIsPacked,
        .mBytesPerPacket = 4,
        .mFramesPerPacket = 1,
        .mBytesPerFrame = 4,
        .mChannelsPerFrame = 2,
        .mBitsPerChannel = 16,
    };
    AURenderCallbackStruct callback = {.inputProc = Render, .inputProcRefCon = emu};

    // La AudioUnit convierte de 48 kHz a lo que use el hardware. El SPU de la
    // DS resamplea a 48 kHz dentro de ds_core (ds_set_output_sample_rate).
    if (AudioUnitSetProperty(unit, kAudioUnitProperty_StreamFormat, kAudioUnitScope_Input, 0,
                             &format, sizeof(format)) != noErr ||
        AudioUnitSetProperty(unit, kAudioUnitProperty_SetRenderCallback, kAudioUnitScope_Input, 0,
                             &callback, sizeof(callback)) != noErr ||
        AudioUnitInitialize(unit) != noErr) {
        fprintf(stderr, "[ds-audio] No se pudo configurar la salida de audio\n");
        AudioComponentInstanceDispose(unit);
        return false;
    }

    ds_set_output_sample_rate(emu, kSampleRate);
    if (AudioOutputUnitStart(unit) != noErr) {
        fprintf(stderr, "[ds-audio] No se pudo arrancar la salida de audio\n");
        AudioUnitUninitialize(unit);
        AudioComponentInstanceDispose(unit);
        return false;
    }
    // La unidad vive lo que vive el proceso, como el AAudioStream en Android.
    return true;
}
