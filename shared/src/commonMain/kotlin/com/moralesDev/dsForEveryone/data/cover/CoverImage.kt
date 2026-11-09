package com.moralesDev.dsForEveryone.data.cover

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Las reglas de una portada, sin depender de Android.
 *
 * Todo lo que aquí se decide —qué formatos valen, cuánto se puede reducir al
 * decodificar, qué trozo cuadrado se recorta y cuánto hay que girar por el
 * EXIF— es aritmética y tablas. Vive separado del código que toca `Bitmap`
 * precisamente para poder comprobarlo píxel a píxel en la JVM, que es donde se
 * esconden los errores de recorte: una imagen deformada no falla, sólo se ve
 * mal, y eso no lo detecta ninguna excepción.
 */
object CoverImage {

    /** Lado de la portada guardada. Es una imagen de interfaz, no un archivo maestro. */
    const val SIZE = 512

    /**
     * Tope del archivo que el usuario elige. No es una limitación técnica sino
     * una defensa: decodificar una foto de 40 MB antes de saber si sirve es la
     * forma más fácil de quedarse sin memoria.
     */
    const val MAX_BYTES = 10L * 1024 * 1024

    /**
     * Los tres formatos que Android decodifica en todas partes sin sorpresas.
     *
     * Se deja fuera lo que algunos teléfonos sí abren —HEIC, AVIF, GIF— porque
     * una portada que funciona en un móvil y falla en otro es peor que una
     * portada que no se puede poner.
     */
    val ACCEPTED_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    fun isAccepted(mimeType: String?): Boolean =
        mimeType?.lowercase()?.substringBefore(';')?.trim() in ACCEPTED_TYPES

    /**
     * Factor de reducción para `BitmapFactory.Options.inSampleSize`.
     *
     * Sirve para no traer a memoria una imagen de 8000 × 6000 cuando el destino
     * mide 512: se decodifica ya reducida. Sólo admite potencias de dos, que es
     * lo que el decodificador respeta, y nunca baja del tamaño de destino, para
     * no perder calidad antes del reescalado final.
     */
    fun sampleSizeFor(width: Int, height: Int, target: Int = SIZE): Int {
        if (width <= 0 || height <= 0 || target <= 0) return 1
        val smallestSide = minOf(width, height)
        var sample = 1
        while (smallestSide / (sample * 2) >= target) sample *= 2
        return sample
    }

    /**
     * El trozo cuadrado que se convertirá en portada, en píxeles de la imagen ya
     * orientada.
     *
     * Nunca deforma: el lado sale del **menor** de los dos lados de la imagen,
     * así que el recorte siempre cabe y las proporciones se conservan. De una
     * imagen de 1920 × 1080 se toma un cuadrado de 1080; de una de 640 × 636, uno
     * de 636, que es el recorte mínimo posible.
     *
     * @param zoom 1 encuadra todo lo que cabe; por encima acerca. Nunca aleja
     *   por debajo de 1, porque eso obligaría a rellenar con barras.
     * @param centerX,centerY el centro del recorte como fracción de la imagen
     *   (0,5 es el centro). Se ajusta solo para que el cuadrado no se salga.
     */
    fun cropWindow(
        width: Int,
        height: Int,
        zoom: Float = 1f,
        centerX: Float = 0.5f,
        centerY: Float = 0.5f,
    ): CropWindow {
        require(width > 0 && height > 0) { "Imagen sin tamaño: ${width}x$height" }

        val safeZoom = max(1f, if (zoom.isFinite()) zoom else 1f)
        val side = (minOf(width, height) / safeZoom).roundToInt().coerceIn(1, minOf(width, height))

        val left = (centerX * width - side / 2f).roundToInt().coerceIn(0, width - side)
        val top = (centerY * height - side / 2f).roundToInt().coerceIn(0, height - side)
        return CropWindow(left, top, side)
    }

    /**
     * Grados que hay que girar según la etiqueta EXIF.
     *
     * Los píxeles de un JPEG de cámara no están guardados como se ven: la
     * cámara escribe la orientación aparte y espera que quien lo abra la
     * aplique. Ignorarla es lo que hace que una foto salga tumbada.
     *
     * Los modos con espejo se tratan sólo por su giro. Voltear una portada no
     * aporta nada y el caso es rarísimo.
     */
    fun rotationFor(exifOrientation: Int): Int = when (exifOrientation) {
        EXIF_ROTATE_90, EXIF_TRANSPOSE -> 90
        EXIF_ROTATE_180, EXIF_FLIP_VERTICAL -> 180
        EXIF_ROTATE_270, EXIF_TRANSVERSE -> 270
        else -> 0
    }

    // Los valores de la etiqueta Orientation del estándar EXIF (1 a 8), los
    // mismos que las constantes ORIENTATION_* de `ExifInterface` en Android:
    // aquí como números porque esto es código común.
    const val EXIF_NORMAL = 1
    private const val EXIF_ROTATE_180 = 3
    private const val EXIF_FLIP_VERTICAL = 4
    private const val EXIF_TRANSPOSE = 5
    private const val EXIF_ROTATE_90 = 6
    private const val EXIF_TRANSVERSE = 7
    private const val EXIF_ROTATE_270 = 8

    /** Si el giro intercambia ancho y alto. */
    fun swapsAxes(rotationDegrees: Int): Boolean = rotationDegrees == 90 || rotationDegrees == 270
}

/** Un cuadrado dentro de la imagen de origen, en píxeles. */
data class CropWindow(val left: Int, val top: Int, val side: Int) {
    val right: Int get() = left + side
    val bottom: Int get() = top + side
}

/** Por qué no se pudo poner una imagen como portada. */
enum class CoverError { TOO_LARGE, UNSUPPORTED_TYPE, UNREADABLE }
