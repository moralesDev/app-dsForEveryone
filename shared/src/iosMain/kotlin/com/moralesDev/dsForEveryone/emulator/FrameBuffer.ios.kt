package com.moralesDev.dsForEveryone.emulator

actual class FrameBuffer actual constructor() {
    actual val size: Int = DsVideo.WIDTH * DsVideo.HEIGHT * 4

    val bytes: ByteArray = ByteArray(size)

    actual fun fillFromArgb(argb: IntArray) {
        for (i in argb.indices) {
            val pixel = argb[i]
            bytes[i * 4] = (pixel shr 16).toByte() // R
            bytes[i * 4 + 1] = (pixel shr 8).toByte() // G
            bytes[i * 4 + 2] = pixel.toByte() // B
            bytes[i * 4 + 3] = (pixel ushr 24).toByte() // A
        }
    }
}
