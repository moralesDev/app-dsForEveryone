package com.moralesDev.dsForEveryone.data.save

import com.moralesDev.dsForEveryone.data.cheats.CheatsFileDto
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.storage.writeAtomically
import com.moralesDev.dsForEveryone.data.zip.ZipArchiveReader
import com.moralesDev.dsForEveryone.data.zip.ZipFormatException
import com.moralesDev.dsForEveryone.data.zip.ZipWriter
import com.moralesDev.dsForEveryone.util.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Sink
import okio.Source
import okio.buffer
import okio.use
import kotlin.random.Random
import kotlin.time.Clock

/** Un juego dentro de una copia, tal como lo describe el manifiesto. */
@Serializable
data class ArchiveGame(
    /** SHA-256 de la ROM: el mismo nombre que tiene su carpeta en `saves/`. */
    val id: String,
    /** Sólo para enseñarlo al confirmar. Nunca decide nada. */
    val title: String? = null,
)

@Serializable
internal data class ArchiveManifest(
    val format: String,
    val version: Int,
    val exportedAt: Long,
    val games: List<ArchiveGame>,
)

sealed interface ArchiveExportResult {
    /**
     * @param skippedFiles archivos que estaban en disco pero no habrían
     *   superado la importación (un estado truncado, por ejemplo) y por eso
     *   no viajan. Ver [SaveArchive.export].
     */
    data class Exported(val games: Int, val skippedFiles: Int) : ArchiveExportResult

    /** No había nada que guardar: ningún juego con partidas válidas. */
    data object NothingToExport : ArchiveExportResult

    data object WriteFailed : ArchiveExportResult
}

sealed interface ArchivePrepareResult {
    data class Ready(val prepared: PreparedImport) : ArchivePrepareResult

    /** El archivo no es una copia de partidas de esta aplicación. */
    data object NotABackup : ArchivePrepareResult

    /** Copia hecha por una versión más nueva de la aplicación. */
    data class NewerFormat(val version: Int) : ArchivePrepareResult

    /** Es una copia, pero algo dentro no supera las comprobaciones. */
    data object Corrupt : ArchivePrepareResult

    data object TooLarge : ArchivePrepareResult

    /** El proveedor de documentos no entregó el archivo. */
    data object Unreadable : ArchivePrepareResult
}

/** Un juego de una copia ya validada, con lo que hace falta para confirmar. */
data class PreparedGame(
    val id: String,
    val title: String?,
    /** Si este móvil ya tenía partidas de ese juego, que la importación reemplazará. */
    val replacesExisting: Boolean,
)

/**
 * Una copia leída, validada entera y copiada a la carpeta de preparación,
 * esperando a que el usuario confirme. Nada de `saves/` ha cambiado todavía.
 *
 * Quien la recibe debe terminarla con [SaveArchive.commit] o
 * [SaveArchive.discard]; si el proceso muere antes, la limpia
 * [SaveArchive.recoverInterrupted] en el siguiente arranque.
 */
class PreparedImport internal constructor(
    internal val directory: Path,
    val games: List<PreparedGame>,
)

data class CommitOutcome(val imported: Int, val failed: Int)

/**
 * Exporta e importa partidas como un `.zip` que replica `filesDir/saves/`.
 *
 * El formato, las decisiones y lo que se rechaza están en
 * `docs/copia-de-partidas.md`. Lo esencial para leer este código:
 *
 * - **Ninguna ruta se construye a partir del nombre de una entrada.** Los
 *   nombres se reconocen con una expresión cerrada ([parseEntry]) y la ruta
 *   de destino se recompone con las piezas reconocidas. Un nombre que no
 *   encaja rechaza la copia entera.
 * - **Una sola función decide qué archivo es aceptable** ([isAcceptable]), y
 *   la usan las dos direcciones. Exportar deja fuera lo que importar
 *   rechazaría, así que la aplicación no puede producir una copia que luego
 *   no sepa leer.
 * - **Importar no toca `saves/` hasta [commit]**, y entonces cada juego se
 *   reemplaza con dos `rename`, recuperables si el proceso muere en medio.
 *
 * Vive en el módulo común desde iOS-3 (docs/ios.md): el mismo código y el
 * mismo zip ([ZipWriter], [ZipArchiveReader]) en Android y en iOS, para que
 * una copia de un sistema se importe en el otro.
 */
class SaveArchive(
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val fileSystem = FileSystem.SYSTEM

    // ------------------------------------------------------------ exportar

    /**
     * Escribe la copia en [output] y lo cierra.
     *
     * @param titles nombre de cada juego por su id, para el manifiesto. Los
     *   que falten viajan sin título: el juego puede no estar ya en la
     *   biblioteca y sus partidas siguen siendo del usuario.
     * @param onlyRomId exportar sólo ese juego; `null` para todos.
     */
    suspend fun export(
        output: Sink,
        titles: Map<String, String>,
        onlyRomId: String? = null,
    ): ArchiveExportResult = withContext(ioDispatcher) {
        val candidates = if (onlyRomId != null) listOf(onlyRomId) else gameDirectoryIds()

        // Primera pasada: decidir qué viaja. Hace falta antes de escribir
        // nada porque el manifiesto va primero y lista los juegos.
        var skipped = 0
        val plan = candidates.mapNotNull { id ->
            val gameDir = storageManager.savesPath() / id
            val accepted = GAME_FILES.filter { relative ->
                val file = gameDir / relative
                val metadata = fileSystem.metadataOrNull(file)
                when {
                    metadata?.isRegularFile != true -> false
                    // Un battery.sav vacío es «todavía no ha guardado»
                    // (BatterySaveManager lo trata así), no un archivo dañado.
                    relative == BATTERY && (metadata.size ?: 0L) == 0L -> false
                    isAcceptable(parseEntry("$SAVES/$id/$relative")!!, file) -> true
                    else -> {
                        AppLog.w(TAG, "No se exporta $id/$relative: no superaría la importación")
                        skipped++
                        false
                    }
                }
            }
            if (accepted.isEmpty()) null else id to accepted
        }
        if (plan.isEmpty()) {
            runCatching { output.close() }
            return@withContext ArchiveExportResult.NothingToExport
        }

        val manifest = ArchiveManifest(
            format = FORMAT,
            version = FORMAT_VERSION,
            exportedAt = clock(),
            games = plan.map { (id, _) -> ArchiveGame(id, titles[id]) },
        )

        try {
            ZipWriter(output, modifiedAtMillis = manifest.exportedAt).use { zip ->
                zip.writeEntry(MANIFEST, json.encodeToString(ArchiveManifest.serializer(), manifest).encodeToByteArray())
                for ((id, files) in plan) {
                    for (relative in files) {
                        val bytes = fileSystem.read(storageManager.savesPath() / id / relative) { readByteArray() }
                        zip.writeEntry("$SAVES/$id/$relative", bytes)
                    }
                }
            }
            ArchiveExportResult.Exported(games = plan.size, skippedFiles = skipped)
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo escribir la copia", error)
            ArchiveExportResult.WriteFailed
        }
    }

    private fun gameDirectoryIds(): List<String> =
        (fileSystem.listOrNull(storageManager.savesPath()) ?: emptyList())
            .filter { fileSystem.metadataOrNull(it)?.isDirectory == true && ROM_ID.matches(it.name) }
            .map(Path::name)
            .sorted()

    // ------------------------------------------------------------ importar

    /**
     * Lee la copia entera, la valida y la deja preparada. No toca `saves/`.
     *
     * El archivo elegido se copia primero a la carpeta de preparación: el
     * lector de zip común ([ZipArchiveReader]) necesita poder saltar al
     * directorio central, y el flujo del selector de documentos no lo
     * permite. La copia se borra al terminar, vaya como vaya.
     *
     * @param open abre el archivo elegido; `null` si el proveedor no lo da.
     */
    suspend fun prepare(open: () -> Source?): ArchivePrepareResult = withContext(ioDispatcher) {
        val stagingRoot = storageManager.importStagingPath()
        val staging = stagingRoot / "import-${randomName()}"
        val incoming = stagingRoot / "incoming-${randomName()}.zip"
        val result = try {
            val source = open()
            if (source == null) {
                ArchivePrepareResult.Unreadable
            } else if (!copyBounded(source, incoming)) {
                ArchivePrepareResult.TooLarge
            } else {
                ZipArchiveReader(fileSystem, incoming).use { zip -> readInto(zip, staging) }
            }
        } catch (error: ZipFormatException) {
            AppLog.w(TAG, "La copia no es un zip legible", error)
            ArchivePrepareResult.Corrupt
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo leer la copia", error)
            ArchivePrepareResult.Unreadable
        } catch (error: IllegalStateException) {
            // writeAtomically no pudo renombrar dentro de la preparación.
            AppLog.e(TAG, "No se pudo preparar la copia", error)
            ArchivePrepareResult.Unreadable
        } finally {
            fileSystem.delete(incoming, mustExist = false)
        }
        if (result !is ArchivePrepareResult.Ready) fileSystem.deleteRecursively(staging, mustExist = false)
        result
    }

    /**
     * Copia [source] a [target] y lo cierra. `false` si pasa de
     * [MAX_TOTAL_BYTES]: un zip no puede descomprimirse en menos de lo que
     * ocupa comprimido, así que no tendría sentido seguir.
     */
    private fun copyBounded(source: Source, target: Path): Boolean {
        fileSystem.createDirectories(target.parent!!)
        source.buffer().use { input ->
            fileSystem.write(target) {
                var total = 0L
                while (true) {
                    val read = input.read(buffer, BUFFER_BYTES.toLong())
                    if (read == -1L) break
                    total += read
                    if (total > MAX_TOTAL_BYTES) return false
                    emitCompleteSegments()
                }
            }
        }
        return true
    }

    private fun readInto(zip: ZipArchiveReader, staging: Path): ArchivePrepareResult {
        val first = zip.entries.firstOrNull() ?: return ArchivePrepareResult.NotABackup
        if (first.name != MANIFEST) return ArchivePrepareResult.NotABackup
        val manifestBytes = zip.read(first, MAX_MANIFEST_BYTES) ?: return ArchivePrepareResult.NotABackup
        val manifest = try {
            json.decodeFromString(ArchiveManifest.serializer(), manifestBytes.decodeToString())
        } catch (error: SerializationException) {
            return ArchivePrepareResult.NotABackup
        } catch (error: IllegalArgumentException) {
            return ArchivePrepareResult.NotABackup
        }
        if (manifest.format != FORMAT) return ArchivePrepareResult.NotABackup
        if (manifest.version > FORMAT_VERSION) return ArchivePrepareResult.NewerFormat(manifest.version)
        if (manifest.version < 1) return ArchivePrepareResult.Corrupt

        val seen = HashSet<String>()
        val gamesWithFiles = LinkedHashSet<String>()
        var total = 0L
        for (entry in zip.entries.drop(1)) {
            // Las carpetas no crean nada: el destino sale de los archivos.
            if (entry.isDirectory) continue

            val path = parseEntry(entry.name) ?: return ArchivePrepareResult.Corrupt
            if (!seen.add(entry.name)) return ArchivePrepareResult.Corrupt

            // Topes sobre lo descomprimido de verdad, no sobre lo que declara
            // la cabecera del zip, que puede mentir.
            val bytes = zip.read(entry, path.limit) ?: return ArchivePrepareResult.TooLarge
            total += bytes.size
            if (total > MAX_TOTAL_BYTES) return ArchivePrepareResult.TooLarge

            if (!isAcceptable(path, bytes)) return ArchivePrepareResult.Corrupt

            writeAtomically(staging / path.gameId / path.relative, bytes)
            gamesWithFiles += path.gameId
        }

        val manifestIds = manifest.games.map(ArchiveGame::id)
        val consistent = gamesWithFiles.isNotEmpty() &&
            manifestIds.size == manifestIds.toSet().size &&
            manifestIds.toSet() == gamesWithFiles
        if (!consistent) return ArchivePrepareResult.Corrupt

        val games = manifest.games.map { game ->
            PreparedGame(
                id = game.id,
                title = game.title?.take(MAX_TITLE_CHARS),
                replacesExisting = storageManager.hasRomData(game.id),
            )
        }
        return ArchivePrepareResult.Ready(PreparedImport(staging, games))
    }

    /**
     * Reemplaza las partidas de cada juego de la copia por las de la copia.
     * Los juegos que no vienen en ella no se tocan.
     *
     * @param onlyRomId importar sólo ese juego (la entrada desde la hoja de
     *   opciones de una ROM); `null` para todos los de la copia.
     */
    suspend fun commit(prepared: PreparedImport, onlyRomId: String? = null): CommitOutcome =
        withContext(ioDispatcher) {
            val savesRoot = storageManager.savesPath().also(fileSystem::createDirectories)
            var imported = 0
            var failed = 0
            prepared.games
                .filter { onlyRomId == null || it.id == onlyRomId }
                .forEach { game ->
                    if (replaceGame(savesRoot, prepared.directory / game.id, game.id)) {
                        imported++
                    } else {
                        failed++
                    }
                }
            fileSystem.deleteRecursively(prepared.directory, mustExist = false)
            CommitOutcome(imported, failed)
        }

    /**
     * Abandona una copia preparada sin tocar nada.
     *
     * No es `suspend` a propósito: también se llama desde `onCleared` de un
     * ViewModel, cuando su ámbito ya está cancelado. Borrar la preparación son
     * unos pocos `unlink`; si aun así no llega a ocurrir, la limpia
     * [recoverInterrupted] en el siguiente arranque.
     */
    fun discard(prepared: PreparedImport) {
        fileSystem.deleteRecursively(prepared.directory, mustExist = false)
    }

    private fun replaceGame(savesRoot: Path, incoming: Path, id: String): Boolean {
        val target = savesRoot / id
        val replaced = savesRoot / "$REPLACED_PREFIX$id"
        fileSystem.deleteRecursively(replaced, mustExist = false)

        if (fileSystem.exists(target) && !rename(target, replaced)) {
            AppLog.e(TAG, "No se pudo apartar la carpeta actual de $id")
            return false
        }
        if (!rename(incoming, target)) {
            AppLog.e(TAG, "No se pudo colocar la carpeta importada de $id")
            // Deshacer: la carpeta de antes vuelve a su sitio.
            if (fileSystem.exists(replaced)) rename(replaced, target)
            return false
        }
        fileSystem.deleteRecursively(replaced, mustExist = false)
        return true
    }

    /** Lo que era `File.renameTo`: `false` en vez de excepción si no se puede. */
    private fun rename(from: Path, to: Path): Boolean = try {
        fileSystem.atomicMove(from, to)
        true
    } catch (_: IOException) {
        false
    }

    /**
     * Repara lo que deja una importación interrumpida por la muerte del
     * proceso. Se llama una vez al arrancar la aplicación, antes de que nada
     * pueda abrir un juego.
     *
     * - `.replaced-<id>` sin `<id>`: murió entre los dos `rename`. La carpeta
     *   de antes vuelve a su sitio; la importación de ese juego no ocurrió.
     * - `.replaced-<id>` con `<id>`: murió después de colocar la nueva y antes
     *   de borrar la vieja. La importación terminó; sobra la vieja.
     * - Cualquier preparación pendiente: ninguna confirmación sobrevive a la
     *   muerte del proceso, así que se borra.
     */
    fun recoverInterrupted() {
        val savesRoot = storageManager.savesPath()
        (fileSystem.listOrNull(savesRoot) ?: emptyList())
            .filter { fileSystem.metadataOrNull(it)?.isDirectory == true && it.name.startsWith(REPLACED_PREFIX) }
            .forEach { replaced ->
                val target = savesRoot / replaced.name.removePrefix(REPLACED_PREFIX)
                if (fileSystem.exists(target)) {
                    fileSystem.deleteRecursively(replaced, mustExist = false)
                } else if (!rename(replaced, target)) {
                    AppLog.e(TAG, "No se pudo restaurar ${replaced.name}")
                }
            }
        fileSystem.deleteRecursively(storageManager.importStagingPath(), mustExist = false)
    }

    // ------------------------------------------------------------ validación

    private fun isAcceptable(path: EntryPath, file: Path): Boolean {
        if ((fileSystem.metadataOrNull(file)?.size ?: 0L) > path.limit) return false
        val bytes = try {
            fileSystem.read(file) { readByteArray() }
        } catch (error: IOException) {
            return false
        }
        return isAcceptable(path, bytes)
    }

    /** La única definición de «este archivo puede viajar», en las dos direcciones. */
    private fun isAcceptable(path: EntryPath, bytes: ByteArray): Boolean = when (path.kind) {
        EntryKind.BATTERY -> bytes.isNotEmpty()

        EntryKind.CHEATS -> try {
            Json.decodeFromString(CheatsFileDto.serializer(), bytes.decodeToString())
            true
        } catch (error: SerializationException) {
            false
        } catch (error: IllegalArgumentException) {
            false
        }

        // Íntegro, de este juego y de esta ranura. La versión del core no se
        // mira a propósito: un estado de otra versión es un archivo sano del
        // usuario, y la hoja de ranuras ya sabe enseñarlo como no restaurable.
        EntryKind.STATE -> {
            val decoded = SaveStateContainer.decode(bytes)
            decoded is DecodedState.Valid &&
                decoded.header.romSha256.equals(path.gameId, ignoreCase = true) &&
                decoded.header.slot == path.slot
        }

        EntryKind.THUMBNAIL ->
            bytes.size >= PNG_SIGNATURE.size &&
                PNG_SIGNATURE.indices.all { bytes[it] == PNG_SIGNATURE[it] }
    }

    private enum class EntryKind { BATTERY, CHEATS, STATE, THUMBNAIL }

    private data class EntryPath(
        val gameId: String,
        /** Ruta dentro de la carpeta del juego, recompuesta, nunca copiada del zip. */
        val relative: String,
        val kind: EntryKind,
        val slot: Int,
    ) {
        val limit: Int
            get() = when (kind) {
                EntryKind.BATTERY -> MAX_BATTERY_BYTES
                EntryKind.CHEATS -> MAX_CHEATS_BYTES
                EntryKind.STATE -> SaveStateContainer.MAX_STATE_BYTES + SaveStateContainer.MAX_HEADER_BYTES
                EntryKind.THUMBNAIL -> MAX_THUMBNAIL_BYTES
            }
    }

    private fun parseEntry(name: String): EntryPath? {
        val match = ENTRY.matchEntire(name) ?: return null
        val gameId = match.groupValues[1]
        return when (val file = match.groupValues[2]) {
            BATTERY -> EntryPath(gameId, BATTERY, EntryKind.BATTERY, NO_SLOT)
            CHEATS -> EntryPath(gameId, CHEATS, EntryKind.CHEATS, NO_SLOT)
            else -> {
                val slotName = match.groupValues[3]
                val slot = if (slotName == "auto") SaveStateManager.AUTO_SLOT else slotName.removePrefix("slot_").toInt()
                val kind = if (match.groupValues[4] == "state") EntryKind.STATE else EntryKind.THUMBNAIL
                // Recompuesta para no arrastrar nada del nombre original.
                EntryPath(gameId, "states/$slotName.${match.groupValues[4]}", kind, slot)
                    .takeIf { file == it.relative }
            }
        }
    }

    /** Lo que era `UUID.randomUUID()` en el nombre: sólo tiene que no repetirse. */
    private fun randomName(): String = Random.nextLong().toULong().toString(16) + Random.nextLong().toULong().toString(16)

    companion object {
        const val MANIFEST = "dsforeveryone-saves.json"
        const val FORMAT = "dsforeveryone-saves"
        const val FORMAT_VERSION = 1

        /** Lo mismo que acepta «Importar .sav» desde la biblioteca. */
        const val MAX_BATTERY_BYTES = 4 * 1024 * 1024
        const val MAX_CHEATS_BYTES = 1024 * 1024
        const val MAX_THUMBNAIL_BYTES = 2 * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 1024 * 1024
        private const val MAX_TITLE_CHARS = 200

        /**
         * Seis ranuras de hasta ~32 MB por juego dan para muchos juegos antes
         * de llegar aquí. El tope existe para que un zip diseñado para
         * agotar el almacenamiento se rechace en vez de llenarlo.
         */
        const val MAX_TOTAL_BYTES = 4L * 1024 * 1024 * 1024

        private const val SAVES = "saves"
        private const val BATTERY = "battery.sav"
        private const val CHEATS = "cheats.json"
        private const val REPLACED_PREFIX = ".replaced-"
        private const val NO_SLOT = -1
        private const val BUFFER_BYTES = 64 * 1024
        private const val TAG = "SaveArchive"

        private val ROM_ID = Regex("[0-9a-f]{64}")

        private val ENTRY = Regex(
            """saves/([0-9a-f]{64})/(battery\.sav|cheats\.json|states/(slot_[1-5]|auto)\.(state|png))""",
        )

        /** Todo lo que puede haber en la carpeta de un juego, en el orden en que viaja. */
        private val GAME_FILES: List<String> = buildList {
            add(BATTERY)
            add(CHEATS)
            for (slot in listOf("auto") + (1..SaveStateManager.SLOT_COUNT).map { "slot_$it" }) {
                add("states/$slot.state")
                add("states/$slot.png")
            }
        }

        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
        )

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
