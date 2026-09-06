package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
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
    GlassSurface(
        pressed = pressedMask and button.mask != 0,
        label = label,
        opacity = opacity,
        haptic = haptic,
        shape = RoundedCornerShape(9.dp),
        onPress = { isPressed -> onPress(button, isPressed) },
        modifier = modifier,
    )
}

/** Start y Select: cápsulas discretas. */
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
    GlassSurface(
        pressed = pressedMask and button.mask != 0,
        label = label,
        opacity = opacity,
        haptic = haptic,
        shape = RoundedCornerShape(50),
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
    val scale by animateFloatAsState(if (active) 0.92f else 1f, label = "blow-scale")
    val background by animateColorAsState(
        if (active) Accent.copy(alpha = 0.30f * opacity) else Glass.copy(alpha = Glass.alpha * opacity),
        label = "blow-background",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clip(CircleShape)
            .background(background)
            .border(1.dp, GlassBorder.copy(alpha = GlassBorder.alpha * opacity), CircleShape)
            .semantics { this.contentDescription = contentDescription }
            .pressable(haptic = haptic, onPressChange = onBlow),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Air,
            contentDescription = null,
            tint = if (active) Accent100 else textAlpha(0.65f * opacity),
            modifier = Modifier.fillMaxSize(0.5f),
        )
    }
}

@Composable
private fun GlassSurface(
    pressed: Boolean,
    label: String,
    opacity: Float,
    haptic: Boolean,
    shape: Shape,
    onPress: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "glass-scale")
    val background by animateColorAsState(
        if (pressed) Accent.copy(alpha = 0.26f * opacity) else Glass.copy(alpha = Glass.alpha * opacity),
        label = "glass-background",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .scale(scale)
            .clip(shape)
            .background(background)
            .border(1.dp, GlassBorder.copy(alpha = GlassBorder.alpha * opacity), shape)
            .semantics { contentDescription = label }
            .pressable(haptic = haptic, onPressChange = onPress),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (pressed) Accent200 else textAlpha(0.72f * opacity),
        )
    }
}
