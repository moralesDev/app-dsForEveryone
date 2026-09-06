package com.moralesDev.dsForEveryone.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.ui.components.AppLogo
import com.moralesDev.dsForEveryone.ui.components.BrandWordmark
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Inter
import com.moralesDev.dsForEveryone.ui.theme.Neutral900
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import kotlinx.coroutines.delay

/**
 * Pantalla de arranque.
 *
 * Es la continuación de la que pinta el sistema: mismo fondo y mismo símbolo,
 * de modo que al abrir la aplicación no se ve un salto sino que la marca crece
 * y aparece el nombre. Se va sola pasados [DURATION_MS].
 *
 * No hace trabajo real —los ajustes ya se están cargando detrás—, así que su
 * duración es puramente de presentación.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)

    val entrance = remember { Animatable(0f) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entrance.animateTo(1f, tween(500, easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
    }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(DURATION_MS.toInt() - 400, easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        delay(DURATION_MS)
        finish()
    }

    // El halo respira: es lo único que se mueve mientras se espera.
    val glow by rememberInfiniteTransition(label = "glow").animateFloat(
        initialValue = 0.5f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow-alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.16f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.38f),
                        radius = size.width * 0.9f,
                    ),
                )
            }
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .alpha(glow)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Accent.copy(alpha = 0.34f),
                                        Color.Transparent,
                                    ),
                                ),
                            )
                        },
                )
                AppLogo(
                    size = 112.dp,
                    modifier = Modifier
                        .scale(0.86f + 0.14f * entrance.value)
                        .alpha(entrance.value),
                )
            }

            Column(
                modifier = Modifier
                    .padding(top = 26.dp)
                    .alpha(entrance.value),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BrandWordmark(fontSize = 22.sp)
                Text(
                    text = stringResource(R.string.splash_tagline).uppercase(),
                    style = TextStyle(
                        fontFamily = Inter,
                        fontSize = 11.sp,
                        letterSpacing = 0.18.em,
                    ),
                    color = textAlpha(0.45f),
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Neutral900),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.value)
                        .height(2.dp)
                        .background(Accent),
                )
            }
            Text(
                text = stringResource(R.string.splash_credit),
                style = TextStyle(fontFamily = Inter, fontSize = 10.5f.sp),
                color = textAlpha(0.32f),
            )
        }
    }
}

/** Lo que tarda en apartarse. Coincide con la animación del prototipo. */
private const val DURATION_MS = 1_900L
