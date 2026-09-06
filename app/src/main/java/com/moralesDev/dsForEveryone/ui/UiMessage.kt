package com.moralesDev.dsForEveryone.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Mensaje pendiente de traducir.
 *
 * Los ViewModel no pueden construir texto ya traducido: el idioma se elige
 * dentro de la aplicación y puede cambiar mientras el mensaje sigue en
 * pantalla. Guardan el identificador del recurso y sus argumentos, y es la
 * capa de Compose la que lo resuelve con el idioma vigente en cada momento.
 */
data class UiMessage(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList(),
) {
    constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
}

@Composable
fun UiMessage.resolve(): String = stringResource(resId, *args.toTypedArray())
