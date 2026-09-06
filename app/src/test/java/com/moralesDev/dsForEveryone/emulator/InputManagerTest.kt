package com.moralesDev.dsForEveryone.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputManagerTest {

    private val input = InputManager()

    // ---------------------------------------------------------- botones

    @Test
    fun `varios botones pueden estar pulsados a la vez`() {
        input.press(DsButton.A)
        input.press(DsButton.RIGHT)
        input.press(DsButton.L)

        assertTrue(input.isDown(DsButton.A))
        assertTrue(input.isDown(DsButton.RIGHT))
        assertTrue(input.isDown(DsButton.L))
    }

    @Test
    fun `soltar un boton no afecta a los demas`() {
        input.press(DsButton.B)
        input.press(DsButton.DOWN)

        input.release(DsButton.B)

        assertFalse(input.isDown(DsButton.B))
        assertTrue(input.isDown(DsButton.DOWN))
    }

    @Test
    fun `x e y son botones independientes de a y b`() {
        input.press(DsButton.X)
        input.press(DsButton.Y)

        assertTrue(input.isDown(DsButton.X))
        assertTrue(input.isDown(DsButton.Y))
        assertFalse(input.isDown(DsButton.A))
        assertFalse(input.isDown(DsButton.B))
    }

    @Test
    fun `la cruceta admite diagonales`() {
        input.setDirections(setOf(DsButton.UP, DsButton.RIGHT))

        assertTrue(input.isDown(DsButton.UP))
        assertTrue(input.isDown(DsButton.RIGHT))
    }

    @Test
    fun `cambiar de direccion suelta la anterior`() {
        input.setDirections(setOf(DsButton.UP))
        input.setDirections(setOf(DsButton.RIGHT))

        assertFalse(input.isDown(DsButton.UP))
        assertTrue(input.isDown(DsButton.RIGHT))
    }

    @Test
    fun `la cruceta no interfiere con los botones de accion`() {
        input.press(DsButton.A)
        input.setDirections(setOf(DsButton.LEFT))
        input.setDirections(emptySet())

        assertTrue("A debe seguir pulsada al soltar la cruceta", input.isDown(DsButton.A))
    }

    @Test
    fun `releaseAll deja la mascara a cero y suelta el lapiz`() {
        input.press(DsButton.START)
        input.setDirections(setOf(DsButton.DOWN))
        input.setTouch(100, 80, down = true)

        input.releaseAll()

        assertEquals(0, input.currentMask)
        assertFalse(input.currentTouch.down)
    }

    @Test
    fun `la mascara tiene doce bits, no diez`() {
        DsButton.entries.forEach { input.press(it) }

        assertEquals(0xFFF, input.currentMask)
    }

    // ---------------------------------------------------------- lápiz

    @Test
    fun `el toque se refleja en currentTouch`() {
        input.setTouch(200, 50, down = true)

        assertEquals(TouchState(200, 50, down = true), input.currentTouch)
    }

    @Test
    fun `soltar el lapiz ignora la posicion pedida`() {
        input.setTouch(200, 50, down = true)
        input.setTouch(999, 999, down = false)

        assertEquals(TouchState.UP, input.currentTouch)
    }

    @Test
    fun `escribir la misma posicion en cada fotograma no requiere que cambie`() {
        // Estado compartido, no evento: escribir el mismo punto dos veces
        // seguidas es exactamente lo que pasa en cada fotograma mientras el
        // dedo no se mueve, y debe ser una operación válida y estable.
        input.setTouch(10, 10, down = true)
        input.setTouch(10, 10, down = true)

        assertEquals(TouchState(10, 10, down = true), input.currentTouch)
    }

    @Test
    fun `el toque no afecta a los botones ni al reves`() {
        input.press(DsButton.A)
        input.setTouch(50, 50, down = true)

        assertTrue(input.isDown(DsButton.A))
        assertEquals(TouchState(50, 50, down = true), input.currentTouch)
    }

    private fun InputManager.isDown(button: DsButton) = currentMask and button.mask != 0
}
