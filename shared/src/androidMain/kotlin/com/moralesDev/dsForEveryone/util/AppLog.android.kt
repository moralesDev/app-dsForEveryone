package com.moralesDev.dsForEveryone.util

import android.util.Log

actual object AppLog {
    actual fun e(tag: String, message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }
}
