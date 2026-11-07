package com.moralesDev.dsForEveryone.ui.settings

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.rom.RomRepository
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.data.settings.AppLanguage
import com.moralesDev.dsForEveryone.data.settings.EmulatorSettings
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.settings.DirectionalControl
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import com.moralesDev.dsForEveryone.ui.UiMessage
import com.moralesDev.dsForEveryone.ui.archive.PendingArchiveImport
import com.moralesDev.dsForEveryone.ui.archive.SaveArchiveCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    private val romRepository: RomRepository,
    saveArchive: SaveArchive,
) : ViewModel() {

    private val _message = MutableStateFlow<UiMessage?>(null)

    /** Resultado de la última exportación o importación de partidas. */
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val archive = SaveArchiveCoordinator(saveArchive, viewModelScope) { _message.value = it }

    /** Una copia validada que reemplazaría partidas existentes, esperando confirmación. */
    val pendingArchiveImport: StateFlow<PendingArchiveImport?> = archive.pending

    val archiveBusy: StateFlow<Boolean> = archive.busy

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

    fun setDirectionalControl(control: DirectionalControl) {
        viewModelScope.launch { settingsRepository.setDirectionalControl(control) }
    }

    fun setTouchMode(mode: TouchMode) {
        viewModelScope.launch { settingsRepository.setTouchMode(mode) }
    }

    fun setTouchOffsetDp(offsetDp: Float) {
        viewModelScope.launch { settingsRepository.setTouchOffsetDp(offsetDp) }
    }

    // --------------------------------------------------- copia de partidas

    /**
     * Exporta las partidas de todos los juegos, también los que ya no están
     * en la biblioteca: siguen siendo del usuario. Ver `docs/copia-de-partidas.md`.
     */
    fun exportAllSaves(contentResolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        _message.value = null
        viewModelScope.launch {
            val titles = romRepository.observeLibrary().first().associate { it.id to it.displayName }
            archive.export(open = { contentResolver.openOutputStream(uri) }, titles = titles)
        }
    }

    fun onArchiveImportPicked(contentResolver: ContentResolver, uri: Uri?) {
        _message.value = null
        archive.onPicked(open = uri?.let { { contentResolver.openInputStream(it) } })
    }

    fun confirmArchiveImport() = archive.confirm()

    fun cancelArchiveImport() = archive.cancel()

    override fun onCleared() {
        archive.clear()
    }

    private companion object {
        /** Sobrevive a una rotación sin volver a suscribirse a DataStore. */
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
