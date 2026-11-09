package com.moralesDev.dsForEveryone.emulator

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * El bit de cada botón no se deduce: se copia de las cabeceras del core (ver
 * la documentación de [DsButton]). Esta prueba fija esos valores para que
 * una edición futura no los desordene sin darse cuenta.
 */
class DsButtonTest {

    @Test
    fun `el bit de cada boton coincide con el orden de melonDS`() {
        assertEquals(0, DsButton.A.bit)
        assertEquals(1, DsButton.B.bit)
        assertEquals(2, DsButton.SELECT.bit)
        assertEquals(3, DsButton.START.bit)
        assertEquals(4, DsButton.RIGHT.bit)
        assertEquals(5, DsButton.LEFT.bit)
        assertEquals(6, DsButton.UP.bit)
        assertEquals(7, DsButton.DOWN.bit)
        assertEquals(8, DsButton.R.bit)
        assertEquals(9, DsButton.L.bit)
        assertEquals(10, DsButton.X.bit)
        assertEquals(11, DsButton.Y.bit)
    }

    @Test
    fun `cada boton tiene un bit distinto`() {
        val bits = DsButton.entries.map { it.bit }
        assertEquals(bits.size, bits.toSet().size)
    }
}
