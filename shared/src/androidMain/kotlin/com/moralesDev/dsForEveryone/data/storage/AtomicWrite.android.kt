package com.moralesDev.dsForEveryone.data.storage

import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.FileOutputStream

internal actual fun writeDurably(path: Path, bytes: ByteArray) {
    FileOutputStream(path.toFile()).use { stream ->
        stream.write(bytes)
        stream.flush()
        // Sin fsync el rename puede llegar al disco antes que los datos.
        stream.fd.sync()
    }
}

/**
 * La misma escritura atómica sobre un `java.io.File`, para el código que
 * sigue siendo sólo de Android (exportar partidas, portadas) mientras no
 * pase al módulo común. Ver [writeAtomically].
 */
fun File.writeAtomically(bytes: ByteArray) = writeAtomically(toOkioPath(), bytes)
