package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.runtime.Composable

/**
 * Si la pantalla está en vertical: decide la disposición de las pantallas y
 * si los mandos van encima de la inferior. Una por plataforma: en Android,
 * la orientación de la `Configuration`, como siempre; en iOS, el alto y el
 * ancho de la ventana, que es lo que importa también en Split View del iPad.
 */
@Composable
expect fun rememberIsPortrait(): Boolean
