package com.moralesDev.dsForEveryone.ui.game.controls

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Convierte un composable en un botón físico: se pulsa al bajar el dedo y se
 * suelta al levantarlo, sin ninguna noción de "clic".
 *
 * Un `clickable` no sirve aquí. Un mando necesita el flanco de bajada y el de
 * subida por separado, mantener la pulsación indefinidamente y, sobre todo,
 * admitir varios dedos a la vez. Compose entrega cada puntero al nodo sobre el
 * que bajó, así que cada botón con este modificador sigue su propio dedo: A +
 * derecha, L + A o B + abajo funcionan sin tratamiento especial.
 *
 * El `finally` es lo que evita el fallo clásico de los mandos táctiles: si el
 * gesto se cancela (llega una llamada, aparece un diálogo), el botón se suelta
 * igualmente en lugar de quedarse pegado.
 */
fun Modifier.pressable(
    haptic: Boolean = true,
    onPressChange: (Boolean) -> Unit,
): Modifier = composed {
    val hapticFeedback = LocalHapticFeedback.current
    pointerInput(haptic) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            onPressChange(true)
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                }
            } finally {
                onPressChange(false)
            }
        }
    }
}

/**
 * Variante para la cruceta: además de pulsar/soltar informa de la posición del
 * dedo dentro del control, para poder resolver diagonales y el deslizamiento de
 * una dirección a otra sin levantar el dedo.
 */
@Composable
fun Modifier.pressableWithPosition(
    haptic: Boolean = true,
    onPositionChange: (Offset?, Size) -> Unit,
): Modifier {
    val hapticFeedback = LocalHapticFeedback.current
    return this.pointerInput(haptic) {
        awaitEachGesture {
            val bounds = Size(size.width.toFloat(), size.height.toFloat())
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            onPositionChange(down.position, bounds)
            if (haptic) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.VirtualKey)
            }
            try {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    change.consume()
                    onPositionChange(change.position, bounds)
                }
            } finally {
                onPositionChange(null, bounds)
            }
        }
    }
}
