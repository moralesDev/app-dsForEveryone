package com.moralesDev.dsForEveryone.ui

import androidx.compose.ui.graphics.ImageBitmap

/*
 * Lo que se escribe distinto según el idioma —la hora, la fecha, la coma
 * decimal— y lo que se pinta a partir de píxeles crudos. Cada plataforma usa
 * su formateador (java.time en Android, NSDateFormatter/NSNumberFormatter en
 * iOS), con el idioma de la app y la región del teléfono; aquí no hay ni una
 * letra de formato escrita a mano.
 */

/** La hora en formato corto («8:42 p. m.», «20:42»), en la zona del teléfono. */
expect fun formatShortTime(epochMillis: Long): String

/** La fecha en formato medio («15 ago 2026»), en la zona del teléfono. */
expect fun formatMediumDate(epochMillis: Long): String

/** [value] con [decimals] decimales y el separador del idioma («1,5» / «1.5»). */
expect fun formatDecimal(value: Double, decimals: Int): String

/** Un icono cuadrado de [size] px a partir de píxeles 0xAARRGGBB (el del banner de la ROM). */
expect fun argbImageBitmap(argb: IntArray, size: Int): ImageBitmap
