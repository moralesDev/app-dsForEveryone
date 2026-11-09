package com.moralesDev.dsForEveryone.data.cover

import androidx.compose.ui.graphics.ImageBitmap
import com.moralesDev.dsForEveryone.ui.files.PickedDocument

/**
 * La imagen elegida, ya enderezada y lista para encuadrar.
 *
 * @param image lo que pinta la hoja de encuadre (y lo que se recorta al
 *   guardar).
 * @param onRelease suelta la memoria del bitmap cuando ya no hace falta (en
 *   Android, `Bitmap.recycle()`): una foto reducida sigue ocupando megas.
 */
class PickedImage(
    val image: ImageBitmap,
    private val onRelease: () -> Unit = {},
) {
    val width: Int get() = image.width
    val height: Int get() = image.height

    /** Ya es cuadrada: no hay nada que recortar, sólo confirmar. */
    val isSquare: Boolean get() = width == height

    fun release() = onRelease()
}

sealed interface PickResult {
    data class Ready(val image: PickedImage) : PickResult
    data class Failed(val error: CoverError) : PickResult
}

/**
 * Portadas elegidas por el usuario, para sustituir la que sale del banner de
 * la ROM. Común a Android e iOS (docs/ios.md, iOS-3); cada plataforma la
 * implementa con su decodificador de imágenes, porque abrir una foto
 * reducida, enderezarla según el EXIF y guardarla en WebP es justo lo que
 * cada sistema hace a su manera. La aritmética del encuadre es común:
 * [CoverImage].
 */
interface CoverStore {
    /** Ruta de la portada personalizada de [romId], o `null` si no tiene. */
    fun coverPath(romId: String): String?

    /**
     * Momento del último cambio de la portada, o 0 si no hay. La biblioteca lo
     * usa como clave al pintar: sin él, cambiar la portada dejaría en pantalla
     * la anterior, porque la ruta del archivo no cambia.
     */
    fun coverVersion(romId: String): Long

    /** Abre la imagen elegida y la deja lista para encuadrar. */
    suspend fun open(document: PickedDocument): PickResult

    /** Recorta, escala y guarda, de forma atómica. */
    suspend fun save(romId: String, image: PickedImage, zoom: Float, centerX: Float, centerY: Float): Boolean

    /** Quita la portada personalizada y nada más. */
    suspend fun delete(romId: String): Boolean
}
