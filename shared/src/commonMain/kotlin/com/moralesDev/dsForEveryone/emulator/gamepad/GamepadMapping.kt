package com.moralesDev.dsForEveryone.emulator.gamepad

import com.moralesDev.dsForEveryone.emulator.DsButton

/**
 * Qué tecla física de un mando concreto representa cada [GamepadTarget]. Vive
 * uno por mando —identificado por `InputDevice.descriptor` en
 * `EmulatorSettings.gamepadMappings`—, nunca uno global: dos mandos distintos
 * no tienen por qué compartir la misma disposición de botones.
 */
data class GamepadMapping(private val keyCodeByTarget: Map<GamepadTarget, Int>) {

    /** El objetivo que representa [keyCode] en este mando, si hay alguno mapeado a él. */
    fun targetFor(keyCode: Int): GamepadTarget? =
        keyCodeByTarget.entries.firstOrNull { it.value == keyCode }?.key

    /** La tecla física asignada hoy a [target], para mostrarla en la pantalla de mapeo. */
    fun keyCodeFor(target: GamepadTarget): Int? = keyCodeByTarget[target]

    /**
     * Nueva asignación con [target] reasignado a [keyCode]. Si esa tecla ya
     * representaba a otro objetivo, **se intercambian**: el otro objetivo se
     * queda con la tecla que tenía [target] antes (o queda en blanco, si
     * [target] no tenía ninguna) — nunca se pierde una asignación en
     * silencio. Es lo que espera cualquiera que ya haya remapeado un mando
     * antes: reasignar A a lo que hoy es B intercambia A y B, no deja a B sin
     * botón.
     */
    fun with(target: GamepadTarget, keyCode: Int): GamepadMapping {
        val displaced = targetFor(keyCode)
        val updated = keyCodeByTarget.toMutableMap()
        if (displaced != null && displaced != target) {
            val previousKeyCode = keyCodeByTarget[target]
            if (previousKeyCode != null) updated[displaced] = previousKeyCode else updated.remove(displaced)
        }
        updated[target] = keyCode
        return GamepadMapping(updated)
    }

    /** Dejar [target] sin ninguna tecla asignada — vía de escape para quien prefiera vaciar y reasignar a mano en vez de fiarse del intercambio automático de [with]. */
    fun withoutTarget(target: GamepadTarget): GamepadMapping = GamepadMapping(keyCodeByTarget - target)

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
                GamepadTarget.Button(DsButton.A) to GamepadKeyCodes.BUTTON_A,
                GamepadTarget.Button(DsButton.B) to GamepadKeyCodes.BUTTON_B,
                GamepadTarget.Button(DsButton.X) to GamepadKeyCodes.BUTTON_X,
                GamepadTarget.Button(DsButton.Y) to GamepadKeyCodes.BUTTON_Y,
                GamepadTarget.Button(DsButton.L) to GamepadKeyCodes.BUTTON_L1,
                GamepadTarget.Button(DsButton.R) to GamepadKeyCodes.BUTTON_R1,
                GamepadTarget.Button(DsButton.START) to GamepadKeyCodes.BUTTON_START,
                GamepadTarget.Button(DsButton.SELECT) to GamepadKeyCodes.BUTTON_SELECT,
                GamepadTarget.Button(DsButton.UP) to GamepadKeyCodes.DPAD_UP,
                GamepadTarget.Button(DsButton.DOWN) to GamepadKeyCodes.DPAD_DOWN,
                GamepadTarget.Button(DsButton.LEFT) to GamepadKeyCodes.DPAD_LEFT,
                GamepadTarget.Button(DsButton.RIGHT) to GamepadKeyCodes.DPAD_RIGHT,
                GamepadTarget.Blow to GamepadKeyCodes.BUTTON_THUMBL,
            ),
        )

        /**
         * Para un teclado físico, la disposición de casi todos los emuladores
         * de DS (también la de melonDS de escritorio): flechas para la
         * cruceta, X y Z para A y B, S y A para X e Y, Q y W para L y R,
         * Intro para Start, Retroceso para Select y Espacio para soplar.
         */
        val KEYBOARD_DEFAULT = GamepadMapping(
            mapOf(
                GamepadTarget.Button(DsButton.A) to GamepadKeyCodes.KEY_X,
                GamepadTarget.Button(DsButton.B) to GamepadKeyCodes.KEY_Z,
                GamepadTarget.Button(DsButton.X) to GamepadKeyCodes.KEY_S,
                GamepadTarget.Button(DsButton.Y) to GamepadKeyCodes.KEY_A,
                GamepadTarget.Button(DsButton.L) to GamepadKeyCodes.KEY_Q,
                GamepadTarget.Button(DsButton.R) to GamepadKeyCodes.KEY_W,
                GamepadTarget.Button(DsButton.START) to GamepadKeyCodes.ENTER,
                GamepadTarget.Button(DsButton.SELECT) to GamepadKeyCodes.DEL,
                GamepadTarget.Button(DsButton.UP) to GamepadKeyCodes.DPAD_UP,
                GamepadTarget.Button(DsButton.DOWN) to GamepadKeyCodes.DPAD_DOWN,
                GamepadTarget.Button(DsButton.LEFT) to GamepadKeyCodes.DPAD_LEFT,
                GamepadTarget.Button(DsButton.RIGHT) to GamepadKeyCodes.DPAD_RIGHT,
                GamepadTarget.Blow to GamepadKeyCodes.SPACE,
            ),
        )

        /** El mapeo de un aparato que nunca se ha personalizado. */
        fun defaultFor(isKeyboard: Boolean): GamepadMapping = if (isKeyboard) KEYBOARD_DEFAULT else DEFAULT

        /**
         * Inversa de [encode]. Cualquier fallo de formato, o un objetivo
         * repetido o desconocido, hacen que devuelva `null`: mejor caer a
         * [DEFAULT] que arrastrar un mapeo a medio leer. Mismo criterio que
         * `ControlLayout.decode` — salvo que aquí **no** hace falta que
         * estén los trece: un objetivo ausente es uno dejado en blanco a
         * propósito (ver [withoutTarget]), no un mapeo corrupto.
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
            return GamepadMapping(result)
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
