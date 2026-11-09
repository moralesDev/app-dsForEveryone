package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPad

/**
 * iOS no da los píxeles por pulgada de la pantalla. Lo que sí es estable es
 * cuántos **puntos** caben en una pulgada según el tipo de aparato —unos 163
 * en iPhone y en iPad mini, 132 en el resto de iPad— y cuántos píxeles tiene
 * cada punto (la escala, que Compose da como densidad). Es una aproximación:
 * en algún modelo concreto el milímetro puede salir un 5-10 % distinto. Para
 * colocar los mandos («a medio centímetro del centro») basta.
 */
@Composable
actual fun rememberPxPerMm(): PxPerMm {
    val density = LocalDensity.current.density
    return remember(density) {
        val pointsPerInch = if (UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPad) 132f else 163f
        val pxPerMm = density * pointsPerInch / MM_PER_INCH
        PxPerMm(x = pxPerMm, y = pxPerMm)
    }
}
