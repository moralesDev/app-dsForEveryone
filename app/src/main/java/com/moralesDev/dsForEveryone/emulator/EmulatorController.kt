package com.moralesDev.dsForEveryone.emulator

import android.os.Process
import android.util.Log
import android.view.Surface
import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.LockSupport

/** Situación del emulador, tal y como la observa la UI. */
enum class EmulatorStatus { IDLE, LOADING, RUNNING, PAUSED, ERROR }

/**
 * Dueño del hilo de emulación y de su ciclo de vida.
 *
 * Es la única clase que llama al [EmulatorCore], y lo hace siempre desde un
 * hilo dedicado: ni la UI ni los repositorios tocan el core directamente. El
 * hilo propio (en vez de una corrutina) es deliberado: el bucle necesita un
 * ritmo estable y prioridad de vídeo, cosas que un dispatcher compartido no
 * garantiza.
 *
 * Las operaciones que deben ocurrir entre fotogramas —guardar y cargar
 * estados, capturar una miniatura— no se ejecutan desde fuera: se encolan
 * como comandos y el propio hilo las atiende en un punto seguro del bucle.
 * Así no hace falta ningún cerrojo alrededor del core.
 *
 * Estructura heredada de `app-emulador` sin cambios; lo que cambia es el
 * período —el de la DS, no el de la GBA— y que cada vuelta también entrega
 * al core la posición del lápiz, no sólo la máscara de botones.
 */
class EmulatorController(private val core: EmulatorCore) {

    val input = InputManager()

    private val _status = MutableStateFlow(EmulatorStatus.IDLE)
    val status: StateFlow<EmulatorStatus> = _status.asStateFlow()

    /** Fotogramas emulados por segundo. La distinción con los presentados es del renderizador GL (fase 2.5). */
    private val _framesPerSecond = MutableStateFlow(0)
    val framesPerSecond: StateFlow<Int> = _framesPerSecond.asStateFlow()

    /**
     * Emite la savedata del cartucho cada vez que el juego la modifica. Quien
     * lo recoja es responsable de persistirla; el emulador no conoce el disco.
     */
    private val _batteryUpdates = MutableSharedFlow<ByteArray>(extraBufferCapacity = 4)
    val batteryUpdates: SharedFlow<ByteArray> = _batteryUpdates.asSharedFlow()

    private val commands = LinkedBlockingQueue<Runnable>()

    @Volatile
    private var thread: EmulationThread? = null

    @Volatile
    private var latestFrame: IntArray? = null

    /**
     * Último fotograma producido por el core, actualizado al final de cada
     * vuelta del bucle. Lo lee el renderizador GL (fase 2.5) desde su propio
     * hilo, una vez por fotograma de pantalla: es estado compartido, igual
     * que la máscara de botones y el lápiz, no un comando encolado ni una
     * llamada `suspend`.
     */
    val currentFrame: IntArray? get() = latestFrame

    /** Identidad técnica del core; es lo que se graba en cada save state. */
    val coreName: String get() = core.coreName
    val coreVersion: String get() = core.coreVersion

    /** Cómo se llama el core de cara al usuario, en la pantalla de ajustes. */
    val coreLabel: String get() = core.name

    /**
     * Detiene la sesión anterior, carga la ROM y deja el emulador **en
     * pausa**. Arrancar es una decisión de la UI: [resume].
     */
    suspend fun open(rom: ByteArray, batterySave: ByteArray?): CoreLoadResult =
        withContext(Dispatchers.Default) {
            close()
            _status.value = EmulatorStatus.LOADING

            val result = core.load(rom, batterySave)
            if (result is CoreLoadResult.Success) {
                EmulationThread().also { thread = it; it.start() }
                _status.value = EmulatorStatus.PAUSED
            } else {
                _status.value = EmulatorStatus.ERROR
            }
            result
        }

    fun resume() {
        val running = thread ?: return
        running.paused = false
        LockSupport.unpark(running)
        _status.value = EmulatorStatus.RUNNING
    }

    fun pause() {
        val running = thread ?: return
        running.paused = true
        // Un botón que se quede pulsado al abrir el menú reaparecería pulsado
        // al reanudar; se sueltan todos, lápiz incluido.
        input.releaseAll()
        _status.value = EmulatorStatus.PAUSED
    }

    /**
     * Termina la sesión. No bloquea: el propio hilo libera el core al salir
     * del bucle, de modo que se puede llamar desde `onCleared()` sin riesgo
     * de ANR.
     */
    fun stop() {
        thread?.let {
            it.paused = false
            it.running = false
            LockSupport.unpark(it)
        }
        thread = null
        _framesPerSecond.value = 0
        latestFrame = null
        _status.value = EmulatorStatus.IDLE
    }

    /** Espera a que el hilo anterior muera de verdad antes de reutilizar el core. */
    private suspend fun close() = withContext(Dispatchers.Default) {
        val previous = thread
        stop()
        previous?.join(JOIN_TIMEOUT_MS)
    }

    /** Ver el contrato de hilos de [EmulatorCore.attachSurface]. */
    fun attachSurface(surface: Surface?) = core.attachSurface(surface)

    suspend fun saveState(): ByteArray? = onEmulationThread { core.serializeState() }

    /** Bytes que ocupa hoy el estado de la ROM cargada; 0 si no hay ninguna. */
    suspend fun stateSize(): Int = onEmulationThread { core.stateSize() }

    /**
     * Restaura un estado y, si no se puede, deja la partida donde estaba.
     *
     * La red de seguridad es el estado que se serializa justo antes: si el
     * core rechaza el archivo a medio camino, se vuelve a meter el anterior.
     */
    suspend fun loadState(state: ByteArray): Boolean = onEmulationThread {
        val rollback = core.serializeState()
        if (core.deserializeState(state)) return@onEmulationThread true

        if (rollback != null && !core.deserializeState(rollback)) {
            Log.e(TAG, "El estado anterior tampoco se pudo restaurar")
        }
        false
    }

    suspend fun captureFrame(): IntArray? = onEmulationThread { core.captureFrame() }

    /** Fuerza la escritura de la savedata, aunque no esté marcada. */
    suspend fun flushBattery(): ByteArray? = onEmulationThread {
        core.consumeBatteryDirty()
        core.readBattery()
    }

    /**
     * Ejecuta [block] en el hilo de emulación, entre fotogramas. Si no hay
     * hilo vivo lo ejecuta en el sitio: sin bucle no hay nada con lo que
     * competir.
     */
    private suspend fun <T> onEmulationThread(block: () -> T): T {
        val running = thread
        if (running == null || !running.isAlive) return block()

        val result = CompletableDeferred<T>()
        commands.add(
            Runnable {
                try {
                    result.complete(block())
                } catch (error: Throwable) {
                    result.completeExceptionally(error)
                }
            },
        )
        LockSupport.unpark(running)
        return result.await()
    }

    private inner class EmulationThread : Thread("ds-emulation") {

        @Volatile var running = true

        @Volatile var paused = true

        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            var nextFrameAt = System.nanoTime()
            var windowStartedAt = nextFrameAt
            var framesInWindow = 0
            var lastBatteryCheckAt = nextFrameAt

            try {
                while (running) {
                    if (paused) {
                        // Sigue atendiendo comandos: guardar estado desde el
                        // menú ocurre precisamente con la emulación detenida.
                        commands.poll(PAUSED_POLL_MS, TimeUnit.MILLISECONDS)?.run()
                        nextFrameAt = System.nanoTime()
                        windowStartedAt = nextFrameAt
                        framesInWindow = 0
                        continue
                    }

                    drainCommands()

                    core.setKeys(input.currentMask)
                    val touch = input.currentTouch
                    core.setTouch(touch.x, touch.y, touch.down)
                    core.setMicNoise(input.currentMicNoise)
                    core.runFrame()
                    latestFrame = core.captureFrame()

                    val now = System.nanoTime()

                    // Reloj de pared, no cuenta de fotogramas: a velocidades
                    // distintas de 1x un contador de frames dejaría de valer
                    // un segundo real.
                    if (now - lastBatteryCheckAt >= BATTERY_CHECK_INTERVAL_NANOS) {
                        lastBatteryCheckAt = now
                        if (core.consumeBatteryDirty()) {
                            core.readBattery()?.let(_batteryUpdates::tryEmit)
                        }
                    }

                    framesInWindow++
                    if (now - windowStartedAt >= FPS_WINDOW_NANOS) {
                        val windowNanos = now - windowStartedAt
                        _framesPerSecond.value =
                            (framesInWindow * 1_000_000_000L / windowNanos).toInt()
                        windowStartedAt = now
                        framesInWindow = 0
                    }

                    nextFrameAt += DsVideo.FRAME_DURATION_NANOS
                    val sleepNanos = nextFrameAt - System.nanoTime()
                    if (sleepNanos > 0) {
                        LockSupport.parkNanos(sleepNanos)
                    } else {
                        // Vamos tarde: se reancla el reloj en lugar de
                        // acumular deuda y provocar una ráfaga de fotogramas.
                        nextFrameAt = System.nanoTime()
                    }
                }
            } catch (error: Throwable) {
                Log.e(TAG, "El hilo de emulación terminó con un error", error)
                _status.value = EmulatorStatus.ERROR
            } finally {
                // Los comandos pendientes se atienden antes de soltar el
                // core, para no dejar a nadie esperando un save state que no
                // llegará.
                drainCommands()
                core.attachSurface(null)
                core.unload()
            }
        }

        private fun drainCommands() {
            while (true) (commands.poll() ?: return).run()
        }
    }

    private companion object {
        const val TAG = "EmulatorController"
        const val PAUSED_POLL_MS = 50L
        const val JOIN_TIMEOUT_MS = 1_000L
        const val FPS_WINDOW_NANOS = 500_000_000L

        /** Una comprobación por segundo de reloj real: suficiente y sin castigar el disco. */
        const val BATTERY_CHECK_INTERVAL_NANOS = 1_000_000_000L
    }
}
