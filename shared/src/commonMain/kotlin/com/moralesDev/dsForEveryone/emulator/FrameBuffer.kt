package com.moralesDev.dsForEveryone.emulator

/**
 * El búfer del fotograma: 256×384 píxeles R, G, B, A (las dos pantallas
 * apiladas, ver DsVideo). Uno solo, reservado una vez y reutilizado cada
 * vuelta del bucle (ver EmulatorController.currentFrame): pedir uno nuevo
 * por fotograma eran ~90 MB/s de basura y candidato serio al
 * OutOfMemoryError de la fase 4.C (docs/estabilizacion-core.md).
 *
 * Cada plataforma lo guarda como lo necesita su renderizador: en Android, un
 * `ByteBuffer` directo que se sube a GL sin copias; en iOS, un `ByteArray`.
 */
expect class FrameBuffer() {
    /** Bytes que caben: [DsVideo.WIDTH] × [DsVideo.HEIGHT] × 4. */
    val size: Int

    /** Copia un fotograma 0xAARRGGBB (lo que da `captureFrame`) como R, G, B, A. */
    fun fillFromArgb(argb: IntArray)
}
