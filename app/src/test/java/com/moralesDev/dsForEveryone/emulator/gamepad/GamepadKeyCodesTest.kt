package com.moralesDev.dsForEveryone.emulator.gamepad

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [GamepadKeyCodes] copia como números las constantes de `KeyEvent`, porque
 * vive en el módulo común. Si alguno dejara de coincidir, los mapeos
 * guardados y el mapeo por defecto apuntarían a otros botones.
 */
class GamepadKeyCodesTest {

    @Test
    fun `cada codigo vale lo mismo que su constante de KeyEvent`() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, GamepadKeyCodes.DPAD_UP)
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, GamepadKeyCodes.DPAD_DOWN)
        assertEquals(KeyEvent.KEYCODE_DPAD_LEFT, GamepadKeyCodes.DPAD_LEFT)
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, GamepadKeyCodes.DPAD_RIGHT)
        assertEquals(KeyEvent.KEYCODE_BUTTON_A, GamepadKeyCodes.BUTTON_A)
        assertEquals(KeyEvent.KEYCODE_BUTTON_B, GamepadKeyCodes.BUTTON_B)
        assertEquals(KeyEvent.KEYCODE_BUTTON_X, GamepadKeyCodes.BUTTON_X)
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, GamepadKeyCodes.BUTTON_Y)
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, GamepadKeyCodes.BUTTON_L1)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, GamepadKeyCodes.BUTTON_R1)
        assertEquals(KeyEvent.KEYCODE_BUTTON_L2, GamepadKeyCodes.BUTTON_L2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_R2, GamepadKeyCodes.BUTTON_R2)
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBL, GamepadKeyCodes.BUTTON_THUMBL)
        assertEquals(KeyEvent.KEYCODE_BUTTON_THUMBR, GamepadKeyCodes.BUTTON_THUMBR)
        assertEquals(KeyEvent.KEYCODE_BUTTON_MODE, GamepadKeyCodes.BUTTON_MODE)
        assertEquals(KeyEvent.KEYCODE_BUTTON_START, GamepadKeyCodes.BUTTON_START)
        assertEquals(KeyEvent.KEYCODE_BUTTON_SELECT, GamepadKeyCodes.BUTTON_SELECT)
    }
}
