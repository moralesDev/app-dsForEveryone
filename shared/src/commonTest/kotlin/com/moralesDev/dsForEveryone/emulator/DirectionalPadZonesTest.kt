package com.moralesDev.dsForEveryone.emulator

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * El reparto del área de la cruceta en ocho cuñas, comprobado coordenada a
 * coordenada. El criterio no cambia respecto al de `app-emulador`: sin
 * geometría fuera de Compose no hay forma de preguntarle nada a esto.
 */
class DirectionalPadZonesTest {

    private fun at(x: Float, y: Float, diagonalWidth: Float = DirectionalPadZones.DEFAULT_DIAGONAL_WIDTH_DEGREES) =
        DirectionalPadZones.directionsAt(x, y, SIDE, SIDE, diagonalWidth)

    private val center = SIDE / 2f

    /** Punto a [distance] px del centro, en la dirección [angleDegrees] (0 = derecha, sentido horario). */
    private fun pointAt(angleDegrees: Float, distance: Float): Pair<Float, Float> {
        val radians = angleDegrees.toDouble() * PI / 180.0
        return (center + distance * cos(radians)).toFloat() to (center + distance * sin(radians)).toFloat()
    }

    // ---------------------------------------------------------- las ocho direcciones

    @Test
    fun `las cuatro cardinales dan su unico boton`() {
        val (rx, ry) = pointAt(0f, RADIUS)
        val (dx, dy) = pointAt(90f, RADIUS)
        val (lx, ly) = pointAt(180f, RADIUS)
        val (ux, uy) = pointAt(270f, RADIUS)

        assertEquals(setOf(DsButton.RIGHT), at(rx, ry))
        assertEquals(setOf(DsButton.DOWN), at(dx, dy))
        assertEquals(setOf(DsButton.LEFT), at(lx, ly))
        assertEquals(setOf(DsButton.UP), at(ux, uy))
    }

    @Test
    fun `las cuatro diagonales dan sus dos botones`() {
        val (drx, dry) = pointAt(45f, RADIUS)
        val (dlx, dly) = pointAt(135f, RADIUS)
        val (ulx, uly) = pointAt(225f, RADIUS)
        val (urx, ury) = pointAt(315f, RADIUS)

        assertEquals(setOf(DsButton.DOWN, DsButton.RIGHT), at(drx, dry))
        assertEquals(setOf(DsButton.DOWN, DsButton.LEFT), at(dlx, dly))
        assertEquals(setOf(DsButton.UP, DsButton.LEFT), at(ulx, uly))
        assertEquals(setOf(DsButton.UP, DsButton.RIGHT), at(urx, ury))
    }

    // ---------------------------------------------------------- zona neutra

    @Test
    fun `el centro exacto no produce nada`() {
        assertEquals(emptySet<DsButton>(), at(center, center))
    }

    @Test
    fun `el disco neutro mide lo que dice y no mas`() {
        val radius = SIDE * DirectionalPadZones.DEAD_ZONE_RATIO
        assertEquals(emptySet<DsButton>(), at(center + radius - 1f, center), "dentro del disco no hay dirección")
        assertTrue(at(center + radius + 1f, center).isNotEmpty(), "justo fuera del disco sí la hay")
    }

    // ---------------------------------------------------------- cubre el círculo sin huecos ni solapes

    /**
     * Barre el círculo completo, fuera de la zona muerta, y comprueba las
     * dos propiedades a la vez: nunca da más de dos botones (nunca un hueco
     * "sin dueño" convertido en combinación rara) y aparecen las ocho
     * combinaciones esperadas — ni una de más, ni una de menos.
     */
    @Test
    fun `las ocho cunas cubren el circulo sin solaparse y sin huecos`() {
        val expected = setOf(
            setOf(DsButton.RIGHT),
            setOf(DsButton.DOWN, DsButton.RIGHT),
            setOf(DsButton.DOWN),
            setOf(DsButton.DOWN, DsButton.LEFT),
            setOf(DsButton.LEFT),
            setOf(DsButton.UP, DsButton.LEFT),
            setOf(DsButton.UP),
            setOf(DsButton.UP, DsButton.RIGHT),
        )
        val seen = mutableSetOf<Set<DsButton>>()

        var angle = 0f
        while (angle < 360f) {
            val (x, y) = pointAt(angle, RADIUS)
            val result = at(x, y)

            assertTrue(
                result.isEmpty() || result in expected,
                "($angle°) da una combinación inválida: $result",
            )
            assertTrue(result.isNotEmpty(), "($angle°) no debería estar vacío fuera de la zona muerta")
            seen += result
            angle += 0.25f
        }

        assertEquals(expected, seen, "deben aparecer las ocho combinaciones")
    }

    @Test
    fun `las ocho cunas reparten el circulo en proporciones parecidas dentro de su tipo`() {
        val counts = mutableMapOf<Set<DsButton>, Int>()
        var angle = 0f
        while (angle < 360f) {
            val (x, y) = pointAt(angle, RADIUS)
            val result = at(x, y)
            counts[result] = (counts[result] ?: 0) + 1
            angle += 0.1f
        }

        val cardinalCounts = counts.filterKeys { it.size == 1 }.values
        val diagonalCounts = counts.filterKeys { it.size == 2 }.values

        assertEquals(4, cardinalCounts.size)
        assertEquals(4, diagonalCounts.size)
        // Dentro de cada tipo (cardinal o diagonal) el reparto debe ser
        // simétrico: las cuatro cardinales miden lo mismo entre sí, y lo
        // mismo las cuatro diagonales.
        assertTrue(cardinalCounts.max() - cardinalCounts.min() <= 2)
        assertTrue(diagonalCounts.max() - diagonalCounts.min() <= 2)
    }

    // ---------------------------------------------------------- continuidad (un único control)

    @Test
    fun `deslizar de arriba a la diagonal y a la derecha no salta combinaciones`() {
        // De UP (270°) a RIGHT (360°=0°) pasando por la diagonal UP-RIGHT
        // (315°): la secuencia de conjuntos debe ser monótona, sin volver
        // atrás ni saltarse la diagonal.
        val seenOrder = mutableListOf<Set<DsButton>>()
        var angle = 270f
        while (angle <= 360f) {
            val (x, y) = pointAt(angle % 360f, RADIUS)
            val result = at(x, y)
            if (seenOrder.isEmpty() || seenOrder.last() != result) seenOrder += result
            angle += 1f
        }

        assertEquals(
            listOf(setOf(DsButton.UP), setOf(DsButton.UP, DsButton.RIGHT), setOf(DsButton.RIGHT)),
            seenOrder,
        )
    }

    // ---------------------------------------------------------- ancho configurable

    @Test
    fun `un ancho de diagonal mayor agranda las cunas diagonales`() {
        val narrow = DirectionalPadZones.directionsAt(
            pointAt(50f, RADIUS).first,
            pointAt(50f, RADIUS).second,
            SIDE,
            SIDE,
            diagonalWidthDegrees = 10f,
        )
        val wide = DirectionalPadZones.directionsAt(
            pointAt(50f, RADIUS).first,
            pointAt(50f, RADIUS).second,
            SIDE,
            SIDE,
            diagonalWidthDegrees = 80f,
        )

        // A 50° del eje horizontal: con una diagonal estrecha (10°, la cuña
        // diagonal sólo llega hasta 45+5=50°) cae justo en el límite hacia
        // DOWN; con una diagonal muy ancha (80°) cae dentro de la diagonal.
        assertEquals(setOf(DsButton.DOWN), narrow)
        assertEquals(setOf(DsButton.DOWN, DsButton.RIGHT), wide)
    }

    // ---------------------------------------------------------- casos degenerados

    @Test
    fun `un control sin tamano no produce direcciones`() {
        assertEquals(emptySet<DsButton>(), DirectionalPadZones.directionsAt(10f, 10f, 0f, 0f))
        assertEquals(emptySet<DsButton>(), DirectionalPadZones.directionsAt(10f, 10f, -1f, 10f))
    }

    @Test
    fun `un ancho de diagonal fuera de rango se rechaza`() {
        assertFailsWith<IllegalArgumentException> {
            at(center + RADIUS, center, diagonalWidth = 91f)
        }
    }

    private companion object {
        const val SIDE = 150f
        const val RADIUS = 60f
    }
}
