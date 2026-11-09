package com.moralesDev.dsForEveryone.emulator

import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ciclo de vida del hilo de emulación.
 *
 * Se prueba con un core de mentira porque el real vive en código nativo
 * (fase 4), que una prueba de JVM no puede cargar. Lo que se comprueba aquí
 * es el contrato que rodea al core: que arranque en pausa, que sólo avance
 * cuando se le dice, que la máscara y el lápiz lleguen correctamente, que los
 * comandos se atiendan también en pausa y que al parar libere el core.
 */
class EmulatorControllerTest {

    private val core = FakeEmulatorCore()
    private val controller = EmulatorController(core)

    @After
    fun tearDown() {
        controller.stop()
    }

    @Test
    fun `abrir una ROM deja el emulador en pausa, no corriendo`() = runTest {
        val result = controller.open(ROM, batterySave = null)

        assertEquals(CoreLoadResult.Success, result)
        assertEquals(EmulatorStatus.PAUSED, controller.status.value)
        // Arrancar es una decisión de la UI: nadie ha pedido correr todavía.
        assertEquals(0, core.frames.get())
    }

    @Test
    fun `la ROM y la partida llegan al core tal cual`() = runTest {
        val battery = byteArrayOf(9, 8, 7)

        controller.open(ROM, battery)

        assertTrue(ROM.contentEquals(core.loadedRom))
        assertTrue(battery.contentEquals(core.loadedBattery))
    }

    @Test
    fun `un core que rechaza la ROM deja el controlador en error`() = runTest {
        val rejecting = FakeEmulatorCore(loadResult = CoreLoadResult.InvalidRom)
        val other = EmulatorController(rejecting)

        val result = other.open(ROM, batterySave = null)

        assertEquals(CoreLoadResult.InvalidRom, result)
        assertEquals(EmulatorStatus.ERROR, other.status.value)
    }

    @Test
    fun `reanudar avanza fotogramas y pausar los detiene`() = runTest {
        controller.open(ROM, batterySave = null)

        controller.resume()
        assertEquals(EmulatorStatus.RUNNING, controller.status.value)
        waitUntil { core.frames.get() > 2 }

        controller.pause()
        assertEquals(EmulatorStatus.PAUSED, controller.status.value)
        // Se deja terminar el fotograma en curso antes de tomar la medida.
        Thread.sleep(80)
        val afterPause = core.frames.get()
        Thread.sleep(120)

        assertEquals("En pausa no debe avanzar", afterPause, core.frames.get())
    }

    @Test
    fun `currentFrame se actualiza mientras corre y se limpia al parar`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { controller.currentFrame != null }

        assertNotNull(controller.currentFrame)

        controller.stop()

        assertEquals(null, controller.currentFrame)
    }

    // ---------------------------------------------------------- máscara de botones

    @Test
    fun `la mascara de doce bits llega al core en cada fotograma`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.input.press(DsButton.A)
        controller.input.press(DsButton.RIGHT)
        controller.input.press(DsButton.X)

        controller.resume()
        waitUntil { core.lastKeys.get() > 0 }

        assertEquals(
            DsButton.A.mask or DsButton.RIGHT.mask or DsButton.X.mask,
            core.lastKeys.get(),
        )
    }

    @Test
    fun `pausar suelta todos los botones`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.input.press(DsButton.B)

        controller.pause()

        assertEquals(0, controller.input.currentMask)
    }

    @Test
    fun `el soplido llega al core mientras el boton este pulsado`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.input.setMicNoise(true)

        controller.resume()
        waitUntil { core.micNoiseActive.get() }

        controller.input.setMicNoise(false)
        waitUntil { !core.micNoiseActive.get() }
    }

    // ---------------------------------------------------------- lápiz

    @Test
    fun `la posicion tactil llega al core mientras el dedo esta abajo`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.input.setTouch(120, 80, down = true)

        controller.resume()
        waitUntil { core.lastTouch.down }

        assertEquals(FakeEmulatorCore.TouchSample(120, 80, down = true), core.lastTouch)
    }

    @Test
    fun `la posicion tactil se reporta en cada fotograma, no solo cuando cambia`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.input.setTouch(50, 50, down = true)

        controller.resume()
        waitUntil { core.frames.get() > 5 }
        controller.pause()

        val samplesWithFinger = synchronized(core.touchSamples) { core.touchSamples.toList() }
            .filter { it.down }

        assertTrue(
            "debe haber más de una muestra con el dedo abajo mientras no se mueve: ${samplesWithFinger.size}",
            samplesWithFinger.size > 1,
        )
        assertTrue(samplesWithFinger.all { it == FakeEmulatorCore.TouchSample(50, 50, down = true) })
    }

    @Test
    fun `soltar el dedo llega al core como down=false`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.input.setTouch(10, 10, down = true)
        controller.resume()
        waitUntil { core.lastTouch.down }

        controller.input.setTouch(0, 0, down = false)
        waitUntil { !core.lastTouch.down }

        assertFalse(core.lastTouch.down)
    }

    @Test
    fun `pausar suelta tambien el lapiz`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.input.setTouch(30, 30, down = true)

        controller.pause()

        assertFalse(controller.input.currentTouch.down)
    }

    // ---------------------------------------------------------- comandos en pausa

    @Test
    fun `guardar y cargar estado funciona con la emulacion en pausa`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 3 }
        controller.pause()

        val saved = controller.saveState()
        assertNotNull("El comando debe atenderse aunque esté en pausa", saved)
        val savedCounter = core.counter

        core.counter = 9999
        assertTrue(controller.loadState(saved!!))

        assertEquals(savedCounter, core.counter)
    }

    @Test
    fun `la cola de comandos se sigue atendiendo con la emulacion en pausa`() = runTest {
        controller.open(ROM, batterySave = null)
        // Nunca se llamó a resume(): el hilo está en pausa desde open().
        assertEquals(EmulatorStatus.PAUSED, controller.status.value)

        assertEquals(4, controller.stateSize())
        assertNotNull(controller.captureFrame())
    }

    @Test
    fun `una carga fallida deja la partida donde estaba`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 3 }
        controller.pause()

        val counterBefore = core.counter

        val bad = byteArrayOf(0, 0, 0, 42)
        core.rejectedState = bad
        assertFalse(controller.loadState(bad))

        assertEquals(
            "La partida en curso no puede perderse por un estado defectuoso",
            counterBefore,
            core.counter,
        )
    }

    @Test
    fun `parar libera el core y vuelve a reposo`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 1 }

        controller.stop()

        assertEquals(EmulatorStatus.IDLE, controller.status.value)
        waitUntil { core.unloaded.get() }
        assertTrue("El hilo debe soltar el core al salir del bucle", core.unloaded.get())
    }

    // ---------------------------------------------------------- casos límite

    @Test
    fun `pausar antes de abrir ninguna ROM no hace nada`() = runTest {
        controller.pause()

        assertEquals(EmulatorStatus.IDLE, controller.status.value)
    }

    @Test
    fun `reanudar despues de parar no revive el emulador`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.stop()

        controller.resume()

        assertEquals(EmulatorStatus.IDLE, controller.status.value)
    }

    @Test
    fun `parar dos veces seguidas es inofensivo`() = runTest {
        controller.open(ROM, batterySave = null)

        controller.stop()
        controller.stop()

        assertEquals(EmulatorStatus.IDLE, controller.status.value)
    }

    @Test
    fun `abrir otra ROM con una ya cargada suelta la anterior`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 1 }

        val other = ByteArray(64) { 7 }
        controller.open(other, batterySave = null)

        assertEquals(2, core.loads.get())
        assertTrue(other.contentEquals(core.loadedRom))
        assertEquals(EmulatorStatus.PAUSED, controller.status.value)
    }

    @Test
    fun `sin hilo vivo los comandos se ejecutan igualmente`() = runTest {
        // Nunca se abrió ninguna ROM: no hay hilo al que encolar nada.
        assertNotNull(controller.saveState())
        assertFalse(controller.status.value == EmulatorStatus.RUNNING)
    }

    // ---------------------------------------------------------- avance rápido

    @Test
    fun `el avance rapido emula mas fotogramas en la misma ventana de reloj`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 2 }

        val normalStart = core.frames.get()
        Thread.sleep(WINDOW_MS)
        val atNormalSpeed = core.frames.get() - normalStart

        controller.setSpeed(2f)
        Thread.sleep(30)
        val fastStart = core.frames.get()
        Thread.sleep(WINDOW_MS)
        val atDoubleSpeed = core.frames.get() - fastStart

        // El margen es generoso a propósito: lo que se comprueba es que el
        // período se acorta de verdad, no que el reloj de una máquina de
        // pruebas cargada acierte los 2x clavados.
        assertTrue(
            "A 2x deberían salir bastantes más fotogramas: $atNormalSpeed frente a $atDoubleSpeed",
            atDoubleSpeed > atNormalSpeed * 1.4,
        )
    }

    @Test
    fun `el avance rapido silencia el audio y volver a 1x lo devuelve`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 2 }
        assertFalse("A 1x no se silencia nada", core.audioMuted.get())

        controller.setSpeed(1.5f)
        waitUntil { core.audioMuted.get() }

        controller.setSpeed(1f)
        waitUntil { !core.audioMuted.get() }
    }

    @Test
    fun `el silenciado solo se toca cuando la velocidad cambia`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.setSpeed(2f)
        controller.resume()
        // No basta con esperar al silencio: el hilo arranca en pausa y ya
        // silencia ahí, así que `audioMuted` puede cumplirse antes de que el
        // bucle corriendo aplique la velocidad (y vuelva a silenciar una vez).
        // La cuenta se toma con fotogramas ya corriendo.
        waitUntil { core.audioMuted.get() && core.frames.get() > 2 }

        // Muchos fotogramas después, sigue habiendo una sola llamada: el
        // core no se toca una vez por vuelta para repetirle lo mismo.
        val changes = core.audioMuteChanges.get()
        Thread.sleep(WINDOW_MS)

        assertTrue(core.frames.get() > 5)
        assertEquals(changes, core.audioMuteChanges.get())
    }

    @Test
    fun `en pausa no suena nada, y al reanudar vuelve el sonido`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 2 }
        assertFalse(core.audioMuted.get())

        // Es lo que pasa al irse de la aplicación: sin esto seguiría sonando
        // el audio que quedó en el anillo.
        controller.pause()
        waitUntil { core.audioMuted.get() }

        controller.resume()
        waitUntil { !core.audioMuted.get() }
    }

    @Test
    fun `reanudar en avance rapido sigue silenciado`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.setSpeed(2f)
        controller.resume()
        waitUntil { core.audioMuted.get() }

        controller.pause()
        waitUntil { core.audioMuted.get() }
        controller.resume()

        // Sigue en 2x: reanudar no devuelve el sonido que el avance rápido
        // quitó, sólo el que quitó la pausa.
        Thread.sleep(80)
        assertTrue(core.audioMuted.get())
    }

    // ---------------------------------------------------------- la tapa

    @Test
    fun `la tapa llega al core, y solo cuando cambia`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        waitUntil { core.frames.get() > 5 }

        // Nadie ha tocado la tapa: el core no se entera de nada. Esto no es
        // una optimización, es el contrato: abrirla dispara una interrupción.
        assertEquals(0, core.lidChanges.get())

        controller.input.setLidClosed(true)
        waitUntil { core.lidClosed.get() }
        assertEquals(1, core.lidChanges.get())

        // Muchos fotogramas con la tapa cerrada, y sigue habiendo una sola
        // llamada: no se reenvía el estado cada vuelta como la máscara.
        val frames = core.frames.get()
        waitUntil { core.frames.get() > frames + 5 }
        assertEquals(1, core.lidChanges.get())

        controller.input.setLidClosed(false)
        waitUntil { !core.lidClosed.get() }
        assertEquals(2, core.lidChanges.get())
    }

    @Test
    fun `pausar no abre la tapa`() = runTest {
        controller.open(ROM, batterySave = null)
        controller.resume()
        controller.input.setLidClosed(true)
        waitUntil { core.lidClosed.get() }

        // pause() suelta todos los botones; la tapa no es uno de ellos.
        controller.pause()
        Thread.sleep(80)

        assertTrue(core.lidClosed.get())
        assertEquals(1, core.lidChanges.get())
    }

    @Test
    fun `la velocidad se recorta a lo que la UI puede pedir`() {
        controller.setSpeed(5f)
        assertEquals(2f, controller.speedMultiplier, 0.001f)

        // Cámara lenta no es una función de este emulador.
        controller.setSpeed(0.25f)
        assertEquals(1f, controller.speedMultiplier, 0.001f)
    }

    private fun waitUntil(timeoutMs: Long = 2_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("La condición no se cumplió en $timeoutMs ms")
    }

    private companion object {
        val ROM = ByteArray(256) { it.toByte() }

        /** Ventana de medida del avance rápido: ~18 fotogramas a 1x, ~36 a 2x. */
        const val WINDOW_MS = 300L
    }
}
