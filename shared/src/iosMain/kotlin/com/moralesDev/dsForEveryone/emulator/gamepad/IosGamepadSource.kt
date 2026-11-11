package com.moralesDev.dsForEveryone.emulator.gamepad

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.GameController.GCController
import platform.GameController.GCControllerButtonInput
import platform.GameController.GCControllerDidConnectNotification
import platform.GameController.GCControllerDidDisconnectNotification
import platform.GameController.GCExtendedGamepad

/**
 * Los mandos de iOS (framework GameController) vistos como [GamepadSource].
 * Sin permisos ni nada en `Info.plist`: un mando emparejado en Ajustes →
 * Bluetooth, o por cable, aparece en `GCController.controllers()`.
 *
 * Cada botón se traduce al código de tecla de Android que le corresponde
 * ([GamepadKeyCodes]): un mapeo guardado significa lo mismo en las dos
 * plataformas, y [GamepadMapping.DEFAULT] sirve tal cual. Lo que pase después
 * —captura o juego— lo decide [GamepadKeyDispatcher], común con Android.
 *
 * Sólo cuentan los mandos con perfil extendido (Xbox, DualShock, DualSense,
 * Switch Pro, MFi): los que tienen todos los botones de la DS.
 *
 * GameController entrega las pulsaciones en la cola principal, igual que
 * las notificaciones de conexión que se registran aquí.
 */
class IosGamepadSource(private val dispatcher: GamepadKeyDispatcher) : GamepadSource {

    private val _connectedDevices = MutableStateFlow(currentGamepads())
    override val connectedDevices: StateFlow<List<GamepadDevice>> = _connectedDevices.asStateFlow()

    private val _connections = MutableSharedFlow<GamepadDevice>(extraBufferCapacity = 1)
    override val connections: Flow<GamepadDevice> = _connections.asSharedFlow()

    override val events: Flow<GamepadInput> get() = dispatcher.events

    override var isActive: Boolean
        get() = dispatcher.isActive
        set(value) {
            dispatcher.isActive = value
        }

    override fun beginCapture(onCaptured: (keyCode: Int) -> Unit) = dispatcher.beginCapture(onCaptured)

    override fun cancelCapture() = dispatcher.cancelCapture()

    init {
        val center = NSNotificationCenter.defaultCenter
        val queue = NSOperationQueue.mainQueue
        center.addObserverForName(GCControllerDidConnectNotification, null, queue) { _ -> refresh() }
        center.addObserverForName(GCControllerDidDisconnectNotification, null, queue) { _ -> refresh() }
        gamepads().forEach(::listen)
    }

    private fun refresh() {
        val previous = _connectedDevices.value.map { it.descriptor }.toSet()
        val controllers = gamepads()
        controllers.forEach(::listen)
        val current = controllers.map { it.toGamepadDevice() }
        _connectedDevices.value = current
        current.filter { it.descriptor !in previous }.forEach { _connections.tryEmit(it) }
    }

    /**
     * Engancha cada botón del mando a [GamepadKeyDispatcher]. Se puede llamar
     * más de una vez con el mismo mando: cada manejador sustituye al anterior.
     */
    private fun listen(controller: GCController) {
        val pad = controller.extendedGamepad ?: return
        val descriptor = controller.descriptor()
        buttonsOf(pad).forEach { (button, keyCode) ->
            button?.setPressedChangedHandler { _, _, pressed ->
                dispatcher.dispatch(descriptor, keyCode, pressed)
            }
        }
    }

    private fun currentGamepads(): List<GamepadDevice> = gamepads().map { it.toGamepadDevice() }

    private fun gamepads(): List<GCController> =
        GCController.controllers().filterIsInstance<GCController>().filter { it.extendedGamepad != null }

    private companion object {
        /** Cada botón del perfil extendido con su código de Android equivalente. */
        fun buttonsOf(pad: GCExtendedGamepad): List<Pair<GCControllerButtonInput?, Int>> = listOf(
            pad.dpad.up to GamepadKeyCodes.DPAD_UP,
            pad.dpad.down to GamepadKeyCodes.DPAD_DOWN,
            pad.dpad.left to GamepadKeyCodes.DPAD_LEFT,
            pad.dpad.right to GamepadKeyCodes.DPAD_RIGHT,
            // Como en Android: A es el de abajo, B el de la derecha, X el
            // de la izquierda e Y el de arriba, lleve el mando las letras
            // que lleve.
            pad.buttonA to GamepadKeyCodes.BUTTON_A,
            pad.buttonB to GamepadKeyCodes.BUTTON_B,
            pad.buttonX to GamepadKeyCodes.BUTTON_X,
            pad.buttonY to GamepadKeyCodes.BUTTON_Y,
            pad.leftShoulder to GamepadKeyCodes.BUTTON_L1,
            pad.rightShoulder to GamepadKeyCodes.BUTTON_R1,
            pad.leftTrigger to GamepadKeyCodes.BUTTON_L2,
            pad.rightTrigger to GamepadKeyCodes.BUTTON_R2,
            pad.leftThumbstickButton to GamepadKeyCodes.BUTTON_THUMBL,
            pad.rightThumbstickButton to GamepadKeyCodes.BUTTON_THUMBR,
            pad.buttonMenu to GamepadKeyCodes.BUTTON_START,
            pad.buttonOptions to GamepadKeyCodes.BUTTON_SELECT,
            pad.buttonHome to GamepadKeyCodes.BUTTON_MODE,
        )

        /**
         * GameController no da un identificador que sobreviva a una
         * reconexión, así que el mapeo va por modelo: fabricante y tipo. Dos
         * mandos iguales comparten mapeo.
         */
        fun GCController.descriptor(): String =
            "ios:${vendorName ?: "?"}:${productCategory}"

        fun GCController.toGamepadDevice(): GamepadDevice =
            GamepadDevice(descriptor(), vendorName ?: productCategory)
    }
}
