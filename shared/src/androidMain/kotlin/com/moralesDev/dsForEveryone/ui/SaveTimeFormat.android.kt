package com.moralesDev.dsForEveryone.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import com.moralesDev.dsForEveryone.ui.locale.LocalSystemLocale
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Cómo se escribe el momento en que se guardó un save state.
 *
 * Dos reglas, y las dos importan:
 *
 * **Siempre fecha y hora.** Nunca sólo la hora, ni «Hoy, 20:42». Un save state
 * se compara con otros para decidir cuál recuperar, y «8:42 p. m.» no distingue
 * el de esta tarde del de la semana pasada. La fecha es lo que hace que la
 * información sirva para elegir.
 *
 * **El formato no se escribe a mano.** Nada de `"d MMM · HH:mm"`: el orden de
 * los campos, el nombre del mes y si el reloj es de 12 o de 24 horas son
 * decisiones del idioma, no del programador. Se delega en
 * [DateTimeFormatter.ofLocalizedDateTime], que da «15 ago 2026, 8:42 p. m.» en
 * español y «Aug 15, 2026, 8:42 PM» en inglés sin que aquí haya una sola letra
 * de formato.
 *
 * El valor de partida es siempre `savedAt` del contenedor E4FS, en milisegundos
 * desde época y en UTC. Se convierte a la zona del teléfono sólo para pintarlo;
 * lo que hay dentro del archivo no se toca nunca, y es el mismo número con el
 * que se ordenan los estados para saber cuál es el último.
 */
object SaveTimeFormat {

    fun formatter(locale: Locale, zone: ZoneId): DateTimeFormatter =
        DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)

    fun format(savedAt: Long, locale: Locale, zone: ZoneId): String =
        formatter(locale, zone).format(Instant.ofEpochMilli(savedAt))

    /**
     * Combina el idioma elegido en la aplicación con la región del teléfono.
     *
     * El selector de idioma sólo dice «español» o «inglés», sin país, y el país
     * es justo lo que decide si el reloj va en 12 o en 24 horas. Tomando la
     * región del sistema cuando el idioma coincide, quien tiene el teléfono en
     * español de Colombia sigue leyendo «8:42 p. m.» y no «20:42», que es lo que
     * espera ver. Si el idioma no coincide, manda el de la aplicación.
     */
    fun resolveLocale(appLocale: Locale, systemLocale: Locale): Locale =
        if (appLocale.language == systemLocale.language && systemLocale.country.isNotEmpty()) {
            systemLocale
        } else {
            appLocale
        }
}

/**
 * Formateador listo para usar en Compose, atado al idioma vigente de la
 * aplicación. Se recalcula solo cuando ese idioma cambia.
 *
 * Los dos locales se leen al pintar, no una sola vez al construir la función:
 * [LocalConfiguration] ya viene sustituido por `ProvideAppLanguage` con el
 * idioma elegido en la app, y [LocalSystemLocale] es la región real del
 * teléfono para lo único que no debe cambiar con ese idioma —12 o 24 horas—.
 * Ninguno de los dos sale de `Locale.getDefault()`: ese valor no es
 * observable para Compose, así que un cambio de idioma en caliente no
 * garantizaría volver a pintar esto.
 */
@Composable
actual fun rememberSaveTimeFormatter(): (Long) -> String {
    val appLocale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]!!
    val locale = SaveTimeFormat.resolveLocale(appLocale, LocalSystemLocale.current)
    val formatter = remember(locale) { SaveTimeFormat.formatter(locale, ZoneId.systemDefault()) }
    return remember(formatter) { { savedAt -> formatter.format(Instant.ofEpochMilli(savedAt)) } }
}
