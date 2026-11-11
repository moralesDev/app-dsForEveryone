package com.moralesDev.dsForEveryone.data.cover

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.storage.writeAtomically
import com.moralesDev.dsForEveryone.ui.files.ExistingFile
import com.moralesDev.dsForEveryone.ui.files.PickedDocument
import com.moralesDev.dsForEveryone.util.AppLog
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

/**
 * Las portadas en iOS, con el mismo recorrido que en Android
 * (`CoverRepository`): comprobar el tamaño antes de abrir, decodificar ya
 * reducida, enderezar según el EXIF, recortar en cuadrado, escalar a
 * [CoverImage.SIZE] y guardar en WebP. La aritmética del encuadre es la
 * común, [CoverImage].
 *
 * Abrir usa `UIImage`, que entiende HEIC —el formato de las fotos del iPhone
 * y del iPad, que Skia no sabe leer— y aplica la orientación del EXIF al
 * dibujarse. Recortar, escalar y codificar usa Skia, que es con lo que
 * Compose pinta en iOS y con lo que ya está hecha la imagen.
 */
@OptIn(ExperimentalForeignApi::class)
class IosCoverStore(
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
) : CoverStore {

    private val fileSystem = FileSystem.SYSTEM

    override fun coverPath(romId: String): String? =
        storageManager.coverPath(romId).takeIf(fileSystem::exists)?.toString()

    override fun coverVersion(romId: String): Long =
        fileSystem.metadataOrNull(storageManager.coverPath(romId))?.lastModifiedAtMillis ?: 0L

    override suspend fun open(document: PickedDocument): PickResult = withContext(ioDispatcher) {
        val url = (document as? ExistingFile)?.url ?: return@withContext PickResult.Failed(CoverError.UNREADABLE)
        val access = url.startAccessingSecurityScopedResource()
        try {
            val data = NSData.dataWithContentsOfURL(url) ?: return@withContext PickResult.Failed(CoverError.UNREADABLE)
            if (data.length.toLong() > CoverImage.MAX_BYTES) return@withContext PickResult.Failed(CoverError.TOO_LARGE)

            val original = UIImage(data = data) ?: return@withContext PickResult.Failed(CoverError.UNSUPPORTED_TYPE)
            // `size` ya viene con la orientación aplicada (ancho y alto como se ve).
            val (width, height) = original.size.useContents { width to height }
            if (width <= 0.0 || height <= 0.0) return@withContext PickResult.Failed(CoverError.UNREADABLE)

            // Reducida como en Android (CoverImage.sampleSizeFor), y enderezada:
            // dibujar un UIImage aplica su orientación.
            val sample = CoverImage.sampleSizeFor(width.toInt(), height.toInt())
            val targetWidth = width / sample
            val targetHeight = height / sample
            val format = UIGraphicsImageRendererFormat.defaultFormat().apply { scale = 1.0 }
            val upright = UIGraphicsImageRenderer(size = CGSizeMake(targetWidth, targetHeight), format = format)
                .imageWithActions { original.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight)) }

            val png = UIImagePNGRepresentation(upright) ?: return@withContext PickResult.Failed(CoverError.UNREADABLE)
            val bitmap = Image.makeFromEncoded(png.toByteArray()).toComposeImageBitmap()
            PickResult.Ready(PickedImage(bitmap))
        } catch (error: Exception) {
            AppLog.w(TAG, "No se pudo abrir la imagen elegida", error)
            PickResult.Failed(CoverError.UNREADABLE)
        } finally {
            if (access) url.stopAccessingSecurityScopedResource()
        }
    }

    override suspend fun save(
        romId: String,
        image: PickedImage,
        zoom: Float,
        centerX: Float,
        centerY: Float,
    ): Boolean = withContext(ioDispatcher) {
        try {
            val window = CoverImage.cropWindow(image.width, image.height, zoom, centerX, centerY)
            val source = Image.makeFromBitmap(image.image.asSkiaBitmap())
            val surface = Surface.makeRasterN32Premul(CoverImage.SIZE, CoverImage.SIZE)
            surface.canvas.drawImageRect(
                source,
                Rect.makeLTRB(window.left.toFloat(), window.top.toFloat(), window.right.toFloat(), window.bottom.toFloat()),
                Rect.makeWH(CoverImage.SIZE.toFloat(), CoverImage.SIZE.toFloat()),
            )
            val encoded = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.WEBP, QUALITY)?.bytes
                ?: return@withContext false
            writeAtomically(storageManager.coverPath(romId), encoded)
            true
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo guardar la portada de $romId", error)
            false
        } catch (error: IllegalStateException) {
            AppLog.e(TAG, "No se pudo reemplazar la portada de $romId", error)
            false
        }
    }

    override suspend fun delete(romId: String): Boolean = withContext(ioDispatcher) {
        try {
            fileSystem.delete(storageManager.coverPath(romId), mustExist = false)
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun NSData.toByteArray(): ByteArray {
        val bytes = ByteArray(length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
        return bytes
    }

    private companion object {
        const val TAG = "IosCoverStore"

        /** La misma calidad que en Android. */
        const val QUALITY = 88
    }
}
