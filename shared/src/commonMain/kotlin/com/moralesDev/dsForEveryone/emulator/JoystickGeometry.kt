package com.moralesDev.dsForEveryone.emulator

import kotlin.math.sqrt

/**
 * El joystick en pantalla, reducido a geometría: dónde se pinta la bola y qué
 * direcciones de la DS produce cada posición del dedo.
 *
 * La DS no tiene stick analógico: sólo cruceta. El joystick es otra forma de
 * **pulsar la misma cruceta** —más cómoda para muchos pulgares, porque no
 * hay que acertar en un brazo—, así que su salida es exactamente la de
 * [DirectionalPadZones]: un conjunto de uno o dos [DsButton] adyacentes. No
 * hay intensidad, ni falta que hace.
 *
 * Fuera de Compose por el mismo motivo que la cruceta: se prueba punto por
 * punto en JVM.
 */
object JoystickGeometry {

    /**
     * Radio de la zona muerta, como fracción del radio del joystick. Más
     * grande que la de la cruceta ([DirectionalPadZones.DEAD_ZONE_RATIO] es
     * un 10 % del lado, un 20 % del radio): en un stick el pulgar descansa
     * sobre la bola, y un temblor alrededor del centro no puede soltar
     * direcciones.
     */
    const val DEAD_ZONE_RATIO = 0.3f

    /**
     * Ocho sectores iguales de 45°. En la cruceta las diagonales son más
     * estrechas (ver [DirectionalPadZones.DEFAULT_DIAGONAL_WIDTH_DEGREES])
     * porque se pulsan con la yema sobre una cruz; en un stick se apunta con
     * un ángulo, y ahí lo natural es repartir el círculo a partes iguales.
     */
    const val DIAGONAL_WIDTH_DEGREES = 45f

    /**
     * Cuánto puede alejarse el centro de la bola del centro del joystick, como
     * fracción del radio. La bola nunca se sale del aro aunque el dedo sí.
     */
    const val KNOB_TRAVEL_RATIO = 0.5f

    /**
     * Direcciones para el dedo en ([dx], [dy]) respecto al centro del
     * joystick, en píxeles, con y hacia abajo positivo.
     *
     * No recorta al radio: mientras el dedo no se levante, Compose sigue
     * entregando su posición aunque se salga del control, y lo que se quiere
     * entonces es conservar la dirección, no perderla.
     */
    fun directions(dx: Float, dy: Float, radius: Float): Set<DsButton> {
        if (radius <= 0f) return emptySet()
        val deadZone = radius * DEAD_ZONE_RATIO
        if (dx * dx + dy * dy < deadZone * deadZone) return emptySet()
        return DirectionalPadZones.directionsForAngle(dx, dy, DIAGONAL_WIDTH_DEGREES)
    }

    /**
     * Desplazamiento de la bola para el dedo en ([dx], [dy]): el mismo
     * vector, recortado a [KNOB_TRAVEL_RATIO] × [radius] sin cambiar de
     * dirección.
     */
    fun knobOffset(dx: Float, dy: Float, radius: Float): Pair<Float, Float> {
        if (radius <= 0f) return 0f to 0f
        val maxTravel = radius * KNOB_TRAVEL_RATIO
        val distance = sqrt(dx * dx + dy * dy)
        if (distance <= maxTravel) return dx to dy
        val scale = maxTravel / distance
        return dx * scale to dy * scale
    }
}
