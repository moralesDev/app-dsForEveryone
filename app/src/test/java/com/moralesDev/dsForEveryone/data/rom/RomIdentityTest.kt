package com.moralesDev.dsForEveryone.data.rom

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomIdentityTest {

    private val bannerOffset = 0x4000L

    /** Cabecera + banner en su desplazamiento real + relleno hasta [totalSize], como una ROM de verdad. */
    private fun buildRom(
        header: ByteArray = DsRomFixtures.validHeaderBytes(bannerOffset = bannerOffset),
        banner: ByteArray? = DsRomFixtures.validBannerBytes(),
        totalSize: Int = (bannerOffset + DsBanner.SIZE + 4096).toInt(),
    ): ByteArray {
        val rom = ByteArray(totalSize) { 0xFF.toByte() } // relleno como una ROM real, no ceros
        header.copyInto(rom, 0)
        banner?.copyInto(rom, bannerOffset.toInt())
        return rom
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `una rom bien formada da hash, cabecera y banner correctos`() {
        val rom = buildRom()

        val fingerprint = RomIdentity.read(ByteArrayInputStream(rom))

        assertEquals(sha256Hex(rom), fingerprint.id)
        assertEquals(rom.size.toLong(), fingerprint.sizeBytes)
        assertTrue(fingerprint.isValid)
        assertNotNull(fingerprint.header)
        assertEquals(bannerOffset, fingerprint.header!!.bannerOffset)
        assertNotNull(fingerprint.banner)
        assertEquals("Test Game", fingerprint.banner!!.titles[BannerLanguage.ENGLISH])
    }

    @Test
    fun `el hash no depende de donde caigan los limites de bloque de lectura`() {
        val rom = buildRom()

        // Un InputStream que sólo entrega un byte por lectura fuerza a RomIdentity
        // a cruzar el desplazamiento del banner en cientos de bloques distintos.
        val oneByteAtATime = object : ByteArrayInputStream(rom) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 1))
        }

        val fingerprint = RomIdentity.read(oneByteAtATime)

        assertEquals(sha256Hex(rom), fingerprint.id)
        assertNotNull(fingerprint.banner)
        assertEquals("Test Game", fingerprint.banner!!.titles[BannerLanguage.ENGLISH])
    }

    @Test
    fun `una cabecera con el checksum estropeado no da una identidad valida`() {
        val header = DsRomFixtures.validHeaderBytes(bannerOffset = bannerOffset)
        header[0x050] = (header[0x050] + 1).toByte()
        val rom = buildRom(header = header)

        val fingerprint = RomIdentity.read(ByteArrayInputStream(rom))

        assertFalse(fingerprint.isValid)
        assertNull(fingerprint.header)
    }

    @Test
    fun `una rom cortada antes del banner no produce banner, pero conserva la cabecera`() {
        val rom = buildRom().copyOf((bannerOffset - 10).toInt())

        val fingerprint = RomIdentity.read(ByteArrayInputStream(rom))

        assertTrue(fingerprint.isValid)
        assertNull(fingerprint.banner)
    }

    @Test
    fun `una rom cortada a mitad del banner no produce banner`() {
        val rom = buildRom().copyOf((bannerOffset + DsBanner.SIZE / 2).toInt())

        val fingerprint = RomIdentity.read(ByteArrayInputStream(rom))

        assertTrue(fingerprint.isValid)
        assertNull(fingerprint.banner)
    }

    @Test
    fun `un cartucho sin banner declarado no intenta leer ninguno`() {
        val rom = buildRom(header = DsRomFixtures.validHeaderBytes(bannerOffset = 0L), banner = null)

        val fingerprint = RomIdentity.read(ByteArrayInputStream(rom))

        assertTrue(fingerprint.isValid)
        assertNull(fingerprint.banner)
    }

    @Test
    fun `un archivo que no es una rom de DS no da una identidad valida`() {
        val notARom = ByteArray(4096) { (it % 256).toByte() }

        val fingerprint = RomIdentity.read(ByteArrayInputStream(notARom))

        assertFalse(fingerprint.isValid)
        assertNull(fingerprint.header)
        assertNull(fingerprint.banner)
        assertEquals(sha256Hex(notARom), fingerprint.id)
    }
}
