package com.moralesDev.dsForEveryone.ui.game.gamepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadDevice
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadMapping
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadSource
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Mapeo del primer mando conectado (entregable posterior a la fase 4).
 *
 * A propósito no hay aquí ningún `EmulatorController`: este destino sólo se
 * alcanza desde Configuración, nunca desde una partida en marcha (ver
 * `AppNavHost`), mismo motivo que `LayoutEditorViewModel`. Si en el futuro se
 * admite más de un mando a la vez, esta pantalla es la que elige cuál mapear;
 * hoy sólo el proyecto vio nunca uno solo conectado, así que toma el primero.
 *
 * Común a Android e iOS: los mandos llegan por [GamepadSource], y los códigos
 * de tecla son los mismos en las dos (`GamepadKeyCodes`).
 */
class GamepadMappingViewModel(
    private val settingsRepository: SettingsRepository,
    private val gamepads: GamepadSource,
) : ViewModel() {

    val connectedDevice: StateFlow<GamepadDevice?> = gamepads.connectedDevices
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, gamepads.connectedDevices.value.firstOrNull())

    val mapping: StateFlow<GamepadMapping> = combine(settingsRepository.settings, connectedDevice) { settings, device ->
        device?.let { settings.gamepadMappings[it.descriptor] } ?: GamepadMapping.DEFAULT
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GamepadMapping.DEFAULT)

    /** El objetivo esperando su próximo botón, o `null` si no se está capturando nada. */
    private val _capturingTarget = MutableStateFlow<GamepadTarget?>(null)
    val capturingTarget: StateFlow<GamepadTarget?> = _capturingTarget.asStateFlow()

    init {
        gamepads.isActive = true
    }

    override fun onCleared() {
        gamepads.isActive = false
        gamepads.cancelCapture()
    }

    fun beginCapture(target: GamepadTarget) {
        val device = connectedDevice.value ?: return
        _capturingTarget.value = target
        gamepads.beginCapture { keyCode ->
            _capturingTarget.value = null
            viewModelScope.launch {
                settingsRepository.setGamepadMapping(device.descriptor, mapping.value.with(target, keyCode))
            }
        }
    }

    fun cancelCapture() {
        _capturingTarget.value = null
        gamepads.cancelCapture()
    }

    /**
     * Vía de escape para quien esté capturando un botón y prefiera vaciar la
     * asignación en vez de fiarse del intercambio automático de
     * `GamepadMapping.with` — por ejemplo, para dejar A y B en blanco y
     * reasignarlos a mano en el orden que quiera.
     */
    fun leaveCapturingTargetBlank() {
        val target = _capturingTarget.value ?: return
        val device = connectedDevice.value ?: return
        _capturingTarget.value = null
        gamepads.cancelCapture()
        viewModelScope.launch {
            settingsRepository.setGamepadMapping(device.descriptor, mapping.value.withoutTarget(target))
        }
    }

    fun resetToDefault() {
        val device = connectedDevice.value ?: return
        viewModelScope.launch { settingsRepository.resetGamepadMapping(device.descriptor) }
    }
}
