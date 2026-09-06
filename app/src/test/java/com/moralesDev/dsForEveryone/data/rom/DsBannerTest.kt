package com.moralesDev.dsForEveryone.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DsBannerTest {

    @Test
    fun `un banner bien formado decodifica el icono y los titulos`() {
        val bytes = DsRomFixtures.validBannerBytes(
            titles = mapOf(
                BannerLanguage.JAPANESE to "",
                BannerLanguage.ENGLISH to "Test Game",
                BannerLanguage.SPANISH to "Juego de prueba",
            ),
        )

        val banner = DsBanner.parse(bytes)

        requireNotNull(banner)
        assertEquals(DsBanner.ICON_SIZE * DsBanner.ICON_SIZE, banner.icon.size)
        // Todo el icono se rellenó con el índice de paleta 1: rojo puro opaco.
        assertTrue(banner.icon.all { it == 0xFFFF0000.toInt() })

        // El japonés se pidió vacío y no debe aparecer: sólo cuentan los idiomas rellenados.
        assertEquals(setOf(BannerLanguage.ENGLISH, BannerLanguage.SPANISH), banner.titles.keys)
        assertEquals("Test Game", banner.titles[BannerLanguage.ENGLISH])
        assertEquals("Juego de prueba", banner.titles[BannerLanguage.SPANISH])
    }

    @Test
    fun `un banner con el CRC estropeado no se lee`() {
        val bytes = DsRomFixtures.validBannerBytes()
        bytes[0x300] = (bytes[0x300] + 1).toByte() // dentro del hueco del título japonés, cubierto por el CRC

        assertNull(DsBanner.parse(bytes))
    }

    @Test
    fun `un banner truncado no se lee`() {
        val bytes = DsRomFixtures.validBannerBytes().copyOf(DsBanner.SIZE - 1)

        assertNull(DsBanner.parse(bytes))
    }

    @Test
    fun `dos banners con el mismo contenido son iguales, aunque el icono sea un array distinto`() {
        val a = DsBanner.parse(DsRomFixtures.validBannerBytes())
        val b = DsBanner.parse(DsRomFixtures.validBannerBytes())

        requireNotNull(a)
        requireNotNull(b)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
