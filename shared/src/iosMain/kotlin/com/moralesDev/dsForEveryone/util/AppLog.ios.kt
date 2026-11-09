package com.moralesDev.dsForEveryone.util

import platform.Foundation.NSLog

actual object AppLog {
    actual fun e(tag: String, message: String, error: Throwable?) {
        NSLog("E/%@: %@%@", tag, message, error?.let { " — $it" } ?: "")
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        NSLog("W/%@: %@%@", tag, message, error?.let { " — $it" } ?: "")
    }
}
