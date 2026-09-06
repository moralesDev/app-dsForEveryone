package com.moralesDev.dsForEveryone.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * La garantía central del soporte de `.zip`: el identificador de una ROM sale
 * de su contenido descomprimido, nunca del contenedor. Un mismo juego suelto
 * y comprimido tiene que caer en la misma entrada de `saves/<hash>/`, o
 * pierde la partida al recomprimirse con otra herramienta.
 *
 * Esto es lo que hace que [RomRepository.import] trate un `.zip` ya en la
 * biblioteca como [RomImportResult.AlreadyInLibrary] en vez de duplicarlo, sin
 * que esa clase necesite saber nada de zips: el hash ya le llega igual.
 */
class RomZipIdentityTest {

    @Test
    fun `el mismo contenido produce el mismo hash suelto o dentro de un zip`() {
        val rom = romOf(gameCode = "ATSE")

        val loose = RomIdentity.read(ByteArrayInputStream(rom))

        val zipBytes = zipOf("New Super Mario Bros.nds" to rom)
        val fromZip = (RomZipReader.resolveRom { ByteArrayInputStream(zipBytes) }
            as RomZipReader.Resolution.Single).fingerprint

        assertEquals(loose.id, fromZip.id)
        assertEquals(loose.sizeBytes, fromZip.sizeBytes)
        assertNotNull(loose.header)
        assertEquals(loose.header?.title, fromZip.header?.title)
        assertEquals(loose.header?.gameCode, fromZip.header?.gameCode)
    }

    @Test
    fun `el mismo juego recomprimido con otro nombre interno sigue dando el mismo hash`() {
        // Dos .zip "distintos" del mismo juego: cambia el nombre de la entrada
        // y el nivel de compresión, nunca los bytes de la ROM. El hash no debe
        // notar la diferencia.
        val rom = romOf(gameCode = "BSME")

        val zipA = zipOf("rom.nds" to rom, level = 0)
        val zipB = zipOf("carpeta/Juego (v2).NDS" to rom, level = 9)

        fun hashOf(zipBytes: ByteArray): String =
            (RomZipReader.resolveRom { ByteArrayInputStream(zipBytes) } as RomZipReader.Resolution.Single)
                .fingerprint.id

        assertEquals(hashOf(zipA), hashOf(zipB))
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>, level: Int = -1): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).apply { if (level >= 0) setLevel(level) }.use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** Cabecera y banner válidos de verdad, con relleno detrás como una ROM real. */
    private fun romOf(gameCode: String): ByteArray {
        val bannerOffset = 0x4000L
        val rom = ByteArray((bannerOffset + DsBanner.SIZE + 4096).toInt()) { 0xFF.toByte() }
        DsRomFixtures.validHeaderBytes(gameCode = gameCode, bannerOffset = bannerOffset).copyInto(rom)
        DsRomFixtures.validBannerBytes().copyInto(rom, bannerOffset.toInt())
        return rom
    }
}
