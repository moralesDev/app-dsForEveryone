package com.moralesDev.dsForEveryone.emulator.gamepad

import android.view.InputDevice

/**
 * Si [InputDevice] es un mando de juego de verdad: tiene que anunciar
 * `SOURCE_GAMEPAD` o `SOURCE_JOYSTICK`, y no ser virtual —Android sintetiza
 * dispositivos virtuales (teclado del sistema, etc.) que también podrían
 * traer esas fuentes por accidente en algunos fabricantes—.
 */
internal fun InputDevice.isGamepad(): Boolean =
    !isVirtual &&
        (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)

internal fun InputDevice.toGamepadDevice(): GamepadDevice = GamepadDevice(descriptor, name ?: descriptor)
