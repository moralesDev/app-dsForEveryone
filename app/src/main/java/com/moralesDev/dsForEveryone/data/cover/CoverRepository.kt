package com.moralesDev.dsForEveryone.data.cover

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.storage.writeAtomically
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import com.moralesDev.dsForEveryone.data.storage.coverFile
import com.moralesDev.dsForEveryone.ui.files.PickedDocument
import androidx.core.net.toUri
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * Portadas elegidas por el usuario, para sustituir la que sale del banner de
 * la ROM (extraída en `RomRepository.import`, fase 3.1/3.2).
 *
 * El recorrido de una imagen es siempre el mismo: se comprueba antes de abrirla,
 * se decodifica ya reducida, se endereza según el EXIF, se recorta en cuadrado,
 * se escala a 512 y se guarda en WebP. La original no se conserva; sólo se
 * guarda lo que la biblioteca va a pintar.
 *
 * El orden importa. Mirar el tamaño y el tipo **antes** de decodificar es lo que
 * evita que una foto de 40 MB tumbe la aplicación para acabar diciendo que no
 * servía, y enderezar **antes** de recortar es lo que hace que el cuadrado salga
 * de donde el usuario ve la imagen y no de donde están los píxeles.
 */
class CoverRepository(
    private val contentResolver: ContentResolver,
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
) : CoverStore {

    fun coverFile(romId: String): File = storageManager.coverFile(romId)

    fun hasCover(romId: String): Boolean = coverFile(romId).exists()

    override fun coverPath(romId: String): String? = coverFile(romId).takeIf { it.exists() }?.absolutePath

    override suspend fun open(document: PickedDocument): PickResult = open(document.reference.toUri())

    /**
     * Momento del último cambio de la portada, o 0 si no hay.
     *
     * La biblioteca lo usa como clave al pintar: sin él, cambiar la portada
     * dejaría en pantalla la anterior, porque la ruta del archivo no cambia.
     */
    override fun coverVersion(romId: String): Long = coverFile(romId).let {
        if (it.exists()) it.lastModified() else 0L
    }

    /**
     * Abre la imagen que el usuario eligió y la deja lista para encuadrar.
     *
     * Se decodifica con `inSampleSize`, es decir, reducida ya en el propio
     * decodificador: una imagen de 8000 × 6000 nunca llega entera a memoria.
     */
    suspend fun open(uri: Uri): PickResult = withContext(ioDispatcher) {
        val size = fileSizeOf(uri)
        if (size != null && size > CoverImage.MAX_BYTES) {
            return@withContext PickResult.Failed(CoverError.TOO_LARGE)
        }

        try {
            // Primera pasada: sólo la cabecera. `decodeStream` devuelve `null` a
            // propósito con `inJustDecodeBounds`, así que lo que hay que
            // comprobar es que el flujo se abriera, no lo que devuelve.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val header = contentResolver.openInputStream(uri)
            if (header == null) {
                Log.w(TAG, "El proveedor no entregó la imagen elegida")
                return@withContext PickResult.Failed(CoverError.UNREADABLE)
            }
            header.use { BitmapFactory.decodeStream(it, null, bounds) }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "Imagen sin dimensiones legibles")
                return@withContext PickResult.Failed(CoverError.UNREADABLE)
            }
            if (!CoverImage.isAccepted(bounds.outMimeType)) {
                Log.w(TAG, "Formato no admitido: ${bounds.outMimeType}")
                return@withContext PickResult.Failed(CoverError.UNSUPPORTED_TYPE)
            }

            // Segunda pasada: ya reducida al vuelo por el decodificador.
            val options = BitmapFactory.Options().apply {
                inSampleSize = CoverImage.sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val pixels = contentResolver.openInputStream(uri)
            if (pixels == null) {
                Log.w(TAG, "El proveedor dejó de entregar la imagen")
                return@withContext PickResult.Failed(CoverError.UNREADABLE)
            }
            val decoded = pixels.use { BitmapFactory.decodeStream(it, null, options) }
            if (decoded == null) {
                Log.w(TAG, "La imagen no se pudo decodificar")
                return@withContext PickResult.Failed(CoverError.UNREADABLE)
            }

            val upright = upright(uri, decoded)
            PickResult.Ready(PickedImage(upright.asImageBitmap(), onRelease = upright::recycle))
        } catch (error: IOException) {
            Log.w(TAG, "No se pudo leer la imagen elegida", error)
            PickResult.Failed(CoverError.UNREADABLE)
        } catch (error: SecurityException) {
            // El permiso del selector se revoca al reiniciarse el proceso.
            Log.w(TAG, "Sin permiso sobre la imagen elegida", error)
            PickResult.Failed(CoverError.UNREADABLE)
        } catch (error: OutOfMemoryError) {
            Log.w(TAG, "La imagen elegida no cabe en memoria", error)
            PickResult.Failed(CoverError.TOO_LARGE)
        }
    }

    /**
     * Recorta, escala y guarda. La escritura es atómica: un fallo a mitad deja
     * la portada anterior intacta en lugar de media imagen.
     */
    override suspend fun save(
        romId: String,
        image: PickedImage,
        zoom: Float,
        centerX: Float,
        centerY: Float,
    ): Boolean = withContext(ioDispatcher) {
        try {
            val window = CoverImage.cropWindow(image.width, image.height, zoom, centerX, centerY)
            val cropped = Bitmap.createBitmap(
                image.image.asAndroidBitmap(), window.left, window.top, window.side, window.side,
            )
            val scaled = cropped.scale(CoverImage.SIZE)
            if (cropped !== scaled) cropped.recycle()

            val encoded = ByteArrayOutputStream().use { output ->
                scaled.compress(webpFormat(), QUALITY, output)
                output.toByteArray()
            }
            scaled.recycle()

            storageManager.coverFile(romId).writeAtomically(encoded)
            true
        } catch (error: IOException) {
            Log.e(TAG, "No se pudo guardar la portada de $romId", error)
            false
        } catch (error: IllegalStateException) {
            Log.e(TAG, "No se pudo reemplazar la portada de $romId", error)
            false
        } catch (error: OutOfMemoryError) {
            Log.e(TAG, "Sin memoria al recortar la portada de $romId", error)
            false
        }
    }

    /**
     * Quita la portada personalizada y nada más: la biblioteca vuelve a
     * mostrar la extraída del banner de la ROM. La partida del juego, las
     * ranuras, el respaldo automático y sus miniaturas no se tocan.
     */
    override suspend fun delete(romId: String): Boolean = withContext(ioDispatcher) {
        storageManager.coverFile(romId).delete()
    }

    private fun fileSizeOf(uri: Uri): Long? =
        contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                null
            }
        }

    /**
     * Aplica la orientación EXIF. Sin esto, una foto hecha en vertical con el
     * teléfono girado sale tumbada: los píxeles están como los guardó la cámara
     * y el giro vive en una etiqueta aparte.
     */
    private fun upright(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    CoverImage.EXIF_NORMAL,
                )
            } ?: CoverImage.EXIF_NORMAL
        } catch (error: IOException) {
            // Un PNG o un WebP sin EXIF no es un problema: no hay nada que girar.
            Log.d(TAG, "Sin datos EXIF utilizables", error)
            CoverImage.EXIF_NORMAL
        }

        val degrees = CoverImage.rotationFor(orientation)
        if (degrees == 0) return bitmap

        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height,
            Matrix().apply { postRotate(degrees.toFloat()) },
            true,
        )
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }

    /**
     * WebP con pérdida. `WEBP_LOSSY` sólo existe desde Android 11; por debajo,
     * `WEBP` a secas hace exactamente lo mismo con este nivel de calidad. Está
     * obsoleto, y ése es el motivo de la anotación: no hay alternativa en las
     * versiones que la aplicación todavía admite (`minSdk 26`).
     */
    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }

    private fun Bitmap.scale(side: Int): Bitmap =
        if (width == side && height == side) this else Bitmap.createScaledBitmap(this, side, side, true)

    private companion object {
        const val TAG = "CoverRepository"

        /** Suficiente para 512 px de interfaz y deja el archivo en decenas de kB. */
        const val QUALITY = 88
    }
}
