package com.moralesDev.dsForEveryone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moralesDev.dsForEveryone.R

/**
 * Inter, la tipografía del sistema de diseño.
 *
 * Se usa el archivo variable y se fija el eje de peso por variante, en vez de
 * empaquetar un archivo por grosor: un solo recurso de 876 KB cubre todos los
 * pesos y evita que el sistema falsee la negrita sintéticamente.
 */
private fun interFont(weight: Int) = Font(
    resId = R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

internal val Inter = FontFamily(
    interFont(400),
    interFont(500),
    interFont(600),
)

/**
 * Escala del diseño. Los tamaños salen del sistema visual documentado:
 * 27 para el título de pantalla, 18-19 para secciones, 13.5 para interfaz,
 * 11.5 para metadatos y 10 para los rótulos en versalitas.
 */
internal val DsEmuTypography = Typography(
    // "Mis juegos"
    headlineSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 27.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.02).em,
    ),
    // Cabeceras de pantalla secundaria y de hoja
    titleLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.015).em,
    ),
    // Nombre de juego, filas destacadas
    titleMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 14.5f.sp,
        lineHeight = 18.sp,
    ),
    // Etiqueta de botón
    titleSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5f.sp,
        lineHeight = 17.sp,
    ),
    // Texto de interfaz
    bodyMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5f.sp,
        lineHeight = 21.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5f.sp,
        lineHeight = 19.sp,
    ),
    // Metadatos: última partida, tamaño, fecha
    labelMedium = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5f.sp,
        lineHeight = 15.sp,
    ),
    // Rótulo de sección en versalitas
    labelLarge = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.16.em,
    ),
    labelSmall = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 10.5f.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.12.em,
    ),
)
