package com.moralesDev.dsForEveryone.emulator.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * De punto de la vista a coordenada de la DS, comprobado en el borde exacto:
 * lo que importa no es que el centro funcione, sino que el recorte pase
 * justo donde debe y ni un píxel más allá.
 */
class TouchRouterTest {

    // Pantalla táctil de prueba: 256x192 a escala 1:1, sin desplazar.
    private val touchScreen = LayoutRect(0f, 0f, 256f, 192f)
    private val placement = ScreenPlacement(
        topScreen = LayoutRect(0f, -200f, 256f, -8f),
        touchScreen = touchScreen,
    )

    // ---------------------------- fuera de la pantalla

    @Test
    fun `un punto fuera de la pantalla tactil devuelve null`() {
        assertNull(TouchRouter.map(-1f, 100f, placement))
        assertNull(TouchRouter.map(300f, 100f, placement))
        assertNull(TouchRouter.map(100f, -1f, placement))
        assertNull(TouchRouter.map(100f, 300f, placement))
        // Sobre la pantalla superior, que no es la táctil.
        assertNull(TouchRouter.map(100f, -100f, placement))
    }

    // ---------------------------- las cuatro esquinas exactas

    @Test
    fun `las cuatro esquinas dan exactamente los extremos`() {
        assertEquals(DsPoint(0, 0), TouchRouter.map(0f, 0f, placement))
        assertEquals(DsPoint(255, 0), TouchRouter.map(256f, 0f, placement))
        assertEquals(DsPoint(0, 191), TouchRouter.map(0f, 192f, placement))
        assertEquals(DsPoint(255, 191), TouchRouter.map(256f, 192f, placement))
    }

    // ---------------------------- el recorte ocurre EN el borde

    @Test
    fun `el borde izquierdo corta exactamente ahi, no un pixel antes ni despues`() {
        assertEquals("justo dentro", 0, TouchRouter.map(0f, 96f, placement)!!.x)
        assertNull("justo fuera", TouchRouter.map(-0.5f, 96f, placement))
    }

    @Test
    fun `el borde derecho corta exactamente ahi, no un pixel antes ni despues`() {
        assertEquals("justo dentro, en el borde", 255, TouchRouter.map(256f, 96f, placement)!!.x)
        assertEquals("un pixel dentro del borde derecho", 255, TouchRouter.map(255.9f, 96f, placement)!!.x)
        assertNull("justo fuera", TouchRouter.map(256.5f, 96f, placement))
    }

    @Test
    fun `el borde superior corta exactamente ahi, no un pixel antes ni despues`() {
        assertEquals("justo dentro", 0, TouchRouter.map(128f, 0f, placement)!!.y)
        assertNull("justo fuera", TouchRouter.map(128f, -0.5f, placement))
    }

    @Test
    fun `el borde inferior corta exactamente ahi, no un pixel antes ni despues`() {
        assertEquals("justo dentro, en el borde", 191, TouchRouter.map(128f, 192f, placement)!!.y)
        assertEquals("un pixel dentro del borde inferior", 191, TouchRouter.map(128f, 191.9f, placement)!!.y)
        assertNull("justo fuera", TouchRouter.map(128f, 192.5f, placement))
    }

    // ---------------------------- pantallas intercambiadas

    @Test
    fun `funciona igual con las pantallas intercambiadas`() {
        val size = LayoutSize(1080f, 2200f)
        val normal = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f, swapped = false)
        val swapped = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f, swapped = true)

        // La táctil está en un sitio distinto en cada caso...
        assertTrue(normal.touchScreen != swapped.touchScreen)

        // ...pero en los dos, sus cuatro esquinas dan los mismos extremos.
        for (placement in listOf(normal, swapped)) {
            val screen = placement.touchScreen
            assertEquals(DsPoint(0, 0), TouchRouter.map(screen.left, screen.top, placement))
            assertEquals(DsPoint(255, 0), TouchRouter.map(screen.right, screen.top, placement))
            assertEquals(DsPoint(0, 191), TouchRouter.map(screen.left, screen.bottom, placement))
            assertEquals(DsPoint(255, 191), TouchRouter.map(screen.right, screen.bottom, placement))
        }

        // Y tocar la pantalla que NO es la táctil en cada caso no da nada.
        assertNull(TouchRouter.map(normal.topScreen.centerX, normal.topScreen.centerY, normal))
        assertNull(TouchRouter.map(swapped.topScreen.centerX, swapped.topScreen.centerY, swapped))
    }

    // ---------------------------- pantalla táctil reducida (30%)

    @Test
    fun `funciona con la pantalla tactil escalada al 30 por ciento`() {
        val placement = ScreenLayout.resolve(
            LayoutMode.MAIN_AND_SECONDARY,
            LayoutSize(1000f, 1000f),
            secondaryScale = 0.3f,
        )
        val screen = placement.touchScreen
        // La secundaria reducida es bastante más pequeña que la pantalla
        // completa: si esto fallara, la prueba estaría comprobando por error
        // la pantalla principal.
        assertTrue(screen.width < 400f)

        assertEquals(DsPoint(0, 0), TouchRouter.map(screen.left, screen.top, placement))
        assertEquals(DsPoint(255, 0), TouchRouter.map(screen.right, screen.top, placement))
        assertEquals(DsPoint(0, 191), TouchRouter.map(screen.left, screen.bottom, placement))
        assertEquals(DsPoint(255, 191), TouchRouter.map(screen.right, screen.bottom, placement))
        assertEquals(
            DsPoint(128, 96),
            TouchRouter.map(screen.centerX, screen.centerY, placement),
        )
        assertNull(TouchRouter.map(screen.right + 1f, screen.centerY, placement))
    }

    // ---------------------------- pantalla oculta (área cero)

    @Test
    fun `una pantalla tactil oculta nunca produce un toque`() {
        val placement = ScreenLayout.resolve(LayoutMode.TOP_ONLY, LayoutSize(1000f, 1000f))

        assertNull(TouchRouter.map(0f, 0f, placement))
        assertNull(TouchRouter.map(500f, 500f, placement))
    }
}
