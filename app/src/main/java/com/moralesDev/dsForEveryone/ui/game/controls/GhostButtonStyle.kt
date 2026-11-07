package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * La superficie de todos los mandos en pantalla: **transparente**, con un
 * borde claro y el texto claro.
 *
 * En vertical los mandos van encima de la pantalla inferior (ver
 * `ControlLayout.overlayProfile`), así que un botón opaco es un trozo de
 * juego que no se ve. Aquí el botón es sólo su contorno: el juego se sigue
 * viendo a través, y al pulsar se rellena un poco de blanco para que el dedo
 * sepa que ha entrado.
 *
 * Sobre un fondo blanco —una pantalla de carga, un menú claro— el borde
 * claro desaparecería. Por eso, justo por dentro del borde, hay un halo
 * oscuro y fino: sobre negro se ve el borde, sobre blanco se ve el halo, y
 * entre medias los dos. Las letras y los iconos llevan la misma idea en
 * forma de sombra ([ghostShadow], [GhostIcon]).
 *
 * El recorte con [shape] no es sólo estético: Compose descarta los toques
 * fuera del contorno recortado, así que un botón redondo no roba toques a la
 * pantalla táctil por las esquinas de su caja.
 *
 * @param opacity la del grupo en el perfil (editor de disposición): escala
 *   el borde, el relleno y [ghostContentColor] por igual.
 */
@Composable
fun GhostSurface(
    shape: Shape,
    pressed: Boolean,
    opacity: Float,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val fill by animateFloatAsState(if (pressed) PRESSED_FILL_ALPHA else IDLE_FILL_ALPHA, label = "ghost-fill")
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = fill * opacity), shape)
            .drawWithCache {
                val outline = shape.createOutline(size, layoutDirection, this)
                val halo = Stroke(width = HALO_WIDTH.toPx())
                onDrawBehind { drawOutline(outline, Color.Black.copy(alpha = HALO_ALPHA * opacity), style = halo) }
            }
            .border(BORDER_WIDTH, Color.White.copy(alpha = BORDER_ALPHA * opacity), shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Color de letras e iconos sobre [GhostSurface]. */
fun ghostContentColor(opacity: Float): Color = Color.White.copy(alpha = CONTENT_ALPHA * opacity)

/**
 * Sombra oscura difusa para las letras claras: lo que las mantiene legibles
 * cuando debajo el juego está en blanco.
 */
fun ghostShadow(opacity: Float): Shadow =
    Shadow(color = Color.Black.copy(alpha = SHADOW_ALPHA * opacity), offset = Offset.Zero, blurRadius = TEXT_SHADOW_BLUR)

/** Aro fino de la dirección: borde claro con halo oscuro por dentro, como [GhostSurface]. */
fun Modifier.ghostRing(opacity: Float): Modifier = this
    .border(RING_HALO_WIDTH, Color.Black.copy(alpha = HALO_ALPHA * opacity), CircleShape)
    .border(1.dp, Color.White.copy(alpha = RING_ALPHA * opacity), CircleShape)

/**
 * Un icono claro con una copia oscura detrás, desplazada un poco hacia
 * abajo: la versión para iconos de [ghostShadow], que `Icon` no tiene.
 */
@Composable
fun GhostIcon(
    imageVector: ImageVector,
    tint: Color,
    opacity: Float,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Box(modifier) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.Black.copy(alpha = SHADOW_ALPHA * opacity),
            modifier = Modifier
                .matchParentSize()
                .offset(y = ICON_SHADOW_OFFSET),
        )
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.matchParentSize(),
        )
    }
}

private val BORDER_WIDTH = 1.5.dp

/**
 * El trazo se centra en el contorno y el recorte se come la mitad de fuera:
 * lo que se ve es una franja de la mitad de ancho, justo por dentro del
 * borde claro.
 */
private val HALO_WIDTH = 5.dp
private const val HALO_ALPHA = 0.32f
private val RING_HALO_WIDTH = 2.5.dp
private const val RING_ALPHA = 0.45f
private const val SHADOW_ALPHA = 0.75f
private const val TEXT_SHADOW_BLUR = 6f
private val ICON_SHADOW_OFFSET = 1.dp
private const val BORDER_ALPHA = 0.75f
private const val CONTENT_ALPHA = 0.9f

/** En reposo, apenas un velo: lo justo para que el botón se lea sobre un fondo negro. */
private const val IDLE_FILL_ALPHA = 0.06f
private const val PRESSED_FILL_ALPHA = 0.32f
