package com.moralesDev.dsForEveryone.emulator.gamepad

/**
 * Los códigos de tecla de mando con los que se guardan los mapeos: los de
 * `android.view.KeyEvent` (`KEYCODE_BUTTON_A`…), copiados como números.
 *
 * Son API pública y estable de Android: no cambian de una versión a otra, y
 * el mapeo guardado de cada usuario depende de ellos. Viven aquí, y no como
 * referencias a `KeyEvent`, porque [GamepadMapping] está en el módulo común
 * y `KeyEvent` no existe en iOS. GamepadKeyCodesTest, en `:app`, comprueba
 * en la JVM que cada uno sigue valiendo lo mismo que su constante.
 *
 * En iOS, los botones del mando (framework GameController) se traducen a
 * estos mismos códigos (`IosGamepadSource`), para que un mapeo signifique lo
 * mismo en los dos.
 */
object GamepadKeyCodes {
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val BUTTON_A = 96
    const val BUTTON_B = 97
    const val BUTTON_X = 99
    const val BUTTON_Y = 100
    const val BUTTON_L1 = 102
    const val BUTTON_R1 = 103
    const val BUTTON_L2 = 104
    const val BUTTON_R2 = 105
    const val BUTTON_THUMBL = 106
    const val BUTTON_THUMBR = 107
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
    const val BUTTON_MODE = 110

    // Teclado físico. Las letras van seguidas: KEY_A + 0 … KEY_A + 25 son
    // A … Z; las cifras, KEY_0 + 0 … KEY_0 + 9.
    const val KEY_0 = 7
    const val KEY_A = 29
    const val KEY_Q = KEY_A + 16
    const val KEY_S = KEY_A + 18
    const val KEY_W = KEY_A + 22
    const val KEY_X = KEY_A + 23
    const val KEY_Z = KEY_A + 25
    const val COMMA = 55
    const val PERIOD = 56
    const val ALT_LEFT = 57
    const val ALT_RIGHT = 58
    const val SHIFT_LEFT = 59
    const val SHIFT_RIGHT = 60
    const val TAB = 61
    const val SPACE = 62
    const val ENTER = 66
    const val DEL = 67
    const val ESCAPE = 111
    const val CTRL_LEFT = 113
    const val CTRL_RIGHT = 114

    /**
     * El nombre de [keyCode] tal como lo da `KeyEvent.keyCodeToString` sin el
     * prefijo `KEYCODE_`, para los de esta lista; `null` para cualquier otro.
     */
    fun nameOf(keyCode: Int): String? = NAMES[keyCode]

    private val NAMES = mapOf(
        DPAD_UP to "DPAD_UP",
        DPAD_DOWN to "DPAD_DOWN",
        DPAD_LEFT to "DPAD_LEFT",
        DPAD_RIGHT to "DPAD_RIGHT",
        BUTTON_A to "BUTTON_A",
        BUTTON_B to "BUTTON_B",
        BUTTON_X to "BUTTON_X",
        BUTTON_Y to "BUTTON_Y",
        BUTTON_L1 to "BUTTON_L1",
        BUTTON_R1 to "BUTTON_R1",
        BUTTON_L2 to "BUTTON_L2",
        BUTTON_R2 to "BUTTON_R2",
        BUTTON_THUMBL to "BUTTON_THUMBL",
        BUTTON_THUMBR to "BUTTON_THUMBR",
        BUTTON_START to "BUTTON_START",
        BUTTON_SELECT to "BUTTON_SELECT",
        BUTTON_MODE to "BUTTON_MODE",
        COMMA to "COMMA",
        PERIOD to "PERIOD",
        ALT_LEFT to "ALT_LEFT",
        ALT_RIGHT to "ALT_RIGHT",
        SHIFT_LEFT to "SHIFT_LEFT",
        SHIFT_RIGHT to "SHIFT_RIGHT",
        TAB to "TAB",
        SPACE to "SPACE",
        ENTER to "ENTER",
        DEL to "DEL",
        ESCAPE to "ESCAPE",
        CTRL_LEFT to "CTRL_LEFT",
        CTRL_RIGHT to "CTRL_RIGHT",
    ) + ('A'..'Z').mapIndexed { i, letter -> KEY_A + i to letter.toString() } +
        (0..9).map { digit -> KEY_0 + digit to digit.toString() }
}
