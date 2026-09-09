package com.moralesDev.dsForEveryone.data.rom

import android.os.ParcelFileDescriptor
import com.moralesDev.dsForEveryone.data.db.RomDao
import com.moralesDev.dsForEveryone.data.db.RomEntity
import com.moralesDev.dsForEveryone.data.db.toIconBytes
import com.moralesDev.dsForEveryone.data.db.toRomInfo
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.PushbackInputStream

/**
 * Biblioteca de juegos: alta, consulta y lectura de ROMs.
 *
 * Las ROMs **no se copian** al almacenamiento de la aplicación. Se guarda el
 * `Uri` del documento que devuelve el Storage Access Framework y se pide un
 * permiso persistente sobre él, que sobrevive a reinicios del dispositivo. Así
 * la biblioteca recuerda los juegos sin duplicar cientos de megas por título
 * ni pedir permisos de almacenamiento global, que en Android moderno además ya
 * no se conceden para esto. La única excepción es transitoria, no una copia
 * duradera: ver [copyToScratchDescriptor].
 *
 * El precio es que el usuario puede mover o borrar el archivo por debajo. Ese
 * caso se detecta ([isAvailable]) y se marca en la biblioteca en lugar de
 * fallar al arrancar el juego.
 */
class RomRepository(
    private val documentAccess: DocumentAccess,
    private val romDao: RomDao,
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observeLibrary(): Flow<List<RomInfo>> =
        romDao.observeAll().map { entities ->
            entities.map { entity -> entity.toRomInfo(isAvailable = documentAccess.hasReadPermission(entity.documentUri)) }
        }

    suspend fun findById(romId: String): RomInfo? = withContext(ioDispatcher) {
        romDao.findById(romId)?.let { entity ->
            entity.toRomInfo(isAvailable = documentAccess.hasReadPermission(entity.documentUri))
        }
    }

    /**
     * Da de alta el documento elegido por el usuario. Admite un `.nds` suelto,
     * o un `.zip` que traiga uno adentro (ver [resolveFingerprint]).
     *
     * Calcula el hash leyendo el archivo una sola vez: identificar la ROM y
     * validar su cabecera comparten el mismo recorrido.
     */
    suspend fun import(uri: String): RomImportResult = withContext(ioDispatcher) {
        try {
            documentAccess.takePersistableReadPermission(uri)

            val fileName = documentAccess.displayNameOf(uri) ?: uri.substringAfterLast('/')
            val fingerprint = when (val resolved = resolveFingerprint(uri)) {
                is FingerprintResolution.Ready -> resolved.fingerprint
                FingerprintResolution.Unreadable -> return@withContext RomImportResult.Unreadable
                FingerprintResolution.NoRomInZip -> return@withContext RomImportResult.NoRomInZip
                is FingerprintResolution.MultipleRomsInZip ->
                    return@withContext RomImportResult.MultipleRomsInZip(resolved.count)
                FingerprintResolution.EncryptedZip -> return@withContext RomImportResult.EncryptedZip
                FingerprintResolution.CorruptZip -> return@withContext RomImportResult.CorruptZip
                FingerprintResolution.RomTooLarge -> return@withContext RomImportResult.RomTooLarge
            }

            val header = fingerprint.header
                ?: return@withContext RomImportResult.UnrecognizedRom

            val existing = romDao.findById(fingerprint.id)
            if (existing != null) {
                romDao.updateLocation(fingerprint.id, uri, fileName)
                return@withContext RomImportResult.AlreadyInLibrary(
                    existing.copy(documentUri = uri, fileName = fileName).toRomInfo(),
                )
            }

            val entity = RomEntity(
                id = fingerprint.id,
                documentUri = uri,
                fileName = fileName,
                displayName = suggestDisplayName(fileName, header.title),
                internalTitle = header.title,
                gameCode = header.gameCode,
                icon = fingerprint.banner?.icon?.toIconBytes(),
                sizeBytes = fingerprint.sizeBytes,
                addedAt = System.currentTimeMillis(),
                lastPlayedAt = null,
            )
            romDao.upsert(entity)
            RomImportResult.Imported(entity.toRomInfo())
        } catch (error: SecurityException) {
            RomImportResult.Unreadable
        } catch (error: FileNotFoundException) {
            RomImportResult.Unreadable
        }
    }

    /**
     * Contenido completo de la ROM, listo para entregar al core.
     *
     * Si el documento es un `.zip` (por los mismos bytes de cabecera que en
     * [import], nunca por el nombre), se entrega el contenido ya
     * descomprimido de su única entrada de ROM — el core nunca ve el
     * contenedor.
     *
     * Copia el archivo entero a un `ByteArray`: para una ROM suelta grande
     * (128 MB no es raro en cartuchos reales de DS) eso puede agotar el heap
     * de Java. [openRomForCore] es la vía que lo evita cuando se puede; este
     * método sigue existiendo para el caso `.zip`, que sí necesita
     * descomprimirse en memoria, y para pruebas.
     */
    suspend fun readRomBytes(rom: RomInfo): ByteArray? = withContext(ioDispatcher) {
        try {
            val rawStream = documentAccess.openInputStream(rom.documentUri) ?: return@withContext null
            val pushback = PushbackInputStream(rawStream, ZIP_SIGNATURE_SIZE)

            if (!sniffIsZip(pushback)) {
                pushback.use { it.readBytes() }
            } else {
                pushback.close()
                RomZipReader.extractRom { openOrThrow(rom.documentUri) }
            }
        } catch (error: SecurityException) {
            null
        } catch (error: FileNotFoundException) {
            null
        }
    }

    /**
     * Como [readRomBytes], pero entrega la vía más barata para el core: un
     * descriptor de archivo sin copiar nada a un `ByteArray` de Kotlin cuando
     * la ROM está suelta (no dentro de un `.zip`), y sólo cae a bytes en
     * memoria cuando hace falta descomprimir. Ver
     * [com.moralesDev.dsForEveryone.emulator.core.EmulatorCore.loadFromDescriptor].
     *
     * Cuando el proveedor SAF no da descriptor directo, la vía de emergencia
     * es [copyToScratchDescriptor] — copia en flujo a un archivo temporal, no
     * [readRomBytes]: caer ahí revive el `OutOfMemoryError` que este método
     * existe para evitar (pasó con Pokémon Platino, 128 MB, en un proveedor
     * que no entregaba descriptor; ver `docs/estabilizacion-core.md`).
     */
    suspend fun openRomForCore(rom: RomInfo): RomSource? = withContext(ioDispatcher) {
        try {
            val rawStream = documentAccess.openInputStream(rom.documentUri) ?: return@withContext null
            val pushback = PushbackInputStream(rawStream, ZIP_SIGNATURE_SIZE)
            val isZip = sniffIsZip(pushback)
            pushback.close()

            if (isZip) {
                RomZipReader.extractRom { openOrThrow(rom.documentUri) }?.let(RomSource::Bytes)
            } else {
                val fd = documentAccess.openFileDescriptor(rom.documentUri)
                if (fd != null) RomSource.Descriptor(fd, fd.statSize) else copyToScratchDescriptor(rom)
            }
        } catch (error: SecurityException) {
            null
        } catch (error: FileNotFoundException) {
            null
        }
    }

    /**
     * Copia [rom] en flujo (buffer fijo, nunca un `ByteArray` con la ROM
     * entera) a un archivo temporal de [StorageManager.newRomScratchFile] y
     * abre un descriptor real sobre ese archivo para el core.
     *
     * El archivo se borra apenas se abre el descriptor, tenga éxito o no: en
     * Linux el inodo sigue vivo mientras el descriptor esté abierto, así que
     * el core lo sigue leyendo con normalidad y nunca queda una copia
     * duradera de la ROM en el almacenamiento de la app — la promesa de la
     * clase ("las ROMs no se copian") sigue valiendo, esto es sólo un puente.
     */
    private fun copyToScratchDescriptor(rom: RomInfo): RomSource.Descriptor? {
        val scratch = storageManager.newRomScratchFile()
        return try {
            val input = documentAccess.openInputStream(rom.documentUri) ?: return null
            input.use { source -> FileOutputStream(scratch).use { sink -> source.copyTo(sink) } }
            val length = scratch.length()
            documentAccess.openLocalFileDescriptor(scratch)?.let { fd -> RomSource.Descriptor(fd, length) }
        } catch (error: IOException) {
            null
        } catch (error: SecurityException) {
            null
        } finally {
            scratch.delete()
        }
    }

    /**
     * Flujo sin tocar sobre el documento de la ROM. Sirve para calcular su
     * CRC32 sin cargarlo en memoria cuando [openRomForCore] entregó un
     * [RomSource.Descriptor]: ese descriptor ya viaja hacia el core y no se
     * puede leer dos veces, así que la identidad del save state necesita su
     * propio flujo.
     */
    suspend fun openRawStream(rom: RomInfo): InputStream? = withContext(ioDispatcher) {
        documentAccess.openInputStream(rom.documentUri)
    }

    suspend fun markPlayed(romId: String) = withContext(ioDispatcher) {
        romDao.markPlayed(romId, System.currentTimeMillis())
    }

    suspend fun rename(romId: String, displayName: String) = withContext(ioDispatcher) {
        romDao.rename(romId, displayName)
    }

    /**
     * Quita el juego de la biblioteca. [deleteSaves] es una decisión explícita
     * del usuario: por defecto las partidas se conservan, para que volver a
     * añadir la misma ROM las recupere intactas.
     */
    suspend fun remove(romId: String, deleteSaves: Boolean) = withContext(ioDispatcher) {
        romDao.findById(romId)?.let { documentAccess.releasePersistableReadPermission(it.documentUri) }
        romDao.delete(romId)
        if (deleteSaves) storageManager.deleteRomData(romId)
    }

    /**
     * Detecta si [uri] es un `.zip` por sus primeros bytes —nunca por el
     * nombre, que el SAF puede mentir— y, si lo es, localiza la única entrada
     * de ROM de adentro. Un archivo suelto se hashea tal cual.
     *
     * El zip no se extrae a disco: se descomprime en flujo, en cada apertura,
     * igual que el resto de la lectura de la ROM.
     */
    private fun resolveFingerprint(uri: String): FingerprintResolution {
        val rawStream = documentAccess.openInputStream(uri) ?: return FingerprintResolution.Unreadable
        val pushback = PushbackInputStream(rawStream, ZIP_SIGNATURE_SIZE)
        val isZip = sniffIsZip(pushback)

        if (!isZip) {
            return FingerprintResolution.Ready(pushback.use(RomIdentity::read))
        }
        pushback.close()

        return when (val resolution = RomZipReader.resolveRom { openOrThrow(uri) }) {
            RomZipReader.Resolution.None -> FingerprintResolution.NoRomInZip
            is RomZipReader.Resolution.Multiple ->
                FingerprintResolution.MultipleRomsInZip(resolution.count)
            RomZipReader.Resolution.Encrypted -> FingerprintResolution.EncryptedZip
            RomZipReader.Resolution.Corrupt -> FingerprintResolution.CorruptZip
            RomZipReader.Resolution.TooLarge -> FingerprintResolution.RomTooLarge
            is RomZipReader.Resolution.Single -> FingerprintResolution.Ready(resolution.fingerprint)
        }
    }

    private fun openOrThrow(uri: String): InputStream =
        documentAccess.openInputStream(uri) ?: throw FileNotFoundException(uri)

    /**
     * Lee los primeros bytes de [pushback] y los devuelve al flujo con
     * `unread`, sin importar si eran o no la firma de un zip: quien llame a
     * continuación no debe notar que alguien miró antes.
     */
    private fun sniffIsZip(pushback: PushbackInputStream): Boolean {
        val head = ByteArray(ZIP_SIGNATURE_SIZE)
        var total = 0
        while (total < head.size) {
            val read = pushback.read(head, total, head.size - total)
            if (read < 0) break
            total += read
        }
        if (total > 0) pushback.unread(head, 0, total)
        return total == head.size && RomZipReader.isZip(head)
    }

    /**
     * Prefiere el nombre del archivo, que suele ser el que el usuario reconoce
     * ("New Super Mario Bros"), y recurre al título interno del cartucho
     * cuando el archivo se llama algo como `rom1.nds`.
     */
    private fun suggestDisplayName(fileName: String, title: String): String {
        val withoutExtension = fileName.substringBeforeLast('.', fileName).trim()
        return withoutExtension.ifBlank { title.ifBlank { "Juego sin título" } }
    }

    /** Qué salió de mirar el documento elegido, antes de saber si ya estaba en la biblioteca. */
    private sealed interface FingerprintResolution {
        data class Ready(val fingerprint: RomFingerprint) : FingerprintResolution
        data object Unreadable : FingerprintResolution
        data object NoRomInZip : FingerprintResolution
        data class MultipleRomsInZip(val count: Int) : FingerprintResolution
        data object EncryptedZip : FingerprintResolution
        data object CorruptZip : FingerprintResolution
        data object RomTooLarge : FingerprintResolution
    }

    private companion object {
        /** Tamaño de la firma local de un `.zip`: 4 bytes bastan para reconocerla. */
        const val ZIP_SIGNATURE_SIZE = 4
    }
}

sealed interface RomImportResult {
    data class Imported(val rom: RomInfo) : RomImportResult
    data class AlreadyInLibrary(val rom: RomInfo) : RomImportResult

    /** El archivo se pudo leer pero su cabecera no es la de un cartucho de DS reconocible. */
    data object UnrecognizedRom : RomImportResult
    data object Unreadable : RomImportResult

    /** El `.zip` no trae ninguna entrada `.nds` reconocible. */
    data object NoRomInZip : RomImportResult

    /** El `.zip` trae más de una ROM; no se elige por el usuario, se rechaza. */
    data class MultipleRomsInZip(val count: Int) : RomImportResult

    data object EncryptedZip : RomImportResult
    data object CorruptZip : RomImportResult

    /** La ROM descomprimida supera [RomZipReader.MAX_UNCOMPRESSED_BYTES]. */
    data object RomTooLarge : RomImportResult
}

/** Lo que devuelve [RomRepository.openRomForCore]: la vía más barata disponible para esta ROM. */
sealed interface RomSource {
    /** Ya está en memoria: una ROM `.zip` recién descomprimida, o una ROM suelta cuando no hubo más remedio. */
    data class Bytes(val data: ByteArray) : RomSource {
        override fun equals(other: Any?): Boolean =
            other is Bytes && data.contentEquals(other.data)

        override fun hashCode(): Int = data.contentHashCode()
    }

    /**
     * Descriptor abierto sobre el archivo real de la ROM, sin copiar su
     * contenido. [length] es su tamaño en bytes ([ParcelFileDescriptor.getStatSize]).
     */
    data class Descriptor(val fd: ParcelFileDescriptor, val length: Long) : RomSource
}
