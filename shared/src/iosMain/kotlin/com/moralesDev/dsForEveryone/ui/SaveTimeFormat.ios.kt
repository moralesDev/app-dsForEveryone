package com.moralesDev.dsForEveryone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970

/**
 * Lo mismo que en Android: estilo medio para la fecha y corto para la hora,
 * con el idioma de la app y la región del teléfono (que decide 12 o 24
 * horas). El idioma sale de `Locale.current`, que sigue al elegido en la app.
 */
@Composable
actual fun rememberSaveTimeFormatter(): (Long) -> String {
    val language = Locale.current.language
    return remember(language) {
        val region = NSLocale.currentLocale.countryCode
        val formatter = NSDateFormatter().apply {
            locale = NSLocale(localeIdentifier = if (region != null) "${language}_$region" else language)
            dateStyle = NSDateFormatterMediumStyle
            timeStyle = NSDateFormatterShortStyle
        }
        val format: (Long) -> String = { savedAt ->
            formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(savedAt / 1000.0))
        }
        format
    }
}
