package com.moralesDev.dsForEveryone.data.save

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

/**
 * Las miniaturas de las ranuras en iOS: el fotograma a una imagen de Skia y
 * de ahí a PNG. Un entero ARGB guardado en little-endian queda en memoria
 * como B, G, R, A: justo `BGRA_8888`, así que no hay que reordenar canales.
 */
object SkiaThumbnailEncoder : ThumbnailEncoder {
    override fun encodePng(argb: IntArray, width: Int, height: Int): ByteArray? {
        val bytes = ByteArray(width * height * 4)
        for (i in argb.indices) {
            val pixel = argb[i]
            bytes[i * 4] = pixel.toByte()
            bytes[i * 4 + 1] = (pixel shr 8).toByte()
            bytes[i * 4 + 2] = (pixel shr 16).toByte()
            bytes[i * 4 + 3] = (pixel ushr 24).toByte()
        }
        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.UNPREMUL)
        val image = Image.makeRaster(info, bytes, width * 4)
        return try {
            image.encodeToData(EncodedImageFormat.PNG)?.bytes
        } finally {
            image.close()
        }
    }
}
