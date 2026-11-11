package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.runtime.Composable

/**
 * Píxeles por milímetro **real** de la pantalla, en horizontal y en vertical.
 *
 * Los dp no sirven para esto: son 1/160 de pulgada sólo en teoría, y cada
 * fabricante los redondea a su cubo de densidad. Cuando lo que se pide es
 * «3 mm hacia el centro» o «medio centímetro por debajo del centro», lo que cuenta es
 * lo que mide una regla sobre el cristal, y eso lo dicen `xdpi`/`ydpi`.
 *
 * Algunos teléfonos informan `xdpi`/`ydpi` disparatados; si se alejan más de
 * la mitad de `densityDpi`, se usa ese en su lugar.
 */
data class PxPerMm(val x: Float, val y: Float)

/**
 * Lo calcula cada plataforma: en Android con `xdpi`/`ydpi`; en iOS, que no
 * da los píxeles por pulgada, con los puntos por pulgada de cada tipo de
 * aparato y la escala de la pantalla (ver PhysicalUnits.ios.kt).
 */
@Composable
expect fun rememberPxPerMm(): PxPerMm

/** Cuánto se acercan al centro la dirección y el rombo en vertical. */
const val CONTROLS_INWARD_SHIFT_MM = 3f

/**
 * Lo mismo en horizontal: 1 cm más que en vertical. Con un iPad tumbado las
 * manos lo sujetan por los lados y el pulgar llega cómodo bastante hacia
 * dentro; pegados al borde quedaban incómodos, y con 2 cm, demasiado lejos.
 */
const val CONTROLS_INWARD_SHIFT_LANDSCAPE_MM = 13f

/** A cuánto por debajo del centro empieza el techo de los mandos en vertical. */
const val CONTROLS_CEILING_BELOW_CENTER_MM = 5f

/** A cuánto por debajo de la pantalla inferior van Select y Start en vertical. */
const val SYSTEM_BUTTONS_BELOW_SCREEN_MM = 5f

/** Milímetros por pulgada, para las dos plataformas. */
const val MM_PER_INCH = 25.4f
