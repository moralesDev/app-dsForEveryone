package com.moralesDev.dsForEveryone.data.save

import android.graphics.Bitmap
import android.util.Log
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.storage.writeAtomically
import com.moralesDev.dsForEveryone.emulator.DsVideo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/** Una de las ranuras de guardado rápido de una ROM. */
data class SaveStateSlot(
    /** 1..5 para las del usuario, [SaveStateManager.AUTO_SLOT] para el respaldo. */
    val index: Int,
    /** `null` cuando la ranura está vacía. */
    val savedAt: Long?,
    /** Tamaño del archivo completo, contenedor incluido. */
    val sizeBytes: Long,
    /** Ruta de la miniatura del momento guardado, si se pudo capturar. */
    val thumbnailPath: String?,
    /** Core que generó el estado, como `stub-0`. */
    val core: String?,
    /**
     * Por qué esta ranura no puede restaurarse, o `null` si puede. Una ranura
     * ocupada e inservible no es lo mismo que una vacía: se puede borrar o
     * sobrescribir, pero no cargar.
     */
    val problem: StateRejection?,
) {
    val isEmpty: Boolean get() = savedAt == null

    val isRestorable: Boolean get() = !isEmpty && problem == null

    val isAutoBackup: Boolean get() = index == SaveStateManager.AUTO_SLOT
}

/**
 * Lo que la biblioteca necesita saber de un juego sin abrirlo: cuándo se guardó
 * por última vez y de dónde salió ese guardado.
 */
data class LastSaveSummary(
    val slot: Int,
    val savedAt: Long,
) {
    val isAutoBackup: Boolean get() = slot == SaveStateManager.AUTO_SLOT
}

/** Resultado de recuperar el estado de una ranura. */
sealed interface StateReadResult {
    data class Loaded(val payload: ByteArray) : StateReadResult {
        override fun equals(other: Any?): Boolean =
            other is Loaded && payload.contentEquals(other.payload)

        override fun hashCode(): Int = payload.contentHashCode()
    }

    /** La ranura no tiene nada guardado. */
    data object Empty : StateReadResult

    /** Hay un archivo, pero no se puede restaurar en esta sesión. */
    data class Rejected(val reason: StateRejection) : StateReadResult

    /** El archivo existe pero el disco no lo entrega. */
    data object Unreadable : StateReadResult
}

/**
 * Guardado rápido: una copia del estado completo de la máquina, independiente
 * del sistema de guardado del juego.
 *
 * Cada ranura pertenece exclusivamente a su ROM porque cuelga de la carpeta que
 * nombra el hash de esa ROM. Ese aislamiento por ruta ya impide mezclar juegos,
 * pero no basta: la misma carpeta puede contener estados de una versión anterior
 * del core, o archivos que se quedaron a medias. Por eso el estado no se guarda
 * en crudo sino dentro de un [SaveStateContainer], que lleva la identidad de la
 * ROM, la del core y un hash de integridad, y por eso nada llega al core sin
 * pasar antes por [StateIdentity.reject].
 */
class SaveStateManager(
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param identity sesión contra la que se contrasta cada ranura, o `null`
     *   si todavía no hay ROM cargada; en ese caso se describen las ranuras sin
     *   juzgar su compatibilidad.
     */
    suspend fun slots(romId: String, identity: StateIdentity?): List<SaveStateSlot> =
        withContext(ioDispatcher) {
            (1..SLOT_COUNT).map { index -> readSlot(romId, index, identity) }
        }

    /** La ranura del sistema. Siempre existe como concepto, aunque esté vacía. */
    suspend fun autoSlot(romId: String, identity: StateIdentity?): SaveStateSlot =
        withContext(ioDispatcher) { readSlot(romId, AUTO_SLOT, identity) }

    /**
     * Cuándo se guardó por última vez, sin abrir el juego.
     *
     * La biblioteca lo llama para cada ROM de la lista, así que no puede pagar
     * lo que paga la comprobación completa de [read]: lee sólo cabeceras y no
     * comprueba la integridad del contenido ni el `stateSize`, que exigen tener
     * la ROM cargada en el core. Es suficiente para decidir si «Continuar
     * partida» se ofrece y para poner una fecha; la comprobación completa llega
     * al elegir una ranura en la hoja, y si algo falla entonces se avisa allí.
     */
    suspend fun lastSaveSummary(
        romId: String,
        coreName: String,
        coreVersion: String,
    ): LastSaveSummary? = withContext(ioDispatcher) {
        (listOf(AUTO_SLOT) + (1..SLOT_COUNT))
            .mapNotNull { slot ->
                val header = parsedHeader(romId, slot) ?: return@mapNotNull null
                val belongsHere = header.romSha256.equals(romId, ignoreCase = true) &&
                    header.coreName == coreName && header.coreVersion == coreVersion
                if (belongsHere) LastSaveSummary(slot, header.savedAt) else null
            }
            .maxByOrNull { it.savedAt }
    }

    private fun parsedHeader(romId: String, slot: Int): SaveStateHeader? {
        val file = storageManager.stateFile(romId, slot)
        if (!file.exists()) return null
        return (peek(file, file.length()) as? PeekResult.Parsed)?.header
    }

    /**
     * Recupera el estado de una ranura, ya validado contra [identity].
     *
     * El payload que devuelve es el único que ha superado las cinco
     * comprobaciones —firma, versión del contenedor, integridad, juego y core—,
     * así que quien lo reciba puede entregárselo al core sin más trámite.
     */
    suspend fun read(romId: String, slot: Int, identity: StateIdentity): StateReadResult =
        withContext(ioDispatcher) {
            val file = storageManager.stateFile(romId, slot)
            if (!file.exists()) return@withContext StateReadResult.Empty

            val bytes = try {
                file.readBytes()
            } catch (error: IOException) {
                Log.e(TAG, "No se pudo leer la ranura $slot de $romId", error)
                return@withContext StateReadResult.Unreadable
            } catch (error: OutOfMemoryError) {
                // Un archivo absurdamente grande no debe tumbar la aplicación.
                Log.e(TAG, "Ranura $slot de $romId demasiado grande", error)
                return@withContext StateReadResult.Unreadable
            }

            when (val decoded = SaveStateContainer.decode(bytes)) {
                is DecodedState.Rejected -> StateReadResult.Rejected(decoded.reason)
                is DecodedState.Valid -> {
                    val rejection = identity.reject(decoded.header)
                    if (rejection != null) {
                        StateReadResult.Rejected(rejection)
                    } else {
                        StateReadResult.Loaded(decoded.payload)
                    }
                }
            }
        }

    /**
     * @param frame último fotograma en ARGB_8888, 256×384 (las dos pantallas
     *   apiladas, ver [DsVideo]), para la miniatura. Es opcional: sin él la
     *   ranura sigue siendo válida, sólo se ve sin previsualización.
     */
    suspend fun write(
        romId: String,
        slot: Int,
        payload: ByteArray,
        frame: IntArray?,
        identity: StateIdentity,
        savedAt: Long = System.currentTimeMillis(),
    ): Boolean = withContext(ioDispatcher) {
        require(slot == AUTO_SLOT || slot in 1..SLOT_COUNT) { "Ranura fuera de rango: $slot" }

        val header = SaveStateHeader(
            containerVersion = SaveStateContainer.CONTAINER_VERSION,
            coreName = identity.coreName,
            coreVersion = identity.coreVersion,
            romSha256 = identity.romSha256,
            romCrc32 = identity.romCrc32,
            savedAt = savedAt,
            slot = slot,
            stateSize = payload.size,
            payloadSha256 = SaveStateContainer.sha256(payload),
        )

        try {
            // La escritura es atómica: o queda el estado anterior o queda el
            // nuevo. Un cierre a mitad no puede dejar media ranura, que sería
            // indistinguible de una partida perdida.
            storageManager.stateFile(romId, slot)
                .writeAtomically(SaveStateContainer.encode(header, payload))
            // La miniatura va después y su fallo no invalida la ranura: es
            // decoración, y perderla no impide restaurar.
            frame?.let { runCatching { writeThumbnail(romId, slot, it) } }
            true
        } catch (error: IOException) {
            Log.e(TAG, "No se pudo escribir la ranura $slot de $romId", error)
            false
        } catch (error: IllegalStateException) {
            Log.e(TAG, "No se pudo reemplazar la ranura $slot de $romId", error)
            false
        }
    }

    /** Borra una ranura y sólo esa: ni la ROM, ni la partida, ni las demás. */
    suspend fun delete(romId: String, slot: Int) = withContext(ioDispatcher) {
        require(slot == AUTO_SLOT || slot in 1..SLOT_COUNT) { "Ranura fuera de rango: $slot" }
        storageManager.stateFile(romId, slot).delete()
        storageManager.stateThumbnailFile(romId, slot).delete()
        Unit
    }

    private fun readSlot(romId: String, index: Int, identity: StateIdentity?): SaveStateSlot {
        val stateFile = storageManager.stateFile(romId, index)
        if (!stateFile.exists()) {
            return SaveStateSlot(index, savedAt = null, sizeBytes = 0, null, null, null)
        }

        val thumbnail = storageManager.stateThumbnailFile(romId, index)
            .takeIf(File::exists)?.absolutePath
        val length = stateFile.length()

        return when (val peeked = peek(stateFile, length)) {
            // Un archivo ilegible o ajeno se muestra ocupado y averiado, nunca
            // vacío: si apareciera vacío, el primer guardado lo pisaría sin
            // preguntar y el usuario nunca sabría que hubo algo ahí.
            is PeekResult.Rejected -> SaveStateSlot(
                index = index,
                savedAt = stateFile.lastModified(),
                sizeBytes = length,
                thumbnailPath = thumbnail,
                core = null,
                problem = peeked.reason,
            )

            is PeekResult.Parsed -> SaveStateSlot(
                index = index,
                savedAt = peeked.header.savedAt,
                sizeBytes = length,
                thumbnailPath = thumbnail,
                core = peeked.header.core,
                problem = identity?.reject(peeked.header),
            )
        }
    }

    /**
     * Lee del archivo sólo lo que ocupa la cabecera. Listar las cinco ranuras
     * ocurre cada vez que se abre el menú de pausa, y un estado de DS puede
     * pesar varios megas: cargarlos enteros para enseñar una fecha sería
     * absurdo.
     */
    private fun peek(file: File, length: Long): PeekResult = try {
        val head = ByteArray(minOf(length, SaveStateContainer.MAX_HEADER_BYTES.toLong()).toInt())
        RandomAccessFile(file, "r").use { it.readFully(head) }
        SaveStateContainer.peek(head, length)
    } catch (error: IOException) {
        Log.w(TAG, "Cabecera ilegible en ${file.name}", error)
        PeekResult.Rejected(StateRejection.Truncated)
    }

    /** El único tamaño de fotograma que existe en este proyecto: las dos pantallas apiladas. */
    private fun writeThumbnail(romId: String, slot: Int, frame: IntArray) {
        if (frame.size != DsVideo.WIDTH * DsVideo.HEIGHT) return
        val bitmap = Bitmap.createBitmap(frame, DsVideo.WIDTH, DsVideo.HEIGHT, Bitmap.Config.ARGB_8888)
        val encoded = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        storageManager.stateThumbnailFile(romId, slot).writeAtomically(encoded)
    }

    companion object {
        /** Ranuras que maneja el usuario. */
        const val SLOT_COUNT = 5

        /**
         * Ranura del respaldo automático, reservada al sistema. El usuario no
         * puede guardar en ella; sí restaurarla y borrarla.
         */
        const val AUTO_SLOT = StorageManager.AUTO_SLOT

        private const val TAG = "SaveStateManager"
    }
}
