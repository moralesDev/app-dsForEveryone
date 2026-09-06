package com.moralesDev.dsForEveryone.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent900
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Inter
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Pantalla de carga entre pantallas.
 *
 * Es opaca a propósito, y por eso funciona. La pantalla de juego (fase 2) usa
 * un renderizador GL que es una capa propia del compositor por debajo de la
 * ventana; al salir del juego esa capa se destruye, la vista se desmonta, el
 * core libera la ROM y suelta la superficie, y la biblioteca compone su
 * primer fotograma. Nada de eso es instantáneo, y verlo ocurrir parece que la
 * pantalla se estropea. Tapándolo con algo estable, el mismo trabajo se lee
 * como una espera normal.
 *
 * No es un adorno que se pinta encima y ya: quien la muestra
 * ([com.moralesDev.dsForEveryone.ui.navigation.ScreenTransitions]) se asegura
 * de que esté **dibujada** antes de tocar nada, y de retirarla sólo cuando el
 * destino ya ha pintado.
 *
 * Se traga los toques mientras está visible. Durante el cambio de pantalla no
 * hay nada debajo a lo que un toque deba llegar, y dejarlos pasar permitiría
 * pulsar a ciegas un botón de la pantalla que se está yendo.
 */
@Composable
fun LoadingVeil(modifier: Modifier = Modifier) {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(LOADING_VEIL_FADE_MS, easing = LinearEasing))
    }

    val spin by rememberInfiniteTransition(label = "veil").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing), RepeatMode.Restart),
        label = "veil-spin",
    )

    val label = stringResource(R.string.tr_loading)

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(entrance.value)
            .background(Background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.44f),
                        radius = size.width * 0.83f,
                    ),
                )
            }
            // Absorbe todo lo que se toque mientras dura.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
            .semantics { contentDescription = label }
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(RING_SIZE)) {
                    val width = RING_STROKE.toPx()
                    val inset = width / 2f
                    drawCircle(
                        color = Accent900,
                        radius = size.minDimension / 2f - inset,
                        style = Stroke(width = width),
                    )
                    // Un cuarto de vuelta encendido, girando: es el
                    // `border-top-color` del prototipo.
                    drawArc(
                        color = Accent,
                        startAngle = spin - 90f,
                        sweepAngle = 90f,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - width, size.height - width),
                        style = Stroke(width = width, cap = StrokeCap.Round),
                    )
                }
                AppLogo(size = 44.dp)
            }

            Text(
                text = label.uppercase(),
                style = TextStyle(
                    fontFamily = Inter,
                    fontSize = 12.5f.sp,
                    letterSpacing = 0.12.em,
                ),
                color = textAlpha(0.42f),
            )
        }
    }
}

/**
 * Coincide con la animación `emuFade` del prototipo.
 *
 * Es público dentro del módulo porque quien lo muestra necesita saberlo: hasta
 * que el fundido no termina el velo es translúcido, y lo que ocurra debajo se
 * ve. Ver `ScreenTransitions`.
 */
internal const val LOADING_VEIL_FADE_MS = 180

private val RING_SIZE = 78.dp
private val RING_STROKE = 2.dp
