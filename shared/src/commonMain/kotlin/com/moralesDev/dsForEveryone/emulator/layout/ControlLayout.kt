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
     * Tamaños de [overlayProfile], como múltiplo de `minTouchTarget`. Más
     * pequeños que los de la banda de [defaultProfile] porque aquí van
     * encima del juego:
     *
     * - La dirección conserva el tamaño grande: es la que más precisión pide.
     * - El rombo, un 20 % menor que la dirección.
     * - L y R, la mitad de alto y un 25 % menos de largo que en la banda.
     * - Soplar, del mismo tamaño que el botón de apartar los mandos, con el
     *   que hace pareja a la misma altura al otro lado.
     * - Start y Select, pastillas con el nombre debajo, la mitad que antes.
     */
    private const val OVERLAY_DIRECTIONAL_SCALE = PRIMARY_SCALE
    private const val OVERLAY_ACTION_SCALE = PRIMARY_SCALE * 0.8f
    private const val OVERLAY_SHOULDER_WIDTH_SCALE = SHOULDER_WIDTH_SCALE * 0.75f
    private const val OVERLAY_SHOULDER_HEIGHT_SCALE = SHOULDER_HEIGHT_SCALE * 0.5f
    private const val OVERLAY_SYSTEM_WIDTH_SCALE = 0.9f
    private const val OVERLAY_SYSTEM_HEIGHT_SCALE = 0.6f

    /** El botón de apartar los mandos, y soplar, que es de su mismo tamaño. */
    private const val HIDE_TOGGLE_SCALE = 0.6f
    private const val OVERLAY_BLOW_SCALE = HIDE_TOGGLE_SCALE

    /** Alto de la fila de soplar y del botón de apartar: el mayor de los dos. */
    private const val OVERLAY_MIDDLE_ROW_SCALE = HIDE_TOGGLE_SCALE

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
        ceiling: Float = area.top,
        inwardShift: Float = 0f,
        systemTop: Float? = null,
        anchorBottom: Boolean = false,
        blowBelow: Float? = null,
        shouldersBetween: Pair<Float, Float>? = null,
    ): ControlLayoutProfile {
        val fromCustom = custom?.let { fromFractions(it, area) }
        return fromCustom ?: if (overlay) {
            overlayProfile(
                area, minTouchTarget, opacity, ceiling, inwardShift, systemTop, anchorBottom, blowBelow, shouldersBetween,
            )
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
     * los bordes y **colgados de [ceiling]**: L y R empiezan justo en el
     * techo y el resto baja desde ahí, por encima de la pantalla inferior. Lo
     * que sobra queda abajo, libre.
     *
     * ```
     * [ L ]                          [ R ]
     *
     * ( DIRECCIÓN )             ( A·B·X·Y )
     * (soplar) [select] [start]           (>)
     * ```
     *
     * Por qué así:
     *
     * - Es la forma de la consola: L arriba a la izquierda y R arriba a la
     *   derecha, encima de la mano que ya está en la dirección o en el rombo.
     *   Soplar va abajo a la izquierda, a la altura de Select, cuando Select
     *   y Start bajan bajo la pantalla ([systemTop]); si no, justo debajo de
     *   L. El botón de apartar los mandos va abajo a la derecha, a la altura
     *   de Start ([hideToggleBounds]): los dos se miran.
     * - Select y Start quedan abajo, a cada lado del centro. Si el teléfono
     *   es tan estrecho que la dirección o el rombo llegarían a pisarlos, los
     *   dos racimos suben una fila en vez de encoger.
     * - Colgado del techo y no pegado al borde de abajo: así los mandos quedan
     *   hacia el centro del teléfono, donde lo sujetan las manos, y no en el
     *   filo inferior.
     * - Todo menos la dirección es pequeño a propósito —L y R finos, soplar,
     *   Start y Select diminutos—: van encima del juego, y cada milímetro de
     *   botón es un milímetro de pantalla inferior que no se ve bien.
     *
     * Si no cabe entre [ceiling] y el borde de abajo —un hueco minúsculo, o
     * un objetivo táctil enorme—, todo se escala por igual hasta caber: los
     * grupos nunca se solapan, ni salen de [area], ni suben de [ceiling].
     *
     * @param ceiling el borde de arriba que ningún grupo puede pasar, ver
     *   [overlayCeiling]. Por defecto, el de [area].
     * @param inwardShift cuánto se acercan al centro la dirección y el rombo,
     *   cada uno desde su borde, en las mismas unidades que [area]. Quien
     *   llama lo convierte de milímetros reales. Nunca los deja pasar su
     *   mitad: como mucho quedan a medio hueco del centro.
     * @param systemTop si no es `null`, a qué altura empiezan Select y Start
     *   en lugar de ir en el bloque: debajo de la pantalla inferior, ver
     *   [lowerScreenBottom]. Nunca pisan la dirección ni el rombo —si les
     *   quedan debajo, bajan hasta librarlos— ni se salen por abajo: si
     *   debajo de la pantalla no caben, van lo más abajo posible. Sólo si ni
     *   eso cabe se quedan donde iban en el bloque.
     * @param anchorBottom el bloque se queda pegado al borde de abajo en vez
     *   de subir hasta [ceiling]. Es el perfil en horizontal (un iPad
     *   tumbado): las pantallas van lado a lado y ocupan el ancho entero, así
     *   que cada racimo va a su esquina de abajo, donde están los pulgares, y
     *   Select y Start abajo en el centro.
     * @param blowBelow si no es `null`, soplar baja justo por debajo de esa
     *   altura —el borde de abajo de las pantallas, en horizontal—, en el
     *   borde izquierdo de los mandos, para no tapar la pantalla izquierda.
     *   Si ahí pisa la dirección, se corre a su derecha; si tampoco cabe, se
     *   queda en el bloque.
     * @param shouldersBetween si no es `null`, L y R van centrados a lo alto
     *   en esa franja —el hueco entre las dos pantallas apiladas, en
     *   vertical, el de [stackedGap]—, cada uno en su borde y en fila con la
     *   barra de velocidad. Ahí no tapan nada. Si no caben en la franja o
     *   pisarían algo, se quedan en el bloque.
     */
    fun overlayProfile(
        area: LayoutRect,
        minTouchTarget: Float,
        opacity: Float = DEFAULT_OPACITY,
        ceiling: Float = area.top,
        inwardShift: Float = 0f,
        systemTop: Float? = null,
        anchorBottom: Boolean = false,
        blowBelow: Float? = null,
        shouldersBetween: Pair<Float, Float>? = null,
    ): ControlLayoutProfile {
        val top = ceiling.coerceIn(area.top, area.bottom)
        val usable = LayoutRect(area.left, top, area.right, area.bottom)

        // Cada racimo cabe en su mitad: cuenta el más ancho de los dos, dos veces.
        val requestedWidth = minTouchTarget * (
            2f * maxOf(OVERLAY_DIRECTIONAL_SCALE, OVERLAY_ACTION_SCALE) + 3f * OVERLAY_GAP_SCALE
            )
        // Peor caso a lo alto: Select/Start en su propia fila, debajo.
        val requestedHeight = minTouchTarget * (
            5f * OVERLAY_GAP_SCALE + OVERLAY_SYSTEM_HEIGHT_SCALE + OVERLAY_DIRECTIONAL_SCALE +
                OVERLAY_MIDDLE_ROW_SCALE + OVERLAY_SHOULDER_HEIGHT_SCALE
            )
        val fit = minOf(
            1f,
            if (requestedWidth > 0f) usable.width.coerceAtLeast(0f) / requestedWidth else 0f,
            if (requestedHeight > 0f) usable.height.coerceAtLeast(0f) / requestedHeight else 0f,
        )
        val unit = minTouchTarget * fit
        val gap = unit * OVERLAY_GAP_SCALE
        val directional = unit * OVERLAY_DIRECTIONAL_SCALE
        val action = unit * OVERLAY_ACTION_SCALE
        val systemWidth = unit * OVERLAY_SYSTEM_WIDTH_SCALE
        val systemHeight = unit * OVERLAY_SYSTEM_HEIGHT_SCALE
        val blowSide = unit * OVERLAY_BLOW_SCALE
        val middleRow = unit * OVERLAY_MIDDLE_ROW_SCALE
        val shoulderWidth = unit * OVERLAY_SHOULDER_WIDTH_SCALE
        val shoulderHeight = unit * OVERLAY_SHOULDER_HEIGHT_SCALE

        val bottom = usable.bottom - gap
        val centerX = usable.centerX
        val select = LayoutRect(centerX - gap / 2f - systemWidth, bottom - systemHeight, centerX - gap / 2f, bottom)
        val start = LayoutRect(centerX + gap / 2f, bottom - systemHeight, centerX + gap / 2f + systemWidth, bottom)

        val leftEdge = usable.left + gap
        val rightEdge = usable.right - gap
        val shift = inwardShift.coerceAtLeast(0f)
        val dpadLeft = (leftEdge + shift).coerceAtMost((centerX - gap / 2f - directional).coerceAtLeast(leftEdge))
        val actionRight = (rightEdge - shift).coerceAtLeast((centerX + gap / 2f + action).coerceAtMost(rightEdge))
        val reachesCenter = dpadLeft + directional + gap > select.left || actionRight - action - gap < start.right
        val primaryBottom = if (reachesCenter) select.top - gap else bottom

        val dpad = LayoutRect(dpadLeft, primaryBottom - directional, dpadLeft + directional, primaryBottom)
        val actionRect = LayoutRect(actionRight - action, primaryBottom - action, actionRight, primaryBottom)
        val rowBottom = minOf(dpad.top, actionRect.top) - gap
        val rowCenterY = rowBottom - middleRow / 2f
        val blow = LayoutRect(leftEdge, rowCenterY - blowSide / 2f, leftEdge + blowSide, rowCenterY + blowSide / 2f)
        val shoulderBottom = rowBottom - middleRow - gap
        val l = LayoutRect(leftEdge, shoulderBottom - shoulderHeight, leftEdge + shoulderWidth, shoulderBottom)
        val r = LayoutRect(rightEdge - shoulderWidth, l.top, rightEdge, l.bottom)

        // Hasta aquí todo se ha apilado desde abajo; ahora el bloque entero
        // sube hasta que L y R tocan el techo. `fit` garantiza que cabe, así
        // que el desplazamiento sólo puede ser hacia arriba (o nulo).
        val lift = if (anchorBottom) 0f else (l.top - usable.top).coerceAtLeast(0f)

        val groups = mapOf(
            ControlGroup.L to l,
            ControlGroup.BLOW to blow,
            ControlGroup.DIRECTIONAL_PAD to dpad,
            ControlGroup.SELECT to select,
            ControlGroup.R to r,
            ControlGroup.ACTION_BUTTONS to actionRect,
            ControlGroup.START to start,
        ).mapValues { (_, bounds) ->
            LayoutRect(bounds.left, bounds.top - lift, bounds.right, bounds.bottom - lift)
        }
        val withSystem = if (systemTop != null) {
            withBlowBesideSystemButtons(withSystemButtonsAt(groups, systemTop, usable, gap), usable, gap)
        } else {
            groups
        }
        val blowPlaced = blowBelow?.let { withBlowBelow(withSystem, it, usable, gap) } ?: withSystem
        val placed = shouldersBetween?.let { withShouldersBetween(blowPlaced, it, area) } ?: blowPlaced
        return ControlLayoutProfile(placed.mapValues { (_, bounds) -> ControlGroupPlacement(bounds, opacity) })
    }

    /**
     * Lleva L y R a la franja [band], centrados a lo alto, ver
     * [overlayProfile]. Devuelve [groups] sin tocar si no caben en ella o
     * pisarían algo.
     */
    private fun withShouldersBetween(
        groups: Map<ControlGroup, LayoutRect>,
        band: Pair<Float, Float>,
        area: LayoutRect,
    ): Map<ControlGroup, LayoutRect> {
        val (bandTop, bandBottom) = band
        val l = groups.getValue(ControlGroup.L)
        val r = groups.getValue(ControlGroup.R)
        if (bandBottom - bandTop < l.height) return groups
        val top = (bandTop + bandBottom) / 2f - l.height / 2f
        if (top < area.top || top + l.height > area.bottom) return groups
        val moved = groups + mapOf(
            ControlGroup.L to LayoutRect(l.left, top, l.right, top + l.height),
            ControlGroup.R to LayoutRect(r.left, top, r.right, top + r.height),
        )
        val overlaps = moved.entries.any { (group, rect) ->
            moved.any { (other, otherRect) -> other != group && LayoutEngine.overlaps(rect, otherRect) }
        }
        return if (overlaps) groups else moved
    }

    /**
     * Baja soplar justo por debajo de [screensBottom], ver [overlayProfile]:
     * primero en el borde izquierdo de los mandos (el de L); si ahí pisa
     * algo, a la derecha de lo que pisa. Devuelve [groups] sin tocar si no
     * cabe sin salirse, sin pasar del centro o sin pisar nada.
     */
    private fun withBlowBelow(
        groups: Map<ControlGroup, LayoutRect>,
        screensBottom: Float,
        usable: LayoutRect,
        gap: Float,
    ): Map<ControlGroup, LayoutRect> {
        val blow = groups.getValue(ControlGroup.BLOW)
        val others = (groups - ControlGroup.BLOW).values
        val top = screensBottom + gap
        fun at(left: Float) = LayoutRect(left, top, left + blow.width, top + blow.height)
        fun fits(rect: LayoutRect) =
            rect.bottom <= usable.bottom && rect.right <= usable.centerX && others.none { LayoutEngine.overlaps(rect, it) }

        val first = at(groups.getValue(ControlGroup.L).left)
        val candidate = if (fits(first)) {
            first
        } else {
            val blocking = others.filter { LayoutEngine.overlaps(first, it) }
            blocking.maxOfOrNull { it.right + gap }?.let(::at)?.takeIf(::fits)
        }
        return if (candidate != null) groups + (ControlGroup.BLOW to candidate) else groups
    }

    /**
     * El hueco bajo las dos pantallas cuando van lado a lado, como (borde de
     * abajo de las pantallas, borde de arriba de Select y Start); `null` si
     * van apiladas o si no queda hueco. Ahí va la barra de velocidad en
     * horizontal, centrada, como en vertical va en [stackedGap].
     */
    fun belowScreensGap(placement: ScreenPlacement, profile: ControlLayoutProfile): Pair<Float, Float>? {
        if (stackedGap(placement) != null) return null
        val screensBottom = maxOf(placement.topScreen.bottom, placement.touchScreen.bottom)
        val systemTop = minOf(
            profile.groups.getValue(ControlGroup.SELECT).bounds.top,
            profile.groups.getValue(ControlGroup.START).bounds.top,
        )
        return if (systemTop > screensBottom) screensBottom to systemTop else null
    }

    /**
     * Dónde va el centro de la barra de velocidad en horizontal: en el hueco
     * de [belowScreensGap], a la altura de soplar si soplar está en ese hueco
     * —en fila con él, ver `blowBelow` en [overlayProfile]—, y si no, en el
     * medio del hueco. `null` si no hay hueco o no cabe una barra de
     * [barHeight].
     */
    fun speedBarCenterY(placement: ScreenPlacement, profile: ControlLayoutProfile, barHeight: Float): Float? {
        val (gapTop, gapBottom) = belowScreensGap(placement, profile) ?: return null
        if (gapBottom - gapTop < barHeight) return null
        val blow = profile.groups.getValue(ControlGroup.BLOW).bounds
        val besideBlow = blow.centerY
        return if (besideBlow - barHeight / 2f >= gapTop && besideBlow + barHeight / 2f <= gapBottom) {
            besideBlow
        } else {
            (gapTop + gapBottom) / 2f
        }
    }

    /**
     * Baja Select y Start a [systemTop], ver [overlayProfile]. Devuelve
     * [groups] sin tocar si en ese sitio no caben sin pisar nada.
     */
    private fun withSystemButtonsAt(
        groups: Map<ControlGroup, LayoutRect>,
        systemTop: Float,
        usable: LayoutRect,
        gap: Float,
    ): Map<ControlGroup, LayoutRect> {
        val select = groups.getValue(ControlGroup.SELECT)
        val start = groups.getValue(ControlGroup.START)
        val height = select.height
        val others = (groups - ControlGroup.SELECT - ControlGroup.START).values
        // Si algún grupo queda encima en vertical de Select o Start (mismas
        // columnas), tienen que pasar por debajo de él.
        val clearance = others
            .filter { other -> other.left < start.right && other.right > select.left }
            .maxOfOrNull { it.bottom + gap }
            ?: usable.top
        // Si debajo de la pantalla no caben —un iPad en vertical, donde las
        // dos pantallas ocupan casi todo el alto—, lo más abajo posible: sobre
        // el borde inferior de la pantalla, no en mitad de ella, que es donde
        // quedaban en el bloque.
        val lowest = usable.bottom - gap - height
        val newTop = minOf(maxOf(systemTop, clearance), lowest)
        if (newTop < clearance) return groups

        val dy = newTop - select.top
        val moved = groups + mapOf(
            ControlGroup.SELECT to LayoutRect(select.left, select.top + dy, select.right, select.bottom + dy),
            ControlGroup.START to LayoutRect(start.left, start.top + dy, start.right, start.bottom + dy),
        )
        val overlaps = moved.entries.any { (group, rect) ->
            moved.any { (other, otherRect) -> other != group && LayoutEngine.overlaps(rect, otherRect) }
        }
        return if (overlaps) groups else moved
    }

    /**
     * Lleva soplar abajo a la izquierda: pegado al borde izquierdo de los
     * mandos (el de L) y a la altura de Select y Start, simétrico al botón de
     * apartar los mandos ([hideToggleBounds]) al otro lado. Así no tapa la
     * pantalla inferior y queda siempre a mano.
     *
     * Si ahí pisara algún grupo —un teléfono tan estrecho que la dirección
     * comparte fila con Select—, baja justo por debajo de lo que pisa. Si
     * tampoco cabe ahí, se queda debajo de L, donde iba.
     */
    private fun withBlowBesideSystemButtons(
        groups: Map<ControlGroup, LayoutRect>,
        usable: LayoutRect,
        gap: Float,
    ): Map<ControlGroup, LayoutRect> {
        val blow = groups.getValue(ControlGroup.BLOW)
        val left = groups.getValue(ControlGroup.L).left
        val side = blow.height
        val others = (groups - ControlGroup.BLOW).values
        fun at(top: Float) = LayoutRect(left, top, left + blow.width, top + side)
        fun fits(rect: LayoutRect) =
            rect.bottom <= usable.bottom && others.none { LayoutEngine.overlaps(rect, it) }

        val aligned = at(groups.getValue(ControlGroup.SELECT).centerY - side / 2f)
        val candidate = if (fits(aligned)) {
            aligned
        } else {
            val below = others.filter { LayoutEngine.overlaps(aligned, it) }.maxOf { it.bottom } + gap
            at(below).takeIf(::fits)
        }
        return if (candidate != null) groups + (ControlGroup.BLOW to candidate) else groups
    }

    /**
     * El techo de [overlayProfile]: lo más bajo de dos límites.
     *
     * - El borde de abajo de la pantalla que queda físicamente arriba, cuando
     *   las dos están una encima de la otra: los mandos van encima de la
     *   pantalla inferior, nunca de la superior. Si no están apiladas —lado a
     *   lado, una sola, o la secundaria dentro de la principal— este límite
     *   no existe.
     * - [belowCenter] por debajo del centro de [area]: así se deja visible
     *   más de la pantalla inferior que la que ocuparían los mandos subiendo
     *   hasta el borde. Quien llama lo convierte de centímetros reales.
     */
    fun overlayCeiling(placement: ScreenPlacement, area: LayoutRect, belowCenter: Float = 0f): Float {
        val fromCenter = if (belowCenter > 0f) area.centerY + belowCenter else area.top
        val fromScreens = stackedGap(placement)?.let { (upperBottom, _) -> upperBottom } ?: area.top
        return maxOf(fromCenter, fromScreens).coerceIn(area.top, area.bottom)
    }

    /**
     * Los grupos que tapan alguna de las dos pantallas: los que el botón de
     * apartar los mandos se lleva a los lados. Los que no tapan nada —Select
     * y Start bajo la pantalla inferior, o todos cuando las pantallas van en
     * la mitad de arriba— se quedan donde están: apartarlos no destaparía
     * nada y sólo obligaría a devolverlos para pulsarlos.
     *
     * Soplar no se aparta nunca, tape o no: hay juegos que lo piden en mitad
     * de una escena (apagar una vela), y tener que sacar los mandos antes
     * llega tarde.
     */
    fun groupsCoveringScreens(profile: ControlLayoutProfile, placement: ScreenPlacement): Set<ControlGroup> =
        profile.groups.filterValues { group ->
            LayoutEngine.overlaps(group.bounds, placement.topScreen) ||
                LayoutEngine.overlaps(group.bounds, placement.touchScreen)
        }.keys - NEVER_HIDDEN

    /** Los grupos que se quedan siempre a la vista, ver [groupsCoveringScreens]. */
    private val NEVER_HIDDEN = setOf(ControlGroup.BLOW)

    /**
     * El borde de abajo de la pantalla que queda físicamente abajo, cuando
     * están una encima de la otra; `null` si no lo están. Debajo van Select
     * y Start, ver [overlayProfile].
     */
    fun lowerScreenBottom(placement: ScreenPlacement): Float? {
        if (stackedGap(placement) == null) return null
        return maxOf(placement.topScreen.bottom, placement.touchScreen.bottom)
    }

    /**
     * El hueco entre las dos pantallas cuando están una encima de la otra,
     * como (borde de abajo de la de arriba, borde de arriba de la de abajo);
     * `null` si no están apiladas. Ahí va la barra de velocidad.
     */
    fun stackedGap(placement: ScreenPlacement): Pair<Float, Float>? {
        val a = placement.topScreen
        val b = placement.touchScreen
        if (a.width <= 0f || a.height <= 0f || b.width <= 0f || b.height <= 0f) return null
        val (upper, lower) = if (a.top <= b.top) a to b else b to a
        return if (upper.bottom <= lower.top) upper.bottom to lower.top else null
    }

    /**
     * Dónde va el botón de apartar los mandos: abajo a la derecha, pegado al
     * borde derecho de los mandos (el de R) y a la altura de Start y Select.
     * Se deriva del perfil y no se guarda: no es un mando de la DS, no se
     * arrastra en el editor, y así sigue a R y a Start los mueva quien los
     * mueva.
     *
     * Si ahí pisara algún grupo —en un teléfono ancho, Start y Select
     * comparten fila con el rombo, que llega hasta el borde derecho—, baja
     * justo por debajo de lo que pisa. Nunca sale de [area].
     *
     * Del tamaño de soplar, pero nunca mayor que [minTouchTarget].
     */
    fun hideToggleBounds(profile: ControlLayoutProfile, minTouchTarget: Float, area: LayoutRect): LayoutRect {
        val r = profile.groups.getValue(ControlGroup.R).bounds
        val start = profile.groups.getValue(ControlGroup.START).bounds
        val blow = profile.groups.getValue(ControlGroup.BLOW).bounds
        val side = blow.height.coerceAtMost(minTouchTarget)
        val right = r.right.coerceAtMost(area.right)

        fun at(top: Float): LayoutRect {
            val clampedTop = top.coerceIn(area.top, (area.bottom - side).coerceAtLeast(area.top))
            return LayoutRect(right - side, clampedTop, right, clampedTop + side)
        }

        val aligned = at(start.centerY - side / 2f)
        val blocking = profile.groups.values.map { it.bounds }.filter { LayoutEngine.overlaps(aligned, it) }
        if (blocking.isEmpty()) return aligned
        return at(blocking.maxOf { it.bottom } + side * OVERLAY_GAP_SCALE / HIDE_TOGGLE_SCALE)
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
