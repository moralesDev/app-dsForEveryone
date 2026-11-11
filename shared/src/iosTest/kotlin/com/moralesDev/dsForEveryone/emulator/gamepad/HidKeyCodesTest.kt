package com.moralesDev.dsForEveryone.emulator.gamepad

import platform.GameController.GCKeyCodeDownArrow
import platform.GameController.GCKeyCodeKeyA
import platform.GameController.GCKeyCodeKeyQ
import platform.GameController.GCKeyCodeKeyS
import platform.GameController.GCKeyCodeKeyW
import platform.GameController.GCKeyCodeKeyX
import platform.GameController.GCKeyCodeKeyZ
import platform.GameController.GCKeyCodeLeftArrow
import platform.GameController.GCKeyCodeDeleteOrBackspace
import platform.GameController.GCKeyCodeOne
import platform.GameController.GCKeyCodeReturnOrEnter
import platform.GameController.GCKeyCodeRightArrow
import platform.GameController.GCKeyCodeRightShift
import platform.GameController.GCKeyCodeSpacebar
import platform.GameController.GCKeyCodeUpArrow
import platform.GameController.GCKeyCodeZero
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [androidKeyCodeForHid] usa los números HID a pelo; aquí se comprueba contra
 * las constantes de GameController que siguen valiendo eso, y que las teclas
 * del mapeo por defecto del teclado llegan a lo que él espera.
 */
class HidKeyCodesTest {

    @Test
    fun `las teclas del mapeo por defecto se traducen a su codigo de Android`() {
        assertEquals(GamepadKeyCodes.KEY_X, androidKeyCodeForHid(GCKeyCodeKeyX))
        assertEquals(GamepadKeyCodes.KEY_Z, androidKeyCodeForHid(GCKeyCodeKeyZ))
        assertEquals(GamepadKeyCodes.KEY_S, androidKeyCodeForHid(GCKeyCodeKeyS))
        assertEquals(GamepadKeyCodes.KEY_A, androidKeyCodeForHid(GCKeyCodeKeyA))
        assertEquals(GamepadKeyCodes.KEY_Q, androidKeyCodeForHid(GCKeyCodeKeyQ))
        assertEquals(GamepadKeyCodes.KEY_W, androidKeyCodeForHid(GCKeyCodeKeyW))
        assertEquals(GamepadKeyCodes.ENTER, androidKeyCodeForHid(GCKeyCodeReturnOrEnter))
        assertEquals(GamepadKeyCodes.DEL, androidKeyCodeForHid(GCKeyCodeDeleteOrBackspace))
        assertEquals(GamepadKeyCodes.SPACE, androidKeyCodeForHid(GCKeyCodeSpacebar))
        assertEquals(GamepadKeyCodes.DPAD_UP, androidKeyCodeForHid(GCKeyCodeUpArrow))
        assertEquals(GamepadKeyCodes.DPAD_DOWN, androidKeyCodeForHid(GCKeyCodeDownArrow))
        assertEquals(GamepadKeyCodes.DPAD_LEFT, androidKeyCodeForHid(GCKeyCodeLeftArrow))
        assertEquals(GamepadKeyCodes.DPAD_RIGHT, androidKeyCodeForHid(GCKeyCodeRightArrow))
    }

    @Test
    fun `las cifras y los modificadores`() {
        assertEquals(GamepadKeyCodes.KEY_0 + 1, androidKeyCodeForHid(GCKeyCodeOne))
        assertEquals(GamepadKeyCodes.KEY_0, androidKeyCodeForHid(GCKeyCodeZero))
        assertEquals(GamepadKeyCodes.SHIFT_RIGHT, androidKeyCodeForHid(GCKeyCodeRightShift))
    }

    @Test
    fun `una tecla sin traduccion no llega`() {
        assertNull(androidKeyCodeForHid(58L)) // F1
    }
}
