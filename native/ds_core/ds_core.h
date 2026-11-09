/*
 * ds_core: melonDS detrás de una API en C, sin nada de ninguna plataforma.
 *
 * Copyright (c) 2026 DsForEveryone. GPL-3.0, como melonDS (ver LICENSE).
 *
 * Es la frontera nativa que comparten Android e iOS (ver docs/ios.md, fase
 * iOS-1). Por encima, cada plataforma pone su traductor:
 *
 * - Android: native/emulator_bridge/ds_bridge.cpp traduce JNI a estas
 *   funciones, y audio_aaudio.cpp saca el sonido con ds_audio_read.
 * - iOS: Kotlin/Native las llama directamente por cinterop (fase iOS-4); la
 *   prueba de ios/spike/ ya las usa desde Objective-C++.
 *
 * Contrato de hilos, el mismo que documenta EmulatorCore.kt: todo se llama
 * desde el hilo de emulación, salvo
 *
 * - ds_set_mic_noise, ds_set_audio_muted y ds_consume_battery_dirty, que son
 *   atómicos y valen desde cualquier hilo;
 * - ds_audio_read, que es el consumidor del anillo de audio y sólo lo llama
 *   el hilo de la salida de audio de la plataforma.
 *
 * Por qué C y no C++: JNI y cinterop de Kotlin/Native entienden C sin más, y
 * así ningún tipo de melonDS cruza la frontera.
 */
#ifndef DS_CORE_H
#define DS_CORE_H

#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct DsEmulator DsEmulator;

enum {
    DS_SCREEN_WIDTH = 256,
    DS_SCREEN_HEIGHT = 192,
    /** Las dos pantallas apiladas: la superior en las filas 0-191, la inferior en las 192-383. */
    DS_FRAME_PIXELS = DS_SCREEN_WIDTH * DS_SCREEN_HEIGHT * 2,
};

/** Resultado de ds_load. Los mismos valores que espera NativeBridge.kt. */
enum {
    DS_LOAD_OK = 0,
    DS_LOAD_INVALID_ROM = 1,
    DS_LOAD_FAILED = 2,
};

/** Frecuencia nativa del SPU de la DS: la que se pide a la salida de audio. */
enum { DS_AUDIO_SAMPLE_RATE_HINT = 32768 };

/**
 * Recibe bytes que sólo viven durante la llamada (un save state, la partida
 * de batería): quien llama los copia a donde los quiera. Así la memoria no
 * cambia de dueño al cruzar la frontera.
 */
typedef void (*DsByteSink)(const uint8_t* data, size_t length, void* context);

/** La versión de melonDS empaquetada, fijada a mano. Es la que se graba en cada save state. */
const char* ds_core_version(void);

DsEmulator* ds_create(void);
void ds_destroy(DsEmulator* emu);

/**
 * La frecuencia que de verdad entrega la salida de audio (puede no ser la
 * pedida). Tiene que llegar antes de ds_load: el SPU resamplea a ella.
 */
void ds_set_output_sample_rate(DsEmulator* emu, double hz);

/**
 * Deja un cartucho jugando. `rom` sólo tiene que vivir durante la llamada
 * (melonDS lo copia); `save` es la partida de batería previa, o NULL.
 */
int ds_load(DsEmulator* emu, const uint8_t* rom, uint32_t rom_length,
            const uint8_t* save, uint32_t save_length);
void ds_unload(DsEmulator* emu);

void ds_run_frame(DsEmulator* emu);

/** El fotograma como bytes R,G,B,A, DS_FRAME_PIXELS × 4. false si no hay partida o no cabe. */
bool ds_copy_frame_rgba(DsEmulator* emu, uint8_t* out, size_t capacity);

/** El fotograma como enteros 0xAARRGGBB, DS_FRAME_PIXELS de ellos. */
bool ds_copy_frame_argb(DsEmulator* emu, uint32_t* out, size_t count);

/** Máscara de doce bits en el orden de DsButton.kt, 1 = pulsado. */
void ds_set_keys(DsEmulator* emu, uint32_t mask);
void ds_set_touch(DsEmulator* emu, int x, int y, bool down);
void ds_set_mic_noise(DsEmulator* emu, bool active);
void ds_set_lid_closed(DsEmulator* emu, bool closed);
void ds_set_audio_muted(DsEmulator* emu, bool muted);

/**
 * Llena `out` con `frames` fotogramas estéreo de 16 bits, completando con
 * silencio lo que falte. Devuelve cuántos eran sonido de verdad.
 */
uint32_t ds_audio_read(DsEmulator* emu, int16_t* out, uint32_t frames);

uint32_t ds_state_size(DsEmulator* emu);
bool ds_serialize_state(DsEmulator* emu, DsByteSink sink, void* context);
bool ds_deserialize_state(DsEmulator* emu, const uint8_t* state, size_t length);

bool ds_read_battery(DsEmulator* emu, DsByteSink sink, void* context);
bool ds_consume_battery_dirty(DsEmulator* emu);

/**
 * Sustituye todos los trucos. `codes[i]` son líneas "XXXXXXXX YYYYYYYY"
 * unidas por '\n'. En `rejected_line[i]` queda -1 si el truco se aceptó, o
 * la línea (desde 0) que no tenía esa forma.
 */
void ds_apply_cheats(DsEmulator* emu, const char* const* codes, const bool* enabled,
                     size_t count, int32_t* rejected_line);

#ifdef __cplusplus
}
#endif

#endif  // DS_CORE_H
