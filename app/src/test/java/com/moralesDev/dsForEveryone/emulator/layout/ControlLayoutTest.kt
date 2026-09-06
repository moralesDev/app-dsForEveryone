package com.moralesDev.dsForEveryone.emulator.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
