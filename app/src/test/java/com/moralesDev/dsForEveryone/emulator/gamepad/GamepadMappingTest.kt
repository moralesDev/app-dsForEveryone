package com.moralesDev.dsForEveryone.emulator.gamepad

import com.moralesDev.dsForEveryone.emulator.DsButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadMappingTest {

    private val a = GamepadTarget.Button(DsButton.A)
    private val b = GamepadTarget.Button(DsButton.B)

    @Test
    fun `el mapeo por defecto cubre los doce botones mas el soplido`() {
        assertEquals(13, GamepadTarget.ALL.size)
        for (target in GamepadTarget.ALL) {
            assertTrue("$target sin tecla en el mapeo por defecto", GamepadMapping.DEFAULT.keyCodeFor(target) != null)
        }
    }

    @Test
    fun `encode y decode son inversos para un mando`() {
        val decoded = GamepadMapping.decode(GamepadMapping.DEFAULT.encode())

        assertEquals(GamepadMapping.DEFAULT, decoded)
    }

    @Test
    fun `decode admite un mapeo parcial, un objetivo ausente queda en blanco`() {
        val partial = GamepadMapping.decode("${a.encode()}:96")

        assertEquals(96, partial?.keyCodeFor(a))
        assertNull(partial?.keyCodeFor(b))
    }

    @Test
    fun `decode se rinde con null ante un objetivo desconocido o un formato roto`() {
        assertNull(GamepadMapping.decode("ESTO_NO_EXISTE:1"))
        assertNull(GamepadMapping.decode("BTN_A-1"))
        assertNull(GamepadMapping.decode(""))
    }

    @Test
    fun `reasignar a una tecla ya usada intercambia los dos objetivos`() {
        // A tiene su tecla por defecto, B tiene la suya. Reasignar A a la
        // tecla de B no debe dejar a B sin botón: B se queda con la que
        // tenía A.
        val aKeyCode = GamepadMapping.DEFAULT.keyCodeFor(a)!!
        val bKeyCode = GamepadMapping.DEFAULT.keyCodeFor(b)!!

        val swapped = GamepadMapping.DEFAULT.with(a, bKeyCode)

        assertEquals(bKeyCode, swapped.keyCodeFor(a))
        assertEquals(aKeyCode, swapped.keyCodeFor(b))
    }

    @Test
    fun `intercambiar con un objetivo que estaba en blanco deja al desplazado tambien en blanco`() {
        val bKeyCode = GamepadMapping.DEFAULT.keyCodeFor(b)!!
        val aBlank = GamepadMapping.DEFAULT.withoutTarget(a)

        val reassigned = aBlank.with(a, bKeyCode)

        assertEquals(bKeyCode, reassigned.keyCodeFor(a))
        assertNull(reassigned.keyCodeFor(b))
    }

    @Test
    fun `withoutTarget deja un objetivo sin tecla sin tocar los demas`() {
        val blanked = GamepadMapping.DEFAULT.withoutTarget(a)

        assertNull(blanked.keyCodeFor(a))
        assertEquals(GamepadMapping.DEFAULT.keyCodeFor(b), blanked.keyCodeFor(b))
    }

    @Test
    fun `targetFor encuentra el objetivo asignado a una tecla concreta`() {
        val target = GamepadMapping.DEFAULT.targetFor(GamepadMapping.DEFAULT.keyCodeFor(a)!!)

        assertEquals(a, target)
    }

    @Test
    fun `dos mandos distintos no comparten mapeo`() {
        val mandoUno = GamepadMapping.DEFAULT.with(a, keyCode = 111)
        val mandoDos = GamepadMapping.DEFAULT.with(a, keyCode = 222)
        val byDescriptor = mapOf("mando-1" to mandoUno, "mando-2" to mandoDos)

        val decoded = GamepadMapping.decodeAll(GamepadMapping.encodeAll(byDescriptor))

        assertEquals(111, decoded.getValue("mando-1").keyCodeFor(a))
        assertEquals(222, decoded.getValue("mando-2").keyCodeFor(a))
    }

    @Test
    fun `decodeAll descarta solo la linea rota, no el resto de mandos`() {
        val valid = "mando-1|${GamepadMapping.DEFAULT.encode()}"
        val encoded = "$valid\nlinea-sin-formato\nmando-2|ESTO_NO_EXISTE:1"

        val decoded = GamepadMapping.decodeAll(encoded)

        assertEquals(setOf("mando-1"), decoded.keys)
    }
}
