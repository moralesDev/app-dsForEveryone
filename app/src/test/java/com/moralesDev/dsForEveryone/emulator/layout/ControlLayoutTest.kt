package com.moralesDev.dsForEveryone.emulator.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlLayoutTest {

    private val orientations = listOf(
        "vertical" to LayoutSize(1080f, 2200f),
        "horizontal" to LayoutSize(2200f, 1080f),
    )

    /** El hueco entero, sin barras del sistema: el caso de siempre. */
    private fun LayoutSize.asArea() = LayoutRect(0f, 0f, width, height)

    private val minTouchTargets = listOf(
        1f,
        48f,
        80f,
        // Mucho más grande que cualquier margen disponible: el recorte a la
        // celda es lo único que puede evitar el solape aquí.
        5000f,
    )

    @Test
    fun `ningun grupo se solapa con otro ni con la pantalla tactil, en las dos orientaciones y con cualquier tamano`() {
        for ((label, size) in orientations) {
            for (mode in LayoutMode.entries) {
                val placement = ScreenLayout.resolve(mode, size, gap = 0.03f, secondaryScale = 0.35f)
                for (minTouchTarget in minTouchTargets) {
                    val profile = ControlLayout.defaultProfile(size.asArea(), placement.touchScreen, minTouchTarget)
                    assertFalse(
                        "$mode en $label con objetivo táctil de $minTouchTarget solapa",
                        ControlLayout.hasOverlap(profile, placement.touchScreen),
                    )
                }
            }
        }
    }

    @Test
    fun `un perfil por defecto trae los siete grupos`() {
        val profile = ControlLayout.defaultProfile(
            LayoutRect(0f, 0f, 1000f, 1000f),
            LayoutRect(0f, 600f, 1000f, 1000f),
            48f,
        )

        assertEquals(ControlGroup.entries.toSet(), profile.groups.keys)
    }

    @Test
    fun `la opacidad pedida se respeta en cada grupo`() {
        val profile = ControlLayout.defaultProfile(
            LayoutRect(0f, 0f, 1000f, 1000f),
            LayoutRect(0f, 600f, 1000f, 1000f),
            48f,
            opacity = 0.5f,
        )

        profile.groups.values.forEach { assertEquals(0.5f, it.opacity, 0.001f) }
    }

    @Test
    fun `la cruceta y el rombo de accion son mas grandes que L, R, Start, Select y soplar`() {
        val profile = ControlLayout.defaultProfile(
            LayoutRect(0f, 0f, 1080f, 2200f),
            ScreenLayout.resolve(LayoutMode.STACKED, LayoutSize(1080f, 2200f), gap = 0.03f).touchScreen,
            48f,
        )

        val dpadArea = profile.groups.getValue(ControlGroup.DIRECTIONAL_PAD).bounds.let { it.width * it.height }
        val actionArea = profile.groups.getValue(ControlGroup.ACTION_BUTTONS).bounds.let { it.width * it.height }
        val lArea = profile.groups.getValue(ControlGroup.L).bounds.let { it.width * it.height }
        val rArea = profile.groups.getValue(ControlGroup.R).bounds.let { it.width * it.height }
        val startArea = profile.groups.getValue(ControlGroup.START).bounds.let { it.width * it.height }
        val selectArea = profile.groups.getValue(ControlGroup.SELECT).bounds.let { it.width * it.height }
        val blowArea = profile.groups.getValue(ControlGroup.BLOW).bounds.let { it.width * it.height }

        assertTrue(dpadArea > lArea && dpadArea > rArea)
        assertTrue(actionArea > lArea && actionArea > rArea)
        assertTrue(lArea > startArea && lArea > selectArea && lArea > blowArea)
        assertTrue(rArea > startArea && rArea > selectArea && rArea > blowArea)
    }

    @Test
    fun `ningun objetivo tactil queda por debajo del minimo pedido, cuando hay banda suficiente`() {
        val available = LayoutSize(1080f, 2200f)
        val touchScreen = ScreenLayout.resolve(LayoutMode.STACKED, available, gap = 0.03f).touchScreen
        val minTouchTarget = 48f

        val profile = ControlLayout.defaultProfile(available.asArea(), touchScreen, minTouchTarget)

        profile.groups.values.forEach { placement ->
            assertTrue(placement.bounds.width >= minTouchTarget - 0.01f)
            assertTrue(placement.bounds.height >= minTouchTarget - 0.01f)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `un perfil incompleto no se acepta`() {
        ControlLayoutProfile(
            mapOf(ControlGroup.L to ControlGroupPlacement(LayoutRect(0f, 0f, 10f, 10f), 1f)),
        )
    }

    @Test
    fun `un perfil completo se acepta sin lanzar`() {
        val allGroups = ControlGroup.entries.associateWith {
            ControlGroupPlacement(LayoutRect(0f, 0f, 10f, 10f), 1f)
        }

        ControlLayoutProfile(allGroups) // no debe lanzar
    }

    @Test
    fun `hasOverlap detecta dos grupos superpuestos entre si`() {
        val allGroups = ControlGroup.entries.associateWith {
            ControlGroupPlacement(LayoutRect(0f, 0f, 100f, 100f), 1f)
        }
        val profile = ControlLayoutProfile(allGroups)

        assertEquals(true, ControlLayout.hasOverlap(profile, LayoutRect(500f, 500f, 500f, 500f)))
    }

    // --- Persistencia del perfil (entregable 2.7): fracciones y su codificación ---

    @Test
    fun `toFractions seguido de fromFractions reproduce el mismo perfil`() {
        val available = LayoutSize(1080f, 2200f)
        val placement = ScreenLayout.resolve(LayoutMode.STACKED, available, gap = 0.03f)
        val original = ControlLayout.defaultProfile(available.asArea(), placement.touchScreen, 48f, opacity = 0.6f)

        val fractions = ControlLayout.toFractions(original, available.asArea())
        val rebuilt = ControlLayout.fromFractions(fractions, available.asArea())

        assertEquals(ControlGroup.entries.toSet(), fractions.keys)
        requireNotNull(rebuilt)
        ControlGroup.entries.forEach { group ->
            val expected = original.groups.getValue(group).bounds
            val actual = rebuilt.groups.getValue(group).bounds
            assertEquals(expected.left, actual.left, 0.01f)
            assertEquals(expected.top, actual.top, 0.01f)
            assertEquals(expected.right, actual.right, 0.01f)
            assertEquals(expected.bottom, actual.bottom, 0.01f)
            assertEquals(0.6f, rebuilt.groups.getValue(group).opacity, 0.001f)
        }
    }

    @Test
    fun `fromFractions rechaza un mapa incompleto`() {
        val partial = mapOf(
            ControlGroup.L to ControlGroupFraction(0f, 0f, 0.1f, 0.1f, 1f),
        )
        assertNull(ControlLayout.fromFractions(partial, LayoutRect(0f, 0f, 1000f, 1000f)))
    }

    @Test
    fun `fromFractions rechaza un hueco sin area`() {
        val complete = ControlGroup.entries.associateWith { ControlGroupFraction(0f, 0f, 0.1f, 0.1f, 1f) }
        assertNull(ControlLayout.fromFractions(complete, LayoutRect(0f, 0f, 0f, 0f)))
    }

    @Test
    fun `resolveProfile sin perfil guardado cae al perfil por defecto`() {
        val available = LayoutSize(1000f, 1000f)
        val touchScreen = LayoutRect(0f, 600f, 1000f, 1000f)
        val minTouchTarget = 48f

        val resolved = ControlLayout.resolveProfile(null, available.asArea(), touchScreen, minTouchTarget)
        val default = ControlLayout.defaultProfile(available.asArea(), touchScreen, minTouchTarget, ControlLayout.DEFAULT_OPACITY)

        assertEquals(default, resolved)
    }

    @Test
    fun `resolveProfile con perfil guardado no usa el perfil por defecto`() {
        val available = LayoutSize(1000f, 1000f)
        val touchScreen = LayoutRect(0f, 600f, 1000f, 1000f)
        val minTouchTarget = 48f
        val custom = ControlGroup.entries.associateWith { ControlGroupFraction(0.4f, 0.4f, 0.1f, 0.1f, 0.9f) }

        val resolved = ControlLayout.resolveProfile(custom, available.asArea(), touchScreen, minTouchTarget)

        assertEquals(0.9f, resolved.groups.getValue(ControlGroup.L).opacity, 0.001f)
        assertEquals(400f, resolved.groups.getValue(ControlGroup.L).bounds.left, 0.01f)
    }

    @Test
    fun `encode seguido de decode reproduce las mismas fracciones`() {
        val fractions = ControlGroup.entries.associateWith { group ->
            ControlGroupFraction(0.1f, 0.2f, 0.15f, 0.15f, if (group == ControlGroup.BLOW) 0.5f else 0.8f)
        }

        val decoded = ControlLayout.decode(ControlLayout.encode(fractions))

        assertEquals(fractions, decoded)
    }

    @Test
    fun `decode rechaza texto vacio, incompleto o con numeros invalidos`() {
        assertNull(ControlLayout.decode(""))
        assertNull(ControlLayout.decode("L:0,0,0.1,0.1,1"))
        assertNull(ControlLayout.decode(ControlGroup.entries.joinToString(";") { "${it.name}:0,0,0.1,noesnumero,1" }))
        assertNull(ControlLayout.decode(ControlGroup.entries.joinToString(";") { "${it.name}:0,0,0.1" }))
        assertTrue(ControlLayout.decode("NO_EXISTE:0,0,0.1,0.1,1") == null)
    }

    // --- Las barras del sistema: nada pulsable puede quedar debajo de una ---

    /**
     * Un mando debajo de una barra del sistema es un mando que no se puede
     * pulsar: el toque se lo queda el sistema. Con márgenes, ningún grupo
     * sale del hueco utilizable, ni en el perfil por defecto ni al reconstruir
     * uno guardado.
     */
    @Test
    fun `ningun grupo del perfil por defecto se sale del hueco utilizable`() {
        // Barra de estado arriba y de navegación a un lado y abajo.
        val insets = LayoutInsets(left = 0f, top = 66f, right = 120f, bottom = 48f)
        for ((label, size) in orientations) {
            val area = size.asArea().inset(insets)
            for (mode in LayoutMode.entries) {
                val touchScreen = ScreenLayout.resolve(mode, size, gap = 0.03f, insets = insets).touchScreen
                for (minTouchTarget in minTouchTargets) {
                    val profile = ControlLayout.defaultProfile(area, touchScreen, minTouchTarget)
                    profile.groups.forEach { (group, placement) ->
                        assertTrue(
                            "$group en $label ($mode, objetivo $minTouchTarget) se sale: ${placement.bounds}",
                            LayoutEngine.isWithin(placement.bounds, area),
                        )
                    }
                    assertFalse(
                        "$mode en $label con objetivo táctil de $minTouchTarget solapa",
                        ControlLayout.hasOverlap(profile, touchScreen),
                    )
                }
            }
        }
    }

    @Test
    fun `un perfil guardado se reconstruye dentro del hueco utilizable`() {
        val area = LayoutSize(2200f, 1080f).asArea()
            .inset(LayoutInsets(top = 66f, right = 120f, bottom = 48f))
        val custom = ControlGroup.entries.associateWith { ControlGroupFraction(0f, 0f, 0.1f, 0.1f, 0.9f) }

        val profile = requireNotNull(ControlLayout.fromFractions(custom, area))

        // La fracción (0,0) es la esquina del hueco utilizable, no la de la
        // ventana: un grupo pegado al borde queda pulsable, no bajo la barra.
        profile.groups.values.forEach { placement ->
            assertEquals(area.left, placement.bounds.left, 0.01f)
            assertEquals(area.top, placement.bounds.top, 0.01f)
            assertTrue(LayoutEngine.isWithin(placement.bounds, area))
        }
    }

    @Test
    fun `las fracciones van y vuelven igual con un hueco desplazado`() {
        val size = LayoutSize(2200f, 1080f)
        val insets = LayoutInsets(left = 66f, top = 66f, right = 120f, bottom = 48f)
        val area = size.asArea().inset(insets)
        val touchScreen = ScreenLayout.resolve(
            LayoutMode.SIDE_BY_SIDE,
            size,
            gap = 0.03f,
            insets = insets,
        ).touchScreen
        val original = ControlLayout.defaultProfile(area, touchScreen, 48f, opacity = 0.6f)

        val rebuilt = requireNotNull(ControlLayout.fromFractions(ControlLayout.toFractions(original, area), area))

        ControlGroup.entries.forEach { group ->
            val expected = original.groups.getValue(group).bounds
            val actual = rebuilt.groups.getValue(group).bounds
            assertEquals(expected.left, actual.left, 0.01f)
            assertEquals(expected.top, actual.top, 0.01f)
            assertEquals(expected.right, actual.right, 0.01f)
            assertEquals(expected.bottom, actual.bottom, 0.01f)
        }
    }

    @Test
    fun `fractionOf es la misma cuenta que toFractions`() {
        val area = LayoutRect(50f, 60f, 1050f, 560f)
        val bounds = LayoutRect(150f, 110f, 250f, 210f)
        val profile = ControlLayoutProfile(
            ControlGroup.entries.associateWith { ControlGroupPlacement(bounds, 0.7f) },
        )

        val suelta = ControlLayout.fractionOf(bounds, area, 0.7f)

        assertEquals(0.1f, suelta.left, 0.001f)
        assertEquals(0.1f, suelta.top, 0.001f)
        assertEquals(suelta, ControlLayout.toFractions(profile, area).getValue(ControlGroup.L))
    }
}
