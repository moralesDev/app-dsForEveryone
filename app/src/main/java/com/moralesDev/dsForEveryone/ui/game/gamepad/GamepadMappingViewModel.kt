package com.moralesDev.dsForEveryone.ui.game.gamepad

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadDevice
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadInputRouter
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadManager
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadMapping
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
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
 */
@HiltViewModel
class GamepadMappingViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val gamepadManager: GamepadManager,
    private val gamepadInputRouter: GamepadInputRouter,
) : ViewModel() {

    val connectedDevice: StateFlow<GamepadDevice?> = gamepadManager.connectedDevices
        .map { it.firstOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, gamepadManager.connectedDevices.value.firstOrNull())

    val mapping: StateFlow<GamepadMapping> = combine(settingsRepository.settings, connectedDevice) { settings, device ->
        device?.let { settings.gamepadMappings[it.descriptor] } ?: GamepadMapping.DEFAULT
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GamepadMapping.DEFAULT)

    /** El objetivo esperando su próximo botón, o `null` si no se está capturando nada. */
    private val _capturingTarget = MutableStateFlow<GamepadTarget?>(null)
    val capturingTarget: StateFlow<GamepadTarget?> = _capturingTarget.asStateFlow()

    init {
        gamepadInputRouter.isActive = true
    }

    override fun onCleared() {
        gamepadInputRouter.isActive = false
        gamepadInputRouter.cancelCapture()
    }

    fun beginCapture(target: GamepadTarget) {
        val device = connectedDevice.value ?: return
        _capturingTarget.value = target
        gamepadInputRouter.beginCapture { keyCode ->
            _capturingTarget.value = null
            viewModelScope.launch {
                settingsRepository.setGamepadMapping(device.descriptor, mapping.value.with(target, keyCode))
            }
        }
    }

    fun cancelCapture() {
        _capturingTarget.value = null
        gamepadInputRouter.cancelCapture()
    }

    fun resetToDefault() {
        val device = connectedDevice.value ?: return
        viewModelScope.launch { settingsRepository.resetGamepadMapping(device.descriptor) }
    }
}
