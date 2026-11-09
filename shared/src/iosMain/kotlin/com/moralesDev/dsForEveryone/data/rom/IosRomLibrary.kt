package com.moralesDev.dsForEveryone.data.rom

import com.moralesDev.dsForEveryone.data.db.RomDao
import com.moralesDev.dsForEveryone.data.db.RomEntity
import com.moralesDev.dsForEveryone.data.db.toIconBytes
import com.moralesDev.dsForEveryone.data.db.toRomInfo
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.zip.ZipArchiveReader
import com.moralesDev.dsForEveryone.data.zip.ZipFormatException
import com.moralesDev.dsForEveryone.ui.files.ExistingFile
import com.moralesDev.dsForEveryone.ui.files.PickedDocument
import com.moralesDev.dsForEveryone.util.AppLog
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.use
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.create
import kotlin.time.Clock

/**
 * La biblioteca en iOS: lo mismo que `RomRepository` en Android, con lo que
 * da iOS para recordar un archivo sin copiarlo.
 *
 * Las ROMs **no se copian** a la app. Al añadir una se guarda un marcador
 * («bookmark») de la URL que dio el selector, en el mismo campo de la base de
 * datos que en Android guarda el `Uri` (`documentUri`, con el prefijo
 * [BOOKMARK_PREFIX]). Con ese marcador iOS vuelve a dar acceso al archivo en
 * su sitio, entre arranques, mientras no se mueva o se borre; si deja de
 * resolverse, el juego sale como no disponible, igual que en Android cuando
 * se pierde el permiso.
 *
 * Mismas reglas que en Android para un `.zip`: una sola ROM `.nds` dentro, y
 * no más de [MAX_UNCOMPRESSED_BYTES] descomprimida. Para leerlo se copia
 * primero a un temporal, porque el lector de zip común parte del directorio
 * central (ver ZipArchiveReader); el temporal se borra al terminar.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosRomLibrary(
    private val romDao: RomDao,
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
) : RomLibrary {

    private val fileSystem = FileSystem.SYSTEM

    override fun observeLibrary(): Flow<List<RomInfo>> =
        romDao.observeAll().map { entities ->
            entities.map { it.toRomInfo(isAvailable = resolve(it.documentUri) != null) }
        }

    override suspend fun findById(romId: String): RomInfo? = withContext(ioDispatcher) {
        romDao.findById(romId)?.let { it.toRomInfo(isAvailable = resolve(it.documentUri) != null) }
    }

    override suspend fun import(document: PickedDocument): RomImportResult = withContext(ioDispatcher) {
        val url = (document as? ExistingFile)?.url ?: return@withContext RomImportResult.Unreadable
        val access = url.startAccessingSecurityScopedResource()
        try {
            val path = url.path?.toPath() ?: return@withContext RomImportResult.Unreadable
            val bookmark = url.bookmarkDataWithOptions(0u, includingResourceValuesForKeys = null, relativeToURL = null, error = null)
                ?: return@withContext RomImportResult.Unreadable
            val reference = BOOKMARK_PREFIX + bookmark.base64EncodedStringWithOptions(0u)
            val fileName = url.lastPathComponent ?: path.name

            val fingerprint = when (val resolved = fingerprintOf(path)) {
                is Resolution.Ready -> resolved.fingerprint
                is Resolution.Failed -> return@withContext resolved.result
            }
            val header = fingerprint.header ?: return@withContext RomImportResult.UnrecognizedRom

            val existing = romDao.findById(fingerprint.id)
            if (existing != null) {
                romDao.updateLocation(fingerprint.id, reference, fileName)
                return@withContext RomImportResult.AlreadyInLibrary(
                    existing.copy(documentUri = reference, fileName = fileName).toRomInfo(),
                )
            }

            val entity = RomEntity(
                id = fingerprint.id,
                documentUri = reference,
                fileName = fileName,
                displayName = suggestDisplayName(fileName, header.title),
                internalTitle = header.title,
                gameCode = header.gameCode,
                icon = fingerprint.banner?.icon?.toIconBytes(),
                sizeBytes = fingerprint.sizeBytes,
                addedAt = Clock.System.now().toEpochMilliseconds(),
                lastPlayedAt = null,
            )
            romDao.upsert(entity)
            RomImportResult.Imported(entity.toRomInfo())
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo leer la ROM elegida", error)
            RomImportResult.Unreadable
        } finally {
            if (access) url.stopAccessingSecurityScopedResource()
        }
    }

    override suspend fun markPlayed(romId: String) = withContext(ioDispatcher) {
        romDao.markPlayed(romId, Clock.System.now().toEpochMilliseconds())
    }

    override suspend fun rename(romId: String, displayName: String) = withContext(ioDispatcher) {
        romDao.rename(romId, displayName)
    }

    override suspend fun remove(romId: String, deleteSaves: Boolean) = withContext(ioDispatcher) {
        romDao.delete(romId)
        if (deleteSaves) storageManager.deleteRomData(romId)
    }

    /**
     * La URL del archivo de una ROM ya añadida, o `null` si su marcador ya
     * no lleva a ningún sitio. Quien vaya a leerla tiene que pedir acceso con
     * `startAccessingSecurityScopedResource` (lo hará la carga de la ROM).
     */
    fun resolve(reference: String): NSURL? {
        if (!reference.startsWith(BOOKMARK_PREFIX)) return null
        val data = NSData.create(base64EncodedString = reference.removePrefix(BOOKMARK_PREFIX), options = 0u)
            ?: return null
        return memScoped {
            val stale = alloc<BooleanVar>()
            NSURL.URLByResolvingBookmarkData(data, options = 0u, relativeToURL = null, bookmarkDataIsStale = stale.ptr, error = null)
        }
    }

    /** Hash, cabecera y banner de la ROM: suelta, o la única de un `.zip`. */
    private fun fingerprintOf(path: Path): Resolution {
        // Por los primeros bytes, nunca por el nombre: igual que en Android.
        val head = fileSystem.read(path) { if (request(4)) readByteArray(4) else ByteArray(0) }
        if (!isZip(head)) {
            val fingerprinter = RomFingerprinter()
            fileSystem.source(path).buffer().use { source ->
                val buffer = ByteArray(RomFingerprinter.BUFFER_SIZE)
                while (true) {
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == -1) break
                    fingerprinter.update(buffer, read)
                }
            }
            return Resolution.Ready(fingerprinter.finish())
        }

        val copy = storageManager.newRomScratchPath()
        return try {
            fileSystem.copy(path, copy)
            ZipArchiveReader(fileSystem, copy).use { zip ->
                val roms = zip.entries.filter { !it.isDirectory && it.name.lowercase().endsWith(".nds") }
                when {
                    roms.isEmpty() -> Resolution.Failed(RomImportResult.NoRomInZip)
                    roms.size > 1 -> Resolution.Failed(RomImportResult.MultipleRomsInZip(roms.size))
                    else -> {
                        val bytes = zip.read(roms.single(), MAX_UNCOMPRESSED_BYTES.toInt())
                            ?: return Resolution.Failed(RomImportResult.RomTooLarge)
                        val fingerprinter = RomFingerprinter()
                        fingerprinter.update(bytes, bytes.size)
                        Resolution.Ready(fingerprinter.finish())
                    }
                }
            }
        } catch (error: ZipFormatException) {
            AppLog.w(TAG, "El .zip no se puede leer", error)
            Resolution.Failed(RomImportResult.CorruptZip)
        } finally {
            fileSystem.delete(copy, mustExist = false)
        }
    }

    private fun isZip(head: ByteArray): Boolean =
        head.size == 4 && head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
            head[2] == 0x03.toByte() && head[3] == 0x04.toByte()

    /** Igual que en Android: el nombre del archivo, o el título interno si no dice nada. */
    private fun suggestDisplayName(fileName: String, title: String): String {
        val withoutExtension = fileName.substringBeforeLast('.', fileName).trim()
        return withoutExtension.ifBlank { title.ifBlank { "Juego sin título" } }
    }

    private sealed interface Resolution {
        data class Ready(val fingerprint: RomFingerprint) : Resolution
        data class Failed(val result: RomImportResult) : Resolution
    }

    companion object {
        /** Lo que en Android es un `content://…`, aquí es un marcador en base64. */
        const val BOOKMARK_PREFIX = "bookmark:"

        /** El mismo tope que `RomZipReader` en Android: 1 GB. */
        const val MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024

        private const val TAG = "IosRomLibrary"
    }
}
