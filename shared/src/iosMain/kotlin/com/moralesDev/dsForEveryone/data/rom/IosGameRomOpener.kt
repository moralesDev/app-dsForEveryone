package com.moralesDev.dsForEveryone.data.rom

import com.moralesDev.dsForEveryone.data.hash.Crc32
import com.moralesDev.dsForEveryone.emulator.EmulatorController

/** [GameRomOpener] de iOS: los bytes por el marcador de la ROM (ver [IosRomLibrary.readForCore]). */
class IosGameRomOpener(private val library: IosRomLibrary) : GameRomOpener {
    override suspend fun open(rom: RomInfo, controller: EmulatorController, batterySave: ByteArray?): OpenedRom? {
        val bytes = library.readForCore(rom) ?: return null
        val result = controller.open(bytes, batterySave)
        return OpenedRom(result, Crc32().apply { update(bytes) }.value)
    }
}
