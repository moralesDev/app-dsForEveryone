package com.moralesDev.dsForEveryone.data.cheats

import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `cheats.json` sobre disco de verdad, con el mismo motivo que
 * `SaveStateManagerTest`: que dos juegos no se mezclen y que un archivo roto
 * no llegue a interpretarse son afirmaciones sobre archivos, no sobre objetos
 * en memoria.
 */
class CheatsManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var storage: StorageManager
    private lateinit var manager: CheatsManager

    private val gameA = "a".repeat(64)
    private val gameB = "b".repeat(64)

    @Before
    fun setUp() {
        storage = StorageManager(folder.root)
        manager = CheatsManager(storage, Dispatchers.Unconfined)
    }

    private fun entry(id: String = "1", enabled: Boolean = true) = CheatEntry(
        id = id,
        label = "Objetos infinitos",
        lines = listOf("94000130 FFFB0000", "0221AFCA 00000001", "D2000000 00000000"),
        enabled = enabled,
    )

    @Test
    fun `sin archivo previo la lista esta vacia`() = runTest {
        assertEquals(CheatsLoadResult.Loaded(emptyList<CheatEntry>()), manager.read(gameA))
    }

    @Test
    fun `lo escrito se recupera tal cual`() = runTest {
        val entries = listOf(entry("1"), entry("2", enabled = false))

        assertTrue(manager.write(gameA, entries))

        assertEquals(CheatsLoadResult.Loaded(entries), manager.read(gameA))
    }

    @Test
    fun `dos juegos no comparten claves`() = runTest {
        manager.write(gameA, listOf(entry("a-only")))
        manager.write(gameB, listOf(entry("b-only")))

        assertEquals(CheatsLoadResult.Loaded(listOf(entry("a-only"))), manager.read(gameA))
        assertEquals(CheatsLoadResult.Loaded(listOf(entry("b-only"))), manager.read(gameB))
    }

    @Test
    fun `sobrescribir reemplaza el contenido entero`() = runTest {
        manager.write(gameA, listOf(entry("1"), entry("2")))
        manager.write(gameA, listOf(entry("solo-esta")))

        assertEquals(CheatsLoadResult.Loaded(listOf(entry("solo-esta"))), manager.read(gameA))
    }

    @Test
    fun `una lista vacia limpia el archivo`() = runTest {
        manager.write(gameA, listOf(entry("1")))
        manager.write(gameA, emptyList())

        assertEquals(CheatsLoadResult.Loaded(emptyList<CheatEntry>()), manager.read(gameA))
    }

    @Test
    fun `un json roto se trata como sin claves y se marca corrupto`() = runTest {
        storage.cheatsFile(gameA).apply { parentFile?.mkdirs() }.writeText("{ esto no es json")

        assertEquals(CheatsLoadResult.Corrupt, manager.read(gameA))
    }

    @Test
    fun `mas de cinco entradas se rechaza entero y no toca disco`() = runTest {
        val tooMany = (1..CheatsManager.MAX_ENTRIES + 1).map { entry(it.toString()) }

        assertFalse(manager.write(gameA, tooMany))
        assertEquals(CheatsLoadResult.Loaded(emptyList<CheatEntry>()), manager.read(gameA))
    }

    @Test
    fun `una entrada con demasiadas lineas se rechaza entero`() = runTest {
        val tooManyLines = entry("1").copy(lines = List(CheatsManager.MAX_LINES + 1) { "94000130 FFFB0000" })

        assertFalse(manager.write(gameA, listOf(tooManyLines)))
        assertEquals(CheatsLoadResult.Loaded(emptyList<CheatEntry>()), manager.read(gameA))
    }

    @Test
    fun `una entrada con demasiados caracteres se rechaza entero`() = runTest {
        val tooLong = entry("1").copy(lines = listOf("A".repeat(CheatsManager.MAX_CHARS + 1)))

        assertFalse(manager.write(gameA, listOf(tooLong)))
        assertEquals(CheatsLoadResult.Loaded(emptyList<CheatEntry>()), manager.read(gameA))
    }

    @Test
    fun `un rechazo por tope no borra lo que ya habia guardado`() = runTest {
        manager.write(gameA, listOf(entry("bueno")))

        val tooMany = (1..CheatsManager.MAX_ENTRIES + 1).map { entry(it.toString()) }
        assertFalse(manager.write(gameA, tooMany))

        assertEquals(CheatsLoadResult.Loaded(listOf(entry("bueno"))), manager.read(gameA))
    }
}
