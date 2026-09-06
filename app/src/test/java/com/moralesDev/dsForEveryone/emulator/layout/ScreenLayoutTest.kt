package com.moralesDev.dsForEveryone.emulator.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las seis propiedades que tiene que cumplir cualquier disposición de las
 * dos pantallas, comprobadas coordenada a coordenada y no mirando una
 * captura de pantalla.
 */
class ScreenLayoutTest {

    private val sizes = listOf(
        "teléfono vertical" to LayoutSize(1080f, 2200f),
        "teléfono horizontal" to LayoutSize(2200f, 1080f),
        "hueco muy ancho" to LayoutSize(4000f, 200f),
        "hueco muy alto" to LayoutSize(200f, 4000f),
        "hueco cuadrado" to LayoutSize(1000f, 1000f),
    )

    private val modesWithoutOverlap = LayoutMode.entries - LayoutMode.MAIN_AND_SECONDARY

    // ---------------------------- no solapar

    @Test
    fun `las dos pantallas nunca se solapan, salvo en principal y secundaria`() {
        for ((label, size) in sizes) {
            for (mode in modesWithoutOverlap) {
                for (swapped in listOf(false, true)) {
                    val placement = ScreenLayout.resolve(mode, size, gap = 0.05f, swapped = swapped)
                    assertFalse(
                        "$mode en $label (swapped=$swapped) no debería solapar",
                        LayoutEngine.overlaps(placement.topScreen, placement.touchScreen),
                    )
                }
            }
        }
    }

    @Test
    fun `principal y secundaria solapa a proposito`() {
        val placement = ScreenLayout.resolve(
            LayoutMode.MAIN_AND_SECONDARY,
            LayoutSize(1000f, 1000f),
            secondaryScale = 0.35f,
        )

        assertTrue(LayoutEngine.overlaps(placement.topScreen, placement.touchScreen))
    }

    // ---------------------------- no salirse del hueco

    @Test
    fun `ninguna pantalla se sale del hueco disponible`() {
        for ((label, size) in sizes) {
            val area = LayoutRect(0f, 0f, size.width, size.height)
            for (mode in LayoutMode.entries) {
                for (swapped in listOf(false, true)) {
                    val placement = ScreenLayout.resolve(
                        mode,
                        size,
                        gap = 0.05f,
                        secondaryScale = 0.35f,
                        swapped = swapped,
                    )
                    assertTrue(
                        "$mode en $label: topScreen se sale (${placement.topScreen})",
                        LayoutEngine.isWithin(placement.topScreen, area),
                    )
                    assertTrue(
                        "$mode en $label: touchScreen se sale (${placement.touchScreen})",
                        LayoutEngine.isWithin(placement.touchScreen, area),
                    )
                }
            }
        }
    }

    // ---------------------------- 4:3 exacto

    @Test
    fun `cada pantalla visible mantiene 4-3 exacto`() {
        // Las pantallas ocultas de SOLO_SUPERIOR y SOLO_INFERIOR tienen área
        // cero a propósito (ver ScreenLayout.soloSlots): 0:0 no es una
        // proporción, así que se excluyen aquí y se comprueban aparte.
        for ((label, size) in sizes) {
            for (mode in LayoutMode.entries) {
                val placement = ScreenLayout.resolve(mode, size, gap = 0.05f, secondaryScale = 0.35f)
                listOf(placement.topScreen, placement.touchScreen)
                    .filter { it.width > 0f || it.height > 0f }
                    .forEach { rect ->
                        assertEquals(
                            "$mode en $label: $rect no es 4:3",
                            ScreenLayout.SCREEN_ASPECT_RATIO,
                            rect.width / rect.height,
                            0.01f,
                        )
                    }
            }
        }
    }

    @Test
    fun `la pantalla oculta de los modos de una sola pantalla no tiene area`() {
        val size = LayoutSize(1080f, 2200f)

        val topOnly = ScreenLayout.resolve(LayoutMode.TOP_ONLY, size)
        assertEquals(0f, topOnly.touchScreen.width, 0.001f)
        assertEquals(0f, topOnly.touchScreen.height, 0.001f)

        val bottomOnly = ScreenLayout.resolve(LayoutMode.BOTTOM_ONLY, size)
        assertEquals(0f, bottomOnly.topScreen.width, 0.001f)
        assertEquals(0f, bottomOnly.topScreen.height, 0.001f)
    }

    // ---------------------------- determinismo

    @Test
    fun `la misma entrada da siempre la misma salida`() {
        for ((_, size) in sizes) {
            for (mode in LayoutMode.entries) {
                val first = ScreenLayout.resolve(mode, size, gap = 0.03f, secondaryScale = 0.4f, swapped = true)
                val second = ScreenLayout.resolve(mode, size, gap = 0.03f, secondaryScale = 0.4f, swapped = true)
                assertEquals(first, second)
            }
        }
    }

    // ---------------------------- la táctil es siempre la lógica inferior

    @Test
    fun `intercambiar cambia la posicion fisica pero no cual es la tactil, en apiladas`() {
        val size = LayoutSize(1080f, 2200f)
        val normal = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f, swapped = false)
        val swapped = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f, swapped = true)

        // Son las dos mismas posiciones físicas, con los papeles cambiados.
        assertEquals(normal.topScreen, swapped.touchScreen)
        assertEquals(normal.touchScreen, swapped.topScreen)

        // Sin intercambiar, la táctil se pinta debajo; intercambiada, arriba.
        assertTrue(normal.touchScreen.top > normal.topScreen.top)
        assertTrue(swapped.touchScreen.top < swapped.topScreen.top)
    }

    @Test
    fun `intercambiar cambia la posicion fisica pero no cual es la tactil, en lado a lado`() {
        val size = LayoutSize(2200f, 1080f)
        val normal = ScreenLayout.resolve(LayoutMode.SIDE_BY_SIDE, size, gap = 0.05f, swapped = false)
        val swapped = ScreenLayout.resolve(LayoutMode.SIDE_BY_SIDE, size, gap = 0.05f, swapped = true)

        assertEquals(normal.topScreen, swapped.touchScreen)
        assertEquals(normal.touchScreen, swapped.topScreen)

        assertTrue(normal.touchScreen.left > normal.topScreen.left)
        assertTrue(swapped.touchScreen.left < swapped.topScreen.left)
    }

    @Test
    fun `intercambiar solo superior equivale a solo inferior`() {
        val size = LayoutSize(1080f, 2200f)

        val topOnlySwapped = ScreenLayout.resolve(LayoutMode.TOP_ONLY, size, swapped = true)
        val bottomOnly = ScreenLayout.resolve(LayoutMode.BOTTOM_ONLY, size, swapped = false)

        assertEquals(bottomOnly.touchScreen, topOnlySwapped.touchScreen)
        assertEquals(bottomOnly.topScreen, topOnlySwapped.topScreen)
    }

    // ---------------------------- casos límite explícitos

    @Test
    fun `hueco muy ancho no rompe ningun modo`() {
        val size = LayoutSize(5000f, 150f)
        for (mode in LayoutMode.entries) {
            val placement = ScreenLayout.resolve(mode, size, gap = 0.1f, secondaryScale = 0.3f)
            assertTrue(placement.topScreen.width >= 0f && placement.topScreen.height >= 0f)
            assertTrue(placement.touchScreen.width >= 0f && placement.touchScreen.height >= 0f)
        }
    }

    @Test
    fun `hueco muy alto no rompe ningun modo`() {
        val size = LayoutSize(150f, 5000f)
        for (mode in LayoutMode.entries) {
            val placement = ScreenLayout.resolve(mode, size, gap = 0.1f, secondaryScale = 0.3f)
            assertTrue(placement.topScreen.width >= 0f && placement.topScreen.height >= 0f)
            assertTrue(placement.touchScreen.width >= 0f && placement.touchScreen.height >= 0f)
        }
    }

    @Test
    fun `hueco cuadrado no rompe ningun modo`() {
        val size = LayoutSize(1200f, 1200f)
        for (mode in LayoutMode.entries) {
            val placement = ScreenLayout.resolve(mode, size, gap = 0.1f, secondaryScale = 0.3f)
            assertTrue(placement.topScreen.width >= 0f && placement.topScreen.height >= 0f)
            assertTrue(placement.touchScreen.width >= 0f && placement.touchScreen.height >= 0f)
        }
    }
}
