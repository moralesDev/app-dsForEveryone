package com.moralesDev.dsForEveryone.emulator.layout

/**
 * Cómo se colocan las dos pantallas de la DS en el hueco disponible. La
 * persistencia de cuál está elegida vive en
 * [com.moralesDev.dsForEveryone.data.settings.EmulatorSettings]. Ver
 * docs/pantallas.md.
 */
enum class LayoutMode {
    /** Una encima de la otra. La disposición nativa; por defecto en vertical. */
    STACKED,

    /** Una junto a la otra. Por defecto en horizontal. */
    SIDE_BY_SIDE,

    /** Sólo la pantalla superior, a todo el hueco disponible. */
    TOP_ONLY,

    /** Sólo la pantalla táctil, a todo el hueco disponible. */
    BOTTOM_ONLY,

    /**
     * Una grande y la otra reducida en una esquina, superpuesta. Es el único
     * modo en el que las dos pantallas solapan a propósito.
     */
    MAIN_AND_SECONDARY,
}

/**
 * Las dos pantallas ya colocadas.
 *
 * [touchScreen] es siempre el rectángulo de la pantalla táctil de la DS —la
 * inferior por hardware—, sea cual sea su posición física. Intercambiar las
 * pantallas cambia [swapped] en [ScreenLayout.resolve] y con ello dónde se
 * pinta cada una, pero nunca cuál de los dos rectángulos es la táctil: eso lo
 * fija el campo, no la posición.
 */
data class ScreenPlacement(
    val topScreen: LayoutRect,
    val touchScreen: LayoutRect,
)

/**
 * Coloca las dos pantallas de la DS (256×192 cada una, 4:3) dentro de un
 * hueco disponible. Geometría pura: sin `Context`, sin `Composable`, sin
 * unidades de Compose. Ver docs/pantallas.md.
 */
object ScreenLayout {

    /** 256×192 = 4:3. */
    const val SCREEN_ASPECT_RATIO = 256f / 192f

    /**
     * @param available hueco disponible, en píxeles.
     * @param gap separación entre pantallas, 0..1 relativo al hueco (se
     *   ignora en los modos que sólo muestran una pantalla).
     * @param secondaryScale escala de la pantalla reducida en
     *   [LayoutMode.MAIN_AND_SECONDARY], 0..1 relativo a la principal.
     * @param swapped intercambia qué pantalla se pinta arriba y cuál abajo.
     *   La táctil lógica ([ScreenPlacement.touchScreen]) no cambia por esto.
     * @param insets márgenes que reservan las barras del sistema dentro de
     *   [available]. Las pantallas se colocan sólo en lo que queda: la táctil
     *   no puede caer debajo de la barra de navegación, porque ahí los toques
     *   son del sistema y una opción del juego dibujada en esa franja sería
     *   imposible de pulsar. Las proporciones se calculan ya sobre el hueco
     *   recortado, así que las dos pantallas siguen siendo 4:3.
     * @param upperHalf en todos los modos menos [LayoutMode.STACKED], las
     *   pantallas se colocan en la mitad de arriba del hueco, con su borde
     *   de abajo justo en el centro: de ahí hacia abajo van los mandos en
     *   vertical (ver `ControlLayout.overlayProfile`), y una pantalla
     *   centrada quedaría debajo de ellos. Apiladas ya reparten el hueco
     *   entero entre las dos, con la táctil abajo a propósito, y no cambian.
     * @param alignTop las pantallas, pegadas al borde de arriba del hueco en
     *   vez de centradas a lo alto: en horizontal, todo lo que sobra queda
     *   abajo, para los mandos. No hace nada junto con [upperHalf].
     * @param topReserve con [alignTop], cuánto se deja libre arriba, encima de
     *   las pantallas: el botón de menú, que si no queda sobre una. Nunca
     *   encoge las pantallas: si no sobra tanto alto, se deja lo que sobre.
     */
    fun resolve(
        mode: LayoutMode,
        available: LayoutSize,
        gap: Float = 0f,
        secondaryScale: Float = 0.35f,
        swapped: Boolean = false,
        insets: LayoutInsets = LayoutInsets.NONE,
        upperHalf: Boolean = false,
        alignTop: Boolean = false,
        topReserve: Float = 0f,
    ): ScreenPlacement {
        val clampedGap = gap.coerceIn(0f, 1f)
        val clampedScale = secondaryScale.coerceIn(0.05f, 0.95f)
        val fullArea = LayoutRect(0f, 0f, available.width, available.height).inset(insets)
        val toUpperHalf = upperHalf && mode != LayoutMode.STACKED
        val area = if (toUpperHalf) LayoutRect(fullArea.left, fullArea.top, fullArea.right, fullArea.centerY) else fullArea

        // "first"/"second" son las posiciones físicas ANTES de aplicar el
        // intercambio: first es la que ocuparía la pantalla superior, second
        // la que ocuparía la táctil. `swapped` sólo decide, al final, cuál de
        // las dos etiquetas de ScreenPlacement apunta a cuál.
        val (centeredFirst, centeredSecond) = when (mode) {
            LayoutMode.STACKED -> stackedSlots(area, clampedGap)
            LayoutMode.SIDE_BY_SIDE -> sideBySideSlots(area, clampedGap)
            LayoutMode.TOP_ONLY -> soloSlots(area, showFirst = true)
            LayoutMode.BOTTOM_ONLY -> soloSlots(area, showFirst = false)
            LayoutMode.MAIN_AND_SECONDARY -> mainAndSecondarySlots(area, clampedScale)
        }
        // En la mitad de arriba, el bloque no se centra: se apoya en el
        // centro del hueco y crece hacia arriba.
        val (first, second) = if (toUpperHalf) {
            val blockBottom = listOf(centeredFirst, centeredSecond)
                .filter { it.width > 0f && it.height > 0f }
                .maxOfOrNull { it.bottom } ?: area.bottom
            val dy = area.bottom - blockBottom
            centeredFirst.shiftedDown(dy) to centeredSecond.shiftedDown(dy)
        } else if (alignTop) {
            val visible = listOf(centeredFirst, centeredSecond).filter { it.width > 0f && it.height > 0f }
            val blockTop = visible.minOfOrNull { it.top } ?: area.top
            val blockHeight = (visible.maxOfOrNull { it.bottom } ?: area.top) - blockTop
            val reserve = topReserve.coerceIn(0f, (area.height - blockHeight).coerceAtLeast(0f))
            val dy = area.top + reserve - blockTop
            centeredFirst.shiftedDown(dy) to centeredSecond.shiftedDown(dy)
        } else {
            centeredFirst to centeredSecond
        }

        return if (!swapped) {
            ScreenPlacement(topScreen = first, touchScreen = second)
        } else {
            ScreenPlacement(topScreen = second, touchScreen = first)
        }
    }

    private fun LayoutRect.shiftedDown(dy: Float) = LayoutRect(left, top + dy, right, bottom + dy)

    private fun stackedSlots(area: LayoutRect, gap: Float): Pair<LayoutRect, LayoutRect> {
        val gapPx = gap * area.height
        val maxHeightPerScreen = ((area.height - gapPx) / 2f).coerceAtLeast(0f)
        val size = LayoutEngine.fitAspectRatio(
            LayoutSize(area.width, maxHeightPerScreen),
            SCREEN_ASPECT_RATIO,
        )
        val left = area.left + (area.width - size.width) / 2f
        // Centradas también a lo alto: lo que sobra se reparte arriba y
        // abajo. Pegadas arriba dejaban un hueco vacío bajo la táctil y
        // obligaban a los mandos (que van encima de la pantalla inferior,
        // ver ControlLayout.overlayProfile) a taparla más de la cuenta.
        val top = area.top + (area.height - (size.height * 2f + gapPx)).coerceAtLeast(0f) / 2f
        val first = LayoutRect.ofSize(left, top, size)
        val second = LayoutRect.ofSize(left, first.bottom + gapPx, size)
        return first to second
    }

    private fun sideBySideSlots(area: LayoutRect, gap: Float): Pair<LayoutRect, LayoutRect> {
        val gapPx = gap * area.width
        val maxWidthPerScreen = ((area.width - gapPx) / 2f).coerceAtLeast(0f)
        val size = LayoutEngine.fitAspectRatio(
            LayoutSize(maxWidthPerScreen, area.height),
            SCREEN_ASPECT_RATIO,
        )
        val top = area.top + (area.height - size.height) / 2f
        val first = LayoutRect.ofSize(area.left, top, size)
        val second = LayoutRect.ofSize(first.right + gapPx, top, size)
        return first to second
    }

    /** Una sola pantalla a todo el hueco; la otra queda colapsada (área cero). */
    private fun soloSlots(area: LayoutRect, showFirst: Boolean): Pair<LayoutRect, LayoutRect> {
        val size = LayoutEngine.fitAspectRatio(area.size, SCREEN_ASPECT_RATIO)
        val visible = LayoutEngine.center(size, area)
        val hidden = LayoutEngine.collapsed(area)
        return if (showFirst) visible to hidden else hidden to visible
    }

    /**
     * La principal ocupa el hueco entero en 4:3; la secundaria es una
     * reducción suya anclada a su esquina inferior derecha, por lo que queda
     * siempre dentro de sus límites. Es el único modo en el que se permite
     * el solape, y por construcción: la secundaria es un sub-rectángulo de
     * la principal.
     */
    private fun mainAndSecondarySlots(
        area: LayoutRect,
        secondaryScale: Float,
    ): Pair<LayoutRect, LayoutRect> {
        val mainSize = LayoutEngine.fitAspectRatio(area.size, SCREEN_ASPECT_RATIO)
        val main = LayoutEngine.center(mainSize, area)
        val secondarySize = LayoutSize(
            mainSize.width * secondaryScale,
            mainSize.height * secondaryScale,
        )
        val secondary = LayoutRect(
            left = main.right - secondarySize.width,
            top = main.bottom - secondarySize.height,
            right = main.right,
            bottom = main.bottom,
        )
        return main to secondary
    }
}
