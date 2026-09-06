package com.moralesDev.dsForEveryone.data.rom

/** Cabeceras y banners construidos a mano, con su CRC-16 calculado de verdad. */
object DsRomFixtures {

    fun validHeaderBytes(
        title: String = "TESTGAME",
        gameCode: String = "ATSE",
        makerCode: String = "01",
        bannerOffset: Long = 0x4000L,
        size: Int = DsRomHeader.SIZE,
    ): ByteArray {
        val bytes = ByteArray(size)
        writeAscii(bytes, 0x000, title, 12)
        writeAscii(bytes, 0x00C, gameCode, 4)
        writeAscii(bytes, 0x010, makerCode, 2)
        writeUInt32LE(bytes, 0x068, bannerOffset)
        writeUInt16LE(bytes, 0x15E, crc16(bytes, 0, 0x15E))
        return bytes
    }

    /** Todo el icono con el índice de paleta 1 (rojo puro), y los títulos que se pidan. */
    fun validBannerBytes(
        titles: Map<BannerLanguage, String> = mapOf(BannerLanguage.ENGLISH to "Test Game"),
    ): ByteArray {
        val bytes = ByteArray(DsBanner.SIZE)
        writeUInt16LE(bytes, 0x000, 1) // versión
        for (index in 0 until 512) bytes[0x020 + index] = 0x11.toByte() // ambos nibbles = índice 1

        writeUInt16LE(bytes, 0x220, 0x0000) // color 0: transparente, no importa el valor
        writeUInt16LE(bytes, 0x222, 0x001F) // color 1: rojo puro en BGR555
        for (index in 2 until 16) writeUInt16LE(bytes, 0x220 + index * 2, 0x0000)

        for ((language, text) in titles) {
            writeUtf16(bytes, 0x240 + language.ordinal * 0x100, text)
        }

        writeUInt16LE(bytes, 0x002, crc16(bytes, 0x020, 0x820))
        return bytes
    }

    /** Cabecera + banner en su desplazamiento real + relleno hasta el final, como una ROM de verdad. */
    fun validRomBytes(
        title: String = "TESTGAME",
        gameCode: String = "ATSE",
        bannerOffset: Long = 0x4000L,
        titles: Map<BannerLanguage, String> = mapOf(BannerLanguage.ENGLISH to "Test Game"),
        totalSize: Int = (bannerOffset + DsBanner.SIZE + 4096).toInt(),
    ): ByteArray {
        val rom = ByteArray(totalSize) { 0xFF.toByte() }
        validHeaderBytes(title = title, gameCode = gameCode, bannerOffset = bannerOffset).copyInto(rom)
        validBannerBytes(titles = titles).copyInto(rom, bannerOffset.toInt())
        return rom
    }

    private fun writeAscii(bytes: ByteArray, offset: Int, text: String, length: Int) {
        for (index in 0 until length) {
            bytes[offset + index] = if (index < text.length) text[index].code.toByte() else 0
        }
    }

    private fun writeUtf16(bytes: ByteArray, offset: Int, text: String) {
        for (index in text.indices) writeUInt16LE(bytes, offset + index * 2, text[index].code)
    }

    private fun writeUInt16LE(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }

    private fun writeUInt32LE(bytes: ByteArray, offset: Int, value: Long) {
        bytes[offset] = (value and 0xFF).toByte()
        bytes[offset + 1] = ((value shr 8) and 0xFF).toByte()
        bytes[offset + 2] = ((value shr 16) and 0xFF).toByte()
        bytes[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
