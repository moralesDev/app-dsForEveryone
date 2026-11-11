package com.moralesDev.dsForEveryone.emulator.core

import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.FrameBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * El core de verdad en iOS (IosEmulatorCore: melonDS por cinterop, con su
 * salida de audio), solo y dentro del controlador con su hilo, en el
 * simulador. Una ROM mínima de ceros: no hace nada visible, pero el core la
 * acepta, la ejecuta y la serializa como a cualquier otra.
 */
class IosEmulatorCoreTest {

    private fun minimalRom(): ByteArray = ByteArray(1024 * 1024).also {
        "IOSCORETEST".encodeToByteArray().copyInto(it)
    }

    @Test
    fun `carga corre copia el fotograma y guarda y restaura un estado`() {
        val core = IosEmulatorCore()
        assertEquals("melonds", core.coreName)
        assertEquals("1.1", core.coreVersion)
        assertEquals(CoreLoadResult.Success, core.load(minimalRom(), batterySave = null))

        repeat(30) { core.runFrame() }
        assertTrue(core.captureFrameInto(FrameBuffer()))
        assertEquals(256 * 384, assertNotNull(core.captureFrame()).size)

        val state = assertNotNull(core.serializeState())
        assertEquals(core.stateSize(), state.size)
        repeat(10) { core.runFrame() }
        assertTrue(core.deserializeState(state))

        core.unload()
    }

    @Test
    fun `el tamano del estado al cargar es el mismo que tras correr`() {
        val core = IosEmulatorCore()
        core.load(minimalRom(), batterySave = null)
        val atLoad = core.stateSize()
        repeat(120) { core.runFrame() }
        assertEquals(atLoad, assertNotNull(core.serializeState()).size)
        core.unload()
    }

    @Test
    fun `los trucos invalidos dicen en que linea`() {
        val core = IosEmulatorCore()
        core.load(minimalRom(), batterySave = null)
        val result = core.applyCheats(
            listOf(
                CheatEntry(id = "a", label = "Bueno", lines = listOf("12345678 9ABCDEF0"), enabled = true),
                CheatEntry(id = "b", label = "Malo", lines = listOf("12345678 9ABCDEF0", "esto no"), enabled = false),
            ),
        )
        assertEquals(listOf(null, CheatRejection.InvalidLine(1)), result)
        core.unload()
    }

    @Test
    fun `dentro del controlador corre en su hilo y guarda en pausa`() = runTest {
        val controller = EmulatorController(IosEmulatorCore())
        assertEquals(CoreLoadResult.Success, controller.open(minimalRom(), batterySave = null))
        controller.resume()
        withContext(Dispatchers.Default) {
            withTimeout(5_000) { while (controller.currentFrame == null || controller.framesSinceLoad < 30) delay(10) }
        }
        controller.pause()
        val state = withContext(Dispatchers.Default) { controller.saveState() }
        assertTrue(assertNotNull(state).isNotEmpty())
        assertTrue(withContext(Dispatchers.Default) { controller.loadState(state) })
        controller.stop()
    }
}
