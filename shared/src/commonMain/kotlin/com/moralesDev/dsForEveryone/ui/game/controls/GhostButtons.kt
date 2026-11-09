package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.ui.theme.Accent100

/** L y R: gatillos finos y apaisados, llenan el hueco que les da `ControlLayout`. */
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
    GhostSurface(
        shape = RoundedCornerShape(50),
        pressed = pressedMask and button.mask != 0,
        opacity = opacity,
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label }
            .pressable(haptic = haptic) { isPressed -> onPress(button, isPressed) },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, shadow = ghostShadow(opacity)),
            color = ghostContentColor(opacity),
        )
    }
}

/**
 * Start y Select: una pastilla pequeña con el nombre **debajo**, como están
 * serigrafiados en la consola, en vez de dentro —«SELECT» no cabe legible
 * dentro de una pastilla de este tamaño—.
 *
 * Toda la caja, nombre incluido, es el botón: el nombre es parte de lo que
 * se puede pulsar, así el objetivo no es sólo la pastilla.
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = label }
            .pressable(haptic = haptic) { isPressed -> onPress(button, isPressed) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        GhostSurface(
            shape = RoundedCornerShape(50),
            pressed = pressedMask and button.mask != 0,
            opacity = opacity,
            modifier = Modifier
                .fillMaxWidth(PILL_WIDTH_FRACTION)
                .fillMaxHeight(PILL_HEIGHT_FRACTION),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 8.sp,
                letterSpacing = 0.6.sp,
                shadow = ghostShadow(opacity),
            ),
            color = ghostContentColor(opacity),
            maxLines = 1,
        )
    }
}

/**
 * El botón de soplar: mientras está pulsado, [onBlow] recibe `true` e
 * [com.moralesDev.dsForEveryone.emulator.InputManager.setMicNoise] hace que
 * el micrófono simulado del core oiga la grabación de un soplido. Sin
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
        if (active) Accent100 else ghostContentColor(opacity),
        label = "blow-icon-tint",
    )

    GhostSurface(
        shape = CircleShape,
        pressed = active,
        opacity = opacity,
        modifier = modifier
            .fillMaxSize()
            .semantics { this.contentDescription = contentDescription }
            .pressable(haptic = haptic, onPressChange = onBlow),
    ) {
        GhostIcon(
            imageVector = Icons.Filled.Air,
            tint = iconTint,
            opacity = opacity,
            modifier = Modifier.fillMaxSize(0.6f),
        )
    }
}

private const val PILL_WIDTH_FRACTION = 0.8f
private const val PILL_HEIGHT_FRACTION = 0.42f
