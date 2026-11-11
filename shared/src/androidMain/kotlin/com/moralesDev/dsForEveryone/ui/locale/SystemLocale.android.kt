package com.moralesDev.dsForEveryone.ui.locale

import androidx.compose.runtime.compositionLocalOf
import java.util.Locale

/**
 * Región real del teléfono —la que trae la `Configuration` del sistema antes
 * de que [ProvideAppLanguage] la sustituya por la elegida en la aplicación—,
 * para lo único que no debe seguir al idioma de la app: si el reloj se
 * escribe en 12 o en 24 horas.
 *
 * El valor por defecto sólo se usaría si algo compusiera fuera del árbol de
 * [ProvideAppLanguage], que no ocurre en esta aplicación: `MainActivity`
 * envuelve toda la navegación con él.
 */
// En :shared (androidMain) porque lo lee SaveTimeFormat.android.kt; lo
// provee ProvideAppLanguage, que sigue en :app.
val LocalSystemLocale = compositionLocalOf<Locale> { Locale.getDefault() }
