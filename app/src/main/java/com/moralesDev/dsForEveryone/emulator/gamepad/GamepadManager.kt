package com.moralesDev.dsForEveryone.emulator.gamepad

import android.content.Context
import android.hardware.input.InputManager as SystemInputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Detecta mandos de juego conectados, sin distinguir Bluetooth de USB: para
 * Android ambos llegan como el mismo [InputDevice] genérico (ver
 * docs/entrada.md, «Mando físico»). No hace falta ningún permiso —el
 * `android.hardware.input.InputManager` del sistema (distinto del
 * `InputManager` propio del proyecto, que inyecta botones al core) enumera
 * dispositivos de entrada sin pedir nada—.
 *
 * Vive como singleton de la aplicación: la detección no depende de qué
 * pantalla esté abierta.
 */
class GamepadManager(context: Context) {

    private val systemInputManager =
        context.getSystemService(Context.INPUT_SERVICE) as SystemInputManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _connectedDevices = MutableStateFlow(currentGamepads())
    val connectedDevices: StateFlow<List<GamepadDevice>> = _connectedDevices.asStateFlow()

    /** Emite sólo en cada conexión *nueva* — para el aviso «Mando detectado», no para el estado en curso. */
    private val _connections = MutableSharedFlow<GamepadDevice>(extraBufferCapacity = 1)
    val connections: SharedFlow<GamepadDevice> = _connections.asSharedFlow()

    private val listener = object : SystemInputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh()
        override fun onInputDeviceRemoved(deviceId: Int) = refresh()
        override fun onInputDeviceChanged(deviceId: Int) = refresh()
    }

    init {
        systemInputManager.registerInputDeviceListener(listener, mainHandler)
    }

    /** El mando con este descriptor, si sigue conectado ahora mismo. */
    fun deviceFor(descriptor: String): GamepadDevice? =
        _connectedDevices.value.firstOrNull { it.descriptor == descriptor }

    private fun refresh() {
        val previous = _connectedDevices.value.map { it.descriptor }.toSet()
        val current = currentGamepads()
        _connectedDevices.value = current
        current.filter { it.descriptor !in previous }.forEach { _connections.tryEmit(it) }
    }

    private fun currentGamepads(): List<GamepadDevice> =
        systemInputManager.inputDeviceIds
            .asList()
            .mapNotNull(systemInputManager::getInputDevice)
            .filter(InputDevice::isGamepad)
            .map(InputDevice::toGamepadDevice)
}
