package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.GlassStrong
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * El rombo de acción: A, B, X e Y. En la GBA es un par; en la DS crece a
 * cuatro (ver docs/entrada.md, «Doce botones donde había diez»), así que se
 * reparte en cruz dentro del cuadrado que le asigne `ControlLayout` —Y
 * arriba, A a la derecha, B abajo, X a la izquierda—, con A teñido de acento
 * por ser el que más se pulsa.
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
            accented = false,
            modifier = Modifier.align(Alignment.TopCenter).fillMaxSize(ACTION_FRACTION),
        )
        ActionButton(
            button = DsButton.X,
            label = "X",
            pressedMask = pressedMask,
            opacity = opacity,
            haptic = haptic,
            onPress = onPress,
            accented = false,
            modifier = Modifier.align(Alignment.CenterStart).fillMaxSize(ACTION_FRACTION),
        )
        ActionButton(
            button = DsButton.A,
            label = "A",
            pressedMask = pressedMask,
            opacity = opacity,
            haptic = haptic,
            onPress = onPress,
            accented = true,
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxSize(ACTION_FRACTION),
        )
        ActionButton(
            button = DsButton.B,
            label = "B",
            pressedMask = pressedMask,
            opacity = opacity,
            haptic = haptic,
            onPress = onPress,
            accented = false,
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
    accented: Boolean,
    onPress: (DsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pressed = pressedMask and button.mask != 0
    val background = if (accented) {
        Accent.copy(alpha = (if (pressed) 0.34f else 0.16f) * opacity)
    } else {
        (if (pressed) GlassStrong else Glass).let { it.copy(alpha = it.alpha * opacity) }
    }
    val borderColor = if (accented) {
        Accent.copy(alpha = 0.55f * opacity)
    } else {
        GlassBorder.copy(alpha = GlassBorder.alpha * opacity)
    }
    val contentColor = when {
        accented && pressed -> Accent100
        accented -> Accent200
        pressed -> textAlpha(1f)
        else -> textAlpha(0.72f * opacity)
    }

    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "action-scale")
    val animatedBackground by animateColorAsState(background, label = "action-background")

    Box(
        modifier = modifier
            .scale(scale)
            .clip(CircleShape)
            .background(animatedBackground)
            .border(1.dp, borderColor, CircleShape)
            .semantics { contentDescription = label }
            .pressable(haptic = haptic) { isPressed -> onPress(button, isPressed) },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = contentColor, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium))
    }
}

/** Cada botón ocupa poco menos de la mitad del cuadrado, para que quepan los cuatro sin solaparse. */
private const val ACTION_FRACTION = 0.46f
