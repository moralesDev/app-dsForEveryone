package com.moralesDev.dsForEveryone.emulator

import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import com.moralesDev.dsForEveryone.emulator.core.CheatRejection
import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Core de mentira para probar el controlador sin código nativo.
 *
 * Es lo que permite ejercitar el ciclo de vida —arranque, pausa, reanudación,
 * parada—, la máscara de botones, la posición del lápiz y la cola de
 * comandos en pruebas de JVM, donde no hay ningún `.so` que cargar.
 */
class FakeEmulatorCore(
    private val loadResult: CoreLoadResult = CoreLoadResult.Success,
) : EmulatorCore {

    override val coreName: String = "fake"

    override val coreVersion: String = "0"

    val frames = AtomicInteger(0)
    val lastKeys = AtomicInteger(-1)
    val unloaded = AtomicBoolean(false)
    val loads = AtomicInteger(0)

    /** Historial de posiciones del lápiz recibidas, una entrada por fotograma. */
    val touchSamples = mutableListOf<TouchSample>()

    @Volatile var lastTouch: TouchSample = TouchSample(0, 0, down = false)

    @Volatile var loadedRom: ByteArray? = null
    @Volatile var loadedBattery: ByteArray? = null

    /** Estado interno serializable: es lo que comprueban las pruebas de estado. */
    @Volatile var counter: Int = 0

    @Volatile var battery: ByteArray? = null
    private val dirty = AtomicBoolean(false)

    fun markBatteryDirty(data: ByteArray) {
        battery = data
        dirty.set(true)
    }

    override fun load(rom: ByteArray, batterySave: ByteArray?): CoreLoadResult {
        loadedRom = rom
        loadedBattery = batterySave
        unloaded.set(false)
        loads.incrementAndGet()
        return loadResult
    }

    override fun unload() {
        unloaded.set(true)
    }

    override fun runFrame() {
        counter++
        frames.incrementAndGet()
    }

    override fun setKeys(mask: Int) {
        lastKeys.set(mask)
    }

    override fun setTouch(x: Int, y: Int, down: Boolean) {
        val sample = TouchSample(x, y, down)
        lastTouch = sample
        synchronized(touchSamples) { touchSamples += sample }
    }

    val micNoiseActive = AtomicBoolean(false)

    override fun setMicNoise(active: Boolean) {
        micNoiseActive.set(active)
    }

    /** Silenciado por avance rápido, y cuántas veces se ha cambiado: ver [setAudioMuted]. */
    val audioMuted = AtomicBoolean(false)
    val audioMuteChanges = AtomicInteger(0)

    override fun setAudioMuted(muted: Boolean) {
        audioMuted.set(muted)
        audioMuteChanges.incrementAndGet()
    }

    /**
     * La tapa, y cuántas veces se ha tocado. La cuenta importa tanto como el
     * valor: en el core real, abrir la tapa dispara una interrupción, así que
     * llamarlo de más no es inofensivo.
     */
    val lidClosed = AtomicBoolean(false)
    val lidChanges = AtomicInteger(0)

    override fun setLidClosed(closed: Boolean) {
        lidClosed.set(closed)
        lidChanges.incrementAndGet()
    }

    /**
     * Estado concreto que este core se negará a restaurar, **después** de
     * haber empezado a tocar la máquina. Imita el peor caso real: un
     * `loadState` que falla a mitad y deja el emulador en un punto que no es
     * ni el de antes ni el del archivo.
     */
    @Volatile var rejectedState: ByteArray? = null

    override fun stateSize(): Int = STATE_SIZE

    override fun serializeState(): ByteArray? = byteArrayOf(
        (counter shr 24).toByte(),
        (counter shr 16).toByte(),
        (counter shr 8).toByte(),
        counter.toByte(),
    )

    override fun deserializeState(state: ByteArray): Boolean {
        if (state.size != STATE_SIZE) return false
        if (state.contentEquals(rejectedState)) {
            counter = HALF_RESTORED
            return false
        }
        counter = (state[0].toInt() and 0xFF shl 24) or
            (state[1].toInt() and 0xFF shl 16) or
            (state[2].toInt() and 0xFF shl 8) or
            (state[3].toInt() and 0xFF)
        return true
    }

    override fun readBattery(): ByteArray? = battery

    override fun consumeBatteryDirty(): Boolean = dirty.getAndSet(false)

    override fun captureFrame(): IntArray? = IntArray(4) { counter }

    /** Un elemento por llamada a [applyCheats], en orden: lo que comprueban las pruebas de trucos. */
    val appliedCheats = mutableListOf<List<CheatEntry>>()

    override fun applyCheats(entries: List<CheatEntry>): List<CheatRejection?> {
        synchronized(appliedCheats) { appliedCheats += entries }
        return entries.map { null }
    }

    /** Posición del lápiz, para el registro de [touchSamples]. */
    data class TouchSample(val x: Int, val y: Int, val down: Boolean)

    private companion object {
        const val STATE_SIZE = 4

        /** Valor imposible que delata una restauración dejada a medias. */
        const val HALF_RESTORED = -1
    }
}
