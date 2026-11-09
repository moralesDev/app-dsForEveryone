package com.moralesDev.dsForEveryone.emulator.gamepad

import com.moralesDev.dsForEveryone.emulator.DsButton

/**
 * Lo que un botón físico de un mando puede representar en el juego: uno de
 * los doce [DsButton], o el soplido. El soplido nunca formó parte de la
 * máscara de 12 bits del core (ver `InputManager.setMicNoise`), así que no es
 * un `DsButton` trece — es un objetivo de mapeo aparte, tratado igual en todo
 * lo demás: aparece en la misma lista, se captura de la misma forma, se
 * guarda con el mismo mecanismo.
 */
sealed interface GamepadTarget {

    /** Forma estable para persistencia; ver [GamepadMapping.encode]. */
    fun encode(): String

    data class Button(val button: DsButton) : GamepadTarget {
        override fun encode(): String = "BTN_${button.name}"
    }

    data object Blow : GamepadTarget {
        override fun encode(): String = "BLOW"
    }

    companion object {
        /** Los trece objetivos mapeables, en el orden en que se listan en la pantalla de mapeo. */
        val ALL: List<GamepadTarget> = DsButton.entries.map(::Button) + Blow

        /** Inversa de [GamepadTarget.encode]. `null` si no coincide con ningún objetivo conocido. */
        fun decode(raw: String): GamepadTarget? =
            if (raw == "BLOW") {
                Blow
            } else {
                DsButton.entries.firstOrNull { "BTN_${it.name}" == raw }?.let(::Button)
            }
    }
}
