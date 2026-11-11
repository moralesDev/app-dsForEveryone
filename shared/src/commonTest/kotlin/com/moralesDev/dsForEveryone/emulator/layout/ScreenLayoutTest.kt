package com.moralesDev.dsForEveryone.emulator.layout

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

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
    fun `las dos pantallas nunca se solapan — salvo en principal y secundaria`() {
        for ((label, size) in sizes) {
            for (mode in modesWithoutOverlap) {
                for (swapped in listOf(false, true)) {
                    val placement = ScreenLayout.resolve(mode, size, gap = 0.05f, swapped = swapped)
                    assertFalse(
                        LayoutEngine.overlaps(placement.topScreen, placement.touchScreen),
                        "$mode en $label (swapped=$swapped) no debería solapar",
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
                        LayoutEngine.isWithin(placement.topScreen, area),
                        "$mode en $label: topScreen se sale (${placement.topScreen})",
                    )
                    assertTrue(
                        LayoutEngine.isWithin(placement.touchScreen, area),
                        "$mode en $label: touchScreen se sale (${placement.touchScreen})",
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
                            ScreenLayout.SCREEN_ASPECT_RATIO,
                            rect.width / rect.height,
                            0.01f,
                            "$mode en $label: $rect no es 4:3",
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
    fun `intercambiar cambia la posicion fisica pero no cual es la tactil — en apiladas`() {
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
    fun `intercambiar cambia la posicion fisica pero no cual es la tactil — en lado a lado`() {
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

    // ---------------------------- márgenes de las barras del sistema

    /**
     * En horizontal las dos pantallas ocupan todo el alto, así que la táctil
     * se metía debajo de la barra de navegación y las pulsaciones de esa
     * franja se las quedaba el sistema. Con márgenes, ninguna pantalla puede
     * pisarlos en ningún modo.
     */
    @Test
    fun `ninguna pantalla invade los margenes del sistema`() {
        // Barra de estado arriba y de navegación a la derecha: la colocación
        // típica de un teléfono en horizontal con los tres botones.
        val insets = LayoutInsets(left = 0f, top = 66f, right = 120f, bottom = 48f)
        for ((label, size) in sizes) {
            val usable = LayoutRect(
                insets.left,
                insets.top,
                size.width - insets.right,
                size.height - insets.bottom,
            )
            for (mode in LayoutMode.entries) {
                for (swapped in listOf(false, true)) {
                    val placement = ScreenLayout.resolve(
                        mode,
                        size,
                        gap = 0.05f,
                        secondaryScale = 0.35f,
                        swapped = swapped,
                        insets = insets,
                    )
                    assertTrue(
                        LayoutEngine.isWithin(placement.topScreen, usable),
                        "$mode en $label: topScreen pisa una barra (${placement.topScreen})",
                    )
                    assertTrue(
                        LayoutEngine.isWithin(placement.touchScreen, usable),
                        "$mode en $label: touchScreen pisa una barra (${placement.touchScreen})",
                    )
                }
            }
        }
    }

    @Test
    fun `las pantallas siguen siendo 4-3 con margenes`() {
        val insets = LayoutInsets(top = 66f, right = 120f, bottom = 48f)
        for ((label, size) in sizes) {
            for (mode in LayoutMode.entries) {
                val placement = ScreenLayout.resolve(mode, size, gap = 0.05f, insets = insets)
                listOf(placement.topScreen, placement.touchScreen)
                    .filter { it.width > 0f || it.height > 0f }
                    .forEach { rect ->
                        assertEquals(
                            ScreenLayout.SCREEN_ASPECT_RATIO,
                            rect.width / rect.height,
                            0.01f,
                            "$mode en $label: $rect no es 4:3",
                        )
                    }
            }
        }
    }

    @Test
    fun `sin margenes el resultado no cambia`() {
        for ((_, size) in sizes) {
            for (mode in LayoutMode.entries) {
                val sinMargenes = ScreenLayout.resolve(mode, size, gap = 0.05f, secondaryScale = 0.35f)
                val conCero = ScreenLayout.resolve(
                    mode,
                    size,
                    gap = 0.05f,
                    secondaryScale = 0.35f,
                    insets = LayoutInsets.NONE,
                )
                assertEquals(sinMargenes, conCero)
            }
        }
    }

    @Test
    fun `unos margenes mayores que el hueco no invierten ninguna pantalla`() {
        val size = LayoutSize(600f, 400f)
        val insets = LayoutInsets(left = 400f, top = 300f, right = 400f, bottom = 300f)
        for (mode in LayoutMode.entries) {
            val placement = ScreenLayout.resolve(mode, size, gap = 0.05f, insets = insets)
            assertTrue(placement.topScreen.width >= 0f && placement.topScreen.height >= 0f)
            assertTrue(placement.touchScreen.width >= 0f && placement.touchScreen.height >= 0f)
        }
    }

    @Test
    fun `apiladas — las dos pantallas quedan centradas a lo alto en el hueco`() {
        val insets = LayoutInsets(top = 66f, bottom = 48f)
        val area = LayoutRect(0f, 0f, 1080f, 2400f).inset(insets)

        val placement = ScreenLayout.resolve(LayoutMode.STACKED, LayoutSize(1080f, 2400f), gap = 0.02f, insets = insets)

        val above = placement.topScreen.top - area.top
        val below = area.bottom - placement.touchScreen.bottom
        assertTrue(above > 0f)
        assertEquals(above, below, 0.01f)
    }

    @Test
    fun `en la mitad de arriba — el bloque de pantallas se apoya en el centro y crece hacia arriba`() {
        val size = LayoutSize(1080f, 2400f)
        val insets = LayoutInsets(top = 66f, bottom = 48f)
        val area = LayoutRect(0f, 0f, size.width, size.height).inset(insets)

        for (mode in LayoutMode.entries - LayoutMode.STACKED) {
            for (swapped in listOf(false, true)) {
                val placement = ScreenLayout.resolve(mode, size, gap = 0.03f, swapped = swapped, insets = insets, upperHalf = true)
                val visible = listOf(placement.topScreen, placement.touchScreen).filter { it.width > 0f && it.height > 0f }

                assertEquals(area.centerY, visible.maxOf { it.bottom }, 0.01f, "$mode")
                visible.forEach {
                    assertTrue(it.top >= area.top - 0.01f, "$mode: $it")
                    assertEquals(ScreenLayout.SCREEN_ASPECT_RATIO, it.width / it.height, 0.01f, "$mode: $it")
                }
            }
        }
    }

    @Test
    fun `apiladas no cambian con la mitad de arriba`() {
        val size = LayoutSize(1080f, 2400f)
        assertEquals(
            ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f),
            ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f, upperHalf = true),
        )
    }

    @Test
    fun `una sola pantalla en la mitad de arriba no encoge en un movil alargado`() {
        val size = LayoutSize(1080f, 2400f)
        val centered = ScreenLayout.resolve(LayoutMode.TOP_ONLY, size)
        val upper = ScreenLayout.resolve(LayoutMode.TOP_ONLY, size, upperHalf = true)

        assertEquals(centered.topScreen.width, upper.topScreen.width, 0.01f)
        assertEquals(centered.topScreen.height, upper.topScreen.height, 0.01f)
    }

    @Test
    fun `alignTop — las pantallas arriba del hueco y del mismo tamano que centradas`() {
        val size = LayoutSize(2360f, 1640f)
        val insets = LayoutInsets(0f, 48f, 0f, 40f)
        for (mode in listOf(LayoutMode.SIDE_BY_SIDE, LayoutMode.TOP_ONLY, LayoutMode.MAIN_AND_SECONDARY)) {
            val centered = ScreenLayout.resolve(mode, size, gap = 0.02f, insets = insets)
            val top = ScreenLayout.resolve(mode, size, gap = 0.02f, insets = insets, alignTop = true)
            val visible = listOf(top.topScreen, top.touchScreen).filter { it.width > 0f && it.height > 0f }
            assertEquals(48f, visible.minOf { it.top }, 0.01f, "$mode")
            assertEquals(centered.topScreen.width, top.topScreen.width, 0.01f, "$mode")
            assertEquals(centered.touchScreen.left, top.touchScreen.left, 0.01f, "$mode")
        }
    }

    @Test
    fun `topReserve — deja sitio arriba sin encoger nunca las pantallas`() {
        val insets = LayoutInsets(0f, 48f, 0f, 40f)
        val tablet = LayoutSize(2360f, 1640f)
        val reserved = ScreenLayout.resolve(LayoutMode.SIDE_BY_SIDE, tablet, insets = insets, alignTop = true, topReserve = 124f)
        val plain = ScreenLayout.resolve(LayoutMode.SIDE_BY_SIDE, tablet, insets = insets, alignTop = true)
        assertEquals(48f + 124f, reserved.topScreen.top, 0.01f)
        assertEquals(plain.topScreen.width, reserved.topScreen.width, 0.01f)

        // En un teléfono tumbado no sobra alto: las pantallas no se mueven ni encogen.
        val phone = LayoutSize(2400f, 1080f)
        val phoneReserved = ScreenLayout.resolve(LayoutMode.SIDE_BY_SIDE, phone, alignTop = true, topReserve = 160f)
        val phonePlain = ScreenLayout.resolve(LayoutMode.SIDE_BY_SIDE, phone, alignTop = true)
        assertEquals(phonePlain.topScreen.width, phoneReserved.topScreen.width, 0.01f)
        assertTrue(phoneReserved.topScreen.bottom <= 1080f + 0.01f)
    }
}
