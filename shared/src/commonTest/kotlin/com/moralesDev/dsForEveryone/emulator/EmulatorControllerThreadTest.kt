package com.moralesDev.dsForEveryone.emulator

import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource

/**
 * El hilo de emulación de verdad, en las dos plataformas: en Android sobre
 * `Thread` y `LockSupport`, en iOS sobre `NSThread` y `NSCondition` (ver
 * EmulationThread). Las pruebas detalladas del controlador están en `:app`
 * (EmulatorControllerTest, JVM); ésta existe para que lo que cambia de una
 * plataforma a otra —arrancar el hilo, dormir y despertar— se ejecute
 * también en el simulador de iOS.
 */
@OptIn(ExperimentalAtomicApi::class)
class EmulatorControllerThreadTest {

    private class CountingCore : EmulatorCore {
        val frames = AtomicInt(0)
        val unloaded = AtomicBoolean(false)
        override val coreName = "prueba"
        override val coreVersion = "0"
        override val name = "Prueba"
        override fun load(rom: ByteArray, batterySave: ByteArray?): CoreLoadResult = CoreLoadResult.Success
        override fun unload() = unloaded.store(true)
        override fun runFrame() {
            frames.addAndFetch(1)
        }
        override fun setKeys(mask: Int) = Unit
        override fun setTouch(x: Int, y: Int, down: Boolean) = Unit
        override fun setMicNoise(active: Boolean) = Unit
        override fun stateSize(): Int = 3
        override fun serializeState(): ByteArray = byteArrayOf(1, 2, 3)
        override fun deserializeState(state: ByteArray): Boolean = true
        override fun readBattery(): ByteArray? = null
        override fun consumeBatteryDirty(): Boolean = false
        override fun captureFrame(): IntArray = IntArray(DsVideo.WIDTH * DsVideo.HEIGHT)
    }

    /** Espera de verdad (no el tiempo virtual de runTest) a que se cumpla [condition]. */
    private suspend fun waitUntil(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    @Test
    fun `arranca en pausa — corre al reanudar — atiende comandos en pausa — y suelta el core al parar`() = runTest {
        val core = CountingCore()
        val controller = EmulatorController(core)

        assertEquals(CoreLoadResult.Success, controller.open(ByteArray(16), batterySave = null))
        assertEquals(EmulatorStatus.PAUSED, controller.status.value)
        assertEquals(0, core.frames.load())

        controller.resume()
        waitUntil { core.frames.load() > 10 }

        controller.pause()
        val pausedAt = core.frames.load()
        // En pausa, un comando tiene que despertar al hilo y atenderse sin
        // esperar al siguiente fotograma, porque no lo hay.
        val started = TimeSource.Monotonic.markNow()
        val state = withContext(Dispatchers.Default) { controller.saveState() }
        assertContentEquals(byteArrayOf(1, 2, 3), state)
        assertTrue(started.elapsedNow().inWholeMilliseconds < 1_000, "el comando tardó ${started.elapsedNow()}")
        assertTrue(core.frames.load() <= pausedAt + 1, "siguió corriendo en pausa")

        controller.stop()
        waitUntil { core.unloaded.load() }
        assertEquals(EmulatorStatus.IDLE, controller.status.value)
    }
}
