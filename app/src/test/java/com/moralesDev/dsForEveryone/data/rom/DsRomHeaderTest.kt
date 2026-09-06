package com.moralesDev.dsForEveryone.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DsRomHeaderTest {

    @Test
    fun `una cabecera bien formada se lee y se marca valida`() {
        val bytes = DsRomFixtures.validHeaderBytes(
            title = "NEW MARIOBROS",
            gameCode = "ASME",
            makerCode = "01",
            bannerOffset = 0x8000L,
        )

        val header = DsRomHeader.parse(bytes)

        requireNotNull(header)
        // El título ASCII se recorta a los 12 bytes reales del campo.
        assertEquals("NEW MARIOBRO", header.title)
        assertEquals("ASME", header.gameCode)
        assertEquals("01", header.makerCode)
        assertEquals(0x8000L, header.bannerOffset)
        assertTrue(header.isValid)
    }

    @Test
    fun `una cabecera con el checksum estropeado no se marca valida`() {
        val bytes = DsRomFixtures.validHeaderBytes()
        bytes[0x050] = (bytes[0x050] + 1).toByte() // altera un byte cubierto por el CRC

        val header = DsRomHeader.parse(bytes)

        requireNotNull(header)
        assertFalse(header.isValid)
    }

    @Test
    fun `una cabecera truncada no se lee`() {
        val bytes = DsRomFixtures.validHeaderBytes().copyOf(DsRomHeader.SIZE - 1)

        assertNull(DsRomHeader.parse(bytes))
    }

    @Test
    fun `bannerOffset en 0 significa que no hay banner, y la cabecera sigue siendo valida`() {
        val bytes = DsRomFixtures.validHeaderBytes(bannerOffset = 0L)

        val header = DsRomHeader.parse(bytes)

        requireNotNull(header)
        assertEquals(0L, header.bannerOffset)
        assertTrue(header.isValid)
    }
}
