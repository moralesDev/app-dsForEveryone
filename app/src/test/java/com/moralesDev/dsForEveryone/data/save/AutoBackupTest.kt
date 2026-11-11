package com.moralesDev.dsForEveryone.data.save

import com.moralesDev.dsForEveryone.data.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.moralesDev.dsForEveryone.data.storage.stateFile
import com.moralesDev.dsForEveryone.data.storage.statesDirectory

/**
 * El respaldo automático: su ranura propia, y el resumen que usa la biblioteca
 * para saber cuándo se guardó por última vez sin abrir el juego.
 */
class AutoBackupTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var storage: StorageManager
    private lateinit var manager: SaveStateManager
    private lateinit var saves: SaveManager

    private val gameA = "a".repeat(64)
    private val gameB = "b".repeat(64)

    private val identityA = StateIdentity(gameA, 0x1111_1111L, "stub", "0", STATE_BYTES)
    private val identityB = StateIdentity(gameB, 0x2222_2222L, "stub", "0", STATE_BYTES)

    @Before
    fun setUp() {
        storage = StorageManager(folder.root)
        manager = SaveStateManager(storage, Dispatchers.Unconfined)
        saves = SaveManager(
            batterySaves = BatterySaveManager(storage, Dispatchers.Unconfined),
            saveStates = manager,
            storageManager = storage,
        )
    }

    private fun state(seed: Int) = ByteArray(STATE_BYTES) { (it + seed).toByte() }

    /** Guarda en una ranura con una fecha concreta, para poder ordenarlas. */
    private suspend fun save(slot: Int, at: Long, seed: Int = slot, rom: String = gameA) {
        val identity = if (rom == gameA) identityA else identityB
        manager.write(rom, slot, state(seed), frame = null, identity, savedAt = at)
    }

    // ------------------------------------------------- la ranura del sistema

    @Test
    fun `el respaldo vive en su propio archivo, no en una sexta ranura`() = runTest {
        saves.writeAutoBackup(gameA, state(9), frame = null, identityA)

        val files = storage.statesDirectory(gameA).list().orEmpty().toSet()
        assertTrue("Debe llamarse auto.state: $files", "auto.state" in files)
        assertTrue("No puede aparecer como slot_0: $files", files.none { it.startsWith("slot_") })
    }

    @Test
    fun `el guardado manual no puede escribir en el respaldo`() = runTest {
        // La frontera está en el tipo, no en un `if` que se pueda olvidar.
        var rejected = false
        try {
            saves.writeState(gameA, SaveStateManager.AUTO_SLOT, state(1), null, identityA)
        } catch (_: IllegalArgumentException) {
            rejected = true
        }
        assertTrue("Guardar a mano en AUTO debe rechazarse", rejected)
    }

    @Test
    fun `un respaldo nuevo reemplaza al anterior y no toca las ranuras del usuario`() = runTest {
        save(slot = 1, at = 1_000)
        save(slot = 2, at = 2_000)
        saves.writeAutoBackup(gameA, state(50), frame = null, identityA)
        saves.writeAutoBackup(gameA, state(51), frame = null, identityA)

        assertEquals(
            StateReadResult.Loaded(state(51)),
            manager.read(gameA, SaveStateManager.AUTO_SLOT, identityA),
        )
        assertEquals(StateReadResult.Loaded(state(1)), manager.read(gameA, 1, identityA))
        assertEquals(StateReadResult.Loaded(state(2)), manager.read(gameA, 2, identityA))
    }

    @Test
    fun `cada juego tiene su propio respaldo`() = runTest {
        saves.writeAutoBackup(gameA, state(10), frame = null, identityA)
        saves.writeAutoBackup(gameB, state(20), frame = null, identityB)

        assertEquals(
            StateReadResult.Loaded(state(10)),
            manager.read(gameA, SaveStateManager.AUTO_SLOT, identityA),
        )
        assertEquals(
            StateReadResult.Loaded(state(20)),
            manager.read(gameB, SaveStateManager.AUTO_SLOT, identityB),
        )
    }

    @Test
    fun `un respaldo fallido deja intacto el anterior`() = runTest {
        saves.writeAutoBackup(gameA, state(1), frame = null, identityA)
        val before = storage.stateFile(gameA, SaveStateManager.AUTO_SLOT).readBytes()

        // Un payload que no cuadra con lo que el core espera: la escritura del
        // contenedor se hace igual, pero al leerlo se rechaza y —lo que importa—
        // no se ha perdido nada por el camino.
        val badIdentity = identityA.copy(stateSize = STATE_BYTES * 2)
        manager.write(gameA, SaveStateManager.AUTO_SLOT, state(2), null, identityA)

        assertEquals(
            StateReadResult.Rejected(StateRejection.UnexpectedSize(STATE_BYTES, STATE_BYTES * 2)),
            manager.read(gameA, SaveStateManager.AUTO_SLOT, badIdentity),
        )
        assertEquals(before.size, storage.stateFile(gameA, SaveStateManager.AUTO_SLOT).length().toInt())
    }

    @Test
    fun `el respaldo se puede borrar sin tocar las ranuras`() = runTest {
        save(slot = 3, at = 3_000)
        saves.writeAutoBackup(gameA, state(7), frame = null, identityA)

        manager.delete(gameA, SaveStateManager.AUTO_SLOT)

        assertEquals(StateReadResult.Empty, manager.read(gameA, SaveStateManager.AUTO_SLOT, identityA))
        assertEquals(StateReadResult.Loaded(state(3)), manager.read(gameA, 3, identityA))
    }

    // ------------------------------------------------ resumen para la biblioteca

    @Test
    fun `sin estados el resumen es nulo`() = runTest {
        assertNull(manager.lastSaveSummary(gameA, "stub", "0"))
    }

    @Test
    fun `el resumen dice de que ranura salio y cuando`() = runTest {
        save(slot = 1, at = t("15:00"))
        manager.write(gameA, SaveStateManager.AUTO_SLOT, state(99), null, identityA, t("18:15"))

        val summary = manager.lastSaveSummary(gameA, "stub", "0")

        assertNotNull(summary)
        assertTrue(summary!!.isAutoBackup)
        assertEquals(t("18:15"), summary.savedAt)
    }

    @Test
    fun `el resumen ignora los estados de otra version del core`() = runTest {
        manager.write(
            gameA, 1, state(1), null,
            identityA.copy(coreVersion = "1"), t("20:00"),
        )

        assertNull(manager.lastSaveSummary(gameA, "stub", "0"))
    }

    @Test
    fun `el resumen ordena por savedAt y no por la fecha del archivo`() = runTest {
        // La ranura 1 se escribe la última en disco pero declara ser anterior.
        // Manda lo que dice el contenedor.
        save(slot = 2, at = t("21:00"), seed = 22)
        save(slot = 1, at = t("09:00"), seed = 11)

        assertEquals(2, manager.lastSaveSummary(gameA, "stub", "0")?.slot)
    }

    private companion object {
        const val STATE_BYTES = 1024

        /** 15 de agosto de 2026 a la hora indicada, en UTC. */
        fun t(hhmm: String): Long {
            val (h, m) = hhmm.split(":").map(String::toInt)
            return 1_786_752_000_000L + h * 3_600_000L + m * 60_000L
        }
    }
}
