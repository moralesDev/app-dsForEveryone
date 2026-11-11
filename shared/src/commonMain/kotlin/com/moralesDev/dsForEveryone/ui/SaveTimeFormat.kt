package com.moralesDev.dsForEveryone.ui

import androidx.compose.runtime.Composable

/**
 * Formateador de la fecha de una ranura, listo para usar en Compose y atado
 * al idioma vigente de la aplicación: «15 ago 2026, 8:42 p. m.» en español,
 * «Aug 15, 2026, 8:42 PM» en inglés. El orden de los campos, el nombre del
 * mes y si el reloj va en 12 o en 24 horas los decide el idioma (y la región
 * del teléfono), nunca un patrón escrito aquí.
 *
 * Recibe `savedAt` del contenedor E4FS, en milisegundos desde 1970 en UTC, y
 * lo pinta en la zona del teléfono. Cada plataforma usa su formateador:
 * `java.time` en Android, `NSDateFormatter` en iOS.
 */
@Composable
expect fun rememberSaveTimeFormatter(): (Long) -> String
