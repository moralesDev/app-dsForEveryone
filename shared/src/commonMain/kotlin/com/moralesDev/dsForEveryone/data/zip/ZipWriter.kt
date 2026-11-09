package com.moralesDev.dsForEveryone.data.zip

import com.moralesDev.dsForEveryone.data.hash.Crc32
import okio.Buffer
import okio.BufferedSink
import okio.Closeable
import okio.Deflater
import okio.DeflaterSink
import okio.IOException
import okio.Sink
import okio.buffer
import okio.use

/**
 * Escribe un `.zip` normal, entrada a entrada, en cualquier `Sink` de Okio.
 *
 * Existe porque iOS no trae ninguna API de zip y la copia de partidas
 * (SaveArchive) tiene que producir el mismo archivo en las dos
 * plataformas: una copia hecha en un Android se importa en un iPad y al
 * revés. Lo comprueba ZipInteropTest en la JVM contra `java.util.zip`.
 *
 * Lo justo para eso, sin más:
 *
 * - Cada entrada llega entera en memoria ([writeEntry]): son partidas, de
 *   como mucho unos 32 MB. Así los tamaños y el CRC se conocen antes de
 *   escribir la cabecera, y no hace falta el «descriptor de datos» que usa
 *   `ZipOutputStream` cuando escribe sin saberlos.
 * - Siempre deflate (método 8), nombres en UTF-8 (bit 11).
 * - Sin ZIP64: si algo pasara de 4 GB, [writeEntry] falla con IOException
 *   en vez de escribir un zip que nadie sabría leer.
 *
 * [close] escribe el directorio central y cierra el `Sink`.
 *
 * @param modifiedAtMillis la fecha que llevan todas las entradas (la de la
 *   exportación), en milisegundos desde 1970, en UTC.
 */
class ZipWriter(sink: Sink, modifiedAtMillis: Long) : Closeable {
    private val out: BufferedSink = sink.buffer()
    private val central = Buffer()
    private val dosTime: Int
    private val dosDate: Int
    private var offset = 0L
    private var entries = 0
    private var closed = false

    init {
        val (date, time) = toDosDateTime(modifiedAtMillis)
        dosDate = date
        dosTime = time
    }

    fun writeEntry(name: String, data: ByteArray) {
        check(!closed) { "ZipWriter cerrado" }
        val nameBytes = name.encodeToByteArray()
        val crc = Crc32().apply { update(data) }.value
        val compressed = Buffer()
        DeflaterSink(compressed, Deflater(DEFLATE_LEVEL, true) /* deflate crudo, sin cabecera zlib */).use { deflater ->
            deflater.write(Buffer().write(data), data.size.toLong())
        }
        val compressedSize = compressed.size
        if (offset > MAX_32 || compressedSize > MAX_32 || data.size.toLong() > MAX_32 || entries == MAX_ENTRIES) {
            throw IOException("La copia no cabe en un zip sin ZIP64")
        }

        // Cabecera local.
        out.writeIntLe(LOCAL_HEADER)
        out.writeShortLe(VERSION)
        out.writeShortLe(FLAG_UTF8)
        out.writeShortLe(METHOD_DEFLATED)
        out.writeShortLe(dosTime)
        out.writeShortLe(dosDate)
        out.writeIntLe(crc.toInt())
        out.writeIntLe(compressedSize.toInt())
        out.writeIntLe(data.size)
        out.writeShortLe(nameBytes.size)
        out.writeShortLe(0) // sin campo extra
        out.write(nameBytes)
        out.writeAll(compressed)

        // Su copia en el directorio central, que se escribe al cerrar.
        central.writeIntLe(CENTRAL_HEADER)
        central.writeShortLe(VERSION) // creado por
        central.writeShortLe(VERSION) // necesario para extraer
        central.writeShortLe(FLAG_UTF8)
        central.writeShortLe(METHOD_DEFLATED)
        central.writeShortLe(dosTime)
        central.writeShortLe(dosDate)
        central.writeIntLe(crc.toInt())
        central.writeIntLe(compressedSize.toInt())
        central.writeIntLe(data.size)
        central.writeShortLe(nameBytes.size)
        central.writeShortLe(0) // extra
        central.writeShortLe(0) // comentario
        central.writeShortLe(0) // disco
        central.writeShortLe(0) // atributos internos
        central.writeIntLe(0) // atributos externos
        central.writeIntLe(offset.toInt())
        central.write(nameBytes)

        offset += LOCAL_HEADER_BYTES + nameBytes.size + compressedSize
        entries++
    }

    override fun close() {
        if (closed) return
        closed = true
        val centralSize = central.size
        if (offset > MAX_32 || centralSize > MAX_32) throw IOException("La copia no cabe en un zip sin ZIP64")
        out.writeAll(central)
        out.writeIntLe(END_OF_CENTRAL)
        out.writeShortLe(0) // este disco
        out.writeShortLe(0) // disco del directorio
        out.writeShortLe(entries)
        out.writeShortLe(entries)
        out.writeIntLe(centralSize.toInt())
        out.writeIntLe(offset.toInt())
        out.writeShortLe(0) // comentario
        out.close()
    }

    private companion object {
        const val DEFLATE_LEVEL = 6 // el de ZipOutputStream por defecto
        const val VERSION = 20
        const val FLAG_UTF8 = 0x0800
        const val METHOD_DEFLATED = 8
        const val LOCAL_HEADER_BYTES = 30
        const val MAX_32 = 0xFFFFFFFFL
        const val MAX_ENTRIES = 0xFFFF
    }
}

internal const val LOCAL_HEADER = 0x04034b50
internal const val CENTRAL_HEADER = 0x02014b50
internal const val END_OF_CENTRAL = 0x06054b50

/**
 * Fecha y hora en el formato de MS-DOS que usa zip (resolución de 2 s, desde
 * 1980), en UTC. Antes de 1980 se escribe el 1 de enero de 1980.
 */
internal fun toDosDateTime(epochMillis: Long): Pair<Int, Int> {
    val totalSeconds = epochMillis.floorDiv(1000L)
    val days = totalSeconds.floorDiv(86_400L)
    val secondsOfDay = (totalSeconds - days * 86_400L).toInt()
    // Días desde 1970 a fecha civil (algoritmo de Howard Hinnant).
    val z = days + 719_468
    val era = z.floorDiv(146_097)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
    val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
    val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
    if (year < 1980) return ((0 shl 9) or (1 shl 5) or 1) to 0
    val date = ((year - 1980) shl 9) or (month shl 5) or day
    val time = ((secondsOfDay / 3600) shl 11) or (((secondsOfDay / 60) % 60) shl 5) or ((secondsOfDay % 60) / 2)
    return date to time
}
