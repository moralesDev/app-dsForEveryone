package com.moralesDev.dsForEveryone.data.rom

/** Idiomas del banner, en el mismo orden en que aparecen en el archivo. */
enum class BannerLanguage { JAPANESE, ENGLISH, FRENCH, GERMAN, ITALIAN, SPANISH, CHINESE, KOREAN }

/**
 * Banner de un cartucho: el icono de 32×32 ya convertido a píxeles ARGB (listo
 * para `Bitmap.createBitmap(IntArray, ...)`) y el título en cada idioma que el
 * cartucho declara. Mapa de campos verificado contra GBATEK
 * (`gbatek-ds-cartridge-icon-title.htm`).
 *
 * Convertir el icono a bitmap es aritmética, no una biblioteca: son cuatro
 * tiles de 8×8 en 4 bpp con una paleta de 16 colores.
 */
data class DsBanner(
    /** 32×32 píxeles ARGB, fila a fila. El color de paleta 0 es transparente. */
    val icon: IntArray,
    /** Sólo los idiomas que el cartucho rellenó; los vacíos no aparecen. */
    val titles: Map<BannerLanguage, String>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DsBanner) return false
        return icon.contentEquals(other.icon) && titles == other.titles
    }

    override fun hashCode(): Int = 31 * icon.contentHashCode() + titles.hashCode()

    companion object {
        const val ICON_SIZE = 32

        /** Tamaño total conocido del banner, hasta el final del hueco para coreano. */
        const val SIZE = 0x0A40

        private const val VERSION_CRC_OFFSET = 0x002
        private const val CRC_COVERAGE_START = 0x020
        private const val CRC_COVERAGE_LENGTH = 0x0820 // [0x020, 0x83F], todas las versiones

        private const val ICON_TILES_OFFSET = 0x020
        private const val ICON_PALETTE_OFFSET = 0x220
        private const val PALETTE_COLORS = 16

        private const val TITLE_BASE_OFFSET = 0x240
        private const val TITLE_SLOT_SIZE = 0x100
        private const val TITLE_MAX_CHARS = 128

        private const val TILES_PER_ROW = 4
        private const val TILE_PIXELS = 8
        private const val BYTES_PER_TILE = 32

        /**
         * `null` si el archivo no llega a [SIZE] bytes o si el CRC-16 grabado en
         * el banner no cuadra con su contenido: negarse a leer un banner mal
         * formado en vez de mostrar un icono con basura.
         */
        fun parse(bytes: ByteArray): DsBanner? {
            if (bytes.size < SIZE) return null

            val expectedCrc = bytes.readUInt16LE(VERSION_CRC_OFFSET)
            val actualCrc = crc16(bytes, CRC_COVERAGE_START, CRC_COVERAGE_LENGTH)
            if (actualCrc != expectedCrc) return null

            val palette = IntArray(PALETTE_COLORS) { index ->
                bgr555ToArgb(bytes.readUInt16LE(ICON_PALETTE_OFFSET + index * 2), transparent = index == 0)
            }

            return DsBanner(
                icon = decodeIcon(bytes, palette),
                titles = BannerLanguage.entries.mapNotNull { language ->
                    val text = decodeTitle(bytes, TITLE_BASE_OFFSET + language.ordinal * TITLE_SLOT_SIZE)
                    if (text.isEmpty()) null else language to text
                }.toMap(),
            )
        }

        /**
         * 4×4 tiles de 8×8 píxeles en 4 bpp (2 píxeles por byte, el nibble bajo
         * primero). `tileIndex = fila*4 + columna`, cada tile ocupa 32 bytes.
         */
        private fun decodeIcon(bytes: ByteArray, palette: IntArray): IntArray {
            val pixels = IntArray(ICON_SIZE * ICON_SIZE)
            for (tileRow in 0 until TILES_PER_ROW) {
                for (tileCol in 0 until TILES_PER_ROW) {
                    val tileOffset = ICON_TILES_OFFSET + (tileRow * TILES_PER_ROW + tileCol) * BYTES_PER_TILE
                    for (row in 0 until TILE_PIXELS) {
                        for (pair in 0 until TILE_PIXELS / 2) {
                            val byteValue = bytes[tileOffset + row * (TILE_PIXELS / 2) + pair].toInt() and 0xFF
                            val px = tileCol * TILE_PIXELS + pair * 2
                            val py = tileRow * TILE_PIXELS + row
                            pixels[py * ICON_SIZE + px] = palette[byteValue and 0x0F]
                            pixels[py * ICON_SIZE + px + 1] = palette[(byteValue shr 4) and 0x0F]
                        }
                    }
                }
            }
            return pixels
        }

        /** UTF-16 sin BOM, terminado en `0x0000` o hasta [TITLE_MAX_CHARS] caracteres. */
        private fun decodeTitle(bytes: ByteArray, offset: Int): String {
            val builder = StringBuilder(TITLE_MAX_CHARS)
            for (index in 0 until TITLE_MAX_CHARS) {
                val code = bytes.readUInt16LE(offset + index * 2)
                if (code == 0) break
                builder.append(code.toChar())
            }
            return builder.toString().trim()
        }

        /** Paleta de la DS en BGR555: bits 0-4 rojo, 5-9 verde, 10-14 azul, bit 15 sin usar. */
        private fun bgr555ToArgb(color: Int, transparent: Boolean): Int {
            if (transparent) return 0x00000000
            val r = (color and 0x1F) * 255 / 31
            val g = ((color shr 5) and 0x1F) * 255 / 31
            val b = ((color shr 10) and 0x1F) * 255 / 31
            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
