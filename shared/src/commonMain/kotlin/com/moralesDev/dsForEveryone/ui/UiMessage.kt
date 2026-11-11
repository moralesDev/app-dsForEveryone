package com.moralesDev.dsForEveryone.ui

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Mensaje pendiente de traducir.
 *
 * Los ViewModel no pueden construir texto ya traducido: el idioma se elige
 * dentro de la aplicación y puede cambiar mientras el mensaje sigue en
 * pantalla. Guardan el texto por traducir y sus argumentos, y es la capa de
 * Compose la que lo resuelve con el idioma vigente en cada momento.
 *
 * Desde iOS-3 (docs/ios.md) el texto es un `StringResource` de Compose
 * Multiplatform (`Res.string.…`) y no un `@StringRes Int` de Android: es lo
 * que permite que este mismo mensaje sirva en iOS.
 */
data class UiMessage(
    val resource: StringResource,
    val args: List<Any> = emptyList(),
) {
    constructor(resource: StringResource, vararg args: Any) : this(resource, args.toList())
}

@Composable
fun UiMessage.resolve(): String = stringResource(resource, *args.toTypedArray())
