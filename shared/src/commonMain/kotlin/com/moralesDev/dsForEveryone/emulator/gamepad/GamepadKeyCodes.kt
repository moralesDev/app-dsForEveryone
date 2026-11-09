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
 * En iOS, los botones del mando (framework GameController) se traducirán a
 * estos mismos códigos, para que un mapeo signifique lo mismo en los dos.
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
    const val BUTTON_THUMBL = 106
    const val BUTTON_START = 108
    const val BUTTON_SELECT = 109
}
