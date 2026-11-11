package com.moralesDev.dsForEveryone.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.data.cover.CoverError
import com.moralesDev.dsForEveryone.data.cover.PickResult
import com.moralesDev.dsForEveryone.data.cover.PickedImage
import com.moralesDev.dsForEveryone.data.rom.RomImportResult
import com.moralesDev.dsForEveryone.data.rom.RomInfo
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.data.save.SaveManager
import com.moralesDev.dsForEveryone.ui.UiMessage
import com.moralesDev.dsForEveryone.ui.archive.PendingArchiveImport
import com.moralesDev.dsForEveryone.ui.archive.SaveArchiveCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.cover_failed
import com.moralesDev.dsForEveryone.resources.cover_removed
import com.moralesDev.dsForEveryone.resources.cover_too_large
import com.moralesDev.dsForEveryone.resources.cover_unsupported
import com.moralesDev.dsForEveryone.resources.msg_already
import com.moralesDev.dsForEveryone.resources.msg_imported
import com.moralesDev.dsForEveryone.resources.msg_removed_all
import com.moralesDev.dsForEveryone.resources.msg_removed_keep
import com.moralesDev.dsForEveryone.resources.msg_rom_too_large
import com.moralesDev.dsForEveryone.resources.msg_sav_delete_failed
import com.moralesDev.dsForEveryone.resources.msg_sav_deleted
import com.moralesDev.dsForEveryone.resources.msg_sav_export_failed
import com.moralesDev.dsForEveryone.resources.msg_sav_exported
import com.moralesDev.dsForEveryone.resources.msg_sav_import_failed
import com.moralesDev.dsForEveryone.resources.msg_sav_imported
import com.moralesDev.dsForEveryone.resources.msg_sav_too_large
import com.moralesDev.dsForEveryone.resources.msg_unreadable
import com.moralesDev.dsForEveryone.resources.msg_unrecognized_rom
import com.moralesDev.dsForEveryone.resources.msg_zip_corrupt
import com.moralesDev.dsForEveryone.resources.msg_zip_encrypted
import com.moralesDev.dsForEveryone.resources.msg_zip_multiple
import com.moralesDev.dsForEveryone.resources.msg_zip_no_rom
import okio.use
import okio.buffer
import com.moralesDev.dsForEveryone.ui.files.PickedDocument
import com.moralesDev.dsForEveryone.data.rom.RomLibrary
import com.moralesDev.dsForEveryone.data.cover.CoverStore

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
    /** Una copia de partidas ya validada que reemplazaría las de este juego. */
    val pendingArchiveImport: PendingArchiveImport? = null,
) {
    val isEmpty: Boolean get() = allRoms.isEmpty()
    val hasNoResults: Boolean get() = !isEmpty && roms.isEmpty()
}

/** El juego del menú de opciones, con lo que decide qué acciones de partida tienen sentido. */
data class RomOptionsState(
    val rom: RomInfo,
    val hasBatterySave: Boolean,
    /** Si hay algo que exportar: partida, ranuras o trucos. */
    val hasSaveData: Boolean,
)

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

class RomLibraryViewModel(
    private val romRepository: RomLibrary,
    private val coverRepository: CoverStore,
    private val saveManager: SaveManager,
    saveArchive: SaveArchive,
) : ViewModel() {

    private val transient = MutableStateFlow(TransientState())

    private val archive = SaveArchiveCoordinator(saveArchive, viewModelScope) { message ->
        transient.update { it.copy(romOptions = null, message = message) }
    }
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
        archive.pending,
    ) { roms, currentQuery, state, _, pendingArchive ->
        val items = roms.map { rom ->
            LibraryItem(
                rom = rom,
                coverPath = coverRepository.coverPath(rom.id),
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
            pendingArchiveImport = pendingArchive,
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
    fun onRomPicked(document: PickedDocument?) {
        if (document == null) return
        viewModelScope.launch {
            transient.value = TransientState(isImporting = true)
            val message = when (val result = romRepository.import(document)) {
                is RomImportResult.Imported ->
                    UiMessage(Res.string.msg_imported, result.rom.displayName)

                is RomImportResult.AlreadyInLibrary ->
                    UiMessage(Res.string.msg_already, result.rom.displayName)

                RomImportResult.UnrecognizedRom -> UiMessage(Res.string.msg_unrecognized_rom)
                RomImportResult.Unreadable -> UiMessage(Res.string.msg_unreadable)
                RomImportResult.NoRomInZip -> UiMessage(Res.string.msg_zip_no_rom)
                is RomImportResult.MultipleRomsInZip ->
                    UiMessage(Res.string.msg_zip_multiple, result.count)
                RomImportResult.EncryptedZip -> UiMessage(Res.string.msg_zip_encrypted)
                RomImportResult.CorruptZip -> UiMessage(Res.string.msg_zip_corrupt)
                RomImportResult.RomTooLarge -> UiMessage(Res.string.msg_rom_too_large)
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
                    if (deleteSaves) Res.string.msg_removed_all else Res.string.msg_removed_keep,
                ),
            )
        }
    }

    /** Toque sobre el «…» de una fila: abre el menú de opciones del juego. */
    fun onRomOptionsRequested(item: LibraryItem) {
        // Se consulta al abrir la hoja y no se toma de [item]: una importación
        // desde Configuración pudo cambiarlo sin que la lista se recalculara.
        val romId = item.rom.id
        val options = RomOptionsState(
            rom = item.rom,
            hasBatterySave = saveManager.hasBattery(romId),
            hasSaveData = saveManager.usedBytes(romId) > 0,
        )
        transient.update { it.copy(romOptions = options) }
    }

    fun dismissRomOptions() {
        archive.cancel()
        transient.update { it.copy(romOptions = null, pendingSavImport = null, pendingSavDelete = false) }
    }

    // --------------------------------------------------- copia de partidas

    /** Exporta las partidas de un juego. Ver `docs/copia-de-partidas.md`. */
    fun exportArchive(rom: RomInfo, document: PickedDocument?) {
        archive.export(
            open = document?.let { it::openSink },
            titles = mapOf(rom.id to rom.displayName),
            onlyRomId = rom.id,
        )
    }

    /**
     * Importa desde la hoja de un juego: de la copia sólo se toma ese juego,
     * aunque traiga más. Si ya tenía partidas, se pide confirmación.
     */
    fun onArchiveImportPicked(romId: String, document: PickedDocument?) {
        archive.onPicked(open = document?.let { it::openSource }, onlyRomId = romId)
    }

    fun confirmArchiveImport() = archive.confirm()

    fun cancelArchiveImport() = archive.cancel()

    override fun onCleared() {
        archive.clear()
    }

    // --------------------------------------------------- partida de batería

    /**
     * Vuelca la partida de batería al documento elegido en el selector del
     * sistema. Un documento nulo sólo significa que el usuario cerró el selector
     * sin elegir dónde guardarlo.
     */
    fun exportSav(romId: String, document: PickedDocument?) {
        if (document == null) return
        viewModelScope.launch {
            val data = saveManager.loadBattery(romId)
            val written = data != null && runCatching {
                document.openSink()?.buffer()?.use { it.write(data) } != null
            }.getOrDefault(false)
            transient.update {
                it.copy(
                    romOptions = null,
                    message = UiMessage(
                        if (written) Res.string.msg_sav_exported else Res.string.msg_sav_export_failed,
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
    fun onSavImportPicked(romId: String, document: PickedDocument?) {
        if (document == null) return
        viewModelScope.launch {
            val bytes = runCatching {
                document.openSource()?.buffer()?.use { it.readByteArray() }
            }.getOrNull()
            when {
                bytes == null -> transient.update {
                    it.copy(romOptions = null, message = UiMessage(Res.string.msg_sav_import_failed))
                }

                bytes.size > MAX_SAV_BYTES -> transient.update {
                    it.copy(romOptions = null, message = UiMessage(Res.string.msg_sav_too_large))
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
                    if (written) Res.string.msg_sav_imported else Res.string.msg_sav_import_failed,
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
                        if (deleted) Res.string.msg_sav_deleted else Res.string.msg_sav_delete_failed,
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
    fun onImagePicked(document: PickedDocument?) {
        // De qué juego era se sabe por el estado, no por una variable de la
        // pantalla: el selector es otra aplicación y volver de ella puede
        // recomponer la biblioteca entera.
        val rom = (transient.value.coverAction as? CoverAction.Choose)?.rom
        if (document == null || rom == null) {
            dismissCoverAction()
            return
        }
        viewModelScope.launch {
            transient.update { it.copy(isProcessingCover = true) }
            when (val result = coverRepository.open(document)) {
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
            image.release()
            transient.update {
                it.copy(
                    coverAction = null,
                    isProcessingCover = false,
                    message = if (saved) null else UiMessage(Res.string.cover_failed),
                )
            }
            if (saved) coverRevision.update { revision -> revision + 1 }
        }
    }

    fun deleteCover(rom: RomInfo) {
        viewModelScope.launch {
            coverRepository.delete(rom.id)
            transient.update {
                it.copy(coverAction = null, message = UiMessage(Res.string.cover_removed))
            }
            coverRevision.update { revision -> revision + 1 }
        }
    }

    private fun CoverError.toMessage(): UiMessage = when (this) {
        CoverError.TOO_LARGE -> UiMessage(Res.string.cover_too_large)
        CoverError.UNSUPPORTED_TYPE -> UiMessage(Res.string.cover_unsupported)
        CoverError.UNREADABLE -> UiMessage(Res.string.cover_failed)
    }

    fun consumeMessage() {
        transient.value = transient.value.copy(message = null)
    }

    private fun LibraryItem.matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim().lowercase()
        return rom.displayName.lowercase().contains(needle) ||
            rom.internalTitle.lowercase().contains(needle)
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
