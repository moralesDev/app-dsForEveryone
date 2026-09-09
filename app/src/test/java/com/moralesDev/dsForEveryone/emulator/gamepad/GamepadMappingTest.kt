package com.moralesDev.dsForEveryone.emulator.gamepad

import com.moralesDev.dsForEveryone.emulator.DsButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadMappingTest {

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
    fun `decode se rinde con null si falta alguno de los trece objetivos`() {
        val incomplete = GamepadTarget.ALL.drop(1).joinToString(";") { "${it.encode()}:1" }

        assertNull(GamepadMapping.decode(incomplete))
    }

    @Test
    fun `decode se rinde con null ante un objetivo desconocido o un formato roto`() {
        assertNull(GamepadMapping.decode("ESTO_NO_EXISTE:1"))
        assertNull(GamepadMapping.decode("BTN_A-1"))
        assertNull(GamepadMapping.decode(""))
    }

    @Test
    fun `reasignar un objetivo libera cualquier otro que usara la misma tecla`() {
        val collided = GamepadMapping.DEFAULT.with(GamepadTarget.Button(DsButton.B), keyCode = 999)
        val reassigned = collided.with(GamepadTarget.Button(DsButton.A), keyCode = 999)

        assertEquals(999, reassigned.keyCodeFor(GamepadTarget.Button(DsButton.A)))
        assertNull(reassigned.keyCodeFor(GamepadTarget.Button(DsButton.B)))
    }

    @Test
    fun `targetFor encuentra el objetivo asignado a una tecla concreta`() {
        val target = GamepadMapping.DEFAULT.targetFor(GamepadMapping.DEFAULT.keyCodeFor(GamepadTarget.Button(DsButton.A))!!)

        assertEquals(GamepadTarget.Button(DsButton.A), target)
    }

    @Test
    fun `dos mandos distintos no comparten mapeo`() {
        val mandoUno = GamepadMapping.DEFAULT.with(GamepadTarget.Button(DsButton.A), keyCode = 111)
        val mandoDos = GamepadMapping.DEFAULT.with(GamepadTarget.Button(DsButton.A), keyCode = 222)
        val byDescriptor = mapOf("mando-1" to mandoUno, "mando-2" to mandoDos)

        val decoded = GamepadMapping.decodeAll(GamepadMapping.encodeAll(byDescriptor))

        assertEquals(111, decoded.getValue("mando-1").keyCodeFor(GamepadTarget.Button(DsButton.A)))
        assertEquals(222, decoded.getValue("mando-2").keyCodeFor(GamepadTarget.Button(DsButton.A)))
    }

    @Test
    fun `decodeAll descarta solo la linea rota, no el resto de mandos`() {
        val valid = "mando-1|${GamepadMapping.DEFAULT.encode()}"
        val encoded = "$valid\nlinea-sin-formato\nmando-2|BTN_A:1"

        val decoded = GamepadMapping.decodeAll(encoded)

        assertEquals(setOf("mando-1"), decoded.keys)
    }
}
