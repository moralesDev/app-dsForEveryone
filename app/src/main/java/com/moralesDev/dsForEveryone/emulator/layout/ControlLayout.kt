package com.moralesDev.dsForEveryone.emulator.layout

/**
 * Los siete grupos de mandos que hay que colocar.
 *
 * El plan y docs/entrada.md los describen como «cruceta, A/B/X/Y, L, R,
 * Start+Select, y el botón de soplar» pero dicen **siete** grupos mientras
 * enumeran seis nombres. Para que la cuenta salga y para que el editor
 * (fase 2.7) pueda mover y redimensionar cada uno por separado —que es
 * justo lo que promete—, Start y Select se tratan aquí como dos grupos
 * independientes, no uno compuesto. Es la lectura que hace que «siete» sea
 * literal y no un redondeo.
 */
enum class ControlGroup {
    DIRECTIONAL_PAD,

    /** A, B, X e Y se mueven y escalan juntos, como un único rombo. */
    ACTION_BUTTONS,
    L,
    R,
    START,
    SELECT,

    /** El botón de soplar: inyecta ruido en el micrófono mientras se pulsa. */
    BLOW,
}

/** Posición, tamaño y opacidad de un grupo, dentro de un perfil. */
data class ControlGroupPlacement(
    val bounds: LayoutRect,
    val opacity: Float,
)

/**
 * Un perfil completo: los siete grupos, para una orientación.
 *
 * «Perfiles independientes por orientación» es una decisión de quien
 * persiste esto (ver `EmulatorSettings`, que guardará uno para vertical y
 * otro para horizontal), no de este tipo: un [ControlLayoutProfile] es
 * agnóstico de en qué orientación se use.
 */
data class ControlLayoutProfile(
    val groups: Map<ControlGroup, ControlGroupPlacement>,
) {
    init {
        val missing = ControlGroup.entries.toSet() - groups.keys
        require(missing.isEmpty()) {
            "Un perfil debe traer los siete grupos: faltan $missing"
        }
    }
}

/**
 * Coloca los siete grupos de mandos sobre [LayoutEngine], sin solaparse entre
 * sí ni con la pantalla táctil. Solapar la pantalla superior sí está
 * permitido: no es interactiva, así que no hay ningún toque que perder.
 *
 * Geometría pura: sin `Context`, sin `Composable`, sin unidades de Compose.
 */
object ControlLayout {

    /**
     * Si algún grupo del perfil solapa con otro o con [touchScreen].
     * Se usa tanto para validar un perfil por defecto como el resultado de
     * arrastrar algo en el editor (fase 2.7).
     */
    fun hasOverlap(profile: ControlLayoutProfile, touchScreen: LayoutRect): Boolean {
        val rects = profile.groups.values.map { it.bounds } + touchScreen
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                if (LayoutEngine.overlaps(rects[i], rects[j])) return true
            }
        }
        return false
    }

    /**
     * Perfil de partida para un hueco disponible: coloca los siete grupos en
     * los márgenes que deja [touchScreen] respecto al hueco completo —arriba,
     * L, el botón de soplar y R; abajo, la cruceta, Start, Select y el rombo
     * de acción—, sin invadirla nunca. Cada grupo pide [groupSize], pero se
     * recorta al hueco de su celda si no cabe entero: así ningún tamaño,
     * por grande que sea, puede hacer que dos grupos se solapen.
     *
     * Es un punto de partida, no el resultado final: el editor (fase 2.7)
     * deja mover y redimensionar cada grupo a mano, y ese resultado se valida
     * con [hasOverlap], no con esta función.
     */
    fun defaultProfile(
        available: LayoutSize,
        touchScreen: LayoutRect,
        groupSize: LayoutSize,
        opacity: Float = 0.72f,
    ): ControlLayoutProfile {
        val area = LayoutRect(0f, 0f, available.width, available.height)
        val topMargin = LayoutRect(area.left, area.top, area.right, touchScreen.top.coerceIn(area.top, area.bottom))
        val bottomMargin =
            LayoutRect(area.left, touchScreen.bottom.coerceIn(area.top, area.bottom), area.right, area.bottom)

        val (lCell, blowCell, rCell) = topMargin.splitHorizontally(3)
        val (dpadCell, startCell, selectCell, actionCell) = bottomMargin.splitHorizontally(4)

        val groups = mapOf(
            ControlGroup.L to lCell.place(groupSize, opacity),
            ControlGroup.BLOW to blowCell.place(groupSize, opacity),
            ControlGroup.R to rCell.place(groupSize, opacity),
            ControlGroup.DIRECTIONAL_PAD to dpadCell.place(groupSize, opacity),
            ControlGroup.START to startCell.place(groupSize, opacity),
            ControlGroup.SELECT to selectCell.place(groupSize, opacity),
            ControlGroup.ACTION_BUTTONS to actionCell.place(groupSize, opacity),
        )
        return ControlLayoutProfile(groups)
    }

    private fun LayoutRect.place(requested: LayoutSize, opacity: Float): ControlGroupPlacement {
        val fitted = LayoutSize(
            requested.width.coerceIn(0f, width.coerceAtLeast(0f)),
            requested.height.coerceIn(0f, height.coerceAtLeast(0f)),
        )
        return ControlGroupPlacement(LayoutEngine.center(fitted, this), opacity)
    }

    /** Reparte el rectángulo en [count] columnas iguales, de izquierda a derecha. */
    private fun LayoutRect.splitHorizontally(count: Int): List<LayoutRect> {
        val cellWidth = width / count
        return (0 until count).map { index ->
            LayoutRect(left + cellWidth * index, top, left + cellWidth * (index + 1), bottom)
        }
    }
}
