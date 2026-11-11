package com.moralesDev.dsForEveryone.ui.locale

import androidx.compose.runtime.Composable
import com.moralesDev.dsForEveryone.data.settings.AppLanguage

/**
 * Cambia el idioma de la aplicación en caliente, sin tocar el del sistema ni
 * reiniciar nada. Una por plataforma (ver sus `actual`): lo común es que
 * los textos de Compose Multiplatform eligen por el idioma del proceso
 * (`Locale.current`), así que cada una lo cambia a su manera y rehace la
 * interfaz con una `key` por idioma.
 */
@Composable
expect fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
)
