package com.moralesDev.dsForEveryone.data.save

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Las miniaturas de las ranuras en Android: el fotograma a `Bitmap` y de ahí a PNG. */
object BitmapThumbnailEncoder : ThumbnailEncoder {
    override fun encodePng(argb: IntArray, width: Int, height: Int): ByteArray? {
        val bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)
        val encoded = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        return encoded
    }
}
