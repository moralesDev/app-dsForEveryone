package com.moralesDev.dsForEveryone.data.rom

import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult

/**
 * Entrega una ROM de la biblioteca al core. Cada plataforma a su manera,
 * porque cambia dónde vive la ROM y cuánto cuesta leerla: en Android,
 * `RomRepository.openRomForCore` pasa un descriptor al core para no meter
 * 128 MB en el heap de Java (docs/estabilizacion-core.md); en iOS se abre el
 * marcador de seguridad y se leen los bytes.
 */
fun interface GameRomOpener {
    /**
     * Abre [rom] en [controller] con su partida de batería. `null` si la ROM
     * no se puede leer (el archivo ya no está o se perdió el permiso).
     */
    suspend fun open(rom: RomInfo, controller: EmulatorController, batterySave: ByteArray?): OpenedRom?
}

/**
 * @property crc32 de los bytes que acaban de entrar en el core —lo que se
 *   emula de verdad—, para la identidad de los save states.
 */
data class OpenedRom(val result: CoreLoadResult, val crc32: Long)
