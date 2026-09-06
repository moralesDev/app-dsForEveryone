package com.moralesDev.dsForEveryone.data.storage

import java.io.File
import java.io.FileOutputStream

/**
 * Escribe el archivo de forma atómica: primero en un temporal, luego `rename`.
 *
 * Importa más de lo que parece. Una partida representa decenas de horas y se
 * sobrescribe mientras el juego está corriendo; si el proceso muere a mitad de
 * una escritura directa, lo que queda en disco es medio archivo, es decir, una
 * partida perdida. Con esta forma o queda la versión anterior o queda la
 * nueva, nunca una mezcla.
 */
fun File.writeAtomically(bytes: ByteArray) {
    parentFile?.mkdirs()
    val temporary = File(parentFile, "$name.tmp")
    FileOutputStream(temporary).use { stream ->
        stream.write(bytes)
        stream.flush()
        // Sin fsync el rename puede llegar al disco antes que los datos.
        stream.fd.sync()
    }
    if (!temporary.renameTo(this)) {
        temporary.delete()
        error("No se pudo reemplazar $absolutePath")
    }
}
