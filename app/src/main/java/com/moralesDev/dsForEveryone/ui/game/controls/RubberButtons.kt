package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/** L y R: gatillos rectangulares, llenan el hueco que les da `ControlLayout`. */
@Composable
fun ShoulderButton(
    button: DsButton,
    label: String,
    pressedMask: Int,
    opacity: Float,
    haptic: Boolean,
    onPress: (DsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    RubberLabelSurface(
        pressed = pressedMask and button.mask != 0,
        label = label,
        opacity = opacity,
        haptic = haptic,
        shape = RoundedCornerShape(14.dp),
        onPress = { isPressed -> onPress(button, isPressed) },
        modifier = modifier,
    )
}

/**
 * Start y Select: cápsulas discretas. Son el grupo más pequeño que coloca
 * `ControlLayout` (exactamente el mínimo táctil), así que su etiqueta va en
 * [MaterialTheme.typography.labelSmall], no en el `titleSmall` de L/R: con
 * una sola letra `titleSmall` cabe de sobra, pero «SELECT» en un círculo de
 * 48dp con ese tamaño se parte en dos líneas.
 */
@Composable
fun SystemButton(
    button: DsButton,
    label: String,
    pressedMask: Int,
    opacity: Float,
    haptic: Boolean,
    onPress: (DsButton, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    RubberLabelSurface(
        pressed = pressedMask and button.mask != 0,
        label = label,
        opacity = opacity,
        haptic = haptic,
        shape = RoundedCornerShape(50),
        textStyle = MaterialTheme.typography.labelSmall,
        onPress = { isPressed -> onPress(button, isPressed) },
        modifier = modifier,
    )
}

/**
 * El botón de soplar: mientras está pulsado, [onBlow] recibe `true` e
 * [com.moralesDev.dsForEveryone.emulator.InputManager.setMicNoise] inyecta
 * ruido blanco en la entrada de micrófono simulada del core. Sin
 * `RECORD_AUDIO`: ver docs/entrada.md.
 */
@Composable
fun BlowButton(
    active: Boolean,
    opacity: Float,
    haptic: Boolean,
    contentDescription: String,
    onBlow: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconTint by animateColorAsState(
        if (active) Accent100 else textAlpha(0.65f * opacity),
        label = "blow-icon-tint",
    )

    RubberSurface(
        shape = CircleShape,
        pressed = active,
        opacity = opacity,
        modifier = modifier
            .fillMaxSize()
            .semantics { this.contentDescription = contentDescription }
            .pressable(haptic = haptic, onPressChange = onBlow),
    ) {
        Icon(
            imageVector = Icons.Filled.Air,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.fillMaxSize(0.5f),
        )
    }
}

@Composable
private fun RubberLabelSurface(
    pressed: Boolean,
    label: String,
    opacity: Float,
    haptic: Boolean,
    shape: Shape,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    RubberSurface(
        shape = shape,
        pressed = pressed,
        opacity = opacity,
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label }
            .pressable(haptic = haptic, onPressChange = onPress),
    ) {
        Text(text = label, style = textStyle, color = RubberNeutral.text)
    }
}
