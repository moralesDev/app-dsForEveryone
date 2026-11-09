package com.moralesDev.dsForEveryone.emulator

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class JoystickGeometryTest {

    private val radius = 100f

    @Test
    fun `dentro de la zona muerta no hay direccion`() {
        assertEquals(emptySet<DsButton>(), JoystickGeometry.directions(0f, 0f, radius))
        assertEquals(emptySet<DsButton>(), JoystickGeometry.directions(29f, 0f, radius))
        assertEquals(emptySet<DsButton>(), JoystickGeometry.directions(-20f, 20f, radius))
    }

    @Test
    fun `un pixel fuera de la zona muerta ya hay direccion`() {
        assertEquals(setOf(DsButton.RIGHT), JoystickGeometry.directions(31f, 0f, radius))
    }

    @Test
    fun `las cuatro cardinales — con y hacia abajo`() {
        assertEquals(setOf(DsButton.RIGHT), JoystickGeometry.directions(80f, 0f, radius))
        assertEquals(setOf(DsButton.LEFT), JoystickGeometry.directions(-80f, 0f, radius))
        assertEquals(setOf(DsButton.UP), JoystickGeometry.directions(0f, -80f, radius))
        assertEquals(setOf(DsButton.DOWN), JoystickGeometry.directions(0f, 80f, radius))
    }

    @Test
    fun `las cuatro diagonales a 45 grados`() {
        assertEquals(setOf(DsButton.UP, DsButton.RIGHT), JoystickGeometry.directions(60f, -60f, radius))
        assertEquals(setOf(DsButton.DOWN, DsButton.RIGHT), JoystickGeometry.directions(60f, 60f, radius))
        assertEquals(setOf(DsButton.DOWN, DsButton.LEFT), JoystickGeometry.directions(-60f, 60f, radius))
        assertEquals(setOf(DsButton.UP, DsButton.LEFT), JoystickGeometry.directions(-60f, -60f, radius))
    }

    @Test
    fun `los ocho sectores miden lo mismo`() {
        // A 22° de la horizontal sigue siendo derecha; a 23°, ya diagonal.
        fun at(degrees: Double) = JoystickGeometry.directions(
            (80 * cos((degrees * PI / 180.0))).toFloat(),
            (80 * sin((degrees * PI / 180.0))).toFloat(),
            radius,
        )
        assertEquals(setOf(DsButton.RIGHT), at(22.0))
        assertEquals(setOf(DsButton.DOWN, DsButton.RIGHT), at(23.0))
        assertEquals(setOf(DsButton.DOWN, DsButton.RIGHT), at(67.0))
        assertEquals(setOf(DsButton.DOWN), at(68.0))
    }

    @Test
    fun `el dedo fuera del aro conserva la direccion`() {
        assertEquals(setOf(DsButton.LEFT), JoystickGeometry.directions(-400f, 10f, radius))
    }

    @Test
    fun `nunca da direcciones opuestas ni mas de dos`() {
        for (degrees in 0 until 360) {
            val result = JoystickGeometry.directions(
                (90 * cos((degrees.toDouble() * PI / 180.0))).toFloat(),
                (90 * sin((degrees.toDouble() * PI / 180.0))).toFloat(),
                radius,
            )
            assertTrue(result.size in 1..2, "$degrees°: $result")
            assertTrue(!(DsButton.UP in result && DsButton.DOWN in result))
            assertTrue(!(DsButton.LEFT in result && DsButton.RIGHT in result))
        }
    }

    @Test
    fun `un joystick sin tamano no produce nada`() {
        assertEquals(emptySet<DsButton>(), JoystickGeometry.directions(50f, 0f, 0f))
        assertEquals(0f to 0f, JoystickGeometry.knobOffset(50f, 0f, 0f))
    }

    @Test
    fun `la bola sigue al dedo dentro de su recorrido`() {
        assertEquals(20f to -30f, JoystickGeometry.knobOffset(20f, -30f, radius))
    }

    @Test
    fun `la bola se recorta al recorrido sin cambiar de direccion`() {
        val (x, y) = JoystickGeometry.knobOffset(300f, 400f, radius)
        val maxTravel = radius * JoystickGeometry.KNOB_TRAVEL_RATIO

        assertEquals(maxTravel, sqrt(x * x + y * y), 0.01f)
        assertEquals(300f / 400f, x / y, 0.001f)
    }
}
