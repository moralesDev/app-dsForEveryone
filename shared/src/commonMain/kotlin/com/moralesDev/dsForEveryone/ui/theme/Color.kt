package com.moralesDev.dsForEveryone.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Sistema visual Nocturne, tal y como lo define el diseño.
 *
 * Paleta oscura fija: no sigue el tema del sistema a propósito, porque la
 * pantalla del juego es la fuente de luz de la interfaz y un fondo claro
 * compite con ella y falsea los colores del juego.
 */

val Background = Color(0xFF161826)
val Surface = Color(0xFF232532)

/** Marco de la pantalla del juego: casi negro, para que el borde sea nítido. */
val ScreenBezel = Color(0xFF05060C)

val TextPrimary = Color(0xFFE9E9ED)

/** Acento blurple. Es lo que da identidad propia a la aplicación. */
val Accent = Color(0xFF9184D9)

val Accent100 = Color(0xFFF5F4FF)
val Accent200 = Color(0xFFE7E5FE)
val Accent300 = Color(0xFFD2CEFD)
val Accent400 = Color(0xFFB5ABFC)
val Accent500 = Color(0xFF968AE0)
val Accent600 = Color(0xFF796CBF)
val Accent700 = Color(0xFF5D5294)
val Accent800 = Color(0xFF423A6A)
val Accent900 = Color(0xFF2B2741)

val Neutral600 = Color(0xFF75798C)
val Neutral700 = Color(0xFF595D6C)
val Neutral800 = Color(0xFF3F424D)
val Neutral900 = Color(0xFF292B31)

/**
 * Vidrio traslúcido: relleno al 7 % y 12 % de blanco, borde al 13 %.
 * Es el material del *chrome* de menú —el botón de pausa, las hojas, los
 * diálogos—. Los mandos de la partida no: son transparentes con borde
 * claro, ver `GhostButtonStyle.kt`.
 */
val Glass = Color(0x12E9E9ED)
val GlassStrong = Color(0x1FE9E9ED)
val GlassBorder = Color(0x21E9E9ED)

/**
 * Vidrio sobre el vídeo del juego: el mismo material, invertido.
 *
 * El vidrio de arriba es blanco traslúcido, y funciona porque debajo siempre
 * hay fondo oscuro de la aplicación. Los dos botones que flotan sobre la
 * imagen del juego —menú y avance rápido— no tienen esa garantía: la imagen
 * la pone el juego, y con un fondo claro —un menú de Pokémon, una pantalla de
 * carga en blanco— un botón claro sobre fondo claro desaparece.
 *
 * Por eso aquí el relleno es el fondo de la aplicación casi opaco y el borde
 * es blanco al 35 %: se lee sobre negro y sobre blanco, que es lo único que
 * hay que garantizar cuando lo que hay debajo no lo decide la aplicación.
 */
val GlassOverVideo = Color(0xD9161826)
val GlassOverVideoStrong = Color(0xF2232532)
val GlassOverVideoBorder = Color(0x59F5F4FF)

val Divider = Color(0x29E9E9ED)

/** Acciones destructivas o de salida: rosa apagado, nunca rojo puro. */
val Danger = Color(0xFFE6A8B6)
val DangerIcon = Color(0xFFE9A5B4)
val DangerIconBackground = Color(0xFF3A2130)

/** Velo de los diálogos y hojas: rgba(8, 9, 15, .62) sobre el juego pausado. */
val ScrimColor = Color(0x9E08090F)

/** Texto secundario: el mismo color de texto a distintas opacidades. */
fun textAlpha(alpha: Float) = TextPrimary.copy(alpha = alpha)
