package com.moralesDev.dsForEveryone.emulator

import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import android.view.Surface
import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import com.moralesDev.dsForEveryone.emulator.core.CheatRejection
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    /**
     * Búfer único para el fotograma, reservado una sola vez y reutilizado
     * cada vuelta del bucle: ver [EmulatorCore.captureFrameInto]. Antes de
     * esto, cada fotograma pedía un `IntArray` nuevo al core —60 veces por
     * segundo, ~90 MB/s de basura en el heap de Java— y era candidato serio a
     * ser la causa real del OutOfMemoryError de la fase 4.C (ver
     * docs/estabilizacion-core.md).
     */
    private val frameBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(DsVideo.WIDTH * DsVideo.HEIGHT * BYTES_PER_PIXEL)
            .order(ByteOrder.nativeOrder())

    @Volatile
    private var frameReady = false

    /**
     * Último fotograma producido por el core, actualizado al final de cada
     * vuelta del bucle. Lo lee el renderizador GL (fase 2.5) desde su propio
     * hilo, una vez por fotograma de pantalla: es estado compartido, igual
     * que la máscara de botones y el lápiz, no un comando encolado ni una
     * llamada `suspend`. Siempre el mismo búfer: quien lo consuma debe
     * copiarlo o subirlo antes de que la siguiente vuelta lo reescriba.
     */
    val currentFrame: ByteBuffer? get() = if (frameReady) frameBuffer else null

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
        finishOpen { core.load(rom, batterySave) }

    /**
     * Como [open], pero a partir de un descriptor de archivo abierto sobre la
     * ROM (ver [com.moralesDev.dsForEveryone.emulator.core.EmulatorCore.loadFromDescriptor]):
     * la vía para ROMs sueltas que pueden pesar más de lo que cabe cómodo en
     * el heap de Java.
     */
    suspend fun openFromDescriptor(
        fd: ParcelFileDescriptor,
        length: Long,
        batterySave: ByteArray?,
    ): CoreLoadResult = finishOpen { core.loadFromDescriptor(fd, length, batterySave) }

    private suspend fun finishOpen(load: () -> CoreLoadResult): CoreLoadResult =
        withContext(Dispatchers.Default) {
            close()
            _status.value = EmulatorStatus.LOADING

            val result = load()
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

    /**
     * Velocidad de la emulación, 1x o avance rápido. Es estado compartido
     * —lo escribe la UI, lo lee el hilo de emulación al principio de cada
     * vuelta—, igual que la máscara de botones: un comando encolado sólo
     * añadiría latencia a algo que se cambia con un botón durante la partida.
     *
     * Lo único que cambia es el período entre fotogramas. El silenciado que
     * lo acompaña sí lo aplica el propio hilo, cuando nota que el valor
     * cambió: el core no se toca desde fuera ni para esto.
     */
    fun setSpeed(multiplier: Float) {
        speedMultiplier = multiplier.coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /** La velocidad que está usando el bucle ahora mismo, ya recortada a [MIN_SPEED]..[MAX_SPEED]. */
    @Volatile
    var speedMultiplier: Float = 1f
        private set

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
        frameReady = false
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

    /**
     * Sustituye el conjunto de claves activo por [entries] entero. Se
     * encola como cualquier otro comando: nunca toca el core desde fuera del
     * hilo de emulación, ni siquiera para algo tan aparentemente inocuo como
     * activar o desactivar una clave.
     */
    suspend fun applyCheats(entries: List<CheatEntry>): List<CheatRejection?> =
        onEmulationThread { core.applyCheats(entries) }

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
            var appliedSpeed = 1f
            var audioMuted = false
            var appliedLid = false

            try {
                while (running) {
                    if (paused) {
                        // En pausa no suena nada: sin esto seguiría oyéndose
                        // el audio que quedó en el anillo —medio segundo de
                        // música después de irse de la aplicación— y ese
                        // resto volvería a sonar, viejo, al reanudar.
                        if (!audioMuted) {
                            audioMuted = true
                            core.setAudioMuted(true)
                        }
                        // Sigue atendiendo comandos: guardar estado desde el
                        // menú ocurre precisamente con la emulación detenida.
                        commands.poll(PAUSED_POLL_MS, TimeUnit.MILLISECONDS)?.run()
                        nextFrameAt = System.nanoTime()
                        windowStartedAt = nextFrameAt
                        framesInWindow = 0
                        continue
                    }

                    drainCommands()

                    // La velocidad se lee una vez por vuelta, como la máscara
                    // de botones. El silenciado sólo se toca cuando cambia:
                    // es una llamada al core, y el core es de este hilo.
                    // Corriendo, sonar es lo normal; sólo el avance rápido
                    // silencia (ver setSpeed).
                    val speed = speedMultiplier
                    val shouldMute = speed != 1f
                    if (speed != appliedSpeed || shouldMute != audioMuted) {
                        appliedSpeed = speed
                        audioMuted = shouldMute
                        core.setAudioMuted(shouldMute)
                    }

                    core.setKeys(input.currentMask)
                    val touch = input.currentTouch
                    core.setTouch(touch.x, touch.y, touch.down)
                    core.setMicNoise(input.currentMicNoise)

                    // La tapa no se entrega cada fotograma como los botones:
                    // abrirla dispara una interrupción en el core, así que
                    // sólo se toca cuando el estado cambia de verdad.
                    val lidClosed = input.currentLidClosed
                    if (lidClosed != appliedLid) {
                        appliedLid = lidClosed
                        core.setLidClosed(lidClosed)
                    }
                    core.runFrame()
                    frameReady = core.captureFrameInto(frameBuffer)

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

                    nextFrameAt += DsVideo.frameDurationNanosAt(speed)
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
        const val BYTES_PER_PIXEL = 4

        /**
         * Nunca por debajo del tiempo real (cámara lenta no es una función de
         * este emulador) ni por encima de 2x, que es el tope que ofrece la
         * UI: un valor mayor sólo llegaría aquí por un error de quien llama.
         */
        const val MIN_SPEED = 1f
        const val MAX_SPEED = 2f

        /** Una comprobación por segundo de reloj real: suficiente y sin castigar el disco. */
        const val BATTERY_CHECK_INTERVAL_NANOS = 1_000_000_000L
    }
}
