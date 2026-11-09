package com.moralesDev.dsForEveryone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DsEmuColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Accent100,
    primaryContainer = Accent900,
    onPrimaryContainer = Accent200,
    secondary = Accent400,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = Neutral900,
    onSurfaceVariant = TextPrimary.copy(alpha = 0.55f),
    outline = GlassBorder,
    outlineVariant = Divider,
    error = Danger,
    onError = Background,
    scrim = ScrimColor,
)

@Composable
fun DsEmuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DsEmuColorScheme,
        typography = DsEmuTypography,
        content = content,
    )
}
