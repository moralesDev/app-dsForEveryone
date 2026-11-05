package com.moralesDev.dsForEveryone.ui.archive

import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.data.save.SaveStateManager
import com.moralesDev.dsForEveryone.data.save.StateIdentity
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.ui.UiMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Cuándo se pregunta y cuándo no, y que cancelar no deje nada a medias.
 *
 * Contra un [SaveArchive] real sobre carpetas temporales: el coordinador sólo
 * decide, y lo que se comprueba es lo que acaba en disco.
 */
class SaveArchiveCoordinatorTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val gameA = "a".repeat(64)
    private val gameB = "b".repeat(64)

    private lateinit var source: StorageManager
    private lateinit var target: StorageManager
    private val messages = mutableListOf<UiMessage>()

    @Before
    fun setUp() {
        source = StorageManager(folder.newFolder("source"))
        target = StorageManager(folder.newFolder("target"))
    }

    private suspend fun StorageManager.saveState(game: String, slot: Int) {
        val identity = StateIdentity(game, 1L, "stub", "0", 64)
        SaveStateManager(this, Dispatchers.Unconfined)
            .write(game, slot, ByteArray(64) { slot.toByte() }, frame = null, identity)
    }

    private suspend fun backupOf(storage: StorageManager): ByteArray {
        val output = ByteArrayOutputStream()
        SaveArchive(storage, Dispatchers.Unconfined).export(output, emptyMap())
        return output.toByteArray()
    }

    private fun TestScope.coordinatorFor(storage: StorageManager) =
        SaveArchiveCoordinator(SaveArchive(storage, Dispatchers.Unconfined), this) { messages += it }

    private fun stagingIsEmpty() = target.importStagingDirectory().listFiles().isNullOrEmpty()

    private fun stateOnTarget(game: String, slot: Int) = File(target.savesDirectory(), "$game/states/slot_$slot.state")

    @Test
    fun `si no reemplaza nada importa sin preguntar`() = runTest {
        source.saveState(gameA, 1)
        val backup = backupOf(source)
        val coordinator = coordinatorFor(target)

        coordinator.onPicked({ ByteArrayInputStream(backup) })
        advanceUntilIdle()

        assertNull(coordinator.pending.value)
        assertEquals(listOf(UiMessage(R.string.msg_archive_imported)), messages)
        assertTrue(stateOnTarget(gameA, 1).exists())
        assertFalse(coordinator.busy.value)
    }

    @Test
    fun `si reemplaza partidas pregunta antes y cuenta cuantas`() = runTest {
        source.saveState(gameA, 1)
        source.saveState(gameB, 1)
        val backup = backupOf(source)
        target.saveState(gameA, 3)
        val coordinator = coordinatorFor(target)

        coordinator.onPicked({ ByteArrayInputStream(backup) })
        advanceUntilIdle()

        val pending = coordinator.pending.value!!
        assertEquals(2, pending.gameCount)
        assertEquals(1, pending.replacedCount)
        assertTrue("Nada cambia antes de confirmar", stateOnTarget(gameA, 3).exists())
        assertTrue(messages.isEmpty())

        coordinator.confirm()
        advanceUntilIdle()

        assertNull(coordinator.pending.value)
        assertFalse(stateOnTarget(gameA, 3).exists())
        assertTrue(stateOnTarget(gameA, 1).exists())
        assertTrue(stateOnTarget(gameB, 1).exists())
        assertEquals(listOf(UiMessage(R.string.msg_archive_imported)), messages)
    }

    @Test
    fun `cancelar no toca las partidas y no deja nada preparado`() = runTest {
        source.saveState(gameA, 1)
        val backup = backupOf(source)
        target.saveState(gameA, 3)
        val coordinator = coordinatorFor(target)

        coordinator.onPicked({ ByteArrayInputStream(backup) })
        advanceUntilIdle()
        coordinator.cancel()

        assertNull(coordinator.pending.value)
        assertTrue(stateOnTarget(gameA, 3).exists())
        assertFalse(stateOnTarget(gameA, 1).exists())
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun `desde la hoja de un juego cuenta solo ese juego`() = runTest {
        source.saveState(gameA, 1)
        source.saveState(gameB, 1)
        val backup = backupOf(source)
        target.saveState(gameA, 3)
        val coordinator = coordinatorFor(target)

        // gameA tiene datos aquí, pero se importa desde la hoja de gameB.
        coordinator.onPicked({ ByteArrayInputStream(backup) }, onlyRomId = gameB)
        advanceUntilIdle()

        assertNull(coordinator.pending.value)
        assertTrue(stateOnTarget(gameA, 3).exists())
        assertTrue(stateOnTarget(gameB, 1).exists())
    }

    @Test
    fun `una copia sin el juego de la hoja se descarta y lo dice`() = runTest {
        source.saveState(gameA, 1)
        val backup = backupOf(source)
        val coordinator = coordinatorFor(target)

        coordinator.onPicked({ ByteArrayInputStream(backup) }, onlyRomId = gameB)
        advanceUntilIdle()

        assertEquals(listOf(UiMessage(R.string.msg_archive_not_this_game)), messages)
        assertFalse(File(target.savesDirectory(), gameA).exists())
        assertTrue(stagingIsEmpty())
    }

    @Test
    fun `un proveedor que lanza al abrir es un archivo ilegible`() = runTest {
        val coordinator = coordinatorFor(target)

        coordinator.onPicked({ throw SecurityException("sin permiso") })
        advanceUntilIdle()

        assertEquals(listOf(UiMessage(R.string.msg_archive_unreadable)), messages)
    }

    @Test
    fun `cerrar el selector sin elegir no hace nada`() = runTest {
        val coordinator = coordinatorFor(target)

        coordinator.onPicked(null)
        coordinator.export(null, emptyMap())
        advanceUntilIdle()

        assertTrue(messages.isEmpty())
    }

    @Test
    fun `exportar avisa de lo que se quedo fuera`() = runTest {
        source.saveState(gameA, 1)
        source.saveState(gameA, 2)
        val slot2 = File(source.savesDirectory(), "$gameA/states/slot_2.state")
        slot2.writeBytes(slot2.readBytes().copyOf(10))
        val coordinator = coordinatorFor(source)

        coordinator.export({ ByteArrayOutputStream() }, emptyMap())
        advanceUntilIdle()

        assertEquals(listOf(UiMessage(R.string.msg_archive_exported_skipped, 1)), messages)
    }
}
