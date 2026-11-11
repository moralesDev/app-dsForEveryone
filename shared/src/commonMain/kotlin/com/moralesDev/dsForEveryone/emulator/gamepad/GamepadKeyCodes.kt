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
    )
}
