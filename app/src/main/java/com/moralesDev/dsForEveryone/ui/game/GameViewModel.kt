package com.moralesDev.dsForEveryone.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.settings.EmulatorSettings
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.EmulatorStatus
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Sesión de juego contra `StubEmulatorCore` (entregable 2.6).
 *
 * Abre un "cartucho" que no es ninguna ROM real —el stub sólo necesita
 * suficientes bytes para pasar su comprobación de tamaño mínimo— y arranca en
 * marcha para tener siempre algo que jugar. La sesión de verdad —ROM elegida
 * por el usuario, partidas, save states— llega con la biblioteca real en la
 * fase 3, sobre esta misma pantalla.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val controller: EmulatorController,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<EmulatorSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EmulatorSettings(),
    )

    val status: StateFlow<EmulatorStatus> get() = controller.status

    /** Máscara real de doce bits: lo que se pinta pulsado sale de aquí, no de estado local del composable. */
    val pressedMask: StateFlow<Int> get() = controller.input.pressed

    val micNoiseActive: StateFlow<Boolean> get() = controller.input.micNoiseActive

    /** Lo lee el hilo de GL directamente; no pasa por Compose ni por corrutinas. */
    val currentFrame: IntArray? get() = controller.currentFrame

    init {
        viewModelScope.launch {
            controller.open(FAKE_CARTRIDGE, batterySave = null)
            controller.resume()
        }
    }

    fun onButton(button: DsButton, pressed: Boolean) = controller.input.setPressed(button, pressed)

    fun onDirections(directions: Set<DsButton>) = controller.input.setDirections(directions)

    fun onTouch(x: Int, y: Int, down: Boolean) = controller.input.setTouch(x, y, down)

    fun onMicNoise(active: Boolean) = controller.input.setMicNoise(active)

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun setLayoutMode(mode: LayoutMode, portrait: Boolean) {
        viewModelScope.launch {
            if (portrait) {
                settingsRepository.setPortraitLayoutMode(mode)
            } else {
                settingsRepository.setLandscapeLayoutMode(mode)
            }
        }
    }

    fun setTouchMode(mode: TouchMode) {
        viewModelScope.launch { settingsRepository.setTouchMode(mode) }
    }

    fun setImageFilter(filter: ImageFilter) {
        viewModelScope.launch { settingsRepository.setImageFilter(filter) }
    }

    /**
     * No bloquea: el propio hilo de emulación libera el core al salir del
     * bucle (ver [EmulatorController.stop]).
     */
    override fun onCleared() {
        controller.stop()
    }

    private companion object {
        /**
         * Suficiente para superar la comprobación de tamaño mínimo de
         * `StubEmulatorCore`; el contenido no importa, el stub no lee nada
         * más que el título.
         */
        val FAKE_CARTRIDGE = ByteArray(512)
    }
}
