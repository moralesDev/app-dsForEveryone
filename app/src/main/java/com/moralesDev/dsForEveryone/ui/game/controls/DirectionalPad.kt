package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.emulator.DirectionalPadZones
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.GlassStrong
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

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
 * A diferencia de `app-emulador`, el tamaño no es una constante de diseño
 * (150 dp): lo decide `ControlLayout` y esta rejilla de 3×3 con pesos iguales
 * llena entero lo que le den, cuadrado o no.
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

    Column(
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
    ) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Arm(DsButton.UP, rotation = 0f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Arm(DsButton.LEFT, rotation = -90f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
            Hub(opacity, Modifier.weight(1f).fillMaxHeight())
            Arm(DsButton.RIGHT, rotation = 90f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Arm(DsButton.DOWN, rotation = 180f, pressedMask, opacity, Modifier.weight(1f).fillMaxHeight())
            Spacer(Modifier.weight(1f))
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
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (pressed) Accent.copy(alpha = 0.30f) else GlassStrong.copy(alpha = GlassStrong.alpha * opacity),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowDropUp,
            contentDescription = null,
            tint = if (pressed) Accent100 else textAlpha(0.70f * opacity),
            modifier = Modifier
                .fillMaxSize(ARROW_FRACTION)
                .rotate(rotation),
        )
    }
}

@Composable
private fun Hub(opacity: Float, modifier: Modifier) {
    Box(
        modifier = modifier.background(GlassStrong.copy(alpha = GlassStrong.alpha * opacity)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize(HUB_DOT_FRACTION)
                .clip(CircleShape)
                .background(textAlpha(0.22f)),
        )
    }
}

private const val ARROW_FRACTION = 0.55f
private const val HUB_DOT_FRACTION = 0.28f
