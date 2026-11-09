package com.moralesDev.dsForEveryone.util

/**
 * Registro de avisos y errores del código común. En Android va a logcat
 * (`android.util.Log`); en iOS, a la consola del sistema (la que enseña
 * Xcode). Sólo lo que el código común necesitaba de `Log`: errores y avisos.
 */
expect object AppLog {
    fun e(tag: String, message: String, error: Throwable? = null)

    fun w(tag: String, message: String, error: Throwable? = null)
}
