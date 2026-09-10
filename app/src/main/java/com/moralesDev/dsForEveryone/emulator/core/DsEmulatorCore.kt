package com.moralesDev.dsForEveryone.emulator.core

import android.os.ParcelFileDescriptor
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Core de emulación real, sobre melonDS.
 *
 * Traduce el contrato de [EmulatorCore] a las llamadas del puente JNI y no
 * hace nada más: la emulación entera ocurre en C++. Igual que el resto del
 * proyecto, asume que todas las operaciones llegan desde el hilo de
 * emulación salvo [attachSurface].
 *
 * El manejador nativo se crea una sola vez y vive lo que viva el proceso, con
 * el mismo motivo que en `app-emulador`: la superficie puede engancharse
 * antes de que haya ninguna ROM cargada.
 */
class DsEmulatorCore : EmulatorCore {

    private val handle: Long by lazy {
        if (NativeBridge.isAvailable) NativeBridge.nativeCreate() else 0L
    }

    private val isUsable: Boolean get() = NativeBridge.isAvailable && handle != 0L

    override val coreName: String = "melonds"

    /**
     * `BRIDGE_CORE_VERSION`, fijada a mano en `ds_bridge.cpp`, no un valor
     * generado por el sistema de compilación: ver docs/integracion-core.md,
     * sección 6.
     */
    override val coreVersion: String by lazy {
        if (NativeBridge.isAvailable) NativeBridge.nativeCoreVersion() else "no-disponible"
    }

    override fun load(rom: ByteArray, batterySave: ByteArray?): CoreLoadResult {
        if (!isUsable) return CoreLoadResult.CoreUnavailable
        return when (NativeBridge.nativeLoad(handle, rom, batterySave)) {
            LOAD_OK -> CoreLoadResult.Success
            LOAD_INVALID_ROM -> CoreLoadResult.InvalidRom
            else -> CoreLoadResult.Failure("melonDS no pudo inicializar la ROM.")
        }
    }

    /**
     * Mapea la ROM en memoria en el lado nativo en vez de copiarla a un
     * `ByteArray`: es lo que evita el `OutOfMemoryError` con ROMs sueltas
     * grandes (ver `EmulatorCore.loadFromDescriptor` y
     * `docs/estabilizacion-core.md`).
     */
    override fun loadFromDescriptor(
        fd: ParcelFileDescriptor,
        length: Long,
        batterySave: ByteArray?,
    ): CoreLoadResult {
        if (!isUsable) {
            fd.close()
            return CoreLoadResult.CoreUnavailable
        }
        // detachFd() traspasa la propiedad del descriptor: a partir de aquí
        // es ds_bridge.cpp quien lo cierra, siempre, tenga éxito o no.
        return when (NativeBridge.nativeLoadFd(handle, fd.detachFd(), length, batterySave)) {
            LOAD_OK -> CoreLoadResult.Success
            LOAD_INVALID_ROM -> CoreLoadResult.InvalidRom
            else -> CoreLoadResult.Failure("melonDS no pudo inicializar la ROM.")
        }
    }

    override fun unload() {
        if (isUsable) NativeBridge.nativeUnload(handle)
    }

    override fun runFrame() {
        if (isUsable) NativeBridge.nativeRunFrame(handle)
    }

    override fun setKeys(mask: Int) {
        if (isUsable) NativeBridge.nativeSetKeys(handle, mask)
    }

    override fun setTouch(x: Int, y: Int, down: Boolean) {
        if (isUsable) NativeBridge.nativeSetTouch(handle, x, y, down)
    }

    override fun setAudioMuted(muted: Boolean) {
        if (isUsable) NativeBridge.nativeSetAudioMuted(handle, muted)
    }

    override fun setLidClosed(closed: Boolean) {
        if (isUsable) NativeBridge.nativeSetLidClosed(handle, closed)
    }

    override fun setMicNoise(active: Boolean) {
        if (isUsable) NativeBridge.nativeSetMicNoise(handle, active)
    }

    override fun applyCheats(entries: List<CheatEntry>): List<CheatRejection?> {
        if (!isUsable) return entries.map { null }
        val lines = Array(entries.size) { entries[it].lines.joinToString("\n") }
        val enabled = BooleanArray(entries.size) { entries[it].enabled }
        val rejections = NativeBridge.nativeApplyCheats(handle, lines, enabled)
            ?: return entries.map { null }
        return rejections.map(::parseCheatRejection)
    }

    /** Inversa de la codificación que hace `ds_bridge.cpp`: ver `NativeBridge.nativeApplyCheats`. */
    private fun parseCheatRejection(code: String): CheatRejection? {
        if (code.isEmpty()) return null
        return code.removePrefix("L").toIntOrNull()?.let(CheatRejection::InvalidLine)
    }

    override fun attachSurface(surface: Surface?) {
        // melonDS no dibuja en esta superficie: el vídeo de la DS sale por
        // captureFrame (ver la documentación de EmulatorCore.attachSurface).
    }

    override fun stateSize(): Int =
        if (isUsable) NativeBridge.nativeStateSize(handle) else 0

    override fun serializeState(): ByteArray? =
        if (isUsable) NativeBridge.nativeSerializeState(handle) else null

    override fun deserializeState(state: ByteArray): Boolean =
        isUsable && NativeBridge.nativeDeserializeState(handle, state)

    override fun readBattery(): ByteArray? =
        if (isUsable) NativeBridge.nativeReadBattery(handle) else null

    override fun consumeBatteryDirty(): Boolean =
        isUsable && NativeBridge.nativeConsumeBatteryDirty(handle)

    override fun captureFrame(): IntArray? =
        if (isUsable) NativeBridge.nativeCaptureFrame(handle) else null

    override fun captureFrameInto(buffer: ByteBuffer): Boolean =
        isUsable && NativeBridge.nativeCaptureFrameInto(handle, buffer)

    private companion object {
        const val LOAD_OK = 0
        const val LOAD_INVALID_ROM = 1
    }
}
