package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import com.moralesDev.dsForEveryone.ui.theme.RubberAccentBorder
import com.moralesDev.dsForEveryone.ui.theme.RubberAccentBottom
import com.moralesDev.dsForEveryone.ui.theme.RubberAccentLedge
import com.moralesDev.dsForEveryone.ui.theme.RubberAccentMid
import com.moralesDev.dsForEveryone.ui.theme.RubberAccentText
import com.moralesDev.dsForEveryone.ui.theme.RubberAccentTop
import com.moralesDev.dsForEveryone.ui.theme.RubberBorder
import com.moralesDev.dsForEveryone.ui.theme.RubberBottom
import com.moralesDev.dsForEveryone.ui.theme.RubberLedge
import com.moralesDev.dsForEveryone.ui.theme.RubberMid
import com.moralesDev.dsForEveryone.ui.theme.RubberText
import com.moralesDev.dsForEveryone.ui.theme.RubberTop

/**
 * Los cinco colores que hacen falta para pintar un botón de caucho con
 * relieve: bisel claro arriba ([top]), caída a [mid] y [bottom], [border] de
 * un solo píxel, y el [ledge] sólido que se ve debajo cuando el botón está
 * levantado —y que desaparece casi entero cuando se hunde—.
 */
data class RubberPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
    val border: Color,
    val ledge: Color,
    val text: Color,
)

/** Gris industrial: L, R, Start, Select, soplar, la cruceta. */
val RubberNeutral = RubberPalette(
    top = RubberTop,
    mid = RubberMid,
    bottom = RubberBottom,
    border = RubberBorder,
    ledge = RubberLedge,
    text = RubberText,
)

/** Violeta apagado: sólo el botón A, el que más se pulsa del rombo. */
val RubberAccent = RubberPalette(
    top = RubberAccentTop,
    mid = RubberAccentMid,
    bottom = RubberAccentBottom,
    border = RubberAccentBorder,
    ledge = RubberAccentLedge,
    text = RubberAccentText,
)

/**
 * La superficie compartida de los siete grupos de mandos: un botón de
 * caucho oscuro con relieve, no el vidrio traslúcido del resto de la app
 * (ver [com.moralesDev.dsForEveryone.ui.theme.Glass]).
 *
 * El relieve se construye con dos capas en vez de con sombras CSS —Compose
 * no tiene sombra interior—: [palette.ledge] es un zócalo sólido del mismo
 * contorno, fijo; encima va la cara del botón, que al pulsar se desplaza
 * hacia abajo casi hasta tapar el zócalo, con menos elevación y un
 * degradado radial claro-a-oscuro que imita la luz viniendo de arriba. Un
 * barrido claro arriba y uno oscuro abajo, dentro de la propia cara,
 * terminan de vender el bisel.
 *
 * Sin textura de rejilla ni de puntos del diseño original: a los tamaños
 * reales en pantalla no se distinguen y salen caras de recalcular en cada
 * fotograma para siete grupos a la vez.
 */
@Composable
fun RubberSurface(
    shape: Shape,
    pressed: Boolean,
    opacity: Float,
    modifier: Modifier = Modifier,
    palette: RubberPalette = RubberNeutral,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val pressProgress by animateFloatAsState(if (pressed) 1f else 0f, label = "rubber-press")
    // El zócalo no se mueve nunca: es la base fija. Lo que baja es la cara,
    // hasta dejar sólo PRESSED_LEDGE de zócalo a la vista cuando está pulsado
    // —como el «0 4px 0» de la sombra CSS que se queda en «0 1px 0»—.
    val ledgeOffset = RAISED_LEDGE
    val faceOffset = lerp(0.dp, RAISED_LEDGE - PRESSED_LEDGE, pressProgress)
    val elevation = lerp(RAISED_ELEVATION, PRESSED_ELEVATION, pressProgress)
    val topHighlightAlpha = lerp(0.30f, 0.10f, pressProgress) * opacity
    val bottomShadowAlpha = lerp(0.25f, 0.6f, pressProgress) * opacity

    Box(modifier = modifier) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = ledgeOffset)
                .clip(shape)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    onDrawBehind { drawOutline(outline, palette.ledge, alpha = opacity) }
                },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = faceOffset)
                .shadow(elevation, shape, clip = false)
                .clip(shape)
                .drawWithCache {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    val fill = Brush.radialGradient(
                        colors = listOf(palette.top, palette.mid, palette.bottom),
                        center = Offset(size.width / 2f, 0f),
                        radius = size.height.coerceAtLeast(size.width) * 1.35f,
                    )
                    val bevel = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = topHighlightAlpha),
                        0.4f to Color.Transparent,
                        0.6f to Color.Transparent,
                        1f to Color.Black.copy(alpha = bottomShadowAlpha),
                    )
                    onDrawBehind {
                        drawOutline(outline, fill, alpha = opacity)
                        drawOutline(outline, bevel)
                    }
                }
                .border(1.dp, palette.border.copy(alpha = palette.border.alpha * opacity), shape),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

/** Un círculo pequeño, del mismo material, sin pulsación: el nudo central de la cruceta. */
@Composable
fun RubberHub(opacity: Float, modifier: Modifier = Modifier) {
    RubberSurface(shape = CircleShape, pressed = false, opacity = opacity, modifier = modifier)
}

private val RAISED_LEDGE = 4.dp
private val PRESSED_LEDGE = 1.dp
private val RAISED_ELEVATION = 10.dp
private val PRESSED_ELEVATION = 3.dp
