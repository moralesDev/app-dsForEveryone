package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.moralesDev.dsForEveryone.emulator.DsButton

/**
 * El rombo de acción: A, B, X e Y, en cruz dentro del cuadrado que le asigne
 * `ControlLayout` —Y arriba, A a la derecha, B abajo, X a la izquierda—.
 *
 * Los cuatro botones **no se tocan**, ni siquiera sus cajas: con cada uno
 * ocupando [ACTION_FRACTION] del lado y pegado a su borde, entre dos vecinos
 * queda siempre un hueco (hace falta menos de un tercio del lado para que las
 * cajas no se crucen). Un dedo entre Y y A no pulsa los dos por accidente.
 *
 * Cada botón sigue su propio dedo con [pressable]: A + B a la vez, o A con la
 * dirección, funcionan sin tratamiento especial.
 */
@Composable
fun ActionButtons(
    pressedMask: Int,
    opacity: Float,
    haptic: Boolean,
    onPress: (DsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        listOf(
            Triple(DsButton.Y, "Y", Alignment.TopCenter),
            Triple(DsButton.X, "X", Alignment.CenterStart),
            Triple(DsButton.A, "A", Alignment.CenterEnd),
            Triple(DsButton.B, "B", Alignment.BottomCenter),
        ).forEach { (button, label, alignment) ->
            ActionButton(
                button = button,
                label = label,
                pressedMask = pressedMask,
                opacity = opacity,
                haptic = haptic,
                onPress = onPress,
                modifier = Modifier.align(alignment).fillMaxSize(ACTION_FRACTION),
            )
        }
    }
}

@Composable
private fun ActionButton(
    button: DsButton,
    label: String,
    pressedMask: Int,
    opacity: Float,
    haptic: Boolean,
    onPress: (DsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    GhostSurface(
        shape = CircleShape,
        pressed = pressedMask and button.mask != 0,
        opacity = opacity,
        modifier = modifier
            .semantics { contentDescription = label }
            .pressable(haptic = haptic) { isPressed -> onPress(button, isPressed) },
    ) {
        Text(
            text = label,
            color = ghostContentColor(opacity),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
        )
    }
}

/**
 * Lado de cada botón, como fracción del rombo. Por debajo de 1/3 las cajas de
 * dos vecinos no se cruzan; 0,32 deja además algo de aire entre ellas.
 */
private const val ACTION_FRACTION = 0.32f
