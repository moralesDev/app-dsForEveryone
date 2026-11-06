package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
            .border(BORDER_WIDTH, Color.White.copy(alpha = BORDER_ALPHA * opacity), shape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Color de letras e iconos sobre [GhostSurface]. */
fun ghostContentColor(opacity: Float): Color = Color.White.copy(alpha = CONTENT_ALPHA * opacity)

private val BORDER_WIDTH = 1.5.dp
private const val BORDER_ALPHA = 0.75f
private const val CONTENT_ALPHA = 0.9f

/** En reposo, apenas un velo: lo justo para que el botón se lea sobre un fondo negro. */
private const val IDLE_FILL_ALPHA = 0.06f
private const val PRESSED_FILL_ALPHA = 0.32f
