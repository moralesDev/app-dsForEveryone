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
 * El rombo de acción: A, B, X e Y. En la GBA es un par; en la DS crece a
 * cuatro (ver docs/entrada.md, «Doce botones donde había diez»), así que se
 * reparte en cruz dentro del cuadrado que le asigne `ControlLayout` —Y
 * arriba, A a la derecha, B abajo, X a la izquierda—, con A en la paleta
 * violeta de [RubberAccent] por ser el que más se pulsa.
 *
 * Cada botón sigue su propio dedo con [pressable]: A + B a la vez, o A con la
 * cruceta, funcionan sin tratamiento especial.
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
        ActionButton(
            button = DsButton.Y,
            label = "Y",
            pressedMask = pressedMask,
            opacity = opacity,
            haptic = haptic,
            onPress = onPress,
            palette = RubberNeutral,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize(ACTION_FRACTION),
        )
        ActionButton(
            button = DsButton.X,
            label = "X",
            pressedMask = pressedMask,
            opacity = opacity,
            haptic = haptic,
            onPress = onPress,
            palette = RubberNeutral,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxSize(ACTION_FRACTION),
        )
        ActionButton(
            button = DsButton.A,
            label = "A",
            pressedMask = pressedMask,
            opacity = opacity,
            haptic = haptic,
            onPress = onPress,
            palette = RubberAccent,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxSize(ACTION_FRACTION),
        )
        ActionButton(
            button = DsButton.B,
            label = "B",
            pressedMask = pressedMask,
            opacity = opacity,
            haptic = haptic,
            onPress = onPress,
            palette = RubberNeutral,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxSize(ACTION_FRACTION),
        )
    }
}

@Composable
private fun ActionButton(
    button: DsButton,
    label: String,
    pressedMask: Int,
    opacity: Float,
    haptic: Boolean,
    palette: RubberPalette,
    onPress: (DsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressed = pressedMask and button.mask != 0

    RubberSurface(
        shape = CircleShape,
        pressed = pressed,
        opacity = opacity,
        palette = palette,
        modifier = modifier
            .semantics { contentDescription = label }
            .pressable(haptic = haptic) { isPressed -> onPress(button, isPressed) },
    ) {
        Text(text = label, color = palette.text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
    }
}

/** Cada botón ocupa poco menos de la mitad del cuadrado, para que quepan los cuatro sin solaparse. */
private const val ACTION_FRACTION = 0.46f
