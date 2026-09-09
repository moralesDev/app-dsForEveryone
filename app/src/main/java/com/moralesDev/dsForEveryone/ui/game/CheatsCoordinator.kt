package com.moralesDev.dsForEveryone.ui.game

import com.moralesDev.dsForEveryone.data.cheats.CheatsLoadResult
import com.moralesDev.dsForEveryone.data.cheats.CheatsManager
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import com.moralesDev.dsForEveryone.emulator.core.CheatRejection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Trucos de una sesión de juego: aplicarlos al core, persistirlos en
 * `cheats.json` y mantener el conjunto vigente ([entries]), siempre bajo el
 * mismo candado.
 *
 * Hace falta el candado porque [GameViewModel] lanza cada mutación (añadir,
 * activar, borrar) en su propia corrutina. Sin serializarlas, dos que
 * coinciden en el tiempo pueden partir del mismo [entries] y la que termine
 * después pisa en silencio a la que terminó antes — tanto en lo que queda
 * escrito en disco como en lo que [EmulatorController.applyCheats] le dejó
 * puesto al core, que sustituye el conjunto entero en cada llamada, nunca lo
 * combina con el anterior.
 *
 * Que el core y [entries] se separen importa más de lo habitual: una clave
 * activa en el core que la interfaz muestra apagada sigue escribiendo en la
 * RAM que acaba en `battery.sav`, y ese archivo no tiene ninguna validación
 * que lo detecte después.
 *
 * Vive aparte de [GameViewModel] (no como una función privada suya) por una
 * razón de prueba, no de diseño: `RomRepository`, `SaveManager` y
 * `SettingsRepository` necesitan un `Context`/`ContentResolver` real para
 * construirse, y este proyecto no usa Robolectric ni un framework de mocks
 * (sólo JUnit, `kotlinx-coroutines-test` y dobles escritos a mano), así que
 * `GameViewModel` entero no se puede instanciar en una prueba de JVM. Esta
 * clase sólo depende de [CheatsManager] (real, sobre una carpeta temporal,
 * igual que el resto del sistema de partidas) y de [EmulatorController]
 * (real, con un core de mentira), así que sí se puede probar sola.
 *
 * Orden elegido dentro de cada mutación, y por qué: **core primero, [entries]
 * justo después, disco al final**. El core y lo que ve el usuario no deben
 * separarse ni un instante el uno del otro; el archivo en disco sólo es lo
 * que se recupera la próxima vez que se abra el juego, así que puede quedar
 * un paso por detrás sin que eso sea una inconsistencia real. Si la
 * escritura falla, no hay nada que deshacer: el core y [entries] ya
 * coinciden y siguen siendo la partida correcta, y quien llama sólo necesita
 * avisar del fallo de guardado.
 */
internal class CheatsCoordinator(
    private val romId: String,
    private val cheatsManager: CheatsManager,
    private val controller: EmulatorController,
) {
    private val lock = Mutex()

    @Volatile
    var entries: List<CheatEntry> = emptyList()
        private set

    /** Carga las claves guardadas y las aplica al core si hay alguna. */
    suspend fun load(): CheatsLoadResult = lock.withLock {
        val result = cheatsManager.read(romId)
        if (result is CheatsLoadResult.Loaded) {
            entries = result.entries
            if (entries.isNotEmpty()) controller.applyCheats(entries)
        }
        result
    }

    sealed interface AddResult {
        /** [persisted] es `false` si el core ya tiene la clave pero no se pudo guardar en disco. */
        data class Added(val entry: CheatEntry, val persisted: Boolean) : AddResult
        data object LimitReached : AddResult
        data object TooLarge : AddResult
        data class Rejected(val rejection: CheatRejection) : AddResult
    }

    /**
     * Añade una clave nueva. A diferencia de `app-emulador` (mGBA: tres
     * formatos ambiguos que hay que resolver probando) aquí no hace falta
     * adivinar nada: melonDS sólo entiende Action Replay, así que la
     * candidata se prueba directamente contra el core junto con las que ya
     * había — [EmulatorController.applyCheats] sustituye el conjunto entero
     * en cada llamada, así que si la candidata se rechaza el core queda
     * exactamente como estaba, sin volver a aplicar nada.
     */
    suspend fun add(rawLines: List<String>, label: String): AddResult = lock.withLock {
        if (entries.size >= CheatsManager.MAX_ENTRIES) return@withLock AddResult.LimitReached
        if (rawLines.size > CheatsManager.MAX_LINES || rawLines.sumOf(String::length) > CheatsManager.MAX_CHARS) {
            return@withLock AddResult.TooLarge
        }

        val candidate = CheatEntry(
            id = UUID.randomUUID().toString(),
            label = label,
            lines = rawLines,
            enabled = true,
        )

        val attempted = entries + candidate
        val rejection = controller.applyCheats(attempted).last()
        if (rejection != null) return@withLock AddResult.Rejected(rejection)

        AddResult.Added(candidate, persisted = persist(attempted))
    }

    /** @return `true` si el cambio quedó escrito en disco. */
    suspend fun toggle(id: String, enabled: Boolean): Boolean = lock.withLock {
        val updated = entries.map { if (it.id == id) it.copy(enabled = enabled) else it }
        controller.applyCheats(updated)
        persist(updated)
    }

    /** @return `true` si el cambio quedó escrito en disco. */
    suspend fun delete(id: String): Boolean = lock.withLock {
        val updated = entries.filterNot { it.id == id }
        controller.applyCheats(updated)
        persist(updated)
    }

    /** [entries] se actualiza siempre, aunque falle la escritura: ver el orden en el docstring de la clase. */
    private suspend fun persist(updated: List<CheatEntry>): Boolean {
        entries = updated
        return cheatsManager.write(romId, updated)
    }
}
