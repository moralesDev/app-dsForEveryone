package com.moralesDev.dsForEveryone.data.storage

import okio.FileSystem
import okio.IOException
import okio.Path

/**
 * Escribe el archivo de forma atómica: primero en un temporal, luego `rename`.
 *
 * Importa más de lo que parece. Una partida representa decenas de horas y se
 * sobrescribe mientras el juego está corriendo; si el proceso muere a mitad de
 * una escritura directa, lo que queda en disco es medio archivo, es decir, una
 * partida perdida. Con esta forma o queda la versión anterior o queda la
 * nueva, nunca una mezcla.
 *
 * El temporal se escribe con [writeDurably], que cada plataforma implementa
 * con su propio `fsync`: sin él el `rename` puede llegar al disco antes que
 * los datos. No se usa el `FileHandle.flush()` de Okio porque en iOS sólo
 * hace `fflush` (vacía el búfer de C, no el disco); en la JVM sí sincroniza.
 *
 * @throws IOException si no se pudo escribir el temporal.
 * @throws IllegalStateException si no se pudo reemplazar el archivo: el
 *   temporal se borra y el archivo anterior queda intacto.
 */
fun writeAtomically(path: Path, bytes: ByteArray) {
    val fileSystem = FileSystem.SYSTEM
    val parent = path.parent
    parent?.let(fileSystem::createDirectories)
    val temporary = (parent ?: error("Ruta sin carpeta: $path")) / "${path.name}.tmp"
    writeDurably(temporary, bytes)
    try {
        fileSystem.atomicMove(temporary, path)
    } catch (error: IOException) {
        fileSystem.delete(temporary, mustExist = false)
        error("No se pudo reemplazar $path")
    }
}

/**
 * Escribe [bytes] en [path] (creándolo o truncándolo) y no vuelve hasta que
 * están en el disco de verdad.
 */
internal expect fun writeDurably(path: Path, bytes: ByteArray)
