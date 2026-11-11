package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberPxPerMm(): PxPerMm {
    val metrics = LocalContext.current.resources.displayMetrics
    return remember(metrics.xdpi, metrics.ydpi, metrics.densityDpi) {
        val fallback = metrics.densityDpi.toFloat()
        fun sane(dpi: Float) = if (dpi in fallback * 0.5f..fallback * 1.5f) dpi else fallback
        PxPerMm(x = sane(metrics.xdpi) / MM_PER_INCH, y = sane(metrics.ydpi) / MM_PER_INCH)
    }
}

