package com.moralesDev.dsForEveryone.emulator

import android.os.Process
import java.util.concurrent.locks.LockSupport

internal actual class EmulationThread actual constructor(name: String, body: () -> Unit) {
    private val thread = object : Thread(name) {
        override fun run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_DISPLAY)
            body()
        }
    }

    actual fun start() = thread.start()

    actual val isAlive: Boolean get() = thread.isAlive

    actual fun parkNanos(nanos: Long) = LockSupport.parkNanos(nanos)

    actual fun unpark() = LockSupport.unpark(thread)

    actual fun join(timeoutMillis: Long) = thread.join(timeoutMillis)
}

@Suppress("NOTHING_TO_INLINE")
internal actual inline fun <T> perFrame(block: () -> T): T = block()
