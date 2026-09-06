package com.moralesDev.dsForEveryone.ui.game.layout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.settings.EmulatorSettings
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroup
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroupFraction
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Estado y persistencia del editor de disposición (entregable 2.7).
 *
 * A propósito no hay aquí ningún [com.moralesDev.dsForEveryone.emulator.EmulatorController]:
 * este destino sólo se alcanza desde Configuración, nunca desde una partida en
 * marcha (ver `AppNavHost`), así que «el editor sólo se abre con la emulación
 * detenida» se cumple por construcción del grafo y no hace falta comprobarlo
 * aquí con una bandera.
 */
@HiltViewModel
class LayoutEditorViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<EmulatorSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EmulatorSettings(),
    )

    fun setLayoutMode(mode: LayoutMode, portrait: Boolean) {
        viewModelScope.launch {
            if (portrait) settingsRepository.setPortraitLayoutMode(mode) else settingsRepository.setLandscapeLayoutMode(mode)
        }
    }

    fun setScreensSwapped(swapped: Boolean) {
        viewModelScope.launch { settingsRepository.setScreensSwapped(swapped) }
    }

    fun setScreenGap(gap: Float) {
        viewModelScope.launch { settingsRepository.setScreenGap(gap) }
    }

    fun setSecondaryScale(scale: Float) {
        viewModelScope.launch { settingsRepository.setSecondaryScale(scale) }
    }

    /**
     * Reemplaza sólo [group] dentro de [base] —el perfil completo vigente,
     * fracciones incluidas de los otros seis grupos— y persiste el resultado
     * entero: [com.moralesDev.dsForEveryone.emulator.layout.ControlLayoutProfile]
     * exige los siete grupos siempre, no admite guardar uno suelto.
     */
    fun setControlGroup(
        portrait: Boolean,
        base: Map<ControlGroup, ControlGroupFraction>,
        group: ControlGroup,
        placement: ControlGroupFraction,
    ) {
        val updated = base + (group to placement)
        viewModelScope.launch {
            if (portrait) {
                settingsRepository.setPortraitControlLayout(updated)
            } else {
                settingsRepository.setLandscapeControlLayout(updated)
            }
        }
    }

    /** Borra el perfil guardado de [portrait]: la pantalla de juego y este editor vuelven al perfil por defecto. */
    fun resetControlLayout(portrait: Boolean) {
        viewModelScope.launch {
            if (portrait) settingsRepository.setPortraitControlLayout(null) else settingsRepository.setLandscapeControlLayout(null)
        }
    }
}
