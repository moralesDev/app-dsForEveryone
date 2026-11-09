package com.moralesDev.dsForEveryone.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import com.moralesDev.dsForEveryone.data.settings.AppLanguage
import platform.Foundation.NSUserDefaults

/**
 * En iOS, `Locale.current` de Compose sale de `NSLocale.preferredLanguages`,
 * que para la propia app manda la preferencia `AppleLanguages`. Escribirla
 * cambia el idioma de los textos al recomponer —la `key`— y además lo deja
 * fijado para el próximo arranque. Es el método que documenta JetBrains para
 * cambiar el idioma en tiempo de ejecución.
 */
@Composable
actual fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    remember(language) {
        NSUserDefaults.standardUserDefaults.setObject(listOf(language.tag), forKey = "AppleLanguages")
    }
    key(language) { content() }
}
