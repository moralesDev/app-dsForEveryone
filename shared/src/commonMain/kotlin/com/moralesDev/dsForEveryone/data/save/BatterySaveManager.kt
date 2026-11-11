package com.moralesDev.dsForEveryone.data.save

import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.storage.writeAtomically
import com.moralesDev.dsForEveryone.util.AppLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.IOException

/**
 * Partida propia del juego (EEPROM o Flash del cartucho).
 *
 * Es la que se crea cuando el jugador guarda *dentro* del juego, y la que hace
 * que un juego encuentre su partida al arrancar. El usuario nunca elige este
 * archivo a mano: se localiza por el identificador de la ROM.
 *
 * ```
 * ROM → core → battery save → BatterySaveManager → filesDir/saves/<hash>/battery.sav
 * ```
 */
class BatterySaveManager(
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
) {

    private val fileSystem = FileSystem.SYSTEM

    /** Contenido guardado para esa ROM, o `null` si todavía no ha guardado nunca. */
    suspend fun read(romId: String): ByteArray? = withContext(ioDispatcher) {
        val path = storageManager.batteryPath(romId)
        if (sizeOf(path) == 0L) return@withContext null
        try {
            fileSystem.read(path) { readByteArray() }
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo leer la partida de $romId", error)
            null
        }
    }

    suspend fun write(romId: String, data: ByteArray): Boolean = withContext(ioDispatcher) {
        try {
            writeAtomically(storageManager.batteryPath(romId), data)
            true
        } catch (error: IOException) {
            AppLog.e(TAG, "No se pudo escribir la partida de $romId", error)
            false
        } catch (error: IllegalStateException) {
            AppLog.e(TAG, "No se pudo reemplazar la partida de $romId", error)
            false
        }
    }

    fun exists(romId: String): Boolean = sizeOf(storageManager.batteryPath(romId)) > 0

    /**
     * Borra la partida de batería, dejando el juego como si nunca se hubiera
     * guardado. Es el rescate cuando un `.sav` importado resulta estar mal: no
     * hay forma de "deshacer" un import, pero sí de volver a empezar en blanco
     * en vez de quedarse con datos corruptos para siempre.
     */
    suspend fun delete(romId: String): Boolean = withContext(ioDispatcher) {
        try {
            fileSystem.delete(storageManager.batteryPath(romId), mustExist = false)
            true
        } catch (_: IOException) {
            false
        }
    }

    /** Tamaño del archivo, o 0 si no existe: lo que daba `File.length()`. */
    private fun sizeOf(path: okio.Path): Long = fileSystem.metadataOrNull(path)?.size ?: 0L

    private companion object {
        const val TAG = "BatterySaveManager"
    }
}
