package com.moralesDev.dsForEveryone.emulator

import java.nio.ByteBuffer
import java.nio.ByteOrder

actual class FrameBuffer actual constructor() {
    actual val size: Int = DsVideo.WIDTH * DsVideo.HEIGHT * 4

    /** Directo: el puente JNI escribe en él sin copias y GL lo sube tal cual. */
    val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())

    actual fun fillFromArgb(argb: IntArray) {
        byteBuffer.clear()
        for (pixel in argb) {
            byteBuffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            byteBuffer.put(((pixel shr 8) and 0xFF).toByte()) // G
            byteBuffer.put((pixel and 0xFF).toByte()) // B
            byteBuffer.put(((pixel shr 24) and 0xFF).toByte()) // A
        }
        byteBuffer.position(0)
    }
}
