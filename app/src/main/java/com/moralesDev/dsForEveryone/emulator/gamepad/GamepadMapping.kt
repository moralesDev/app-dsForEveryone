package com.moralesDev.dsForEveryone.emulator.gamepad

import android.view.KeyEvent
import com.moralesDev.dsForEveryone.emulator.DsButton

/**
 * Qué tecla física de un mando concreto representa cada [GamepadTarget]. Vive
 * uno por mando —identificado por `InputDevice.descriptor` en
 * [GamepadMappings]—, nunca uno global: dos mandos distintos no tienen por
 * qué compartir la misma disposición de botones.
 */
data class GamepadMapping(private val keyCodeByTarget: Map<GamepadTarget, Int>) {

    /** El objetivo que representa [keyCode] en este mando, si hay alguno mapeado a él. */
    fun targetFor(keyCode: Int): GamepadTarget? =
        keyCodeByTarget.entries.firstOrNull { it.value == keyCode }?.key

    /** La tecla física asignada hoy a [target], para mostrarla en la pantalla de mapeo. */
    fun keyCodeFor(target: GamepadTarget): Int? = keyCodeByTarget[target]

    /** Nueva asignación con [target] reasignado a [keyCode]; libera cualquier otro objetivo que usara esa misma tecla. */
    fun with(target: GamepadTarget, keyCode: Int): GamepadMapping {
        val withoutCollision = keyCodeByTarget.filterValues { it != keyCode }
        return GamepadMapping(withoutCollision + (target to keyCode))
    }

    fun encode(): String =
        keyCodeByTarget.entries.joinToString(separator = ";") { (target, keyCode) ->
            "${target.encode()}:$keyCode"
        }

    companion object {
        /**
         * Asignación estándar de Android para mandos de juego (`KeyEvent`,
         * constantes `KEYCODE_BUTTON_*`): cubre razonablemente Xbox,
         * PlayStation y la mayoría de mandos genéricos sin que el usuario
         * tenga que tocar nada al conectar uno por primera vez. El soplido
         * cae en el stick izquierdo pulsado (`THUMBL`): en casi ningún juego
         * de DS hace falta pulsar el stick, así que es un hueco libre.
         */
        val DEFAULT = GamepadMapping(
            mapOf(
                GamepadTarget.Button(DsButton.A) to KeyEvent.KEYCODE_BUTTON_A,
                GamepadTarget.Button(DsButton.B) to KeyEvent.KEYCODE_BUTTON_B,
                GamepadTarget.Button(DsButton.X) to KeyEvent.KEYCODE_BUTTON_X,
                GamepadTarget.Button(DsButton.Y) to KeyEvent.KEYCODE_BUTTON_Y,
                GamepadTarget.Button(DsButton.L) to KeyEvent.KEYCODE_BUTTON_L1,
                GamepadTarget.Button(DsButton.R) to KeyEvent.KEYCODE_BUTTON_R1,
                GamepadTarget.Button(DsButton.START) to KeyEvent.KEYCODE_BUTTON_START,
                GamepadTarget.Button(DsButton.SELECT) to KeyEvent.KEYCODE_BUTTON_SELECT,
                GamepadTarget.Button(DsButton.UP) to KeyEvent.KEYCODE_DPAD_UP,
                GamepadTarget.Button(DsButton.DOWN) to KeyEvent.KEYCODE_DPAD_DOWN,
                GamepadTarget.Button(DsButton.LEFT) to KeyEvent.KEYCODE_DPAD_LEFT,
                GamepadTarget.Button(DsButton.RIGHT) to KeyEvent.KEYCODE_DPAD_RIGHT,
                GamepadTarget.Blow to KeyEvent.KEYCODE_BUTTON_THUMBL,
            ),
        )

        /**
         * Inversa de [encode]. Cualquier fallo de formato, un objetivo
         * repetido o desconocido, o que falte alguno de los trece hacen que
         * devuelva `null`: mejor caer a [DEFAULT] que arrastrar un mapeo a
         * medio leer. Mismo criterio que `ControlLayout.decode`.
         */
        fun decode(text: String): GamepadMapping? {
            if (text.isBlank()) return null
            val result = mutableMapOf<GamepadTarget, Int>()
            for (entry in text.split(";")) {
                val parts = entry.split(":")
                if (parts.size != 2) return null
                val target = GamepadTarget.decode(parts[0]) ?: return null
                if (result.containsKey(target)) return null
                val keyCode = parts[1].toIntOrNull() ?: return null
                result[target] = keyCode
            }
            return if (result.keys == GamepadTarget.ALL.toSet()) GamepadMapping(result) else null
        }

        /**
         * Serializa el mapeo de **todos** los mandos que el usuario ha
         * personalizado alguna vez, uno por línea (`descriptor|mapeo`): un
         * único campo de `SettingsRepository`, mismo espíritu que
         * `ControlLayout` para los siete grupos de mandos en pantalla.
         */
        fun encodeAll(byDescriptor: Map<String, GamepadMapping>): String =
            byDescriptor.entries.joinToString(separator = "\n") { (descriptor, mapping) ->
                "$descriptor|${mapping.encode()}"
            }

        /** Inversa de [encodeAll]. Una línea con formato inválido se descarta sin invalidar las demás. */
        fun decodeAll(text: String): Map<String, GamepadMapping> {
            if (text.isBlank()) return emptyMap()
            return text.split("\n").mapNotNull { line ->
                val separatorIndex = line.indexOf('|')
                if (separatorIndex <= 0) return@mapNotNull null
                val descriptor = line.substring(0, separatorIndex)
                val mapping = decode(line.substring(separatorIndex + 1)) ?: return@mapNotNull null
                descriptor to mapping
            }.toMap()
        }
    }
}
