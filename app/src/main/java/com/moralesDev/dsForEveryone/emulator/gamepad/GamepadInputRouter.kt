package com.moralesDev.dsForEveryone.emulator.gamepad

import android.view.KeyEvent

/**
 * Único punto donde entra un `KeyEvent` de mando, venga de Bluetooth o de
 * USB. Se llama desde `Activity.dispatchKeyEvent`, el punto más alto posible:
 * intercepta antes de que el evento le llegue al foco de Compose.
 *
 * Aquí sólo queda lo de Android —que el evento sea de un mando de verdad y
 * no una auto-repetición—; adónde va la pulsación lo decide
 * [GamepadKeyDispatcher], común con iOS.
 */
class GamepadInputRouter(private val dispatcher: GamepadKeyDispatcher) {

    /**
     * Llamar desde `Activity.dispatchKeyEvent`. Devuelve `true` si el evento
     * era de un mando y quedó consumido aquí —no debe seguir su camino normal
     * hacia el foco de Compose—.
     */
    fun dispatch(event: KeyEvent): Boolean {
        if (!dispatcher.isActive) return false
        val device = event.device ?: return false
        if (!device.isGamepad()) return false
        // Auto-repetición de tener el botón mantenido: ya se maneja con el propio ACTION_DOWN/UP, se ignora sin dejarla pasar.
        if (event.repeatCount > 0) return true
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true
        return dispatcher.dispatch(device.descriptor, event.keyCode, event.action == KeyEvent.ACTION_DOWN)
    }
}
