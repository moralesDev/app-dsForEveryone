package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.emulator.JoystickGeometry
import kotlin.math.roundToInt

/**
 * Joystick de ocho direcciones: otra forma de pulsar la cruceta de la DS.
 *
 * Se comporta igual que [DirectionalPad] —un solo control, un solo dedo, cada
 * movimiento sustituye el conjunto de direcciones entero— y su salida es la
 * misma, así que el resto de la app no distingue cuál de los dos está en
 * pantalla. Qué dirección corresponde a cada punto lo decide
 * [JoystickGeometry], fuera de Compose.
 *
 * La bola sigue al dedo, recortada al aro. Es lo único que se pinta desde
 * estado local y no desde la máscara de [com.moralesDev.dsForEveryone.emulator.InputManager]:
 * la máscara dice qué direcciones están pulsadas, no dónde está el pulgar.
 * El `finally` de [pressableWithPosition] la devuelve al centro al soltar o
 * si el gesto se cancela, igual que suelta las direcciones.
 */
@Composable
fun Joystick(
    pressedMask: Int,
    opacity: Float,
    haptic: Boolean,
    onDirections: (Set<DsButton>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.cd_joystick)
    var knob by remember { mutableStateOf(Offset.Zero) }
    val anyDirection = pressedMask and DIRECTION_MASK != 0

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label }
            .pressableWithPosition(haptic = haptic) { position, bounds ->
                if (position == null) {
                    knob = Offset.Zero
                    onDirections(emptySet())
                } else {
                    val radius = minOf(bounds.width, bounds.height) / 2f
                    val dx = position.x - bounds.width / 2f
                    val dy = position.y - bounds.height / 2f
                    val (knobX, knobY) = JoystickGeometry.knobOffset(dx, dy, radius)
                    knob = Offset(knobX, knobY)
                    onDirections(JoystickGeometry.directions(dx, dy, radius))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        RubberSurface(shape = CircleShape, pressed = false, opacity = opacity * BASE_OPACITY, modifier = Modifier.fillMaxSize())
        RubberHub(opacity = opacity, modifier = Modifier.fillMaxSize(HOLLOW_FRACTION))
        RubberSurface(
            shape = CircleShape,
            pressed = anyDirection,
            opacity = opacity,
            palette = RubberAccent,
            modifier = Modifier
                .offset { IntOffset(knob.x.roundToInt(), knob.y.roundToInt()) }
                .fillMaxSize(KNOB_FRACTION),
        )
    }
}

private val DIRECTION_MASK = DsButton.UP.mask or DsButton.DOWN.mask or DsButton.LEFT.mask or DsButton.RIGHT.mask

/** El aro se pinta algo más tenue que la bola: es el fondo, no lo que se toca. */
private const val BASE_OPACITY = 0.8f

/** El hueco por el que se mueve la bola, más oscuro que el aro. */
private const val HOLLOW_FRACTION = 0.72f

/** La bola: algo menos de la mitad del joystick. */
private const val KNOB_FRACTION = 0.46f
