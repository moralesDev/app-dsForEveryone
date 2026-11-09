package com.moralesDev.dsForEveryone.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.preferredLanguages

/**
 * El idioma de la app (el primero de las preferencias, que es lo que fija
 * ProvideAppLanguage en AppleLanguages) con la región del teléfono.
 */
private fun appLocale(): NSLocale {
    val language = (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-') ?: "en"
    val region = NSLocale.currentLocale.countryCode
    return NSLocale(localeIdentifier = if (region != null) "${language}_$region" else language)
}

private fun format(epochMillis: Long, dateStyle: ULong, timeStyle: ULong): String =
    NSDateFormatter().apply {
        locale = appLocale()
        this.dateStyle = dateStyle
        this.timeStyle = timeStyle
    }.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

actual fun formatShortTime(epochMillis: Long): String =
    format(epochMillis, NSDateFormatterNoStyle, NSDateFormatterShortStyle)

actual fun formatMediumDate(epochMillis: Long): String =
    format(epochMillis, NSDateFormatterMediumStyle, NSDateFormatterNoStyle)

actual fun formatDecimal(value: Double, decimals: Int): String =
    NSNumberFormatter().apply {
        locale = appLocale()
        numberStyle = NSNumberFormatterDecimalStyle
        minimumFractionDigits = decimals.toULong()
        maximumFractionDigits = decimals.toULong()
        usesGroupingSeparator = false
    }.stringFromNumber(NSNumber(double = value)) ?: value.toString()

/** Skia en iOS: los píxeles se le dan como bytes B, G, R, A. */
actual fun argbImageBitmap(argb: IntArray, size: Int): ImageBitmap {
    val bytes = ByteArray(size * size * 4)
    for (i in 0 until size * size) {
        val pixel = argb[i]
        bytes[i * 4] = pixel.toByte() // B
        bytes[i * 4 + 1] = (pixel shr 8).toByte() // G
        bytes[i * 4 + 2] = (pixel shr 16).toByte() // R
        bytes[i * 4 + 3] = (pixel ushr 24).toByte() // A
    }
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(size, size, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL))
    bitmap.installPixels(bytes)
    return bitmap.asComposeImageBitmap()
}
