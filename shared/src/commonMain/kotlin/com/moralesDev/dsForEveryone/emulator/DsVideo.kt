package com.moralesDev.dsForEveryone.emulator

/** Características fijas del vídeo de la Nintendo DS. */
object DsVideo {

    /** Ancho y alto de cada una de las dos pantallas, 4:3. */
    const val SCREEN_WIDTH = 256
    const val SCREEN_HEIGHT = 192

    /**
     * El core entrega las dos pantallas como un único framebuffer de
     * [SCREEN_WIDTH]×([SCREEN_HEIGHT]×2): la superior en las filas
     * 0–191, la inferior en las 192–383. Ver docs/pantallas.md.
     */
    const val WIDTH = SCREEN_WIDTH
    const val HEIGHT = SCREEN_HEIGHT * 2

    /**
     * La DS no corre a 60 Hz clavados: 33 513 982 Hz ÷ 560 190 ciclos por
     * fotograma (355 puntos × 263 líneas × 6 ciclos) = 59,8261 Hz. No son los
     * 16 742 706 ns de la GBA ni un 60 Hz redondo.
     */
    const val FRAME_DURATION_NANOS = 16_715_113L

    /**
     * El período de fotograma corriendo a [speedMultiplier] veces la
     * velocidad real: es lo único que cambia el avance rápido. El core sigue
     * emulando un fotograma por vuelta —a la DS no se le puede pedir que
     * corra rápido—; lo que se acorta es la espera entre vueltas.
     *
     * Un multiplicador menor o igual que cero no tiene sentido y se trata
     * como 1x: más vale correr normal que dividir por cero en el hilo de
     * emulación.
     */
    fun frameDurationNanosAt(speedMultiplier: Float): Long {
        if (speedMultiplier <= 0f) return FRAME_DURATION_NANOS
        return (FRAME_DURATION_NANOS / speedMultiplier).toLong()
    }
}
