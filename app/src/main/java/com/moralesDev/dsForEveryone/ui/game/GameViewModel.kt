package com.moralesDev.dsForEveryone.ui.game

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.settings.EmulatorSettings
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
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
 * ViewModel de la pantalla de prueba del renderizador GL (entregable 2.5).
 *
 * Abre un "cartucho" que no es ninguna ROM real —el stub sólo necesita
 * suficientes bytes para pasar su comprobación de tamaño mínimo— y arranca en
 * marcha para tener siempre algo que dibujar. La sesión de juego de verdad
 * —ROM elegida por el usuario, partidas, mandos y táctil reales— llega en el
 * entregable 2.6, cuando esta pantalla se reconstruye por completo.
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

    val framesPerSecond: StateFlow<Int> get() = controller.framesPerSecond

    /** Lo lee el hilo de GL directamente; no pasa por Compose ni por corrutinas. */
    val currentFrame: IntArray? get() = controller.currentFrame

    init {
        viewModelScope.launch {
            controller.open(FAKE_CARTRIDGE, batterySave = null)
            controller.resume()
        }
    }

    fun setLayoutMode(mode: LayoutMode, portrait: Boolean) {
        viewModelScope.launch {
            if (portrait) {
                settingsRepository.setPortraitLayoutMode(mode)
            } else {
                settingsRepository.setLandscapeLayoutMode(mode)
            }
        }
    }

    fun setImageFilter(filter: ImageFilter) {
        viewModelScope.launch { settingsRepository.setImageFilter(filter) }
    }

    fun setScreensSwapped(swapped: Boolean) {
        viewModelScope.launch { settingsRepository.setScreensSwapped(swapped) }
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
