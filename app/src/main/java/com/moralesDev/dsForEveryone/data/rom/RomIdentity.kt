package com.moralesDev.dsForEveryone.data.rom

import java.io.InputStream
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Identidad estable de una ROM: SHA-256 de su contenido completo.
 *
 * El identificador no puede depender del nombre del archivo. El usuario
 * renombra, mueve y vuelve a descargar sus ROMs; si la partida colgara del
 * nombre, dos archivos con el mismo contenido y distinto nombre serían dos
 * juegos, y dos archivos distintos con el mismo nombre compartirían partida.
 * El hash del contenido elimina las dos posibilidades.
 */
object RomIdentity {

    /**
     * Calcula el hash leyendo por bloques, sin cargar la ROM entera en memoria
     * (una ROM de DS llega a varios cientos de MB), y en el mismo recorrido
     * extrae la cabecera y el banner.
     *
     * El banner vive en un desplazamiento variable que sólo se conoce tras leer
     * la cabecera (los primeros [DsRomHeader.SIZE] bytes, que en la práctica
     * siempre caen dentro del primer bloque). A partir de ahí, cada bloque que
     * llega se recorta contra la ventana `[bannerOffset, bannerOffset +
     * DsBanner.SIZE)` y la parte que se solape se copia a su sitio: así no hace
     * falta retroceder en el flujo ni cargar nada de más.
     */
    fun read(stream: InputStream): RomFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        val header = ByteArray(DsRomHeader.SIZE)
        var headerFilled = 0
        var total = 0L

        var bannerOffset = -1L
        var banner: ByteArray? = null
        var bannerFilled = 0

        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            val chunkStart = total
            digest.update(buffer, 0, read)
            total += read

            if (headerFilled < header.size) {
                val take = minOf(read, header.size - headerFilled)
                buffer.copyInto(header, headerFilled, 0, take)
                headerFilled += take
            }

            if (bannerOffset < 0 && headerFilled == header.size) {
                val offset = DsRomHeader.parse(header)?.bannerOffset ?: 0L
                if (offset > 0) {
                    bannerOffset = offset
                    banner = ByteArray(DsBanner.SIZE)
                }
            }

            banner?.let { bannerBuffer ->
                val chunkEnd = chunkStart + read
                val windowEnd = bannerOffset + bannerBuffer.size
                val overlapStart = maxOf(chunkStart, bannerOffset)
                val overlapEnd = minOf(chunkEnd, windowEnd)
                if (overlapStart < overlapEnd) {
                    val length = (overlapEnd - overlapStart).toInt()
                    buffer.copyInto(
                        destination = bannerBuffer,
                        destinationOffset = (overlapStart - bannerOffset).toInt(),
                        startIndex = (overlapStart - chunkStart).toInt(),
                        endIndex = (overlapStart - chunkStart).toInt() + length,
                    )
                    bannerFilled += length
                }
            }
        }

        val parsedHeader = if (headerFilled == header.size) DsRomHeader.parse(header) else null
        val validHeader = parsedHeader?.takeIf { it.isValid }
        val parsedBanner = banner?.takeIf { bannerFilled == it.size }?.let(DsBanner::parse)

        return RomFingerprint(
            id = digest.digest().toHexString(),
            sizeBytes = total,
            header = validHeader,
            banner = parsedBanner,
        )
    }

    /**
     * CRC32 del contenido, la comprobación rápida que acompaña al hash fuerte.
     *
     * No sustituye al SHA-256 ni pretende hacerlo: es trivial construir dos
     * archivos con el mismo CRC. Suma como segunda condición barata —cabe en
     * cuatro bytes y es la cifra con la que las bases de datos de ROMs
     * identifican los volcados—, de modo que un save state guarde las dos y
     * tengan que coincidir las dos.
     *
     * Se calcula sobre los bytes que se le entregan al core, no sobre los que se
     * leyeron al dar de alta la ROM: si el archivo cambió por debajo, lo que
     * interesa es lo que se está emulando de verdad.
     */
    fun crc32(bytes: ByteArray): Long = CRC32().apply { update(bytes) }.value

    private fun ByteArray.toHexString(): String =
        joinToString("") { byte -> "%02x".format(byte) }

    private const val BUFFER_SIZE = 64 * 1024
}

data class RomFingerprint(
    val id: String,
    val sizeBytes: Long,
    /** `null` si la cabecera no llegó a leerse entera o si su comprobación no cuadra. */
    val header: DsRomHeader?,
    /** `null` si el cartucho no declara banner, o si el suyo está truncado o corrupto. */
    val banner: DsBanner?,
) {
    val isValid: Boolean get() = header != null
}
