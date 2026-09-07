package com.moralesDev.dsForEveryone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Sistema visual Nocturne, tal y como lo define el diseño.
 *
 * Paleta oscura fija: no sigue el tema del sistema a propósito, porque la
 * pantalla del juego es la fuente de luz de la interfaz y un fondo claro
 * compite con ella y falsea los colores del juego.
 */

internal val Background = Color(0xFF161826)
internal val Surface = Color(0xFF232532)

/** Marco de la pantalla del juego: casi negro, para que el borde sea nítido. */
internal val ScreenBezel = Color(0xFF05060C)

internal val TextPrimary = Color(0xFFE9E9ED)

/** Acento blurple. Es lo que da identidad propia a la aplicación. */
internal val Accent = Color(0xFF9184D9)

internal val Accent100 = Color(0xFFF5F4FF)
internal val Accent200 = Color(0xFFE7E5FE)
internal val Accent300 = Color(0xFFD2CEFD)
internal val Accent400 = Color(0xFFB5ABFC)
internal val Accent500 = Color(0xFF968AE0)
internal val Accent600 = Color(0xFF796CBF)
internal val Accent700 = Color(0xFF5D5294)
internal val Accent800 = Color(0xFF423A6A)
internal val Accent900 = Color(0xFF2B2741)

internal val Neutral600 = Color(0xFF75798C)
internal val Neutral700 = Color(0xFF595D6C)
internal val Neutral800 = Color(0xFF3F424D)
internal val Neutral900 = Color(0xFF292B31)

/**
 * Vidrio traslúcido: relleno al 7 % y 12 % de blanco, borde al 13 %.
 * Es el material del *chrome* de menú —el botón de pausa, las hojas, los
 * diálogos—. Los mandos de la partida usan el caucho con relieve de más
 * abajo: ver [RubberNeutral] y [RubberAccent].
 */
internal val Glass = Color(0x12E9E9ED)
internal val GlassStrong = Color(0x1FE9E9ED)
internal val GlassBorder = Color(0x21E9E9ED)

/**
 * Caucho oscuro con relieve: la superficie de los siete grupos de mandos
 * (fase 2.8). Bisel superior claro, caída a un centro y un borde oscuros, y
 * un «zócalo» sólido debajo que se hunde al pulsar —ver
 * `RubberButtonStyle.kt`—. Sustituye aquí al vidrio traslúcido porque un
 * mando físico se lee mejor con relieve que con transparencia.
 *
 * [RubberAccent] es la misma estructura con una paleta violeta, reservada
 * para el botón A: es el que más se pulsa y el que necesita distinguirse a
 * simple vista del resto del rombo.
 */
internal val RubberTop = Color(0xFF565B62)
internal val RubberMid = Color(0xFF33373D)
internal val RubberBottom = Color(0xFF181B1F)
internal val RubberBorder = Color(0xFF07080A)
internal val RubberLedge = Color(0xFF0A0B0D)
internal val RubberText = Color(0xFFC7CCD8)

internal val RubberAccentTop = Color(0xFF6A6785)
internal val RubberAccentMid = Color(0xFF3A3A49)
internal val RubberAccentBottom = Color(0xFF1B1A24)
internal val RubberAccentBorder = Color(0xFF0B0A12)
internal val RubberAccentLedge = Color(0xFF0D0C14)
internal val RubberAccentText = Color(0xFFE7E4FF)

internal val Divider = Color(0x29E9E9ED)

/** Acciones destructivas o de salida: rosa apagado, nunca rojo puro. */
internal val Danger = Color(0xFFE6A8B6)
internal val DangerIcon = Color(0xFFE9A5B4)
internal val DangerIconBackground = Color(0xFF3A2130)

/** Velo de los diálogos y hojas: rgba(8, 9, 15, .62) sobre el juego pausado. */
internal val ScrimColor = Color(0x9E08090F)

/** Texto secundario: el mismo color de texto a distintas opacidades. */
internal fun textAlpha(alpha: Float) = TextPrimary.copy(alpha = alpha)
