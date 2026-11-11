package com.moralesDev.dsForEveryone.emulator

import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import com.moralesDev.dsForEveryone.emulator.core.CheatRejection
import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import com.moralesDev.dsForEveryone.util.AppLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.time.TimeSource

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
 *
 * Común a Android e iOS desde iOS-4 (docs/ios.md). Lo que da cada plataforma
 * es el hilo ([EmulationThread]: prioridad, dormir y despertar) y el búfer
 * del fotograma ([FrameBuffer]); el bucle, el reloj absoluto y la cola de
 * comandos son los mismos en las dos.
 */
class EmulatorController(internal val core: EmulatorCore) {

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

    /**
     * Los comandos para el hilo de emulación. Un canal sin límite: `trySend`
     * y `tryReceive` no bloquean ni necesitan cerrojos, y se pueden usar
     * desde un hilo que no es una corrutina, que es lo que es el de
     * emulación.
     */
    private val commands = Channel<() -> Unit>(Channel.UNLIMITED)

    @Volatile
    private var session: Session? = null

    /**
     * Búfer único para el fotograma, reservado una sola vez y reutilizado
     * cada vuelta del bucle: ver [EmulatorCore.captureFrameInto]. Antes de
     * esto, cada fotograma pedía un `IntArray` nuevo al core —60 veces por
     * segundo, ~90 MB/s de basura en el heap de Java— y era candidato serio a
     * ser la causa real del OutOfMemoryError de la fase 4.C (ver
     * docs/estabilizacion-core.md).
     */
    private val frameBuffer = FrameBuffer()

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
    val currentFrame: FrameBuffer? get() = if (frameReady) frameBuffer else null

    /**
     * Fotogramas emulados desde que la partida en curso salió de algún sitio:
     * vuelve a cero al abrir la ROM y al cargar un estado. Sólo lo escribe el
     * hilo de emulación. Es lo que permite saber si de verdad se ha jugado
     * algo —y no sólo tener el juego abierto en pausa— antes de pisar el
     * respaldo automático (ver GameViewModel).
     */
    @Volatile
    var framesSinceLoad: Long = 0L
        private set

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
     * Detiene la sesión anterior, carga con [load] y arranca el hilo en pausa.
     * Lo usan [open] y, en Android, `openFromDescriptor`.
     */
    internal suspend fun finishOpen(load: () -> CoreLoadResult): CoreLoadResult =
        withContext(Dispatchers.Default) {
            close()
            _status.value = EmulatorStatus.LOADING

            val result = load()
            framesSinceLoad = 0L
            if (result is CoreLoadResult.Success) {
                Session().also { session = it; it.thread.start() }
                _status.value = EmulatorStatus.PAUSED
            } else {
                _status.value = EmulatorStatus.ERROR
            }
            result
        }

    fun resume() {
        val running = session ?: return
        running.paused = false
        running.thread.unpark()
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
        val running = session ?: return
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
        session?.let {
            it.paused = false
            it.running = false
            it.thread.unpark()
        }
        session = null
        _framesPerSecond.value = 0
        frameReady = false
        _status.value = EmulatorStatus.IDLE
    }

    /** Espera a que el hilo anterior muera de verdad antes de reutilizar el core. */
    private suspend fun close() = withContext(Dispatchers.Default) {
        val previous = session
        stop()
        previous?.thread?.join(JOIN_TIMEOUT_MS)
    }

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
        if (core.deserializeState(state)) {
            framesSinceLoad = 0L
            return@onEmulationThread true
        }

        if (rollback != null && !core.deserializeState(rollback)) {
            AppLog.e(TAG, "El estado anterior tampoco se pudo restaurar")
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
        val running = session
        if (running == null || !running.thread.isAlive) return block()

        val result = CompletableDeferred<T>()
        commands.trySend {
            try {
                result.complete(block())
            } catch (error: Throwable) {
                result.completeExceptionally(error)
            }
        }
        running.thread.unpark()
        return result.await()
    }

    /** Una sesión de juego: su hilo y los dos interruptores que lo gobiernan. */
    private inner class Session {

        @Volatile var running = true

        @Volatile var paused = true

        val thread = EmulationThread("ds-emulation", ::run)

        private fun run() {
            var nextFrameAt = now()
            var windowStartedAt = nextFrameAt
            var framesInWindow = 0
            var lastBatteryCheckAt = nextFrameAt
            var appliedSpeed = 1f
            var audioMuted = false
            var appliedLid = false

            try {
                while (running) perFrame {
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
                        // Duerme hasta que llegue uno (onEmulationThread
                        // despierta el hilo) o como mucho PAUSED_POLL_MS.
                        drainCommands()
                        thread.parkNanos(PAUSED_POLL_MS * 1_000_000L)
                        nextFrameAt = now()
                        windowStartedAt = nextFrameAt
                        framesInWindow = 0
                        return@perFrame
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
                    framesSinceLoad++
                    frameReady = core.captureFrameInto(frameBuffer)

                    val now = now()

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
                    val sleepNanos = nextFrameAt - now()
                    if (sleepNanos > 0) {
                        thread.parkNanos(sleepNanos)
                    } else {
                        // Vamos tarde: se reancla el reloj en lugar de
                        // acumular deuda y provocar una ráfaga de fotogramas.
                        nextFrameAt = now()
                    }
                }
            } catch (error: Throwable) {
                AppLog.e(TAG, "El hilo de emulación terminó con un error", error)
                _status.value = EmulatorStatus.ERROR
            } finally {
                // Los comandos pendientes se atienden antes de soltar el
                // core, para no dejar a nadie esperando un save state que no
                // llegará.
                drainCommands()
                core.unload()
            }
        }

        private fun drainCommands() {
            while (true) (commands.tryReceive().getOrNull() ?: return).invoke()
        }
    }

    private companion object {
        const val TAG = "EmulatorController"

        /** Reloj monótono en nanosegundos: `System.nanoTime` en Android, el del sistema en iOS. */
        private val clockOrigin = TimeSource.Monotonic.markNow()

        fun now(): Long = clockOrigin.elapsedNow().inWholeNanoseconds
        const val PAUSED_POLL_MS = 50L
        const val JOIN_TIMEOUT_MS = 1_000L
        const val FPS_WINDOW_NANOS = 500_000_000L

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
