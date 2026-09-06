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
 * Posición, tamaño y opacidad de un grupo como fracción del hueco disponible
 * (0..1 en cada eje), independiente de en qué tamaño de pantalla se calculó.
 * Es la forma en la que el editor de disposición (fase 2.7) persiste un
 * perfil en `EmulatorSettings`: guardar píxeles absolutos no sobreviviría a
 * abrir la app en otro dispositivo, o incluso en el mismo con otra barra de
 * sistema.
 */
data class ControlGroupFraction(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val opacity: Float,
)

/**
 * Coloca los siete grupos de mandos sobre [LayoutEngine], sin solaparse entre
 * sí ni con la pantalla táctil. Solapar la pantalla superior sí está
 * permitido: no es interactiva, así que no hay ningún toque que perder.
 *
 * Geometría pura: sin `Context`, sin `Composable`, sin unidades de Compose.
 */
object ControlLayout {

    /** Lado de cada grupo, como fracción del lado menor del hueco disponible, cuando no hay perfil guardado. */
    const val DEFAULT_GROUP_SIZE_FRACTION = 0.20f

    /** Opacidad de cada grupo cuando no hay perfil guardado. */
    const val DEFAULT_OPACITY = 0.85f

    /**
     * El perfil que hay que pintar: el guardado por el usuario en
     * [custom] si es válido y completo, o si no [defaultProfile]. La pantalla
     * de juego y el editor de disposición llaman a esta misma función con los
     * mismos argumentos — es lo que garantiza que uno y otro coloquen igual.
     */
    fun resolveProfile(
        custom: Map<ControlGroup, ControlGroupFraction>?,
        available: LayoutSize,
        touchScreen: LayoutRect,
        groupSize: LayoutSize,
        opacity: Float = DEFAULT_OPACITY,
    ): ControlLayoutProfile {
        val fromCustom = custom?.let { fromFractions(it, available) }
        return fromCustom ?: defaultProfile(available, touchScreen, groupSize, opacity)
    }

    /** [available] en las mismas unidades que las de [ControlGroupPlacement.bounds] (habitualmente píxeles). */
    fun toFractions(
        profile: ControlLayoutProfile,
        available: LayoutSize,
    ): Map<ControlGroup, ControlGroupFraction> {
        if (available.width <= 0f || available.height <= 0f) return emptyMap()
        return profile.groups.mapValues { (_, placement) ->
            val bounds = placement.bounds
            ControlGroupFraction(
                left = bounds.left / available.width,
                top = bounds.top / available.height,
                width = bounds.width / available.width,
                height = bounds.height / available.height,
                opacity = placement.opacity,
            )
        }
    }

    /**
     * Inversa de [toFractions]. Devuelve `null` si faltan grupos o si
     * [available] no tiene área: un perfil a medias no es un perfil, y quien
     * llama debe caer a [defaultProfile] en vez de arrastrar una lectura mala.
     */
    fun fromFractions(
        fractions: Map<ControlGroup, ControlGroupFraction>,
        available: LayoutSize,
    ): ControlLayoutProfile? {
        if (fractions.keys != ControlGroup.entries.toSet()) return null
        if (available.width <= 0f || available.height <= 0f) return null
        val groups = fractions.mapValues { (_, fraction) ->
            ControlGroupPlacement(
                bounds = LayoutRect(
                    left = fraction.left * available.width,
                    top = fraction.top * available.height,
                    right = (fraction.left + fraction.width) * available.width,
                    bottom = (fraction.top + fraction.height) * available.height,
                ),
                opacity = fraction.opacity,
            )
        }
        return runCatching { ControlLayoutProfile(groups) }.getOrNull()
    }

    /** Texto compacto de una sola línea, para guardar un perfil en DataStore. */
    fun encode(fractions: Map<ControlGroup, ControlGroupFraction>): String =
        fractions.entries.joinToString(separator = ";") { (group, fraction) ->
            "${group.name}:${fraction.left},${fraction.top},${fraction.width},${fraction.height},${fraction.opacity}"
        }

    /**
     * Inversa de [encode]. Cualquier fallo de formato, un grupo repetido o
     * ausente, o un número que no sea finito hacen que devuelva `null`: mejor
     * caer al perfil por defecto que arrastrar un valor a medio leer.
     */
    fun decode(text: String): Map<ControlGroup, ControlGroupFraction>? {
        if (text.isBlank()) return null
        val result = mutableMapOf<ControlGroup, ControlGroupFraction>()
        for (entry in text.split(";")) {
            val parts = entry.split(":")
            if (parts.size != 2) return null
            val group = ControlGroup.entries.firstOrNull { it.name == parts[0] } ?: return null
            if (result.containsKey(group)) return null
            val values = parts[1].split(",").map { it.toFloatOrNull() ?: return null }
            if (values.size != 5 || values.any { !it.isFinite() }) return null
            result[group] = ControlGroupFraction(values[0], values[1], values[2], values[3], values[4])
        }
        return if (result.keys == ControlGroup.entries.toSet()) result else null
    }

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
