package com.moralesDev.dsForEveryone.data.zip

import com.moralesDev.dsForEveryone.data.hash.Crc32
import okio.Buffer
import okio.BufferedSource
import okio.Closeable
import okio.EOFException
import okio.FileHandle
import okio.FileSystem
import okio.IOException
import okio.Inflater
import okio.InflaterSource
import okio.Path
import okio.Source
import okio.Timeout
import okio.buffer

/** El archivo dice ser un zip pero no lo es bien: lo que antes era `ZipException`. */
class ZipFormatException(message: String) : IOException(message)

/**
 * Lee un `.zip` que ya está en disco, entrada a entrada y en su orden.
 *
 * Sustituye a `ZipInputStream`, que es de Java, para que la copia de
 * partidas (SaveArchive) funcione igual en iOS. No lee del flujo en orden
 * como aquél: el `InflaterSource` público de Okio envuelve su origen en un
 * búfer que se lleva bytes de más, y en un zip leído en orden esos bytes
 * son la cabecera de la entrada siguiente. En su lugar parte del
 * **directorio central**, que da el tamaño comprimido exacto de cada
 * entrada, y descomprime cada una desde un trozo acotado del archivo.
 *
 * Lo que comprueba, como `ZipInputStream` o más:
 *
 * - Un archivo que no empieza como un zip no tiene entradas ([entries]
 *   vacía): igual que `ZipInputStream`, que devuelve `null` a la primera.
 * - Cada entrada del directorio central tiene que tener su cabecera local
 *   donde dice, con el mismo nombre.
 * - Cifrado, métodos que no sean «sin comprimir» o deflate, y ZIP64 se
 *   rechazan con [ZipFormatException].
 * - [read] mide lo descomprimido de verdad, no lo que declara el zip, y
 *   comprueba el CRC y el tamaño al terminar.
 */
class ZipArchiveReader(fileSystem: FileSystem, path: Path) : Closeable {
    private val handle: FileHandle = fileSystem.openReadOnly(path)

    class Entry internal constructor(
        val name: String,
        internal val method: Int,
        internal val crc: Long,
        internal val compressedSize: Long,
        internal val size: Long,
        internal val localHeaderOffset: Long,
    ) {
        val isDirectory: Boolean get() = name.endsWith("/")
    }

    /** Las entradas en el orden del directorio central, que es el de escritura. */
    val entries: List<Entry> = readCentralDirectory()

    /**
     * El contenido de [entry], o `null` si descomprimido pasa de [limit]
     * bytes: se deja de leer en cuanto se nota.
     */
    fun read(entry: Entry, limit: Int): ByteArray? {
        val dataOffset = localDataOffset(entry)
        val raw: Source = BoundedFileSource(handle, dataOffset, entry.compressedSize)
        val out = Buffer()
        val source = when (entry.method) {
            METHOD_STORED -> raw
            // `true`: deflate crudo, sin la cabecera zlib (así va en un zip).
            METHOD_DEFLATED -> InflaterSource(raw, Inflater(true))
            else -> throw ZipFormatException("Método de compresión ${entry.method}")
        }
        try {
            while (true) {
                val read = try {
                    source.read(out, READ_CHUNK)
                } catch (_: EOFException) {
                    throw ZipFormatException("Datos comprimidos incompletos en ${entry.name}")
                }
                if (read == -1L) break
                if (out.size > limit) return null
            }
        } finally {
            source.close()
        }
        val bytes = out.readByteArray()
        if (bytes.size.toLong() != entry.size) throw ZipFormatException("Tamaño distinto en ${entry.name}")
        if (Crc32().apply { update(bytes) }.value != entry.crc) throw ZipFormatException("CRC distinto en ${entry.name}")
        return bytes
    }

    override fun close() = handle.close()

    private fun readCentralDirectory(): List<Entry> {
        val size = handle.size()
        // Como ZipInputStream: si no empieza por una cabecera local, no es un zip.
        if (size < 4 || readAt(0, 4).readIntLe() != LOCAL_HEADER) return emptyList()

        val tailLength = minOf(size, (END_MIN_BYTES + MAX_COMMENT_BYTES).toLong())
        val tail = readAt(size - tailLength, tailLength.toInt()).readByteArray()
        var endIndex = -1
        for (i in tail.size - END_MIN_BYTES downTo 0) {
            if (tail.intLe(i) == END_OF_CENTRAL) {
                endIndex = i
                break
            }
        }
        if (endIndex < 0) throw ZipFormatException("Falta el final del directorio central")

        val count = tail.shortLe(endIndex + 10)
        val centralSize = tail.intLe(endIndex + 12).toLong() and MASK_32
        val centralOffset = tail.intLe(endIndex + 16).toLong() and MASK_32
        if (centralOffset + centralSize > size - tailLength + endIndex) {
            throw ZipFormatException("Directorio central fuera del archivo")
        }

        val central = readAt(centralOffset, centralSize.toInt())
        return List(count) {
            if (central.readIntLe() != CENTRAL_HEADER) throw ZipFormatException("Directorio central dañado")
            central.skip(4) // versiones
            val flags = central.readShortLe().toInt() and 0xFFFF
            val method = central.readShortLe().toInt() and 0xFFFF
            central.skip(4) // hora y fecha
            val crc = central.readIntLe().toLong() and MASK_32
            val compressed = central.readIntLe().toLong() and MASK_32
            val uncompressed = central.readIntLe().toLong() and MASK_32
            val nameLength = central.readShortLe().toInt() and 0xFFFF
            val extraLength = central.readShortLe().toInt() and 0xFFFF
            val commentLength = central.readShortLe().toInt() and 0xFFFF
            central.skip(8) // disco, atributos
            val localOffset = central.readIntLe().toLong() and MASK_32
            val name = central.readByteArray(nameLength.toLong()).decodeToString()
            central.skip((extraLength + commentLength).toLong())

            if (flags and FLAG_ENCRYPTED != 0) throw ZipFormatException("Entrada cifrada: $name")
            if (method != METHOD_STORED && method != METHOD_DEFLATED) {
                throw ZipFormatException("Método de compresión $method en $name")
            }
            if (compressed == MASK_32 || uncompressed == MASK_32 || localOffset == MASK_32) {
                throw ZipFormatException("ZIP64 no admitido: $name")
            }
            Entry(name, method, crc, compressed, uncompressed, localOffset)
        }
    }

    private fun localDataOffset(entry: Entry): Long {
        val header = readAt(entry.localHeaderOffset, LOCAL_HEADER_BYTES)
        if (header.readIntLe() != LOCAL_HEADER) throw ZipFormatException("Falta la cabecera local de ${entry.name}")
        header.skip(22)
        val nameLength = header.readShortLe().toInt() and 0xFFFF
        val extraLength = header.readShortLe().toInt() and 0xFFFF
        val nameStart = entry.localHeaderOffset + LOCAL_HEADER_BYTES
        val localName = readAt(nameStart, nameLength).readByteArray().decodeToString()
        if (localName != entry.name) throw ZipFormatException("Nombres distintos para ${entry.name}")
        return nameStart + nameLength + extraLength
    }

    /** Exactamente [length] bytes desde [offset], o EOF si el archivo no los tiene. */
    private fun readAt(offset: Long, length: Int): Buffer {
        val buffer = Buffer()
        var position = offset
        while (buffer.size < length) {
            val read = handle.read(position, buffer, length - buffer.size)
            if (read == -1L) throw ZipFormatException("El archivo se acaba antes de tiempo")
            position += read
        }
        return buffer
    }

    private companion object {
        const val METHOD_STORED = 0
        const val METHOD_DEFLATED = 8
        const val FLAG_ENCRYPTED = 1
        const val LOCAL_HEADER_BYTES = 30
        const val END_MIN_BYTES = 22
        const val MAX_COMMENT_BYTES = 0xFFFF
        const val MASK_32 = 0xFFFFFFFFL
        const val READ_CHUNK = 64L * 1024
    }
}

private fun ByteArray.intLe(index: Int): Int =
    (this[index].toInt() and 0xFF) or
        ((this[index + 1].toInt() and 0xFF) shl 8) or
        ((this[index + 2].toInt() and 0xFF) shl 16) or
        ((this[index + 3].toInt() and 0xFF) shl 24)

private fun ByteArray.shortLe(index: Int): Int =
    (this[index].toInt() and 0xFF) or ((this[index + 1].toInt() and 0xFF) shl 8)

/**
 * Los [length] bytes del archivo que empiezan en [offset], y ni uno más: es
 * lo que hace inofensivo el búfer de más del `InflaterSource` de Okio.
 */
private class BoundedFileSource(
    private val handle: FileHandle,
    private var position: Long,
    private var remaining: Long,
) : Source {
    override fun read(sink: Buffer, byteCount: Long): Long {
        if (remaining == 0L) return -1L
        val read = handle.read(position, sink, minOf(byteCount, remaining))
        if (read == -1L) throw EOFException("El archivo se acaba dentro de una entrada")
        position += read
        remaining -= read
        return read
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = Unit
}
