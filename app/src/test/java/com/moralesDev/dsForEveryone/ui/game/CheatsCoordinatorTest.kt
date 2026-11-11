package com.moralesDev.dsForEveryone.ui.game

import com.moralesDev.dsForEveryone.data.cheats.CheatsLoadResult
import com.moralesDev.dsForEveryone.data.cheats.CheatsManager
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.FakeEmulatorCore
import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.moralesDev.dsForEveryone.data.storage.cheatsFile

/**
 * [CheatsCoordinator] se prueba aparte de [GameViewModel] porque éste no se
 * puede construir en una prueba de JVM: `RomRepository`/`SettingsRepository`
 * necesitan un `Context`/`ContentResolver` real, y este proyecto no usa
 * Robolectric ni un framework de mocks. Ver el docstring de la clase.
 *
 * `cheats.json` es de verdad, sobre una carpeta temporal —mismo motivo que
 * `CheatsManagerTest`—, y el core es [FakeEmulatorCore] sobre un
 * [EmulatorController] real, con su hilo de emulación de verdad arrancado:
 * sin él, `EmulatorController` ejecuta los comandos en línea y no hay ningún
 * punto de suspensión real donde forzar una carrera.
 */
class CheatsCoordinatorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val romId = "a".repeat(64)

    private lateinit var storage: StorageManager
    private lateinit var cheatsManager: CheatsManager
    private lateinit var core: FakeEmulatorCore
    private lateinit var controller: EmulatorController
    private lateinit var coordinator: CheatsCoordinator

    @Before
    fun setUp() = runTest {
        storage = StorageManager(folder.root)
        cheatsManager = CheatsManager(storage, Dispatchers.Unconfined)
        core = FakeEmulatorCore()
        controller = EmulatorController(core)
        controller.open(ROM, batterySave = null)
        coordinator = CheatsCoordinator(romId, cheatsManager, controller)
    }

    @After
    fun tearDown() {
        controller.stop()
    }

    private suspend fun addEntry(label: String): CheatEntry {
        val result = coordinator.add(listOf("94000130 FFFB0000"), label)
        check(result is CheatsCoordinator.AddResult.Added) { "se esperaba Added, fue $result" }
        return result.entry
    }

    /**
     * Reproduce la pérdida silenciosa: dos mutaciones que coinciden en el
     * tiempo parten del mismo [CheatsCoordinator.entries] y una se come a la
     * otra.
     *
     * `CoroutineStart.UNDISPATCHED` es lo que fuerza la carrera sin depender
     * de que el hilo real gane o pierda por casualidad: cada corrutina corre
     * en línea hasta su primera suspensión de verdad —dentro de
     * [EmulatorController.applyCheats], que espera al hilo de emulación—, así
     * que las dos llegan a leer `entries` antes de que ninguna lo haya
     * escrito todavía.
     */
    @Test
    fun `dos mutaciones que parten del mismo estado no se pierden una a la otra`() = runTest {
        val a = addEntry("A")
        val b = addEntry("B")

        // addEntry() crea la clave con enabled = true: apagarla es un cambio de
        // verdad, no un no-op que dejaría la carrera invisible sin importar
        // quién gane.
        val job1 = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.toggle(a.id, enabled = false)
        }
        val job2 = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.delete(b.id)
        }
        job1.join()
        job2.join()

        val expected = listOf(a.copy(enabled = false))
        assertEquals(
            "el conjunto en memoria pierde uno de los dos cambios",
            expected,
            coordinator.entries,
        )
        assertEquals(
            "cheats.json en disco pierde uno de los dos cambios",
            expected,
            (cheatsManager.read(romId) as CheatsLoadResult.Loaded).entries,
        )
        assertEquals(
            "lo aplicado al core pierde uno de los dos cambios",
            expected,
            core.appliedCheats.last(),
        )
    }

    @Test
    fun `anadir hasta el tope de cinco funciona, la sexta se rechaza`() = runTest {
        repeat(CheatsManager.MAX_ENTRIES) { addEntry("Truco $it") }
        assertEquals(CheatsManager.MAX_ENTRIES, coordinator.entries.size)

        val sixth = coordinator.add(listOf("94000130 FFFB0000"), "Sexto")

        assertEquals(CheatsCoordinator.AddResult.LimitReached, sixth)
        assertEquals(CheatsManager.MAX_ENTRIES, coordinator.entries.size)
    }

    @Test
    fun `borrar quita la clave del conjunto, de disco y del core`() = runTest {
        val added = addEntry("A")

        val persisted = coordinator.delete(added.id)

        assertTrue(persisted)
        assertTrue(coordinator.entries.isEmpty())
        assertTrue(core.appliedCheats.last().isEmpty())
        assertEquals(
            CheatsLoadResult.Loaded(emptyList()),
            cheatsManager.read(romId),
        )
    }

    @Test
    fun `activar y desactivar cambian solo el interruptor de esa clave`() = runTest {
        val added = addEntry("A")
        assertTrue(added.enabled)

        coordinator.toggle(added.id, enabled = false)
        assertFalse(coordinator.entries.single().enabled)
        assertFalse(core.appliedCheats.last().single().enabled)

        coordinator.toggle(added.id, enabled = true)
        assertTrue(coordinator.entries.single().enabled)
        assertTrue(core.appliedCheats.last().single().enabled)
    }

    /**
     * El core y el estado en memoria son la fuente de verdad de la partida en
     * curso; el archivo es sólo lo que se recupera la próxima vez. Que falle
     * el disco no debe dejar una clave activa en el core que la interfaz
     * muestre apagada (o viceversa) — ver el docstring de [CheatsCoordinator].
     */
    @Test
    fun `si falla la escritura a disco, el core y el estado en memoria quedan al dia igual`() = runTest {
        val added = addEntry("A")

        // La carpeta del juego pasa a ser un archivo: romDirectory()/mkdirs()
        // no puede crear un directorio donde ya hay un archivo con ese nombre,
        // así que ni siquiera se llega a abrir el temporal — falla de forma
        // fiable en cualquier sistema operativo, a diferencia de intentar
        // reemplazar por rename un directorio ya existente (eso sí depende de
        // la plataforma y en Windows puede llegar a "funcionar").
        val cheatsFile = storage.cheatsFile(romId)
        val romDir = cheatsFile.parentFile!!
        assertTrue(cheatsFile.delete())
        assertTrue(romDir.delete())
        assertTrue(romDir.createNewFile())

        val persisted = coordinator.toggle(added.id, enabled = false)

        assertFalse("la escritura a disco debía fallar", persisted)
        assertFalse(
            "el estado en memoria se actualiza aunque falle el disco",
            coordinator.entries.single().enabled,
        )
        assertFalse(
            "el core también queda al día aunque falle el disco",
            core.appliedCheats.last().single().enabled,
        )
    }

    private companion object {
        val ROM = ByteArray(256) { it.toByte() }
    }
}
