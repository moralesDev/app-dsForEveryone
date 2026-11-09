package com.moralesDev.dsForEveryone.ui.files

import androidx.compose.runtime.Composable
import okio.Sink
import okio.Source

/**
 * Un documento elegido por el usuario en el selector de archivos del
 * sistema. No es una ruta: en Android es un `Uri` de SAF y en iOS una URL
 * con permisos de acceso propios; lo único que la app hace con él es abrirlo.
 */
interface PickedDocument {
    /** Para escribir en él (lo que se acaba de crear). `null` si el sistema no lo da. */
    fun openSink(): Sink?

    /** Para leerlo (lo que se acaba de abrir). `null` si el sistema no lo da. */
    fun openSource(): Source?
}

/**
 * El selector para **crear** un documento: el usuario elige dónde y con qué
 * nombre. Devuelve la función que lo abre, con el nombre propuesto; el
 * resultado llega por [onResult], `null` si el usuario lo cancela.
 *
 * Sustituye a `ActivityResultContracts.CreateDocument` en el código común.
 */
@Composable
expect fun rememberCreateDocument(
    mimeType: String,
    onResult: (PickedDocument?) -> Unit,
): (suggestedName: String) -> Unit

/**
 * El selector para **abrir** un documento existente, de cualquier tipo: la
 * app comprueba lo que es al leerlo. Sustituye a
 * `ActivityResultContracts.OpenDocument`.
 */
@Composable
expect fun rememberOpenDocument(
    onResult: (PickedDocument?) -> Unit,
): () -> Unit
