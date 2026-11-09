package com.moralesDev.dsForEveryone.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Locale.getDefault() es el idioma de la app con la región del teléfono: lo
// fija ProvideAppLanguage al cambiar de idioma.

actual fun formatShortTime(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

actual fun formatMediumDate(epochMillis: Long): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.getDefault())
        .format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

actual fun formatDecimal(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

actual fun argbImageBitmap(argb: IntArray, size: Int): ImageBitmap =
    Bitmap.createBitmap(argb, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
