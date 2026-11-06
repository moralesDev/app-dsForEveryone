package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.emulator.DirectionalPadZones
import com.moralesDev.dsForEveryone.emulator.DsButton

/**
 * Cruceta de ocho direcciones.
 *
 * Se comporta como **un único control**, no como ocho botones sueltos: hay un
 * solo manejador de toques para todo el cuadrado, y qué dirección corresponde
 * a cada punto lo decide [DirectionalPadZones], fuera de Compose, para poder
 * comprobarlo coordenada a coordenada en JVM. El dedo pasa de una dirección a
 * otra —diagonales incluidas— sin levantarse: cada movimiento sustituye el
 * conjunto entero, así que la anterior se suelta sola.
 *
 * Visualmente es un aro fino con cuatro flechas transparentes de borde
 * claro ([GhostSurface]), cada una redondeada por el lado de fuera y recta
 * por el que mira al centro: se ve el juego a través. Sigue siendo la misma
 * rejilla de 3×3 con pesos iguales de siempre: el tamaño no es una constante
 * de diseño, lo decide `ControlLayout`.
 */
@Composable
fun DirectionalPad(
    pressedMask: Int,
    opacity: Float,
    haptic: Boolean,
    onDirections: (Set<DsButton>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dpadLabel = stringResource(R.string.cd_dpad)

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = dpadLabel }
            .pressableWithPosition(haptic = haptic) { position, bounds ->
                onDirections(
                    if (position == null) {
                        emptySet()
                    } else {
                        DirectionalPadZones.directionsAt(position.x, position.y, bounds.width, bounds.height)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .border(1.dp, Color.White.copy(alpha = RING_ALPHA * opacity), CircleShape),
        )

        Column(Modifier.fillMaxSize(INNER_FRACTION)) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Arm(DsButton.UP, rotation = 0f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.weight(1f))
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Arm(DsButton.LEFT, rotation = -90f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.weight(1f))
                Arm(DsButton.RIGHT, rotation = 90f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Arm(DsButton.DOWN, rotation = 180f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Arm(
    direction: DsButton,
    rotation: Float,
    pressedMask: Int,
    opacity: Float,
    modifier: Modifier,
) {
    val pressed = pressedMask and direction.mask != 0
    GhostSurface(
        shape = armShape(direction),
        pressed = pressed,
        opacity = opacity,
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDropUp,
            contentDescription = null,
            tint = ghostContentColor(opacity),
            modifier = Modifier
                .fillMaxSize(ARROW_FRACTION)
                .rotate(rotation),
        )
    }
}

/**
 * Redondeado por el lado que mira hacia fuera del disco, recto por el que
 * mira al nudo central: es lo que hace que las cuatro flechas se lean como
 * parte de un círculo y no como cuatro botones cuadrados sueltos.
 */
private fun armShape(direction: DsButton): Shape = when (direction) {
    DsButton.UP -> RoundedCornerShape(topStartPercent = OUTER_PERCENT, topEndPercent = OUTER_PERCENT, bottomEndPercent = INNER_PERCENT, bottomStartPercent = INNER_PERCENT)
    DsButton.DOWN -> RoundedCornerShape(topStartPercent = INNER_PERCENT, topEndPercent = INNER_PERCENT, bottomEndPercent = OUTER_PERCENT, bottomStartPercent = OUTER_PERCENT)
    DsButton.LEFT -> RoundedCornerShape(topStartPercent = OUTER_PERCENT, topEndPercent = INNER_PERCENT, bottomEndPercent = INNER_PERCENT, bottomStartPercent = OUTER_PERCENT)
    DsButton.RIGHT -> RoundedCornerShape(topStartPercent = INNER_PERCENT, topEndPercent = OUTER_PERCENT, bottomEndPercent = OUTER_PERCENT, bottomStartPercent = INNER_PERCENT)
    else -> RoundedCornerShape(INNER_PERCENT)
}

/** Fracción del cuadrado que ocupa la rejilla de flechas, dejando ver el aro del disco base alrededor. */
private const val INNER_FRACTION = 0.84f
private const val ARROW_FRACTION = 0.55f
private const val RING_ALPHA = 0.35f
private const val OUTER_PERCENT = 45
private const val INNER_PERCENT = 15
