package com.moralesDev.dsForEveryone.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EmulationSpeedTest {

    @Test
    fun `las tres velocidades son 1x, 1,5x y 2x`() {
        assertEquals(1f, EmulationSpeed.NORMAL.multiplier, 0.001f)
        assertEquals(1.5f, EmulationSpeed.FAST_1_5X.multiplier, 0.001f)
        assertEquals(2f, EmulationSpeed.FAST_2X.multiplier, 0.001f)
        assertEquals(3, EmulationSpeed.entries.size)
    }

    @Test
    fun `solo 1x no es avance rapido`() {
        assertFalse(EmulationSpeed.NORMAL.isFast)
        assertTrue(EmulationSpeed.FAST_1_5X.isFast)
        assertTrue(EmulationSpeed.FAST_2X.isFast)
    }


    @Test
    fun `un ajuste guardado que ya no existe no se inventa una velocidad`() {
        assertEquals(EmulationSpeed.FAST_2X, EmulationSpeed.fromName("FAST_2X"))
        assertNull(EmulationSpeed.fromName("FAST_16X"))
        assertNull(EmulationSpeed.fromName(null))
    }

    @Test
    fun `por defecto se corre a velocidad normal`() {
        assertEquals(EmulationSpeed.NORMAL, EmulatorSettings().speed)
    }
}
