package com.moralesDev.dsForEveryone.data.cheats

import android.util.Log
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.storage.writeAtomically
import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/** Resultado de leer las claves guardadas de una ROM. */
sealed interface CheatsLoadResult {
    data class Loaded(val entries: List<CheatEntry>) : CheatsLoadResult

    /**
     * El archivo existe pero no se pudo interpretar: JSON roto o ajeno. Se
     * trata como si no hubiera ninguna clave guardada; quien llama decide
     * cómo avisarlo.
     */
    data object Corrupt : CheatsLoadResult
}

/**
 * Claves de una ROM, persistidas aparte de la partida.
 *
 * `saves/<hash>/cheats.json`. El aislamiento entre juegos sale del propio
 * directorio por hash, igual que [com.moralesDev.dsForEveryone.data.save.BatterySaveManager];
 * no hace falta ninguna lógica extra aquí para no mezclar claves de ROMs
 * distintas.
 *
 * Hasta [MAX_ENTRIES] entradas de hasta [MAX_LINES] líneas / [MAX_CHARS]
 * caracteres cada una. El tope lo hace cumplir [write]: nada que lo supere
 * llega a tocar disco. La UI es quien debe impedir que el usuario llegue
 * hasta ahí; esto es la red de seguridad, no la primera línea de defensa.
 */
class CheatsManager(
    private val storageManager: StorageManager,
    private val ioDispatcher: CoroutineDispatcher,
) {

    suspend fun read(romId: String): CheatsLoadResult = withContext(ioDispatcher) {
        val file = storageManager.cheatsFile(romId)
        if (!file.exists() || file.length() == 0L) {
            return@withContext CheatsLoadResult.Loaded(emptyList())
        }

        try {
            val dto = Json.decodeFromString(CheatsFileDto.serializer(), file.readText())
            CheatsLoadResult.Loaded(dto.entries.map(CheatEntryDto::toDomain))
        } catch (error: SerializationException) {
            Log.e(TAG, "cheats.json de $romId no es JSON válido", error)
            CheatsLoadResult.Corrupt
        } catch (error: IOException) {
            Log.e(TAG, "No se pudo leer cheats.json de $romId", error)
            CheatsLoadResult.Corrupt
        }
    }

    /**
     * @return `false` sin tocar disco si alguna entrada de [entries] supera
     *   [MAX_LINES]/[MAX_CHARS], si hay más de [MAX_ENTRIES], o si falla la
     *   escritura.
     */
    suspend fun write(romId: String, entries: List<CheatEntry>): Boolean = withContext(ioDispatcher) {
        if (entries.size > MAX_ENTRIES || !entries.all(::fitsWithinCaps)) {
            Log.e(TAG, "Claves de $romId exceden el tope permitido, no se escriben")
            return@withContext false
        }

        try {
            val dto = CheatsFileDto(entries.map(CheatEntry::toDto))
            val bytes = Json.encodeToString(CheatsFileDto.serializer(), dto).toByteArray()
            storageManager.cheatsFile(romId).writeAtomically(bytes)
            true
        } catch (error: IOException) {
            Log.e(TAG, "No se pudieron escribir las claves de $romId", error)
            false
        } catch (error: IllegalStateException) {
            Log.e(TAG, "No se pudo reemplazar cheats.json de $romId", error)
            false
        }
    }

    private fun fitsWithinCaps(entry: CheatEntry): Boolean =
        entry.lines.size <= MAX_LINES && entry.lines.sumOf(String::length) <= MAX_CHARS

    companion object {
        const val MAX_ENTRIES = 5
        const val MAX_LINES = 80
        const val MAX_CHARS = 2000
        private const val TAG = "CheatsManager"
    }
}
