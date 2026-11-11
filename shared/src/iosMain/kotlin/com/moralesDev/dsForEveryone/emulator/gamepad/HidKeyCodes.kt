package com.moralesDev.dsForEveryone.emulator.gamepad

/**
 * Una tecla de `GCKeyboard` —su `GCKeyCode`, que es el código de uso HID del
 * teclado (USB HID Usage Tables, página 0x07)— traducida al código de Android
 * de [GamepadKeyCodes]. `null` para las teclas que no se pueden mapear: las
 * de función, el bloque numérico, los símbolos. Con letras, cifras, flechas
 * y modificadores sobra para doce botones y el soplido.
 */
internal fun androidKeyCodeForHid(hidUsage: Long): Int? = when (hidUsage) {
    in 4L..29L -> GamepadKeyCodes.KEY_A + (hidUsage - 4L).toInt()
    in 30L..38L -> GamepadKeyCodes.KEY_0 + (hidUsage - 29L).toInt()
    39L -> GamepadKeyCodes.KEY_0
    40L -> GamepadKeyCodes.ENTER
    41L -> GamepadKeyCodes.ESCAPE
    42L -> GamepadKeyCodes.DEL
    43L -> GamepadKeyCodes.TAB
    44L -> GamepadKeyCodes.SPACE
    54L -> GamepadKeyCodes.COMMA
    55L -> GamepadKeyCodes.PERIOD
    79L -> GamepadKeyCodes.DPAD_RIGHT
    80L -> GamepadKeyCodes.DPAD_LEFT
    81L -> GamepadKeyCodes.DPAD_DOWN
    82L -> GamepadKeyCodes.DPAD_UP
    224L -> GamepadKeyCodes.CTRL_LEFT
    225L -> GamepadKeyCodes.SHIFT_LEFT
    226L -> GamepadKeyCodes.ALT_LEFT
    228L -> GamepadKeyCodes.CTRL_RIGHT
    229L -> GamepadKeyCodes.SHIFT_RIGHT
    230L -> GamepadKeyCodes.ALT_RIGHT
    else -> null
}
