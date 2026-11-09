package com.moralesDev.dsForEveryone.ui.files

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import okio.Sink
import okio.Source
import okio.sink
import okio.source

/** Un documento de SAF: su `Uri` y el `ContentResolver` que sabe abrirlo. */
private class SafDocument(
    private val contentResolver: ContentResolver,
    private val uri: Uri,
) : PickedDocument {
    override fun openSink(): Sink? = contentResolver.openOutputStream(uri)?.sink()

    override fun openSource(): Source? = contentResolver.openInputStream(uri)?.source()
}

@Composable
actual fun rememberCreateDocument(
    mimeType: String,
    onResult: (PickedDocument?) -> Unit,
): (suggestedName: String) -> Unit {
    val contentResolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType),
        onResult = { uri -> onResult(uri?.let { SafDocument(contentResolver, it) }) },
    )
    return { suggestedName -> launcher.launch(suggestedName) }
}

@Composable
actual fun rememberOpenDocument(
    onResult: (PickedDocument?) -> Unit,
): () -> Unit {
    val contentResolver = LocalContext.current.contentResolver
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> onResult(uri?.let { SafDocument(contentResolver, it) }) },
    )
    return { launcher.launch(arrayOf("*/*")) }
}
