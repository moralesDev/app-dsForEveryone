package com.moralesDev.dsForEveryone.data.storage

import com.moralesDev.dsForEveryone.data.save.SaveStateManager
import com.moralesDev.dsForEveryone.data.save.StateIdentity
import com.moralesDev.dsForEveryone.data.save.StateReadResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * El almacenamiento sobre un disco de verdad, en las dos plataformas.
 *
 * Las pruebas detalladas de partidas siguen en `:app` (JVM). Éstas existen
 * para que lo que cambia de una plataforma a otra se ejecute también en
 * iOS: sobre todo [writeAtomically], cuyo `fsync` es POSIX con
 * `F_FULLFSYNC` en iOS y `FileDescriptor.sync()` en Android.
 */
class StorageOnDiskTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path

    @BeforeTest
    fun createRoot() {
        root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dsforeveryone-test-${Random.nextLong().toULong()}"
        fileSystem.createDirectories(root)
    }

    @AfterTest
    fun deleteRoot() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    @Test
    fun `escritura atomica — crea carpetas y sobrescribe sin dejar temporales`() {
        val target = root / "a" / "b" / "partida.sav"
        writeAtomically(target, byteArrayOf(1, 2, 3))
        writeAtomically(target, byteArrayOf(4, 5))

        assertContentEquals(byteArrayOf(4, 5), fileSystem.read(target) { readByteArray() })
        assertEquals(listOf("partida.sav"), fileSystem.list(target.parent!!).map { it.name })
    }

    @Test
    fun `escritura atomica de un archivo vacio`() {
        val target = root / "vacio.bin"
        writeAtomically(target, ByteArray(0))
        assertEquals(0L, fileSystem.metadata(target).size)
    }

    @Test
    fun `cada juego en su carpeta y borrar uno no toca a otro`() {
        val storage = StorageManager(root)
        writeAtomically(storage.batteryPath("juegoA"), ByteArray(10))
        writeAtomically(storage.statePath("juegoA", 1), ByteArray(5))
        writeAtomically(storage.batteryPath("juegoB"), ByteArray(7))

        assertTrue(storage.hasRomData("juegoA"))
        assertEquals(15L, storage.usedBytes("juegoA"))

        storage.deleteRomData("juegoA")

        assertFalse(storage.hasRomData("juegoA"))
        assertEquals(0L, storage.usedBytes("juegoA"))
        assertTrue(storage.hasRomData("juegoB"))
    }

    @Test
    fun `preguntar por un juego no crea su carpeta`() {
        val storage = StorageManager(root)
        assertFalse(storage.hasRomData("nunca-visto"))
        assertFalse(fileSystem.exists(storage.savesPath() / "nunca-visto"))
    }

    @Test
    fun `los temporales de ROM no se repiten y existen al volver`() {
        val storage = StorageManager(root)
        val first = storage.newRomScratchPath()
        val second = storage.newRomScratchPath()
        assertNotEquals(first, second)
        assertTrue(fileSystem.exists(first) && fileSystem.exists(second))
    }

    @Test
    fun `un estado guardado se recupera igual — contenedor y hash y disco`() = runTest {
        val storage = StorageManager(root)
        val manager = SaveStateManager(storage, Dispatchers.Unconfined)
        val identity = StateIdentity(
            romSha256 = "ab".repeat(32),
            romCrc32 = 0xCBF43926L,
            coreName = "melonDS",
            coreVersion = "1.1",
            stateSize = 4096,
        )
        val payload = ByteArray(4096) { (it * 7).toByte() }

        assertTrue(manager.write("juego", 1, payload, frame = null, identity = identity))

        val read = manager.read("juego", 1, identity)
        assertEquals(StateReadResult.Loaded(payload), read)
        assertEquals(StateReadResult.Empty, manager.read("juego", 2, identity))
    }
}
