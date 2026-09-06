package com.moralesDev.dsForEveryone.data.rom

/**
 * Cabecera del cartucho de Nintendo DS, según el mapa de campos verificado
 * contra GBATEK (`gbatek-ds-cartridge-header.htm`).
 *
 * Sirve para dos cosas: comprobar que el archivo elegido es realmente una ROM
 * de DS antes de intentar arrancarla, y encontrar el banner (icono y título en
 * varios idiomas) sin adivinar nada, porque su desplazamiento va dentro de la
 * propia cabecera.
 */
data class DsRomHeader(
    /** Título interno, hasta 12 caracteres ASCII (p. ej. `NEW MARIOBROS`). */
    val title: String,
    /** Código de juego de 4 caracteres (p. ej. `ASME` para New Super Mario Bros.). */
    val gameCode: String,
    val makerCode: String,
    /**
     * Desplazamiento absoluto al banner dentro de la ROM. `0` si el cartucho
     * no declara ninguno (algún homebrew antiguo): en ese caso no hay banner
     * que leer, pero la cabecera sigue siendo válida.
     */
    val bannerOffset: Long,
    /** `true` si la comprobación de la cabecera (CRC-16 de `[0x000, 0x15E)`) cuadra. */
    val isValid: Boolean,
) {
    companion object {
        /** Hasta el final del campo de comprobación (`0x15E`, 2 bytes): 352 bytes. */
        const val SIZE = 0x160

        private const val TITLE_OFFSET = 0x000
        private const val TITLE_LENGTH = 12
        private const val GAME_CODE_OFFSET = 0x00C
        private const val GAME_CODE_LENGTH = 4
        private const val MAKER_CODE_OFFSET = 0x010
        private const val MAKER_CODE_LENGTH = 2
        private const val BANNER_OFFSET_FIELD = 0x068
        private const val CHECKSUM_COVERAGE = 0x15E
        private const val CHECKSUM_OFFSET = 0x15E

        fun parse(bytes: ByteArray): DsRomHeader? {
            if (bytes.size < SIZE) return null

            val expectedChecksum = bytes.readUInt16LE(CHECKSUM_OFFSET)
            val actualChecksum = crc16(bytes, 0, CHECKSUM_COVERAGE)

            return DsRomHeader(
                title = bytes.dsAscii(TITLE_OFFSET, TITLE_LENGTH),
                gameCode = bytes.dsAscii(GAME_CODE_OFFSET, GAME_CODE_LENGTH),
                makerCode = bytes.dsAscii(MAKER_CODE_OFFSET, MAKER_CODE_LENGTH),
                bannerOffset = bytes.readUInt32LE(BANNER_OFFSET_FIELD),
                isValid = actualChecksum == expectedChecksum,
            )
        }
    }
}

/**
 * CRC-16 con el que la DS comprueba tanto la cabecera como el banner: init
 * `0xFFFF`, polinomio reflejado `0xA001` (equivalente a CRC-16/MODBUS). Es la
 * función `GetCRC16` de la BIOS, documentada en GBATEK.
 */
internal fun crc16(bytes: ByteArray, offset: Int, length: Int): Int {
    var crc = 0xFFFF
    for (index in offset until offset + length) {
        crc = crc xor (bytes[index].toInt() and 0xFF)
        repeat(8) {
            crc = if (crc and 1 != 0) (crc ushr 1) xor 0xA001 else crc ushr 1
        }
    }
    return crc and 0xFFFF
}

internal fun ByteArray.readUInt16LE(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)

internal fun ByteArray.readUInt32LE(offset: Int): Long =
    (this[offset].toLong() and 0xFF) or
        ((this[offset + 1].toLong() and 0xFF) shl 8) or
        ((this[offset + 2].toLong() and 0xFF) shl 16) or
        ((this[offset + 3].toLong() and 0xFF) shl 24)

/** Bytes imprimibles de `[offset, offset+length)`; el resto (relleno `0x00`) se descarta. */
internal fun ByteArray.dsAscii(offset: Int, length: Int): String =
    buildString(length) {
        for (index in offset until offset + length) {
            val code = this@dsAscii[index].toInt() and 0xFF
            if (code in 0x20..0x7E) append(code.toChar())
        }
    }.trim()
