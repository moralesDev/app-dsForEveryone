package com.moralesDev.dsForEveryone.emulator.gamepad

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Los mandos de Android vistos como [GamepadSource]: la detección de
 * [GamepadManager] y las pulsaciones que [GamepadInputRouter] recibe desde
 * `MainActivity.dispatchKeyEvent` y entrega a [GamepadKeyDispatcher].
 */
class AndroidGamepadSource(
    private val manager: GamepadManager,
    private val dispatcher: GamepadKeyDispatcher,
) : GamepadSource {
    override val connectedDevices: StateFlow<List<GamepadDevice>> get() = manager.connectedDevices
    override val connections: Flow<GamepadDevice> get() = manager.connections
    override val events: Flow<GamepadInput> get() = dispatcher.events

    override var isActive: Boolean
        get() = dispatcher.isActive
        set(value) {
            dispatcher.isActive = value
        }

    override fun beginCapture(onCaptured: (keyCode: Int) -> Unit) = dispatcher.beginCapture(onCaptured)

    override fun cancelCapture() = dispatcher.cancelCapture()
}
