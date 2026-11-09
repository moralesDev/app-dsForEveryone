package com.moralesDev.dsForEveryone.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.settings.EmulatorSettings
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Estado que necesita la aplicación entera antes de pintar nada: el idioma
 * elegido y si ya se vio la presentación inicial.
 *
 * Mientras vale `null` no se dibuja ninguna pantalla, para no mostrar un
 * fotograma en el idioma equivocado ni la biblioteca antes del onboarding.
 */
class MainViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<EmulatorSettings?> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun onWelcomeFinished(skipNextTime: Boolean) {
        viewModelScope.launch { settingsRepository.setSkipWelcome(skipNextTime) }
    }
}
