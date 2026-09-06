package com.moralesDev.dsForEveryone.emulator

import kotlin.math.atan2

/**
 * Reparto del área de la cruceta en zonas táctiles, con diagonales.
 *
 * Vive aquí, fuera de Compose y sin depender de nada de Android, por la misma
 * razón que en `app-emulador`: es geometría pura y se prueba punto por punto
 * en la JVM.
 *
 * ## Por qué diagonales aquí y no en la GBA
 *
 * `DirectionalPadZones` en `app-emulador` reparte el cuadrado en cuatro cuñas
 * y omite las diagonales a propósito: la GBA no las necesita casi nunca. La
 * DS sí — *Mario Kart DS*, *New Super Mario Bros.* — así que el círculo se
 * reparte en **ocho** cuñas: las cuatro cardinales y las cuatro diagonales
 * entre ellas.
 *
 * ## Un único control, un conjunto de botones
 *
 * [directionsAt] devuelve un [Set] de [DsButton], nunca más de dos y nunca
 * botones que no sean adyacentes (no existe `{UP, DOWN}`). Una diagonal es
 * literalmente el conjunto de sus dos cardinales — `{UP, RIGHT}` para la
 * cuña de arriba a la derecha —, así que [InputManager.setDirections] no
 * necesita saber nada de diagonales: ya acepta conjuntos.
 *
 * Sigue siendo **un** control, no cuatro ni ocho botones: deslizar de arriba
 * a la diagonal y a la derecha sin levantar el dedo pasa por
 * `{UP}` → `{UP, RIGHT}` → `{RIGHT}`, una llamada por fotograma.
 *
 * ## El ancho de las cuñas diagonales
 *
 * [diagonalWidthDegrees] es un parámetro, no una constante fija: hace falta
 * ajustarlo con el dedo encima de un dispositivo real. Demasiado estrecho y
 * las diagonales no se alcanzan; demasiado ancho y las cardinales se activan
 * sin querer. Las cuatro cuñas cardinales se reparten a partes iguales lo que
 * dejan las diagonales, así que el círculo completo — descontada la zona
 * muerta central — queda siempre cubierto sin huecos ni solapes, sea cual
 * sea el ancho elegido.
 */
object DirectionalPadZones {

    /**
     * Radio del disco central que no produce nada, como fracción del lado
     * menor del control. Mismo criterio que en `app-emulador`.
     */
    const val DEAD_ZONE_RATIO = 0.10f

    /**
     * Ancho angular por defecto de cada cuña diagonal, en grados.
     * Provisional — ver la documentación de la clase —, y siempre menor que
     * 90° para que a las cuatro cardinales les quede algo de círculo.
     */
    const val DEFAULT_DIAGONAL_WIDTH_DEGREES = 40f

    /**
     * Conjunto de botones que corresponde a un punto, o el conjunto vacío si
     * es el disco central.
     *
     * Acepta puntos fuera del control y no los recorta: mientras el dedo no
     * se levante, Compose sigue entregando su posición aunque se salga del
     * área, y lo que se quiere entonces es conservar la dirección.
     *
     * @param x,y posición dentro del control, en píxeles.
     * @param width,height tamaño del control, en píxeles.
     * @param diagonalWidthDegrees ancho de cada cuña diagonal, en grados,
     *   entre 0 (sin diagonales) y 90 (sin cardinales) exclusive.
     */
    fun directionsAt(
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        diagonalWidthDegrees: Float = DEFAULT_DIAGONAL_WIDTH_DEGREES,
    ): Set<DsButton> {
        if (width <= 0f || height <= 0f) return emptySet()
        require(diagonalWidthDegrees in 0f..90f) {
            "El ancho de la diagonal debe estar entre 0 y 90 grados: $diagonalWidthDegrees"
        }

        val dx = x - width / 2f
        val dy = y - height / 2f

        val deadZone = minOf(width, height) * DEAD_ZONE_RATIO
        if (dx * dx + dy * dy < deadZone * deadZone) return emptySet()

        val cardinalWidth = 90f - diagonalWidthDegrees
        val angleDegrees = normalizeDegrees(Math.toDegrees(atan2(dy, dx).toDouble()).toFloat())
        // Desplaza el origen medio ancho cardinal hacia atrás: así la cuña
        // RIGHT queda centrada en el ángulo 0° en vez de empezar en él.
        val shifted = normalizeDegrees(angleDegrees + cardinalWidth / 2f)

        // Orden clockwise empezando en RIGHT (por el desplazamiento de arriba):
        // cardinal, diagonal, cardinal, diagonal... La suma de los ocho anchos
        // es siempre 360° exactos, así que el círculo queda cubierto sin
        // huecos ni solapes sea cual sea diagonalWidthDegrees.
        val wedges = listOf(
            cardinalWidth to setOf(DsButton.RIGHT),
            diagonalWidthDegrees to setOf(DsButton.DOWN, DsButton.RIGHT),
            cardinalWidth to setOf(DsButton.DOWN),
            diagonalWidthDegrees to setOf(DsButton.DOWN, DsButton.LEFT),
            cardinalWidth to setOf(DsButton.LEFT),
            diagonalWidthDegrees to setOf(DsButton.UP, DsButton.LEFT),
            cardinalWidth to setOf(DsButton.UP),
            diagonalWidthDegrees to setOf(DsButton.UP, DsButton.RIGHT),
        )

        var boundary = 0f
        for ((wedgeWidth, buttons) in wedges) {
            boundary += wedgeWidth
            if (shifted < boundary) return buttons
        }
        // Inalcanzable: la suma de anchos de `wedges` es exactamente 360°, y
        // `shifted` está normalizado a [0, 360), así que siempre cae antes
        // del último límite.
        error("Ángulo fuera de rango: $shifted")
    }

    private fun normalizeDegrees(degrees: Float): Float {
        val mod = degrees % 360f
        return if (mod < 0f) mod + 360f else mod
    }
}
