/*
 * Salida de audio de Android (AAudio) para ds_core.
 *
 * Copyright (c) 2026 DsForEveryone. GPL-3.0, como melonDS (ver LICENSE).
 */
#ifndef DS_AUDIO_AAUDIO_H
#define DS_AUDIO_AAUDIO_H

#include "ds_core.h"

/**
 * Abre y arranca la salida de AAudio, cuyo callback saca el sonido de `emu`
 * con ds_audio_read, y le dice a `emu` la frecuencia real que se obtuvo.
 * Que falle no es motivo para no jugar: sólo silencio, con el mensaje en
 * logcat.
 */
void OpenAAudio(DsEmulator* emu);

#endif  // DS_AUDIO_AAUDIO_H
