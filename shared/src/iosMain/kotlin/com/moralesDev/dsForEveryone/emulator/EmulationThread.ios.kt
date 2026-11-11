package com.moralesDev.dsForEveryone.emulator

import kotlinx.cinterop.autoreleasepool
import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSQualityOfServiceUserInteractive
import platform.Foundation.NSThread
import platform.Foundation.dateWithTimeIntervalSinceNow

internal actual class EmulationThread actual constructor(name: String, body: () -> Unit) {
    /** Protege [permit] y [finished], y es por donde se duerme y se despierta. */
    private val condition = NSCondition()
    private var permit = false
    private var finished = false
    private var started = false

    private val thread = NSThread(
        block = {
            try {
                body()
            } finally {
                condition.lock()
                finished = true
                condition.broadcast()
                condition.unlock()
            }
        },
    ).apply {
        this.name = name
        // Lo más alto que da iOS a un hilo de app: el de la interfaz.
        qualityOfService = NSQualityOfServiceUserInteractive
    }

    actual fun start() {
        started = true
        thread.start()
    }

    actual val isAlive: Boolean
        get() {
            condition.lock()
            val alive = started && !finished
            condition.unlock()
            return alive
        }

    actual fun parkNanos(nanos: Long) {
        condition.lock()
        try {
            if (!permit && nanos > 0) {
                condition.waitUntilDate(NSDate.dateWithTimeIntervalSinceNow(nanos / 1_000_000_000.0))
            }
            permit = false
        } finally {
            condition.unlock()
        }
    }

    actual fun unpark() {
        condition.lock()
        permit = true
        condition.signal()
        condition.unlock()
    }

    actual fun join(timeoutMillis: Long) {
        val deadline = NSDate.dateWithTimeIntervalSinceNow(timeoutMillis / 1000.0)
        condition.lock()
        try {
            while (!finished) {
                if (!condition.waitUntilDate(deadline)) break
            }
        } finally {
            condition.unlock()
        }
    }
}

internal actual inline fun <T> perFrame(block: () -> T): T = autoreleasepool(block)
