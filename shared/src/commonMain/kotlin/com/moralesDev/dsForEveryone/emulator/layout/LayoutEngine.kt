package com.moralesDev.dsForEveryone.emulator.layout

import kotlin.math.round

/** Tamaño en las mismas unidades que el hueco que se le pase (normalmente píxeles). */
data class LayoutSize(val width: Float, val height: Float)

/**
 * Rectángulo con esquinas absolutas, sin unidades de Compose.
 *
 * Un rectángulo de área cero (`width == 0f && height == 0f`) representa una
 * pantalla oculta: no cuenta como solape con nada y [LayoutEngine.overlaps]
 * lo trata siempre como `false`.
 */
data class LayoutRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val size: LayoutSize get() = LayoutSize(width, height)

    companion object {
        fun ofSize(left: Float, top: Float, size: LayoutSize) =
            LayoutRect(left, top, left + size.width, top + size.height)
    }
}

/**
 * Los márgenes que hay que reservar dentro de un hueco, en píxeles: en la app,
 * los de las barras del sistema. No es un tipo de Compose a propósito —quien
 * llama traduce `WindowInsets` a esto— para que la geometría se siga probando
 * en JVM sin Android.
 */
data class LayoutInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    companion object {
        /** Sin márgenes: el hueco entero. */
        val NONE = LayoutInsets()
    }
}

/**
 * El rectángulo que queda al reservar [insets] por cada lado. Si los márgenes
 * se comen el rectángulo entero el resultado queda con área cero, nunca
 * invertido.
 */
fun LayoutRect.inset(insets: LayoutInsets): LayoutRect {
    val insetLeft = left + insets.left
    val insetTop = top + insets.top
    return LayoutRect(
        left = insetLeft,
        top = insetTop,
        right = (right - insets.right).coerceAtLeast(insetLeft),
        bottom = (bottom - insets.bottom).coerceAtLeast(insetTop),
    )
}

/**
 * Geometría pura para colocar rectángulos con restricciones dentro de un
 * área: no solaparse, no salirse, mantener una proporción, ajustar a una
 * rejilla. Sin `Context`, sin `Composable`, sin unidades de Compose.
 *
 * Lo consumen tanto [ScreenLayout] (las dos pantallas) como, más adelante,
 * `ControlLayout` (los siete grupos de mandos): el problema es el mismo —
 * rectángulos que no pueden solaparse dentro de un área— y se resuelve una
 * sola vez. Ver docs/pantallas.md.
 */
object LayoutEngine {

    /**
     * El mayor tamaño con la proporción [aspectRatio] (ancho ÷ alto) que cabe
     * dentro de [bounds] sin salirse por ningún lado.
     */
    fun fitAspectRatio(bounds: LayoutSize, aspectRatio: Float): LayoutSize {
        require(aspectRatio > 0f) { "La proporción debe ser positiva: $aspectRatio" }
        if (bounds.width <= 0f || bounds.height <= 0f) return LayoutSize(0f, 0f)

        val widthConstrained = LayoutSize(bounds.width, bounds.width / aspectRatio)
        return if (widthConstrained.height <= bounds.height) {
            widthConstrained
        } else {
            LayoutSize(bounds.height * aspectRatio, bounds.height)
        }
    }

    /** Centra un rectángulo de [size] dentro de [area]. */
    fun center(size: LayoutSize, area: LayoutRect): LayoutRect {
        val left = area.left + (area.width - size.width) / 2f
        val top = area.top + (area.height - size.height) / 2f
        return LayoutRect.ofSize(left, top, size)
    }

    /** Un rectángulo sin área, en el centro de [area]: el hueco de un elemento oculto. */
    fun collapsed(area: LayoutRect): LayoutRect =
        LayoutRect(area.centerX, area.centerY, area.centerX, area.centerY)

    /**
     * Si dos rectángulos comparten área. Un rectángulo sin área (ancho o alto
     * cero) nunca solapa con nada: es como se representa un elemento oculto.
     */
    fun overlaps(a: LayoutRect, b: LayoutRect): Boolean =
        a.width > 0f && a.height > 0f && b.width > 0f && b.height > 0f &&
            a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top

    /** Si [rect] cabe entero dentro de [area], borde incluido. */
    fun isWithin(rect: LayoutRect, area: LayoutRect): Boolean =
        rect.left >= area.left && rect.top >= area.top &&
            rect.right <= area.right && rect.bottom <= area.bottom

    /** Ajusta un valor al múltiplo de [gridSize] más cercano. `gridSize <= 0` lo deja igual. */
    fun snapToGrid(value: Float, gridSize: Float): Float {
        if (gridSize <= 0f) return value
        return round(value / gridSize) * gridSize
    }
}
