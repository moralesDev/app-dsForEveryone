package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Píxeles por milímetro **real** de la pantalla, en horizontal y en vertical.
 *
 * Los dp no sirven para esto: son 1/160 de pulgada sólo en teoría, y cada
 * fabricante los redondea a su cubo de densidad. Cuando lo que se pide es
 * «3 mm hacia el centro» o «2 cm por debajo del centro», lo que cuenta es
 * lo que mide una regla sobre el cristal, y eso lo dicen `xdpi`/`ydpi`.
 *
 * Algunos teléfonos informan `xdpi`/`ydpi` disparatados; si se alejan más de
 * la mitad de `densityDpi`, se usa ese en su lugar.
 */
data class PxPerMm(val x: Float, val y: Float)

@Composable
fun rememberPxPerMm(): PxPerMm {
    val metrics = LocalContext.current.resources.displayMetrics
    return remember(metrics.xdpi, metrics.ydpi, metrics.densityDpi) {
        val fallback = metrics.densityDpi.toFloat()
        fun sane(dpi: Float) = if (dpi in fallback * 0.5f..fallback * 1.5f) dpi else fallback
        PxPerMm(x = sane(metrics.xdpi) / MM_PER_INCH, y = sane(metrics.ydpi) / MM_PER_INCH)
    }
}

/** Cuánto se acercan al centro la dirección y el rombo en vertical. */
const val CONTROLS_INWARD_SHIFT_MM = 3f

/** A cuánto por debajo del centro empieza el techo de los mandos en vertical. */
const val CONTROLS_CEILING_BELOW_CENTER_MM = 20f

private const val MM_PER_INCH = 25.4f
