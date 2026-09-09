package com.moralesDev.dsForEveryone.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.cover.CoverError
import com.moralesDev.dsForEveryone.data.cover.CoverRepository
import com.moralesDev.dsForEveryone.data.cover.PickResult
import com.moralesDev.dsForEveryone.data.cover.PickedImage
import android.content.ContentResolver
import com.moralesDev.dsForEveryone.data.rom.RomImportResult
import com.moralesDev.dsForEveryone.data.rom.RomInfo
import com.moralesDev.dsForEveryone.data.rom.RomRepository
import com.moralesDev.dsForEveryone.data.save.SaveManager
import com.moralesDev.dsForEveryone.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    /** Todos los juegos de la biblioteca, sin filtrar. */
    val allRoms: List<LibraryItem> = emptyList(),
    /** Los que coinciden con [query]; es lo que se pinta. */
    val roms: List<LibraryItem> = emptyList(),
    val query: String = "",
    val isImporting: Boolean = false,
    val coverAction: CoverAction? = null,
    val isProcessingCover: Boolean = false,
    val message: UiMessage? = null,
    /** El juego cuyo menú de opciones («…») está abierto. */
    val romOptions: RomOptionsState? = null,
    /** Un `.sav` ya leído del selector, esperando que se confirme la sobrescritura. */
    val pendingSavImport: PendingSavImport? = null,
    val pendingSavDelete: Boolean = false,
) {
    val isEmpty: Boolean get() = allRoms.isEmpty()
    val hasNoResults: Boolean get() = !isEmpty && roms.isEmpty()
}

/** El juego del menú de opciones, con lo que decide qué acciones de partida tienen sentido. */
data class RomOptionsState(val rom: RomInfo, val hasBatterySave: Boolean)

/** Un `.sav` leído del selector, pendiente de confirmar que reemplace al actual. */
data class PendingSavImport(val romId: String, val payload: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is PendingSavImport && romId == other.romId && payload.contentEquals(other.payload)

    override fun hashCode(): Int = 31 * romId.hashCode() + payload.contentHashCode()
}

/**
 * Una ROM de la biblioteca junto con su portada.
 *
 * Sin «continuar partida» ni fecha del último guardado todavía: eso es un
 * entregable propio. [hasBatterySave] es la única pieza del sistema de
 * partidas que hace falta aquí —habilita o no «Extraer .sav» / «Eliminar
 * .sav» en el menú de opciones—. [RomInfo.lastPlayedAt] es de la biblioteca
 * en sí —lo pone [markPlayed] al abrir el juego— y no depende de nada de eso.
 */
data class LibraryItem(
    val rom: RomInfo,
    /**
     * Ruta de la portada elegida por el usuario, o `null` si usa el icono del
     * banner. La aplicación no descarga carátulas de ningún sitio.
     */
    val coverPath: String? = null,
    /**
     * Cuándo cambió esa portada. Sirve de clave al pintar: la ruta no cambia al
     * reemplazar la imagen, así que sin esto se quedaría en pantalla la vieja.
     */
    val coverVersion: Long = 0L,
    val hasBatterySave: Boolean = false,
) {
    val hasCustomCover: Boolean get() = coverPath != null
}

/** Qué se está haciendo con la portada de un juego. */
sealed interface CoverAction {
    /** Hoja con «Agregar/Cambiar/Eliminar portada». */
    data class Choose(val rom: RomInfo, val hasCover: Boolean) : CoverAction

    /** Encuadre de la imagen recién elegida. */
    data class Crop(val rom: RomInfo, val image: PickedImage) : CoverAction

    /** Confirmación antes de quitarla. */
    data class ConfirmDelete(val rom: RomInfo) : CoverAction
}

@HiltViewModel
class RomLibraryViewModel @Inject constructor(
    private val romRepository: RomRepository,
    private val coverRepository: CoverRepository,
    private val saveManager: SaveManager,
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())
    private val query = MutableStateFlow("")

    /**
     * Cambia cada vez que se toca una portada. Hace falta porque las portadas no
     * viven en la base de datos: sin esta señal, el flujo de la biblioteca no se
     * enteraría de que hay una imagen nueva que pintar.
     */
    private val coverRevision = MutableStateFlow(0)

    val uiState: StateFlow<LibraryUiState> = combine(
        romRepository.observeLibrary(),
        query,
        transient,
        coverRevision,
    ) { roms, currentQuery, state, _ ->
        val items = roms.map { rom ->
            LibraryItem(
                rom = rom,
                coverPath = coverRepository.coverFile(rom.id).takeIf { it.exists() }?.absolutePath,
                coverVersion = coverRepository.coverVersion(rom.id),
                hasBatterySave = saveManager.hasBattery(rom.id),
            )
        }
        LibraryUiState(
            allRoms = items,
            roms = items.filter { it.matches(currentQuery) },
            query = currentQuery,
            isImporting = state.isImporting,
            coverAction = state.coverAction,
            isProcessingCover = state.isProcessingCover,
            message = state.message,
            romOptions = state.romOptions,
            pendingSavImport = state.pendingSavImport,
            pendingSavDelete = state.pendingSavDelete,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = LibraryUiState(),
    )

    fun onQueryChange(value: String) {
        query.value = value
    }

    /**
     * Recibe el documento elegido en el selector del sistema. Un `null` sólo
     * significa que el usuario canceló.
     */
    fun onRomPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            transient.value = TransientState(isImporting = true)
            val message = when (val result = romRepository.import(uri.toString())) {
                is RomImportResult.Imported ->
                    UiMessage(R.string.msg_imported, result.rom.displayName)

                is RomImportResult.AlreadyInLibrary ->
                    UiMessage(R.string.msg_already, result.rom.displayName)

                RomImportResult.UnrecognizedRom -> UiMessage(R.string.msg_unrecognized_rom)
                RomImportResult.Unreadable -> UiMessage(R.string.msg_unreadable)
                RomImportResult.NoRomInZip -> UiMessage(R.string.msg_zip_no_rom)
                is RomImportResult.MultipleRomsInZip ->
                    UiMessage(R.string.msg_zip_multiple, result.count)
                RomImportResult.EncryptedZip -> UiMessage(R.string.msg_zip_encrypted)
                RomImportResult.CorruptZip -> UiMessage(R.string.msg_zip_corrupt)
                RomImportResult.RomTooLarge -> UiMessage(R.string.msg_rom_too_large)
            }
            transient.value = TransientState(message = message)
        }
    }

    /** Marca el momento en que se abre un juego. Independiente del sistema de partidas. */
    fun onOpen(romId: String) {
        viewModelScope.launch { romRepository.markPlayed(romId) }
    }

    fun remove(romId: String, deleteSaves: Boolean) {
        viewModelScope.launch {
            romRepository.remove(romId, deleteSaves)
            transient.value = TransientState(
                message = UiMessage(
                    if (deleteSaves) R.string.msg_removed_all else R.string.msg_removed_keep,
                ),
            )
        }
    }

    /** Toque sobre el «…» de una fila: abre el menú de opciones del juego. */
    fun onRomOptionsRequested(item: LibraryItem) {
        transient.update { it.copy(romOptions = RomOptionsState(item.rom, item.hasBatterySave)) }
    }

    fun dismissRomOptions() =
        transient.update { it.copy(romOptions = null, pendingSavImport = null, pendingSavDelete = false) }

    // --------------------------------------------------- partida de batería

    /**
     * Vuelca la partida de batería al documento elegido en el selector del
     * sistema. Un `uri` nulo sólo significa que el usuario cerró el selector
     * sin elegir dónde guardarlo.
     */
    fun exportSav(romId: String, contentResolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val data = saveManager.loadBattery(romId)
            val written = data != null && runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(data) }
                true
            }.getOrDefault(false)
            transient.update {
                it.copy(
                    romOptions = null,
                    message = UiMessage(
                        if (written) R.string.msg_sav_exported else R.string.msg_sav_export_failed,
                    ),
                )
            }
        }
    }

    /**
     * Recibe el `.sav` elegido para importar. Un archivo de DS no lleva
     * ninguna identidad dentro —a diferencia de los save states, aquí no hay
     * forma de comprobar que pertenezca a este juego—, así que si ya había
     * una partida se pide confirmar antes de sobrescribirla; si no había
     * ninguna, se escribe directo porque no hay nada que perder.
     */
    fun onSavImportPicked(romId: String, contentResolver: ContentResolver, uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch {
            val bytes = runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull()
            when {
                bytes == null -> transient.update {
                    it.copy(romOptions = null, message = UiMessage(R.string.msg_sav_import_failed))
                }

                bytes.size > MAX_SAV_BYTES -> transient.update {
                    it.copy(romOptions = null, message = UiMessage(R.string.msg_sav_too_large))
                }

                saveManager.hasBattery(romId) -> transient.update {
                    it.copy(pendingSavImport = PendingSavImport(romId, bytes))
                }

                else -> writeSavImport(romId, bytes)
            }
        }
    }

    fun confirmSavImport() {
        val pending = transient.value.pendingSavImport ?: return
        viewModelScope.launch { writeSavImport(pending.romId, pending.payload) }
    }

    fun cancelSavImport() = transient.update { it.copy(pendingSavImport = null) }

    private suspend fun writeSavImport(romId: String, data: ByteArray) {
        val written = saveManager.persistBattery(romId, data)
        transient.update {
            it.copy(
                romOptions = null,
                pendingSavImport = null,
                message = UiMessage(
                    if (written) R.string.msg_sav_imported else R.string.msg_sav_import_failed,
                ),
            )
        }
    }

    /**
     * Pide confirmar el borrado. Es el rescate cuando un import salió mal: no
     * hay forma de deshacerlo, pero sí de volver a empezar en blanco en vez de
     * quedarse con una partida corrupta para siempre.
     */
    fun requestSavDeletion() = transient.update { it.copy(pendingSavDelete = true) }

    fun cancelSavDelete() = transient.update { it.copy(pendingSavDelete = false) }

    fun confirmSavDelete() {
        val romId = transient.value.romOptions?.rom?.id ?: return
        viewModelScope.launch {
            val deleted = saveManager.deleteBattery(romId)
            transient.update {
                it.copy(
                    romOptions = null,
                    pendingSavDelete = false,
                    message = UiMessage(
                        if (deleted) R.string.msg_sav_deleted else R.string.msg_sav_delete_failed,
                    ),
                )
            }
        }
    }

    // ------------------------------------------------------------- portadas

    /** Toque sobre la portada de un juego. */
    fun onCoverTapped(item: LibraryItem) {
        transient.update {
            it.copy(coverAction = CoverAction.Choose(item.rom, item.hasCustomCover))
        }
    }

    fun dismissCoverAction() = transient.update { it.copy(coverAction = null) }

    fun requestCoverDeletion(rom: RomInfo) =
        transient.update { it.copy(coverAction = CoverAction.ConfirmDelete(rom)) }

    /**
     * Recibe la imagen del selector del sistema. Un `null` sólo significa que el
     * usuario cerró el selector sin elegir.
     *
     * Si la imagen no sirve se avisa y **no se toca la portada actual**: quien
     * ya tenía una la conserva, y quien no tenía sigue con el icono del banner.
     */
    fun onImagePicked(uri: Uri?) {
        // De qué juego era se sabe por el estado, no por una variable de la
        // pantalla: el selector es otra aplicación y volver de ella puede
        // recomponer la biblioteca entera.
        val rom = (transient.value.coverAction as? CoverAction.Choose)?.rom
        if (uri == null || rom == null) {
            dismissCoverAction()
            return
        }
        viewModelScope.launch {
            transient.update { it.copy(isProcessingCover = true) }
            when (val result = coverRepository.open(uri)) {
                is PickResult.Ready -> transient.update {
                    it.copy(
                        coverAction = CoverAction.Crop(rom, result.image),
                        isProcessingCover = false,
                    )
                }

                is PickResult.Failed -> transient.update {
                    it.copy(
                        coverAction = null,
                        isProcessingCover = false,
                        message = result.error.toMessage(),
                    )
                }
            }
        }
    }

    fun saveCover(rom: RomInfo, image: PickedImage, zoom: Float, centerX: Float, centerY: Float) {
        viewModelScope.launch {
            transient.update { it.copy(isProcessingCover = true) }
            val saved = coverRepository.save(rom.id, image, zoom, centerX, centerY)
            image.bitmap.recycle()
            transient.update {
                it.copy(
                    coverAction = null,
                    isProcessingCover = false,
                    message = if (saved) null else UiMessage(R.string.cover_failed),
                )
            }
            if (saved) coverRevision.update { revision -> revision + 1 }
        }
    }

    fun deleteCover(rom: RomInfo) {
        viewModelScope.launch {
            coverRepository.delete(rom.id)
            transient.update {
                it.copy(coverAction = null, message = UiMessage(R.string.cover_removed))
            }
            coverRevision.update { revision -> revision + 1 }
        }
    }

    private fun CoverError.toMessage(): UiMessage = when (this) {
        CoverError.TOO_LARGE -> UiMessage(R.string.cover_too_large)
        CoverError.UNSUPPORTED_TYPE -> UiMessage(R.string.cover_unsupported)
        CoverError.UNREADABLE -> UiMessage(R.string.cover_failed)
    }

    fun consumeMessage() {
        transient.value = transient.value.copy(message = null)
    }

    private fun LibraryItem.matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim().lowercase(Locale.getDefault())
        return rom.displayName.lowercase(Locale.getDefault()).contains(needle) ||
            rom.internalTitle.lowercase(Locale.getDefault()).contains(needle)
    }

    private data class TransientState(
        val isImporting: Boolean = false,
        val coverAction: CoverAction? = null,
        val isProcessingCover: Boolean = false,
        val message: UiMessage? = null,
        val romOptions: RomOptionsState? = null,
        val pendingSavImport: PendingSavImport? = null,
        val pendingSavDelete: Boolean = false,
    )

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        /**
         * Una partida de DS real no pasa de 1 Mbit (128 KiB, EEPROM/FRAM) o,
         * en cartuchos con Flash grande, de 8 Mbit (1 MiB). El margen es para
         * no rechazar por poco un chip atípico sin abrir la puerta a que
         * alguien importe por error una ROM entera pensando que es un `.sav`.
         */
        const val MAX_SAV_BYTES = 4L * 1024 * 1024
    }
}
