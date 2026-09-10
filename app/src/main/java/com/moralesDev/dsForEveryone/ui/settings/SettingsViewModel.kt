package com.moralesDev.dsForEveryone.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.settings.AppLanguage
import com.moralesDev.dsForEveryone.data.settings.EmulatorSettings
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Los ajustes que no pertenecen a una partida concreta.
 *
 * Todo lo que se toca aquí es el **mismo** valor que ya usan la pantalla de
 * juego, el editor de disposición o la aplicación entera: no hay una copia
 * «de ajustes» de nada. Cambiar el idioma o el filtro desde aquí es cambiar
 * lo que leerá el próximo juego que se abra —y el que esté abierto, si lo
 * está—, porque todos observan el mismo `SettingsRepository`.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<EmulatorSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = EmulatorSettings(),
    )

    /**
     * El idioma se aplica en caliente: `ProvideAppLanguage` envuelve toda la
     * navegación y vuelve a leer los textos en la siguiente recomposición, sin
     * reiniciar la Activity ni tocar los ajustes del sistema.
     */
    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { settingsRepository.setLanguage(language) }
    }

    /**
     * Si la presentación inicial vuelve a aparecer al abrir la aplicación. Se
     * guarda invertido —[EmulatorSettings.skipWelcome] es «no volver a
     * mostrarla»— porque así lo escribe la propia pantalla de bienvenida con
     * su casilla, y un ajuste no debe cambiar el significado de lo guardado.
     */
    fun setShowWelcome(show: Boolean) {
        viewModelScope.launch { settingsRepository.setSkipWelcome(!show) }
    }

    fun setImageFilter(filter: ImageFilter) {
        viewModelScope.launch { settingsRepository.setImageFilter(filter) }
    }

    fun setTouchMode(mode: TouchMode) {
        viewModelScope.launch { settingsRepository.setTouchMode(mode) }
    }

    fun setTouchOffsetDp(offsetDp: Float) {
        viewModelScope.launch { settingsRepository.setTouchOffsetDp(offsetDp) }
    }

    private companion object {
        /** Sobrevive a una rotación sin volver a suscribirse a DataStore. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
