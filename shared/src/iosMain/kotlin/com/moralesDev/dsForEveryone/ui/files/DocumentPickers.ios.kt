package com.moralesDev.dsForEveryone.ui.files

import androidx.compose.runtime.Composable
import com.moralesDev.dsForEveryone.util.AppLog

/*
 * PENDIENTE (docs/ios.md, iOS-3 paso 5): los selectores de iOS.
 *
 * Abrir es directo (UIDocumentPickerViewController para abrir, con acceso
 * de seguridad a la URL). Crear funciona al revés que en Android: en iOS no
 * se elige primero dónde guardar, sino que se escribe el archivo y después
 * se ofrece al sistema (UIDocumentPickerViewController para exportar). Se
 * hará junto con el arranque de la app en iOS, que es cuando se puede
 * probar. Hasta entonces los botones no hacen nada y lo dicen en el registro.
 */

@Composable
actual fun rememberCreateDocument(
    mimeType: String,
    onResult: (PickedDocument?) -> Unit,
): (suggestedName: String) -> Unit = { suggestedName ->
    AppLog.w(TAG, "Crear documentos aún no está hecho en iOS ($suggestedName)")
    onResult(null)
}

@Composable
actual fun rememberOpenDocument(
    onResult: (PickedDocument?) -> Unit,
): () -> Unit = {
    AppLog.w(TAG, "Abrir documentos aún no está hecho en iOS")
    onResult(null)
}

private const val TAG = "DocumentPickers"
