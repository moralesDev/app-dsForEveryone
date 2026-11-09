package com.moralesDev.dsForEveryone.data.rom

import java.io.InputStream
import java.util.zip.CRC32

/**
 * Identidad de una ROM leída desde un `InputStream` de Android.
 *
 * El cálculo en sí —SHA-256 del contenido, cabecera y banner en el mismo
 * recorrido— vive en [RomFingerprinter], en el módulo común, y es el mismo
 * en iOS. Aquí sólo queda lo propio de Android: ir sacando bloques del
 * flujo que entrega SAF.
 */
object RomIdentity {

    /** Ver [RomFingerprinter]: lee por bloques, sin cargar la ROM entera en memoria. */
    fun read(stream: InputStream): RomFingerprint {
        val fingerprinter = RomFingerprinter()
        val buffer = ByteArray(RomFingerprinter.BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read <= 0) break
            fingerprinter.update(buffer, read)
        }
        return fingerprinter.finish()
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

    /**
     * Igual que [crc32] pero leyendo por bloques desde [stream], sin cargar
     * el archivo entero en memoria. Para cuando la ROM se entrega al core por
     * descriptor ([RomRepository.RomSource.Descriptor]) en vez de por
     * `ByteArray`: ese descriptor ya viaja hacia el core, así que la
     * identidad del save state necesita su propio flujo.
     */
    fun crc32(stream: InputStream): Long {
        val crc = CRC32()
        val buffer = ByteArray(RomFingerprinter.BUFFER_SIZE)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            crc.update(buffer, 0, read)
        }
        return crc.value
    }
}
