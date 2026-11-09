package com.moralesDev.dsForEveryone.ui.archive

import com.moralesDev.dsForEveryone.data.save.ArchiveExportResult
import com.moralesDev.dsForEveryone.data.save.ArchivePrepareResult
import com.moralesDev.dsForEveryone.data.save.PreparedImport
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.ui.UiMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.msg_archive_corrupt
import com.moralesDev.dsForEveryone.resources.msg_archive_export_failed
import com.moralesDev.dsForEveryone.resources.msg_archive_exported
import com.moralesDev.dsForEveryone.resources.msg_archive_exported_skipped
import com.moralesDev.dsForEveryone.resources.msg_archive_import_partial
import com.moralesDev.dsForEveryone.resources.msg_archive_imported
import com.moralesDev.dsForEveryone.resources.msg_archive_newer
import com.moralesDev.dsForEveryone.resources.msg_archive_not_backup
import com.moralesDev.dsForEveryone.resources.msg_archive_not_this_game
import com.moralesDev.dsForEveryone.resources.msg_archive_nothing
import com.moralesDev.dsForEveryone.resources.msg_archive_too_large
import com.moralesDev.dsForEveryone.resources.msg_archive_unreadable

/**
 * Una copia ya validada que reemplazaría partidas existentes, esperando a que
 * el usuario confirme. Si no reemplaza nada, no se pregunta: no hay nada que
 * perder, igual que al importar un `.sav` en un juego sin partida.
 */
data class PendingArchiveImport(
    internal val prepared: PreparedImport,
    /** El juego desde cuya hoja se importa, o `null` desde Configuración. */
    val onlyRomId: String?,
    /** Juegos que entrarán. */
    val gameCount: Int,
    /** De ellos, los que ya tenían partidas aquí y las perderán. */
    val replacedCount: Int,
)

/**
 * Exportar e importar una copia de partidas, desde la biblioteca (un juego) o
 * desde Configuración (todos). Las dos pantallas hacen exactamente lo mismo y
 * sólo cambia [onlyRomId][export]: por eso vive aquí y no repetido en dos
 * ViewModel.
 *
 * Recibe funciones que abren los flujos, no `Uri` ni `ContentResolver`, por
 * la misma razón que [com.moralesDev.dsForEveryone.ui.game.CheatsCoordinator]
 * vive aparte de su ViewModel: así se prueba en JVM contra un [SaveArchive]
 * real sobre una carpeta temporal.
 *
 * Los mensajes salen como [UiMessage] por [onMessage]: el texto lo resuelve
 * Compose con el idioma vigente.
 */
class SaveArchiveCoordinator(
    private val archive: SaveArchive,
    private val scope: CoroutineScope,
    private val onMessage: (UiMessage) -> Unit,
) {
    private val _pending = MutableStateFlow<PendingArchiveImport?>(null)
    val pending: StateFlow<PendingArchiveImport?> = _pending.asStateFlow()

    private val _busy = MutableStateFlow(false)

    /** Mientras se lee o escribe una copia: las entradas se deshabilitan. */
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /**
     * @param open el documento elegido en el selector; `null` si el usuario
     *   lo cerró sin elegir, y entonces no pasa nada.
     */
    fun export(
        open: (() -> OutputStream?)?,
        titles: Map<String, String>,
        onlyRomId: String? = null,
    ) {
        if (open == null) return
        work {
            val output = runCatching(open).getOrNull()
            val result = if (output == null) {
                ArchiveExportResult.WriteFailed
            } else {
                archive.export(output, titles, onlyRomId)
            }
            onMessage(
                when (result) {
                    is ArchiveExportResult.Exported ->
                        if (result.skippedFiles == 0) {
                            UiMessage(Res.string.msg_archive_exported)
                        } else {
                            UiMessage(Res.string.msg_archive_exported_skipped, result.skippedFiles)
                        }

                    ArchiveExportResult.NothingToExport -> UiMessage(Res.string.msg_archive_nothing)
                    ArchiveExportResult.WriteFailed -> UiMessage(Res.string.msg_archive_export_failed)
                },
            )
        }
    }

    /**
     * Lee y valida la copia elegida. Si reemplazaría partidas existentes la
     * deja en [pending] para confirmar; si no, la importa ya.
     */
    fun onPicked(open: (() -> InputStream?)?, onlyRomId: String? = null) {
        if (open == null) return
        work {
            // Un proveedor de documentos puede lanzar SecurityException al
            // abrir; para quien usa la app es lo mismo que no poder leerlo.
            val prepared = when (val result = archive.prepare { runCatching(open).getOrNull() }) {
                is ArchivePrepareResult.Ready -> result.prepared
                else -> {
                    onMessage(result.toMessage())
                    return@work
                }
            }

            val games = prepared.games.filter { onlyRomId == null || it.id == onlyRomId }
            if (games.isEmpty()) {
                archive.discard(prepared)
                onMessage(UiMessage(Res.string.msg_archive_not_this_game))
                return@work
            }

            val replaced = games.count { it.replacesExisting }
            if (replaced == 0) {
                commit(prepared, onlyRomId)
            } else {
                _pending.value = PendingArchiveImport(prepared, onlyRomId, games.size, replaced)
            }
        }
    }

    fun confirm() {
        val pending = _pending.value ?: return
        _pending.value = null
        work { commit(pending.prepared, pending.onlyRomId) }
    }

    fun cancel() {
        val pending = _pending.value ?: return
        _pending.value = null
        archive.discard(pending.prepared)
    }

    /** Para `onCleared`: una confirmación que nadie va a contestar no deja megas preparados. */
    fun clear() = cancel()

    private suspend fun commit(prepared: PreparedImport, onlyRomId: String?) {
        val outcome = archive.commit(prepared, onlyRomId)
        onMessage(
            if (outcome.failed == 0) {
                UiMessage(Res.string.msg_archive_imported)
            } else {
                UiMessage(Res.string.msg_archive_import_partial, outcome.imported, outcome.failed)
            },
        )
    }

    private fun work(block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        scope.launch {
            try {
                block()
            } finally {
                _busy.value = false
            }
        }
    }

    private fun ArchivePrepareResult.toMessage(): UiMessage = when (this) {
        is ArchivePrepareResult.Ready -> UiMessage(Res.string.msg_archive_imported)
        ArchivePrepareResult.NotABackup -> UiMessage(Res.string.msg_archive_not_backup)
        is ArchivePrepareResult.NewerFormat -> UiMessage(Res.string.msg_archive_newer)
        ArchivePrepareResult.Corrupt -> UiMessage(Res.string.msg_archive_corrupt)
        ArchivePrepareResult.TooLarge -> UiMessage(Res.string.msg_archive_too_large)
        ArchivePrepareResult.Unreadable -> UiMessage(Res.string.msg_archive_unreadable)
    }
}
