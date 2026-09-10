package com.moralesDev.dsForEveryone.emulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El período de fotograma, incluido el que impone el avance rápido. Es la
 * única aritmética del avance rápido: todo lo demás —silenciar el audio,
 * pintar el botón— cuelga de aquí.
 */
class DsVideoTest {

    @Test
    fun `a velocidad normal el periodo es el de la DS, no 60 Hz redondos`() {
        assertEquals(DsVideo.FRAME_DURATION_NANOS, DsVideo.frameDurationNanosAt(1f))
        // 59,8261 Hz: si esto se convirtiera en 16 666 666 ns alguien habría
        // redondeado la DS a 60 Hz por el camino.
        assertEquals(16_715_113L, DsVideo.FRAME_DURATION_NANOS)
    }

    @Test
    fun `el doble de velocidad es la mitad de periodo`() {
        assertEquals(DsVideo.FRAME_DURATION_NANOS / 2, DsVideo.frameDurationNanosAt(2f))
    }

    @Test
    fun `una vez y media de velocidad acorta el periodo en la misma proporcion`() {
        val period = DsVideo.frameDurationNanosAt(1.5f)

        assertEquals((DsVideo.FRAME_DURATION_NANOS / 1.5f).toLong(), period)
        assertTrue(period < DsVideo.FRAME_DURATION_NANOS)
        assertTrue(period > DsVideo.frameDurationNanosAt(2f))
    }

    @Test
    fun `un multiplicador imposible corre a velocidad normal, no divide por cero`() {
        assertEquals(DsVideo.FRAME_DURATION_NANOS, DsVideo.frameDurationNanosAt(0f))
        assertEquals(DsVideo.FRAME_DURATION_NANOS, DsVideo.frameDurationNanosAt(-3f))
    }
}
