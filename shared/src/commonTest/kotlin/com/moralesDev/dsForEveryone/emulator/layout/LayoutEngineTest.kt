package com.moralesDev.dsForEveryone.emulator.layout

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class LayoutEngineTest {

    // ---------------------------- fitAspectRatio

    @Test
    fun `cuando el hueco es mas ancho que la proporcion — manda el alto`() {
        // 400x100 es mucho más ancho que 4:3: si se usara todo el ancho, el
        // alto necesario (300) no cabría. El alto disponible (100) manda, y
        // el ancho se deriva de él.
        val size = LayoutEngine.fitAspectRatio(LayoutSize(400f, 100f), 4f / 3f)

        assertEquals(100f, size.height, 0.01f)
        assertEquals(100f * 4f / 3f, size.width, 0.01f)
        assertTrue(size.width <= 400f, "no debe superar el ancho del hueco")
    }

    @Test
    fun `cuando el hueco es mas alto que la proporcion — manda el ancho`() {
        // 100x400 es mucho más alto que 4:3: si se usara todo el alto, el
        // ancho necesario (533) no cabría. El ancho disponible (100) manda.
        val size = LayoutEngine.fitAspectRatio(LayoutSize(100f, 400f), 4f / 3f)

        assertEquals(100f, size.width, 0.01f)
        assertEquals(100f * 3f / 4f, size.height, 0.01f)
        assertTrue(size.height <= 400f, "no debe superar el alto del hueco")
    }

    @Test
    fun `un hueco exactamente 4-3 no recorta nada`() {
        val size = LayoutEngine.fitAspectRatio(LayoutSize(256f, 192f), 256f / 192f)

        assertEquals(256f, size.width, 0.01f)
        assertEquals(192f, size.height, 0.01f)
    }

    @Test
    fun `un hueco sin area da un tamano sin area`() {
        assertEquals(LayoutSize(0f, 0f), LayoutEngine.fitAspectRatio(LayoutSize(0f, 100f), 1f))
        assertEquals(LayoutSize(0f, 0f), LayoutEngine.fitAspectRatio(LayoutSize(100f, 0f), 1f))
    }

    // ---------------------------- center

    @Test
    fun `centra el tamano dentro del area`() {
        val area = LayoutRect(0f, 0f, 100f, 50f)
        val rect = LayoutEngine.center(LayoutSize(40f, 20f), area)

        assertEquals(30f, rect.left, 0.01f)
        assertEquals(15f, rect.top, 0.01f)
        assertEquals(70f, rect.right, 0.01f)
        assertEquals(35f, rect.bottom, 0.01f)
    }

    // ---------------------------- overlaps

    @Test
    fun `dos rectangulos que se cruzan solapan`() {
        val a = LayoutRect(0f, 0f, 10f, 10f)
        val b = LayoutRect(5f, 5f, 15f, 15f)

        assertTrue(LayoutEngine.overlaps(a, b))
    }

    @Test
    fun `dos rectangulos que solo se tocan en el borde no solapan`() {
        val a = LayoutRect(0f, 0f, 10f, 10f)
        val b = LayoutRect(10f, 0f, 20f, 10f)

        assertFalse(LayoutEngine.overlaps(a, b))
    }

    @Test
    fun `un rectangulo sin area nunca solapa`() {
        val collapsed = LayoutRect(5f, 5f, 5f, 5f)
        val other = LayoutRect(0f, 0f, 10f, 10f)

        assertFalse(LayoutEngine.overlaps(collapsed, other))
        assertFalse(LayoutEngine.overlaps(other, collapsed))
    }

    // ---------------------------- isWithin

    @Test
    fun `un rectangulo dentro del area esta dentro — borde incluido`() {
        val area = LayoutRect(0f, 0f, 100f, 100f)

        assertTrue(LayoutEngine.isWithin(LayoutRect(10f, 10f, 90f, 90f), area))
        assertTrue(LayoutEngine.isWithin(area, area), "el borde exacto cuenta como dentro")
    }

    @Test
    fun `un rectangulo que se sale por cualquier lado no esta dentro`() {
        val area = LayoutRect(0f, 0f, 100f, 100f)

        assertFalse(LayoutEngine.isWithin(LayoutRect(-1f, 0f, 50f, 50f), area))
        assertFalse(LayoutEngine.isWithin(LayoutRect(0f, 0f, 101f, 50f), area))
        assertFalse(LayoutEngine.isWithin(LayoutRect(0f, 0f, 50f, 101f), area))
    }

    // ---------------------------- snapToGrid

    @Test
    fun `ajusta al multiplo de rejilla mas cercano`() {
        assertEquals(20f, LayoutEngine.snapToGrid(23f, 10f), 0.001f)
        assertEquals(30f, LayoutEngine.snapToGrid(26f, 10f), 0.001f)
        assertEquals(0f, LayoutEngine.snapToGrid(4f, 10f), 0.001f)
    }

    @Test
    fun `una rejilla de tamano cero o negativo deja el valor igual`() {
        assertEquals(23f, LayoutEngine.snapToGrid(23f, 0f), 0.001f)
        assertEquals(23f, LayoutEngine.snapToGrid(23f, -5f), 0.001f)
    }

    // ---------------------------- inset

    @Test
    fun `inset recorta cada lado por su margen`() {
        val recortado = LayoutRect(0f, 0f, 100f, 200f)
            .inset(LayoutInsets(left = 5f, top = 10f, right = 15f, bottom = 20f))

        assertEquals(LayoutRect(5f, 10f, 85f, 180f), recortado)
    }

    @Test
    fun `inset sin margenes deja el rectangulo igual`() {
        val rect = LayoutRect(3f, 4f, 100f, 200f)

        assertEquals(rect, rect.inset(LayoutInsets.NONE))
    }

    @Test
    fun `unos margenes mayores que el rectangulo lo dejan sin area — no invertido`() {
        val recortado = LayoutRect(0f, 0f, 100f, 100f)
            .inset(LayoutInsets(left = 80f, top = 90f, right = 80f, bottom = 90f))

        assertEquals(80f, recortado.left, 0.001f)
        assertEquals(90f, recortado.top, 0.001f)
        assertEquals(0f, recortado.width, 0.001f)
        assertEquals(0f, recortado.height, 0.001f)
    }
}
