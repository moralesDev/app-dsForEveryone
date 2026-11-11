/*
 * Salida de audio de iOS para ds_core: el equivalente de
 * native/emulator_bridge/audio_aaudio.h en Android.
 *
 * Copyright (c) 2026 DsForEveryone. GPL-3.0, como melonDS (ver LICENSE).
 */
#ifndef DS_AUDIO_IOS_H
#define DS_AUDIO_IOS_H

#include "ds_core.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Abre y arranca la salida de audio (AudioUnit RemoteIO), cuyo callback saca
 * el sonido de [emu] con ds_audio_read, y le dice a [emu] la frecuencia real
 * con ds_set_output_sample_rate. Que falle no es motivo para no jugar: sólo
 * silencio. Devuelve `true` si suena.
 */
bool ds_audio_ios_start(DsEmulator* emu);

#ifdef __cplusplus
}
#endif

#endif  // DS_AUDIO_IOS_H
