package com.moralesDev.dsForEveryone.data.rom

import com.moralesDev.dsForEveryone.data.hash.Sha256
import com.moralesDev.dsForEveryone.data.hash.toHex

/**
 * Identidad estable de una ROM, calculada por bloques: SHA-256 de su
 * contenido completo y, en el mismo recorrido, su cabecera y su banner.
 *
 * El identificador no puede depender del nombre del archivo. El usuario
 * renombra, mueve y vuelve a descargar sus ROMs; si la partida colgara del
 * nombre, dos archivos con el mismo contenido y distinto nombre serían dos
 * juegos, y dos archivos distintos con el mismo nombre compartirían partida.
 * El hash del contenido elimina las dos posibilidades.
 *
 * No lee nada por su cuenta: quien lo usa le va entregando bloques con
 * [update] —desde un `InputStream` en Android (RomIdentity), desde un
 * archivo abierto en iOS— y al final pide [finish]. Así la ROM nunca se
 * carga entera en memoria (una de DS llega a varios cientos de MB).
 *
 * El banner vive en un desplazamiento variable que sólo se conoce tras leer
 * la cabecera (los primeros [DsRomHeader.SIZE] bytes, que en la práctica
 * siempre caen dentro del primer bloque). A partir de ahí, cada bloque que
 * llega se recorta contra la ventana `[bannerOffset, bannerOffset +
 * DsBanner.SIZE)` y la parte que se solape se copia a su sitio: así no hace
 * falta retroceder en el flujo ni cargar nada de más.
 */
class RomFingerprinter {
    private val digest = Sha256()
    private val header = ByteArray(DsRomHeader.SIZE)
    private var headerFilled = 0
    private var total = 0L

    private var bannerOffset = -1L
    private var banner: ByteArray? = null
    private var bannerFilled = 0

    /** Los siguientes [read] bytes de la ROM, desde el principio de [buffer]. */
    fun update(buffer: ByteArray, read: Int) {
        if (read <= 0) return
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

    /** Cierra el cálculo. Se llama una vez, después del último bloque. */
    fun finish(): RomFingerprint {
        val parsedHeader = if (headerFilled == header.size) DsRomHeader.parse(header) else null
        val validHeader = parsedHeader?.takeIf { it.isValid }
        val parsedBanner = banner?.takeIf { bannerFilled == it.size }?.let(DsBanner::parse)

        return RomFingerprint(
            id = digest.digest().toHex(),
            sizeBytes = total,
            header = validHeader,
            banner = parsedBanner,
        )
    }

    companion object {
        /** Tamaño de bloque recomendado para leer la ROM. */
        const val BUFFER_SIZE = 64 * 1024
    }
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
