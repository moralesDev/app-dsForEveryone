package com.moralesDev.dsForEveryone.ui.files

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.moralesDev.dsForEveryone.util.AppLog
import okio.Buffer
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIViewController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import platform.darwin.NSObject

/*
 * Los selectores de archivos de iOS (UIDocumentPickerViewController), con la
 * misma forma que en Android: primero se elige, después se lee o se escribe.
 *
 * - Abrir: se elige el archivo y se lee en su sitio, sin copiarlo, con el
 *   permiso de acceso que iOS da a esa URL («security-scoped»). Importa para
 *   las ROMs, que pesan cientos de MB.
 * - Crear: iOS no tiene un «guardar como» al estilo de Android, pero sí deja
 *   elegir una carpeta. Se elige la carpeta y la app crea dentro el archivo
 *   con el nombre propuesto (« (2)», « (3)»… si ya existe). Así el resultado
 *   que ve el usuario —«exportado»— llega cuando el archivo ya está donde él
 *   dijo, igual que en Android.
 *
 * Los permisos de acceso se piden al abrir el flujo y se sueltan al cerrarlo.
 */

@Composable
actual fun rememberCreateDocument(
    mimeType: String,
    onResult: (PickedDocument?) -> Unit,
): (suggestedName: String) -> Unit {
    val callback by rememberUpdatedState(onResult)
    val presenter = remember { PickerPresenter() }
    return { suggestedName ->
        presenter.present(
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false),
        ) { folder -> callback(folder?.let { NewFileInFolder(it, suggestedName) }) }
    }
}

@Composable
actual fun rememberOpenDocument(
    onResult: (PickedDocument?) -> Unit,
): () -> Unit {
    val callback by rememberUpdatedState(onResult)
    val presenter = remember { PickerPresenter() }
    return {
        presenter.present(
            UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeItem), asCopy = false),
        ) { url -> callback(url?.let(::ExistingFile)) }
    }
}

/**
 * Presenta un selector y entrega lo elegido. Guarda el delegado mientras el
 * selector está abierto: UIKit lo tiene como referencia débil, y sin esto
 * el recolector de Kotlin se lo llevaría y nunca llegaría la respuesta.
 */
private class PickerPresenter {
    private var delegate: PickerDelegate? = null

    fun present(picker: UIDocumentPickerViewController, onPicked: (NSURL?) -> Unit) {
        val presenter = topViewController()
        if (presenter == null) {
            AppLog.w(TAG, "No hay ninguna pantalla desde la que abrir el selector")
            onPicked(null)
            return
        }
        val current = PickerDelegate { url ->
            delegate = null
            onPicked(url)
        }
        delegate = current
        picker.delegate = current
        picker.allowsMultipleSelection = false
        presenter.presentViewController(picker, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }
}

private class PickerDelegate(
    private val onPicked: (NSURL?) -> Unit,
) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        onPicked(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onPicked(null)
    }
}

/** Un archivo que ya existe, elegido para leerlo. */
private class ExistingFile(private val url: NSURL) : PickedDocument {
    override fun openSink(): Sink? = null

    override fun openSource(): Source? {
        val access = url.startAccessingSecurityScopedResource()
        val path = url.path?.toPath() ?: return releasing(access, null)
        return try {
            ReleasingSource(FileSystem.SYSTEM.source(path)) { if (access) url.stopAccessingSecurityScopedResource() }
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo abrir $path", error)
            releasing(access, null)
        }
    }

    private fun <T> releasing(access: Boolean, value: T): T {
        if (access) url.stopAccessingSecurityScopedResource()
        return value
    }
}

/** Un archivo nuevo, [name], dentro de la carpeta elegida. */
private class NewFileInFolder(private val folder: NSURL, private val name: String) : PickedDocument {
    override fun openSource(): Source? = null

    override fun openSink(): Sink? {
        val access = folder.startAccessingSecurityScopedResource()
        val directory = folder.path?.toPath()
        if (directory == null) {
            if (access) folder.stopAccessingSecurityScopedResource()
            return null
        }
        return try {
            val target = freeName(directory)
            ReleasingSink(FileSystem.SYSTEM.sink(target, mustCreate = true)) {
                if (access) folder.stopAccessingSecurityScopedResource()
            }
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo crear $name en $directory", error)
            if (access) folder.stopAccessingSecurityScopedResource()
            null
        }
    }

    /** [name], o «nombre (2).ext», «nombre (3).ext»… si ya existe. */
    private fun freeName(directory: Path): Path {
        val first = directory / name
        if (!FileSystem.SYSTEM.exists(first)) return first
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var counter = 2
        while (true) {
            val candidate = directory / "$base ($counter)$extension"
            if (!FileSystem.SYSTEM.exists(candidate)) return candidate
            counter++
        }
    }
}

/** Un `Source` que suelta el permiso de acceso al cerrarse. */
private class ReleasingSource(private val delegate: Source, private val release: () -> Unit) : Source {
    override fun read(sink: Buffer, byteCount: Long): Long = delegate.read(sink, byteCount)

    override fun timeout(): Timeout = delegate.timeout()

    override fun close() {
        try {
            delegate.close()
        } finally {
            release()
        }
    }
}

/** Un `Sink` que suelta el permiso de acceso al cerrarse. */
private class ReleasingSink(private val delegate: Sink, private val release: () -> Unit) : Sink {
    override fun write(source: Buffer, byteCount: Long) = delegate.write(source, byteCount)

    override fun flush() = delegate.flush()

    override fun timeout(): Timeout = delegate.timeout()

    override fun close() {
        try {
            delegate.close()
        } finally {
            release()
        }
    }
}

private const val TAG = "DocumentPickers"
