package com.moralesDev.dsForEveryone.emulator.layout

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test
import kotlin.test.assertFailsWith

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
    fun `ningun grupo se solapa con otro ni con la pantalla tactil — en las dos orientaciones y con cualquier tamano`() {
        for ((label, size) in orientations) {
            for (mode in LayoutMode.entries) {
                val placement = ScreenLayout.resolve(mode, size, gap = 0.03f, secondaryScale = 0.35f)
                for (minTouchTarget in minTouchTargets) {
                    val profile = ControlLayout.defaultProfile(size.asArea(), placement.touchScreen, minTouchTarget)
                    assertFalse(
                        ControlLayout.hasOverlap(profile, placement.touchScreen),
                        "$mode en $label con objetivo táctil de $minTouchTarget solapa",
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
    fun `la cruceta y el rombo de accion son mas grandes que L — R — Start — Select y soplar`() {
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
    fun `ningun objetivo tactil queda por debajo del minimo pedido — cuando hay banda suficiente`() {
        val available = LayoutSize(1080f, 2200f)
        val touchScreen = ScreenLayout.resolve(LayoutMode.STACKED, available, gap = 0.03f).touchScreen
        val minTouchTarget = 48f

        val profile = ControlLayout.defaultProfile(available.asArea(), touchScreen, minTouchTarget)

        profile.groups.values.forEach { placement ->
            assertTrue(placement.bounds.width >= minTouchTarget - 0.01f)
            assertTrue(placement.bounds.height >= minTouchTarget - 0.01f)
        }
    }

    @Test
    fun `un perfil incompleto no se acepta`() {
        assertFailsWith<IllegalArgumentException> {
            ControlLayoutProfile(
                mapOf(ControlGroup.L to ControlGroupPlacement(LayoutRect(0f, 0f, 10f, 10f), 1f)),
            )
        }
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
    fun `decode rechaza texto vacio — incompleto o con numeros invalidos`() {
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
                            LayoutEngine.isWithin(placement.bounds, area),
                            "$group en $label ($mode, objetivo $minTouchTarget) se sale: ${placement.bounds}",
                        )
                    }
                    assertFalse(
                        ControlLayout.hasOverlap(profile, touchScreen),
                        "$mode en $label con objetivo táctil de $minTouchTarget solapa",
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

    // ------------------------------------------------ Perfil superpuesto (vertical)

    /** Teléfonos en vertical: alto 20:9, 16:9, uno pequeño y un hueco absurdo. */
    private val portraitAreas = listOf(
        LayoutRect(0f, 66f, 1080f, 2352f),
        LayoutRect(0f, 60f, 1080f, 1872f),
        LayoutRect(0f, 40f, 720f, 1232f),
        LayoutRect(0f, 0f, 300f, 300f),
    )

    @Test
    fun `superpuesto — ningun grupo se solapa con otro ni se sale del hueco — con cualquier tamano`() {
        for (area in portraitAreas) {
            for (minTouchTarget in minTouchTargets + 126f) {
                val profile = ControlLayout.overlayProfile(area, minTouchTarget)
                assertFalse(
                    ControlLayout.hasOverlap(profile, touchScreen = null),
                    "$area con objetivo $minTouchTarget solapa",
                )
                profile.groups.forEach { (group, placement) ->
                    assertTrue(
                        LayoutEngine.isWithin(placement.bounds, area),
                        "$group en $area (objetivo $minTouchTarget) se sale: ${placement.bounds}",
                    )
                }
            }
        }
    }

    @Test
    fun `superpuesto — cada grupo va del lado de su mano`() {
        for (area in portraitAreas) {
            val groups = ControlLayout.overlayProfile(area, 126f).groups.mapValues { it.value.bounds }
            listOf(ControlGroup.L, ControlGroup.BLOW, ControlGroup.DIRECTIONAL_PAD, ControlGroup.SELECT).forEach {
                assertTrue(groups.getValue(it).right <= area.centerX, "$it en $area")
            }
            listOf(ControlGroup.R, ControlGroup.ACTION_BUTTONS, ControlGroup.START).forEach {
                assertTrue(groups.getValue(it).left >= area.centerX, "$it en $area")
            }
        }
    }

    @Test
    fun `superpuesto — L arriba — soplar debajo de L y la direccion debajo de soplar`() {
        for (area in portraitAreas) {
            val groups = ControlLayout.overlayProfile(area, 126f).groups.mapValues { it.value.bounds }
            val l = groups.getValue(ControlGroup.L)
            val blow = groups.getValue(ControlGroup.BLOW)
            val dpad = groups.getValue(ControlGroup.DIRECTIONAL_PAD)
            val r = groups.getValue(ControlGroup.R)
            val action = groups.getValue(ControlGroup.ACTION_BUTTONS)

            assertTrue(l.bottom <= blow.top && blow.bottom <= dpad.top, "$area")
            assertTrue(r.bottom <= action.top, "$area")
            assertEquals(l.top, r.top, 0.01f)
            assertEquals(dpad.bottom, action.bottom, 0.01f)
        }
    }

    @Test
    fun `superpuesto — Select y Start abajo — uno a cada lado del centro`() {
        for (area in portraitAreas) {
            val groups = ControlLayout.overlayProfile(area, 126f).groups.mapValues { it.value.bounds }
            val select = groups.getValue(ControlGroup.SELECT)
            val start = groups.getValue(ControlGroup.START)
            val dpad = groups.getValue(ControlGroup.DIRECTIONAL_PAD)

            assertTrue(select.right <= area.centerX && start.left >= area.centerX, "$area")
            assertEquals(select.bottom, start.bottom, 0.01f)
            assertTrue(select.bottom >= dpad.bottom, "$area")
            // Pegados al centro: ningún otro grupo entre ellos y el centro.
            assertTrue(area.centerX - select.right < select.width, "$area")
        }
    }

    @Test
    fun `superpuesto — con sitio de sobra cada grupo tiene su tamano`() {
        val area = portraitAreas.first()
        val m = 126f
        val groups = ControlLayout.overlayProfile(area, m).groups.mapValues { it.value.bounds }

        fun assertSize(group: ControlGroup, width: Float, height: Float) {
            val bounds = groups.getValue(group)
            assertEquals(width, bounds.width, 0.01f, "$group ancho")
            assertEquals(height, bounds.height, 0.01f, "$group alto")
        }
        assertSize(ControlGroup.DIRECTIONAL_PAD, 3f * m, 3f * m)
        // El rombo, un 20 % menor que la dirección.
        assertSize(ControlGroup.ACTION_BUTTONS, 2.4f * m, 2.4f * m)
        // L y R: la mitad de alto y un 25 % menos de largo que en la banda (2,4 × 1,3).
        assertSize(ControlGroup.L, 1.8f * m, 0.65f * m)
        assertSize(ControlGroup.R, 1.8f * m, 0.65f * m)
        // Soplar, la mitad del mínimo táctil.
        assertSize(ControlGroup.BLOW, 0.5f * m, 0.5f * m)
        assertSize(ControlGroup.SELECT, 0.9f * m, 0.6f * m)
        assertSize(ControlGroup.START, 0.9f * m, 0.6f * m)
    }

    @Test
    fun `superpuesto — ningun grupo sube del techo`() {
        for (area in portraitAreas) {
            val ceiling = area.top + area.height * 0.55f
            for (minTouchTarget in minTouchTargets + 126f) {
                val profile = ControlLayout.overlayProfile(area, minTouchTarget, ceiling = ceiling)
                profile.groups.forEach { (group, placement) ->
                    assertTrue(
                        placement.bounds.top >= ceiling - 0.01f,
                        "$group en $area (objetivo $minTouchTarget) sube de $ceiling: ${placement.bounds}",
                    )
                }
                assertFalse(ControlLayout.hasOverlap(profile, touchScreen = null))
            }
        }
    }

    @Test
    fun `el techo es el borde de abajo de la pantalla de arriba — sea cual sea`() {
        val size = LayoutSize(1080f, 2400f)
        val insets = LayoutInsets(top = 66f, bottom = 48f)
        val area = size.asArea().inset(insets)

        val normal = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.02f, insets = insets)
        assertEquals(normal.topScreen.bottom, ControlLayout.overlayCeiling(normal, area), 0.01f)

        val swapped = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.02f, swapped = true, insets = insets)
        assertEquals(swapped.touchScreen.bottom, ControlLayout.overlayCeiling(swapped, area), 0.01f)
    }

    @Test
    fun `sin pantallas apiladas no hay techo`() {
        val size = LayoutSize(1080f, 2400f)
        val area = size.asArea()
        for (mode in listOf(LayoutMode.TOP_ONLY, LayoutMode.BOTTOM_ONLY, LayoutMode.MAIN_AND_SECONDARY, LayoutMode.SIDE_BY_SIDE)) {
            val placement = ScreenLayout.resolve(mode, size)
            assertEquals(area.top, ControlLayout.overlayCeiling(placement, area), 0.01f, "$mode")
        }
    }

    @Test
    fun `el boton de apartar va abajo a la derecha — a la altura de Start — sin pisar nada`() {
        for (area in portraitAreas) {
            for (minTouchTarget in minTouchTargets + 126f) {
                for (systemTop in listOf(null, area.bottom - minTouchTarget * 1.5f)) {
                    val profile = ControlLayout.overlayProfile(
                        area,
                        minTouchTarget,
                        ceiling = area.centerY,
                        inwardShift = 40f,
                        systemTop = systemTop,
                    )
                    val toggle = ControlLayout.hideToggleBounds(profile, minTouchTarget, area)
                    val r = profile.groups.getValue(ControlGroup.R).bounds
                    val start = profile.groups.getValue(ControlGroup.START).bounds
                    val label = "$area, objetivo $minTouchTarget, systemTop $systemTop"

                    assertEquals(r.right, toggle.right, 0.01f, label)
                    assertTrue(LayoutEngine.isWithin(toggle, area), label)
                    profile.groups.forEach { (group, placement) ->
                        assertFalse(LayoutEngine.overlaps(toggle, placement.bounds), "pisa $group: $label")
                    }
                    // Con un hueco realista va justo a la altura de Start.
                    if (minTouchTarget == 126f && area.height > 1000f) {
                        assertEquals(start.centerY, toggle.centerY, 0.01f, label)
                    }
                }
            }
        }
    }

    @Test
    fun `superpuesto — en un 16 9 los mandos pisan la pantalla inferior`() {
        val size = LayoutSize(1080f, 1920f)
        val insets = LayoutInsets(top = 60f, bottom = 48f)
        val area = size.asArea().inset(insets)
        val placement = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.02f, insets = insets)
        val touchScreen = placement.touchScreen

        val profile = ControlLayout.overlayProfile(area, 126f, ceiling = ControlLayout.overlayCeiling(placement, area))

        assertTrue(ControlLayout.hasOverlap(profile, touchScreen))
        assertFalse(ControlLayout.hasOverlap(profile, touchScreen = null))
    }

    @Test
    fun `resolveProfile en modo superpuesto sin perfil guardado cae al superpuesto`() {
        val area = portraitAreas.first()
        val touchScreen = ScreenLayout.resolve(LayoutMode.STACKED, LayoutSize(1080f, 2400f)).touchScreen

        val resolved = ControlLayout.resolveProfile(null, area, touchScreen, 126f, overlay = true)

        assertEquals(ControlLayout.overlayProfile(area, 126f), resolved)
    }

    @Test
    fun `la direccion y el rombo se acercan al centro lo pedido — y nada mas se mueve`() {
        val area = portraitAreas.first()
        val base = ControlLayout.overlayProfile(area, 126f).groups.mapValues { it.value.bounds }
        val shifted = ControlLayout.overlayProfile(area, 126f, inwardShift = 50f).groups.mapValues { it.value.bounds }

        assertEquals(base.getValue(ControlGroup.DIRECTIONAL_PAD).left + 50f, shifted.getValue(ControlGroup.DIRECTIONAL_PAD).left, 0.01f)
        assertEquals(base.getValue(ControlGroup.ACTION_BUTTONS).right - 50f, shifted.getValue(ControlGroup.ACTION_BUTTONS).right, 0.01f)
        listOf(ControlGroup.L, ControlGroup.R, ControlGroup.BLOW).forEach {
            assertEquals(base.getValue(it).left, shifted.getValue(it).left, 0.01f, "$it")
        }
    }

    @Test
    fun `por mucho que se pida acercarlos — nunca pasan su mitad ni se solapan`() {
        for (area in portraitAreas) {
            val profile = ControlLayout.overlayProfile(area, 126f, inwardShift = 10_000f)
            val groups = profile.groups.mapValues { it.value.bounds }
            assertTrue(groups.getValue(ControlGroup.DIRECTIONAL_PAD).right <= area.centerX + 0.01f, "$area")
            assertTrue(groups.getValue(ControlGroup.ACTION_BUTTONS).left >= area.centerX - 0.01f, "$area")
            assertFalse(ControlLayout.hasOverlap(profile, touchScreen = null), "$area")
            groups.values.forEach { assertTrue(LayoutEngine.isWithin(it, area), "$area: $it") }
        }
    }

    @Test
    fun `el techo bajo el centro manda cuando queda mas abajo que la pantalla de arriba`() {
        val size = LayoutSize(1080f, 2400f)
        val area = size.asArea()
        val placement = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f)

        assertEquals(area.centerY + 300f, ControlLayout.overlayCeiling(placement, area, belowCenter = 300f), 0.01f)
        // Sin margen bajo el centro, vuelve a mandar la pantalla de arriba.
        assertEquals(placement.topScreen.bottom, ControlLayout.overlayCeiling(placement, area), 0.01f)
        // Y nunca por debajo del hueco.
        assertEquals(area.bottom, ControlLayout.overlayCeiling(placement, area, belowCenter = 99_999f), 0.01f)
    }

    @Test
    fun `el hueco entre pantallas apiladas — y ninguno si no lo estan`() {
        val size = LayoutSize(1080f, 2400f)
        val stacked = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f)
        assertEquals(stacked.topScreen.bottom to stacked.touchScreen.top, ControlLayout.stackedGap(stacked))

        val swapped = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f, swapped = true)
        assertEquals(swapped.touchScreen.bottom to swapped.topScreen.top, ControlLayout.stackedGap(swapped))

        for (mode in listOf(LayoutMode.SIDE_BY_SIDE, LayoutMode.TOP_ONLY, LayoutMode.MAIN_AND_SECONDARY)) {
            assertNull(ControlLayout.stackedGap(ScreenLayout.resolve(mode, size)), "$mode")
        }
    }

    @Test
    fun `L y R empiezan justo en el techo y el sobrante queda abajo`() {
        val area = portraitAreas.first()
        val ceiling = area.centerY + 200f
        val groups = ControlLayout.overlayProfile(area, 126f, ceiling = ceiling).groups.mapValues { it.value.bounds }

        assertEquals(ceiling, groups.getValue(ControlGroup.L).top, 0.01f)
        assertEquals(ceiling, groups.getValue(ControlGroup.R).top, 0.01f)
        val lowest = groups.values.maxOf { it.bottom }
        assertTrue(area.bottom - lowest > 126f, "el bloque llega hasta $lowest, pegado al borde ${area.bottom}")
    }

    @Test
    fun `Select y Start bajan a la altura pedida — soplar con ellos — y el resto no se mueve`() {
        val area = portraitAreas.first()
        val ceiling = area.centerY + 30f
        val base = ControlLayout.overlayProfile(area, 126f, ceiling = ceiling, inwardShift = 40f)
            .groups.mapValues { it.value.bounds }
        val systemTop = area.bottom - 300f
        val moved = ControlLayout.overlayProfile(area, 126f, ceiling = ceiling, inwardShift = 40f, systemTop = systemTop)
        val groups = moved.groups.mapValues { it.value.bounds }

        assertEquals(systemTop, groups.getValue(ControlGroup.SELECT).top, 0.01f)
        assertEquals(systemTop, groups.getValue(ControlGroup.START).top, 0.01f)
        val blow = groups.getValue(ControlGroup.BLOW)
        assertEquals(groups.getValue(ControlGroup.SELECT).centerY, blow.centerY, 0.01f)
        assertEquals(groups.getValue(ControlGroup.L).left, blow.left, 0.01f)
        (ControlGroup.entries - ControlGroup.SELECT - ControlGroup.START - ControlGroup.BLOW).forEach {
            assertEquals(base.getValue(it), groups.getValue(it), "$it")
        }
        assertFalse(ControlLayout.hasOverlap(moved, touchScreen = null))
    }

    @Test
    fun `Select y Start nunca pisan la direccion ni se salen por abajo`() {
        for (area in portraitAreas) {
            for (systemTop in listOf(area.top, area.centerY, area.bottom - 1f, area.bottom + 500f)) {
                val profile = ControlLayout.overlayProfile(area, 126f, inwardShift = 40f, systemTop = systemTop)
                assertFalse(ControlLayout.hasOverlap(profile, touchScreen = null), "$area, $systemTop")
                profile.groups.values.forEach {
                    assertTrue(LayoutEngine.isWithin(it.bounds, area), "$area, $systemTop: ${it.bounds}")
                }
            }
        }
    }

    @Test
    fun `el borde de abajo de la pantalla inferior — sea cual sea`() {
        val size = LayoutSize(1080f, 2400f)
        val normal = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f)
        assertEquals(normal.touchScreen.bottom, ControlLayout.lowerScreenBottom(normal))
        val swapped = ScreenLayout.resolve(LayoutMode.STACKED, size, gap = 0.05f, swapped = true)
        assertEquals(swapped.topScreen.bottom, ControlLayout.lowerScreenBottom(swapped))
        assertNull(ControlLayout.lowerScreenBottom(ScreenLayout.resolve(LayoutMode.SIDE_BY_SIDE, size)))
    }

    /** El perfil vertical tal como lo pide GameScreen en un teléfono 20:9. */
    private fun portraitScene(mode: LayoutMode, swapped: Boolean = false): Pair<ControlLayoutProfile, ScreenPlacement> {
        val size = LayoutSize(1080f, 2400f)
        val insets = LayoutInsets(top = 66f, bottom = 48f)
        val area = size.asArea().inset(insets)
        val placement = ScreenLayout.resolve(mode, size, gap = 0.05f, swapped = swapped, insets = insets, upperHalf = true)
        val profile = ControlLayout.overlayProfile(
            area,
            126f,
            ceiling = ControlLayout.overlayCeiling(placement, area, belowCenter = 31f),
            inwardShift = 19f,
            systemTop = ControlLayout.lowerScreenBottom(placement)?.plus(31f),
        )
        return profile to placement
    }

    @Test
    fun `apiladas — se apartan los que tapan la pantalla de abajo y Select y Start se quedan`() {
        for (swapped in listOf(false, true)) {
            val (profile, placement) = portraitScene(LayoutMode.STACKED, swapped)
            val hideable = ControlLayout.groupsCoveringScreens(profile, placement)

            assertFalse(ControlGroup.SELECT in hideable, "$swapped")
            assertFalse(ControlGroup.START in hideable, "$swapped")
            assertTrue(ControlGroup.DIRECTIONAL_PAD in hideable, "$swapped")
            assertTrue(ControlGroup.ACTION_BUTTONS in hideable, "$swapped")
        }
    }

    @Test
    fun `apiladas — soplar va abajo a la izquierda frente al boton de apartar — sin tapar la pantalla`() {
        for (swapped in listOf(false, true)) {
            val (profile, placement) = portraitScene(LayoutMode.STACKED, swapped)
            val blow = profile.groups.getValue(ControlGroup.BLOW).bounds
            val area = LayoutSize(1080f, 2400f).asArea().inset(LayoutInsets(top = 66f, bottom = 48f))
            val toggle = ControlLayout.hideToggleBounds(profile, 126f, area)

            assertEquals(toggle.centerY, blow.centerY, 0.01f, "$swapped")
            assertEquals(profile.groups.getValue(ControlGroup.L).bounds.left, blow.left, 0.01f, "$swapped")
            assertFalse(LayoutEngine.overlaps(blow, placement.topScreen), "$swapped")
            assertFalse(LayoutEngine.overlaps(blow, placement.touchScreen), "$swapped")
            assertFalse(ControlLayout.hasOverlap(profile, touchScreen = null), "$swapped")
        }
    }

    @Test
    fun `soplar no se aparta nunca — aunque tape una pantalla`() {
        val (profile, placement) = portraitScene(LayoutMode.STACKED)
        val l = profile.groups.getValue(ControlGroup.L)
        val overBottomScreen = ControlLayoutProfile(profile.groups + (ControlGroup.BLOW to l))
        assertFalse(ControlGroup.BLOW in ControlLayout.groupsCoveringScreens(overBottomScreen, placement))
    }

    @Test
    fun `con las pantallas en la mitad de arriba no hay nada que apartar`() {
        for (mode in listOf(LayoutMode.TOP_ONLY, LayoutMode.BOTTOM_ONLY, LayoutMode.SIDE_BY_SIDE, LayoutMode.MAIN_AND_SECONDARY)) {
            val (profile, placement) = portraitScene(mode)
            assertEquals(emptySet<ControlGroup>(), ControlLayout.groupsCoveringScreens(profile, placement), "$mode")
        }
    }
}
