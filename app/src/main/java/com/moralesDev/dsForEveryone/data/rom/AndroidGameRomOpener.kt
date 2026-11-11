package com.moralesDev.dsForEveryone.data.rom

import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import com.moralesDev.dsForEveryone.emulator.openFromDescriptor

/**
 * [GameRomOpener] de Android. `openRomForCore` evita cargar la ROM entera en
 * un `ByteArray` de Kotlin cuando se puede: una ROM suelta grande (128 MB no
 * es raro en un cartucho real de DS) agotaba el heap antes de llegar siquiera
 * al core (ver docs/estabilizacion-core.md).
 */
class AndroidGameRomOpener(private val romRepository: RomRepository) : GameRomOpener {

    override suspend fun open(rom: RomInfo, controller: EmulatorController, batterySave: ByteArray?): OpenedRom? =
        when (val source = romRepository.openRomForCore(rom)) {
            null -> null
            is RomSource.Bytes -> OpenedRom(controller.open(source.data, batterySave), RomIdentity.crc32(source.data))
            // Con Descriptor el core se queda con ese descriptor, así que el
            // CRC32 necesita un flujo propio.
            is RomSource.Descriptor -> {
                val result = controller.openFromDescriptor(source.fd, source.length, batterySave)
                val crc32 = if (result is CoreLoadResult.Success) {
                    romRepository.openRawStream(rom)?.use(RomIdentity::crc32) ?: 0L
                } else {
                    0L
                }
                OpenedRom(result, crc32)
            }
        }
}
