package com.moralesDev.dsForEveryone.data.save

import android.util.Log
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.storage.writeAtomically
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

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

    /** Contenido guardado para esa ROM, o `null` si todavía no ha guardado nunca. */
    suspend fun read(romId: String): ByteArray? = withContext(ioDispatcher) {
        val file = storageManager.batteryFile(romId)
        if (!file.exists() || file.length() == 0L) return@withContext null
        try {
            file.readBytes()
        } catch (error: IOException) {
            Log.e(TAG, "No se pudo leer la partida de $romId", error)
            null
        }
    }

    suspend fun write(romId: String, data: ByteArray): Boolean = withContext(ioDispatcher) {
        try {
            storageManager.batteryFile(romId).writeAtomically(data)
            true
        } catch (error: IOException) {
            Log.e(TAG, "No se pudo escribir la partida de $romId", error)
            false
        } catch (error: IllegalStateException) {
            Log.e(TAG, "No se pudo reemplazar la partida de $romId", error)
            false
        }
    }

    fun exists(romId: String): Boolean = storageManager.batteryFile(romId).length() > 0

    /**
     * Borra la partida de batería, dejando el juego como si nunca se hubiera
     * guardado. Es el rescate cuando un `.sav` importado resulta estar mal: no
     * hay forma de "deshacer" un import, pero sí de volver a empezar en blanco
     * en vez de quedarse con datos corruptos para siempre.
     */
    suspend fun delete(romId: String): Boolean = withContext(ioDispatcher) {
        val file = storageManager.batteryFile(romId)
        !file.exists() || file.delete()
    }

    private companion object {
        const val TAG = "BatterySaveManager"
    }
}
