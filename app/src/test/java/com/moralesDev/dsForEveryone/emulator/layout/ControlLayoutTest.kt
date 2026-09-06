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

    private val groupSizes = listOf(
        LayoutSize(1f, 1f),
        LayoutSize(80f, 80f),
        LayoutSize(300f, 300f),
        // Mucho más grande que cualquier margen disponible: el recorte a la
        // celda es lo único que puede evitar el solape aquí.
        LayoutSize(5000f, 5000f),
    )

    @Test
    fun `ningun grupo se solapa con otro ni con la pantalla tactil, en las dos orientaciones y con cualquier tamano`() {
        for ((label, size) in orientations) {
            for (mode in LayoutMode.entries) {
                val placement = ScreenLayout.resolve(mode, size, gap = 0.03f, secondaryScale = 0.35f)
                for (groupSize in groupSizes) {
                    val profile = ControlLayout.defaultProfile(size, placement.touchScreen, groupSize)
                    assertFalse(
                        "$mode en $label con grupos de $groupSize solapa",
                        ControlLayout.hasOverlap(profile, placement.touchScreen),
                    )
                }
            }
        }
    }

    @Test
    fun `un perfil por defecto trae los siete grupos`() {
        val profile = ControlLayout.defaultProfile(
            LayoutSize(1000f, 1000f),
            LayoutRect(0f, 600f, 1000f, 1000f),
            LayoutSize(80f, 80f),
        )

        assertEquals(ControlGroup.entries.toSet(), profile.groups.keys)
    }

    @Test
    fun `la opacidad pedida se respeta en cada grupo`() {
        val profile = ControlLayout.defaultProfile(
            LayoutSize(1000f, 1000f),
            LayoutRect(0f, 600f, 1000f, 1000f),
            LayoutSize(80f, 80f),
            opacity = 0.5f,
        )

        profile.groups.values.forEach { assertEquals(0.5f, it.opacity, 0.001f) }
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
        val original = ControlLayout.defaultProfile(available, placement.touchScreen, LayoutSize(180f, 180f), opacity = 0.6f)

        val fractions = ControlLayout.toFractions(original, available)
        val rebuilt = ControlLayout.fromFractions(fractions, available)

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
        assertNull(ControlLayout.fromFractions(partial, LayoutSize(1000f, 1000f)))
    }

    @Test
    fun `fromFractions rechaza un hueco sin area`() {
        val complete = ControlGroup.entries.associateWith { ControlGroupFraction(0f, 0f, 0.1f, 0.1f, 1f) }
        assertNull(ControlLayout.fromFractions(complete, LayoutSize(0f, 0f)))
    }

    @Test
    fun `resolveProfile sin perfil guardado cae al perfil por defecto`() {
        val available = LayoutSize(1000f, 1000f)
        val touchScreen = LayoutRect(0f, 600f, 1000f, 1000f)
        val groupSize = LayoutSize(80f, 80f)

        val resolved = ControlLayout.resolveProfile(null, available, touchScreen, groupSize)
        val default = ControlLayout.defaultProfile(available, touchScreen, groupSize, ControlLayout.DEFAULT_OPACITY)

        assertEquals(default, resolved)
    }

    @Test
    fun `resolveProfile con perfil guardado no usa el perfil por defecto`() {
        val available = LayoutSize(1000f, 1000f)
        val touchScreen = LayoutRect(0f, 600f, 1000f, 1000f)
        val groupSize = LayoutSize(80f, 80f)
        val custom = ControlGroup.entries.associateWith { ControlGroupFraction(0.4f, 0.4f, 0.1f, 0.1f, 0.9f) }

        val resolved = ControlLayout.resolveProfile(custom, available, touchScreen, groupSize)

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
}
