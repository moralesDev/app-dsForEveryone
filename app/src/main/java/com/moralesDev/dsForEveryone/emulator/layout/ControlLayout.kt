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
 *
 * El hueco al que son relativas es el **área utilizable** —la que pasa quien
 * llama, ya sin las barras del sistema—, no la ventana entera: `left = 0f` es
 * el borde izquierdo de lo que se puede pulsar, no el de la pantalla física.
 */
data class ControlGroupFraction(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val opacity: Float,
)

/**
 * Coloca los siete grupos de mandos sobre [LayoutEngine]. Hay dos perfiles
 * por defecto:
 *
 * - [overlayProfile], el de vertical: los mandos van **encima** de la
 *   pantalla inferior, a los dos lados, como en la consola —izquierda L,
 *   soplar, dirección y Select; derecha R, A/B/X/Y y Start—. La táctil sigue
 *   debajo: lo que no cae en un mando le llega al juego, y un botón de la
 *   pantalla de juego aparta los dos lados cuando hace falta la táctil
 *   entera.
 * - [defaultProfile], el de horizontal: una banda fuera de la táctil, sin
 *   solaparla.
 *
 * En los dos los grupos nunca se solapan entre sí. Solapar la pantalla
 * superior siempre está permitido: no es interactiva.
 *
 * Geometría pura: sin `Context`, sin `Composable`, sin unidades de Compose.
 */
object ControlLayout {

    /** Opacidad de cada grupo cuando no hay perfil guardado. */
    const val DEFAULT_OPACITY = 0.85f

    /**
     * El objetivo táctil mínimo recomendado por Material Design, en dp. Quien
     * llama —`GameScreen`, el editor— lo convierte a píxeles con la densidad
     * real antes de pasarlo a [defaultProfile]: esta clase no conoce
     * unidades de Compose.
     */
    const val MIN_TOUCH_TARGET_DP = 48f

    /**
     * La cruceta y el rombo de acción, como múltiplo de `minTouchTarget`: son
     * los que se usan siempre y los que más precisión piden, así que son el
     * elemento más grande de la pantalla.
     */
    private const val PRIMARY_SCALE = 3f

    /**
     * L y R, como múltiplo de `minTouchTarget`: gatillos rectangulares, no
     * cuadrados —más anchos que altos—, y con menos alto que la cruceta.
     * [SHOULDER_HEIGHT_SCALE] decide también el grosor de la fila de arriba
     * en [defaultProfile], así que sigue siendo mayor que el mínimo táctil.
     */
    private const val SHOULDER_WIDTH_SCALE = 2.4f
    private const val SHOULDER_HEIGHT_SCALE = 1.3f

    /** Start, Select y el botón de soplar: exactamente el mínimo táctil, ni un dp menos. */
    private const val SECONDARY_SCALE = 1f

    /**
     * Separación en [overlayProfile] entre grupos y con los bordes, como
     * múltiplo de `minTouchTarget` (12 dp con el mínimo de 48): lo justo para
     * que un pulgar en el borde de un botón no pise el de al lado.
     */
    private const val OVERLAY_GAP_SCALE = 0.25f

    /**
     * El perfil que hay que pintar: el guardado por el usuario en
     * [custom] si es válido y completo, o si no [defaultProfile]. La pantalla
     * de juego y el editor de disposición llaman a esta misma función con los
     * mismos argumentos — es lo que garantiza que uno y otro coloquen igual.
     *
     * @param area el hueco utilizable, en coordenadas absolutas de la
     *   ventana: quien llama le resta ya las barras del sistema. Ningún grupo
     *   sale de ahí, y por eso ninguno puede caer debajo de una barra, donde
     *   el toque se lo queda el sistema y el botón sería imposible de pulsar.
     * @param minTouchTarget objetivo táctil mínimo (48 dp convertidos a
     *   píxeles por quien llama; esta clase no conoce unidades de Compose).
     *   Ver [defaultProfile].
     */
    fun resolveProfile(
        custom: Map<ControlGroup, ControlGroupFraction>?,
        area: LayoutRect,
        touchScreen: LayoutRect,
        minTouchTarget: Float,
        opacity: Float = DEFAULT_OPACITY,
        overlay: Boolean = false,
    ): ControlLayoutProfile {
        val fromCustom = custom?.let { fromFractions(it, area) }
        return fromCustom ?: if (overlay) {
            overlayProfile(area, minTouchTarget, opacity)
        } else {
            defaultProfile(area, touchScreen, minTouchTarget, opacity)
        }
    }

    /**
     * Las fracciones de un grupo dentro de [area]. Es la única traducción de
     * píxeles a fracción que hay: el editor la usa para lo que acaba de
     * arrastrar y [toFractions] para el perfil entero, de modo que el origen
     * del área —que no es (0,0) en cuanto hay una barra a la izquierda o
     * arriba— no se pueda olvidar en un sitio y no en otro.
     */
    fun fractionOf(bounds: LayoutRect, area: LayoutRect, opacity: Float): ControlGroupFraction =
        ControlGroupFraction(
            left = (bounds.left - area.left) / area.width,
            top = (bounds.top - area.top) / area.height,
            width = bounds.width / area.width,
            height = bounds.height / area.height,
            opacity = opacity,
        )

    /** [area] en las mismas unidades que las de [ControlGroupPlacement.bounds] (habitualmente píxeles). */
    fun toFractions(
        profile: ControlLayoutProfile,
        area: LayoutRect,
    ): Map<ControlGroup, ControlGroupFraction> {
        if (area.width <= 0f || area.height <= 0f) return emptyMap()
        return profile.groups.mapValues { (_, placement) ->
            fractionOf(placement.bounds, area, placement.opacity)
        }
    }

    /**
     * Inversa de [toFractions]. Devuelve `null` si faltan grupos o si [area]
     * no tiene área: un perfil a medias no es un perfil, y quien llama debe
     * caer a [defaultProfile] en vez de arrastrar una lectura mala.
     */
    fun fromFractions(
        fractions: Map<ControlGroup, ControlGroupFraction>,
        area: LayoutRect,
    ): ControlLayoutProfile? {
        if (fractions.keys != ControlGroup.entries.toSet()) return null
        if (area.width <= 0f || area.height <= 0f) return null
        val groups = fractions.mapValues { (_, fraction) ->
            ControlGroupPlacement(
                bounds = LayoutRect(
                    left = area.left + fraction.left * area.width,
                    top = area.top + fraction.top * area.height,
                    right = area.left + (fraction.left + fraction.width) * area.width,
                    bottom = area.top + (fraction.top + fraction.height) * area.height,
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
     * Si algún grupo del perfil solapa con otro o con [touchScreen]. Con
     * `touchScreen = null` sólo cuentan los grupos entre sí: es la regla del
     * perfil superpuesto ([overlayProfile]), donde tapar la táctil es justo
     * lo que se busca.
     */
    fun hasOverlap(profile: ControlLayoutProfile, touchScreen: LayoutRect?): Boolean {
        val rects = profile.groups.values.map { it.bounds } + listOfNotNull(touchScreen)
        for (i in rects.indices) {
            for (j in i + 1 until rects.size) {
                if (LayoutEngine.overlaps(rects[i], rects[j])) return true
            }
        }
        return false
    }

    /**
     * Perfil de partida para un hueco disponible: los siete grupos en una
     * única banda de dos filas, repartidos por frecuencia de uso, no por
     * simetría.
     *
     * La banda es siempre una sola —nunca repartida en dos sitios separados
     * de la pantalla— para que L y R queden al alcance del mismo pulgar que
     * ya está sobre la cruceta o el rombo, no en un sitio aparte: eso es
     * justo lo que dejaba R inalcanzable en Mario Kart en `app-emulador`
     * (ver docs/entrada.md). Dentro de esa banda, como en un mando físico,
     * los gatillos van en la fila de arriba y los botones de cara en la de
     * abajo. La fila de abajo es siempre la misma:
     *
     * ```
     * [ CRUCETA ] [St][Sel] [ ACCIÓN ]
     * ```
     *
     * pero la de arriba cambia según [BandChoice.isSideBand]. Con la banda
     * debajo de la táctil (vertical, banda ancha y baja) hay sitio de sobra
     * a lo ancho y soplar queda centrado, sobre Start/Select:
     *
     * ```
     * [   L   ] [ SOPLAR ] [   R    ]
     * ```
     *
     * Con la banda al lado de la táctil (horizontal: docs/entrada.md dice
     * que ahí «los mandos tienen que superponerse a los lados» porque lado a
     * lado no deja banda inferior utilizable) sólo hay una pantalla libre —la
     * superior, no interactiva— para los siete grupos: no hay un lado
     * simétrico al otro lado de la táctil donde poner nada. Ahí soplar se
     * apila encima de L, no al lado, y R queda solo en su columna:
     *
     * ```
     * [ SOPLAR ]           [        ]
     * [   L    ]           [   R    ]
     * ```
     *
     * La fila de arriba necesita [SHOULDER_HEIGHT_SCALE] de grosor —o
     * [SHOULDER_HEIGHT_SCALE] más [SECONDARY_SCALE] si soplar va apilado—; la
     * de abajo necesita [PRIMARY_SCALE], porque ahí van los dos elementos más
     * grandes. L y R además son más anchos que altos ([SHOULDER_WIDTH_SCALE]
     * frente a [SHOULDER_HEIGHT_SCALE]): gatillos rectangulares, no
     * cuadrados.
     *
     * Cada grupo pide su tamaño según [PRIMARY_SCALE],
     * [SHOULDER_WIDTH_SCALE]/[SHOULDER_HEIGHT_SCALE] o [SECONDARY_SCALE],
     * pero se recorta al hueco de su celda si no cabe entero: así ningún
     * tamaño, por grande que sea, puede hacer que dos grupos se solapen.
     *
     * Es un punto de partida, no el resultado final: el editor (fase 2.7)
     * deja mover y redimensionar cada grupo a mano, y ese resultado se valida
     * con [hasOverlap], no con esta función.
     */
    fun defaultProfile(
        area: LayoutRect,
        touchScreen: LayoutRect,
        minTouchTarget: Float,
        opacity: Float = DEFAULT_OPACITY,
    ): ControlLayoutProfile {
        val bandChoice = chooseBand(area, touchScreen, minTouchTarget)
        val band = bandChoice.rect

        val primary = LayoutSize(minTouchTarget * PRIMARY_SCALE, minTouchTarget * PRIMARY_SCALE)
        val shoulder = LayoutSize(minTouchTarget * SHOULDER_WIDTH_SCALE, minTouchTarget * SHOULDER_HEIGHT_SCALE)
        val secondary = LayoutSize(minTouchTarget * SECONDARY_SCALE, minTouchTarget * SECONDARY_SCALE)

        val (topRow, faceRow) = band.splitRowsWeighted(
            listOf(topRowHeightScale(bandChoice.isSideBand), PRIMARY_SCALE),
        )
        val (dpadCell, centerCell, actionCell) = faceRow.splitWeighted(
            listOf(PRIMARY_SCALE, SECONDARY_SCALE * 2f, PRIMARY_SCALE),
        )
        val (startCell, selectCell) = centerCell.splitHorizontally(2)

        val (lCell, blowCell, rCell) = if (bandChoice.isSideBand) {
            val (leftTop, rightTop) = topRow.splitWeighted(listOf(SHOULDER_WIDTH_SCALE, SHOULDER_WIDTH_SCALE))
            val (blowSubCell, lSubCell) = leftTop.splitRowsWeighted(listOf(SECONDARY_SCALE, SHOULDER_HEIGHT_SCALE))
            Triple(lSubCell, blowSubCell, rightTop)
        } else {
            val (leftCell, blowCenterCell, rightCell) = topRow.splitWeighted(
                listOf(SHOULDER_WIDTH_SCALE, SECONDARY_SCALE, SHOULDER_WIDTH_SCALE),
            )
            Triple(leftCell, blowCenterCell, rightCell)
        }

        val groups = mapOf(
            ControlGroup.L to lCell.place(shoulder, opacity),
            ControlGroup.BLOW to blowCell.place(secondary, opacity),
            ControlGroup.R to rCell.place(shoulder, opacity),
            ControlGroup.DIRECTIONAL_PAD to dpadCell.place(primary, opacity),
            ControlGroup.START to startCell.place(secondary, opacity),
            ControlGroup.SELECT to selectCell.place(secondary, opacity),
            ControlGroup.ACTION_BUTTONS to actionCell.place(primary, opacity),
        )
        return ControlLayoutProfile(groups)
    }

    /**
     * Perfil por defecto en vertical: dos racimos, uno por mano, pegados a
     * los bordes y anclados abajo, que suben **por encima** de la pantalla
     * inferior todo lo que haga falta.
     *
     * ```
     * [  L  ]                           [  R  ]
     * [Soplar]
     * [ DIRECCIÓN ]               [ A·B·X·Y ]
     *           [SELECT] [START]
     * ```
     *
     * Por qué así:
     *
     * - Es la forma de la consola: L arriba a la izquierda y R arriba a la
     *   derecha, encima de la mano que ya está en la dirección o en el rombo.
     *   Soplar va justo debajo de L porque también es de la mano izquierda y
     *   se usa poco.
     * - Select y Start quedan abajo, a cada lado del centro, cada uno del lado
     *   de su mano. Si el teléfono es tan estrecho que la dirección o el
     *   rombo llegarían a pisarlos, los dos racimos suben una fila en vez de
     *   encoger.
     * - Anclado abajo y no repartido por toda la pantalla inferior: el pulgar
     *   llega desde el borde de abajo, y L a media pantalla de distancia no
     *   se alcanza sin soltar el teléfono.
     *
     * Si ni así cabe —un hueco minúsculo, o un objetivo táctil enorme—, todo
     * se escala por igual hasta caber: los grupos nunca se solapan ni se
     * salen de [area], por pequeño que sea.
     */
    fun overlayProfile(
        area: LayoutRect,
        minTouchTarget: Float,
        opacity: Float = DEFAULT_OPACITY,
    ): ControlLayoutProfile {
        val requestedWidth = minTouchTarget * (2f * PRIMARY_SCALE + 3f * OVERLAY_GAP_SCALE)
        // Peor caso a lo alto: Select/Start en su propia fila, debajo.
        val requestedHeight = minTouchTarget * (
            5f * OVERLAY_GAP_SCALE + 2f * SECONDARY_SCALE + PRIMARY_SCALE + SHOULDER_HEIGHT_SCALE
            )
        val fit = minOf(
            1f,
            if (requestedWidth > 0f) area.width.coerceAtLeast(0f) / requestedWidth else 0f,
            if (requestedHeight > 0f) area.height.coerceAtLeast(0f) / requestedHeight else 0f,
        )
        val unit = minTouchTarget * fit
        val gap = unit * OVERLAY_GAP_SCALE
        val primary = unit * PRIMARY_SCALE
        val secondary = unit * SECONDARY_SCALE
        val shoulderWidth = unit * SHOULDER_WIDTH_SCALE
        val shoulderHeight = unit * SHOULDER_HEIGHT_SCALE

        val bottom = area.bottom - gap
        val select = LayoutRect(area.centerX - gap / 2f - secondary, bottom - secondary, area.centerX - gap / 2f, bottom)
        val start = LayoutRect(area.centerX + gap / 2f, bottom - secondary, area.centerX + gap / 2f + secondary, bottom)

        val leftEdge = area.left + gap
        val rightEdge = area.right - gap
        val primaryReachesCenter = leftEdge + primary + gap > select.left
        val primaryBottom = if (primaryReachesCenter) select.top - gap else bottom

        val dpad = LayoutRect(leftEdge, primaryBottom - primary, leftEdge + primary, primaryBottom)
        val action = LayoutRect(rightEdge - primary, primaryBottom - primary, rightEdge, primaryBottom)
        val blow = LayoutRect(leftEdge, dpad.top - gap - secondary, leftEdge + secondary, dpad.top - gap)
        val l = LayoutRect(leftEdge, blow.top - gap - shoulderHeight, leftEdge + shoulderWidth, blow.top - gap)
        val r = LayoutRect(rightEdge - shoulderWidth, l.top, rightEdge, l.bottom)

        val groups = mapOf(
            ControlGroup.L to l,
            ControlGroup.BLOW to blow,
            ControlGroup.DIRECTIONAL_PAD to dpad,
            ControlGroup.SELECT to select,
            ControlGroup.R to r,
            ControlGroup.ACTION_BUTTONS to action,
            ControlGroup.START to start,
        ).mapValues { (_, bounds) -> ControlGroupPlacement(bounds, opacity) }
        return ControlLayoutProfile(groups)
    }

    /**
     * La banda única de [defaultProfile] y cómo repartir la fila de arriba
     * dentro de ella. [isSideBand] es `true` cuando la banda vive al lado de
     * la táctil, no debajo: es la señal que usa [defaultProfile] para saber
     * si soplar va centrado sobre Start/Select (banda ancha y baja, típica en
     * vertical) o apilado sobre L (banda que vive en el hueco lateral,
     * típica en horizontal, donde además no hay sitio para un R al otro lado
     * de la táctil —ver el propio [defaultProfile]—).
     */
    private class BandChoice(val rect: LayoutRect, val isSideBand: Boolean)

    /**
     * Dónde vive la banda única de [defaultProfile].
     *
     * Se prefiere el margen que no tapa ninguna pantalla —el que queda entre
     * el borde de la táctil y el borde del hueco disponible en el eje en el
     * que sobra más sitio— y, entre «abajo» y «arriba», siempre «abajo»
     * cuando hay de sobra: «arriba» casi siempre coincide con la pantalla
     * superior completa (pegada al borde del hueco por [ScreenLayout]), y
     * ahí es donde quedaban L y R fuera del alcance del pulgar. Sólo si ni
     * abajo ni a los lados queda banda utilizable se cae a «arriba», que
     * sigue sin solapar la táctil aunque sí tape la superior —permitido,
     * puesto que no es interactiva—.
     */
    private fun chooseBand(area: LayoutRect, touchScreen: LayoutRect, minTouchTarget: Float): BandChoice {
        val topAvail = (touchScreen.top - area.top).coerceAtLeast(0f)
        val bottomAvail = (area.bottom - touchScreen.bottom).coerceAtLeast(0f)
        val leftAvail = (touchScreen.left - area.left).coerceAtLeast(0f)
        val rightAvail = (area.right - touchScreen.right).coerceAtLeast(0f)
        val sideAvail = maxOf(leftAvail, rightAvail)

        return when {
            sideAvail > bottomAvail && sideAvail > 0f -> {
                val thickness = (minTouchTarget * topRowHeightScale(isSideBand = true) + minTouchTarget * PRIMARY_SCALE)
                    .coerceAtMost(area.height)
                val rect = if (leftAvail >= rightAvail) {
                    LayoutRect(area.left, area.bottom - thickness, area.left + leftAvail, area.bottom)
                } else {
                    LayoutRect(area.right - rightAvail, area.bottom - thickness, area.right, area.bottom)
                }
                BandChoice(rect, isSideBand = true)
            }

            bottomAvail > 0f -> {
                val thickness = (minTouchTarget * topRowHeightScale(isSideBand = false) + minTouchTarget * PRIMARY_SCALE)
                    .coerceAtMost(bottomAvail)
                BandChoice(LayoutRect(area.left, area.bottom - thickness, area.right, area.bottom), isSideBand = false)
            }

            topAvail > 0f -> {
                val thickness = (minTouchTarget * topRowHeightScale(isSideBand = false) + minTouchTarget * PRIMARY_SCALE)
                    .coerceAtMost(topAvail)
                BandChoice(LayoutRect(area.left, area.top, area.right, area.top + thickness), isSideBand = false)
            }

            else -> BandChoice(LayoutRect(area.left, area.bottom, area.right, area.bottom), isSideBand = false)
        }
    }

    /**
     * Grosor de la fila de arriba, como múltiplo de `minTouchTarget`. En la
     * banda lateral soplar se apila encima de L —ver [defaultProfile]—, así
     * que esa fila necesita el alto de los dos, no sólo el de L.
     */
    private fun topRowHeightScale(isSideBand: Boolean): Float =
        if (isSideBand) SECONDARY_SCALE + SHOULDER_HEIGHT_SCALE else SHOULDER_HEIGHT_SCALE

    private fun LayoutRect.place(requested: LayoutSize, opacity: Float): ControlGroupPlacement {
        val fitted = LayoutSize(
            requested.width.coerceIn(0f, width.coerceAtLeast(0f)),
            requested.height.coerceIn(0f, height.coerceAtLeast(0f)),
        )
        return ControlGroupPlacement(LayoutEngine.center(fitted, this), opacity)
    }

    /** Reparte el rectángulo en columnas iguales, de izquierda a derecha. */
    private fun LayoutRect.splitHorizontally(count: Int): List<LayoutRect> {
        val cellWidth = width / count
        return (0 until count).map { index ->
            LayoutRect(left + cellWidth * index, top, left + cellWidth * (index + 1), bottom)
        }
    }

    /**
     * Reparte el rectángulo en columnas cuyo ancho es proporcional a cada
     * valor de [weights], de izquierda a derecha. Es lo que traduce «el
     * tamaño se reparte por frecuencia de uso» a hueco reservado: la cruceta
     * y el rombo piden más columna que L, R o el centro.
     */
    private fun LayoutRect.splitWeighted(weights: List<Float>): List<LayoutRect> {
        val total = weights.sum().coerceAtLeast(0.0001f)
        var x = left
        return weights.map { weight ->
            val cellWidth = width * (weight / total)
            val cell = LayoutRect(x, top, x + cellWidth, bottom)
            x += cellWidth
            cell
        }
    }

    /** Igual que [splitWeighted] pero en filas, de arriba a abajo. */
    private fun LayoutRect.splitRowsWeighted(weights: List<Float>): List<LayoutRect> {
        val total = weights.sum().coerceAtLeast(0.0001f)
        var y = top
        return weights.map { weight ->
            val cellHeight = height * (weight / total)
            val cell = LayoutRect(left, y, right, y + cellHeight)
            y += cellHeight
            cell
        }
    }
}
