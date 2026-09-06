package com.moralesDev.dsForEveryone.emulator.layout

/** Coordenada de la pantalla táctil real de la DS: 0..255 en x, 0..191 en y. */
data class DsPoint(val x: Int, val y: Int)

/**
 * Convierte un punto de la vista en una coordenada de la pantalla táctil de
 * la DS, dado dónde está colocada esa pantalla ahora mismo.
 *
 * Geometría pura, igual que [ScreenLayout]: sin `Context`, sin `Composable`,
 * sin unidades de Compose. Ver docs/entrada.md.
 */
object TouchRouter {

    const val TOUCH_WIDTH = 256
    const val TOUCH_HEIGHT = 192

    /**
     * @param x,y punto en las mismas unidades que [ScreenPlacement] (las del
     *   hueco que resolvió [ScreenLayout.resolve]).
     * @return `null` si el punto cae fuera de [ScreenPlacement.touchScreen].
     *   No se recorta al borde: fuera es fuera, y no produce toque. El propio
     *   borde sí cuenta como dentro, para que las esquinas exactas den
     *   siempre las columnas y filas extremas (0 y 255, 0 y 191).
     */
    fun map(x: Float, y: Float, placement: ScreenPlacement): DsPoint? {
        val screen = placement.touchScreen
        if (screen.width <= 0f || screen.height <= 0f) return null
        if (x < screen.left || x > screen.right || y < screen.top || y > screen.bottom) return null

        val relativeX = (x - screen.left) / screen.width
        val relativeY = (y - screen.top) / screen.height
        val column = (relativeX * TOUCH_WIDTH).toInt().coerceIn(0, TOUCH_WIDTH - 1)
        val row = (relativeY * TOUCH_HEIGHT).toInt().coerceIn(0, TOUCH_HEIGHT - 1)
        return DsPoint(column, row)
    }
}
