package com.moralesDev.dsForEveryone.emulator

/**
 * El hilo de emulación: uno propio, no una corrutina (ver CLAUDE.md). Lo
 * justo que necesita EmulatorController, y cada plataforma lo da con lo
 * suyo:
 *
 * - Android: `Thread` con `THREAD_PRIORITY_URGENT_DISPLAY` y `LockSupport`,
 *   como siempre.
 * - iOS: `NSThread` con calidad de servicio interactiva y un `NSCondition`
 *   para dormir y despertar.
 *
 * [parkNanos] y [unpark] tienen la semántica de `LockSupport`: [unpark] deja
 * un permiso, y el siguiente [parkNanos] lo consume y vuelve enseguida en
 * vez de dormir. Así un comando encolado justo antes de que el hilo se duerma
 * no se pierde.
 */
internal expect class EmulationThread(name: String, body: () -> Unit) {
    fun start()

    val isAlive: Boolean

    /** Duerme como mucho [nanos], o hasta [unpark]. Sólo desde el propio hilo. */
    fun parkNanos(nanos: Long)

    fun unpark()

    /** Espera a que el hilo termine, como mucho [timeoutMillis]. */
    fun join(timeoutMillis: Long)
}

/**
 * Una vuelta del bucle de emulación. En iOS va dentro de un
 * `autoreleasepool`: un hilo propio no tiene bucle de eventos que vacíe la
 * reserva, y sin esto cada objeto de Foundation creado por fotograma se
 * quedaría vivo (la prueba del iPad llegó a 5 GB y la cerró el sistema; ver
 * docs/ios.md). En Android no hace nada.
 */
internal expect inline fun <T> perFrame(block: () -> T): T
