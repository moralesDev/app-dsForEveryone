package com.moralesDev.dsForEveryone.data.rom

import app.cash.turbine.test
import com.moralesDev.dsForEveryone.data.db.FakeRomDao
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * `RomRepository` no tenía cobertura en el proyecto hermano y quedó anotado
 * como pendiente; aquí se escribe desde el principio.
 *
 * `ContentResolver` no se puede instanciar en una prueba de JVM, así que se
 * sustituye por [FakeDocumentAccess]: lo que se comprueba es la lógica de
 * `RomRepository` (deduplicado por hash, disponibilidad, zips), no el
 * Storage Access Framework en sí.
 */
class RomRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var documentAccess: FakeDocumentAccess
    private lateinit var dao: FakeRomDao
    private lateinit var repository: RomRepository

    @Before
    fun setUp() {
        documentAccess = FakeDocumentAccess()
        dao = FakeRomDao()
        repository = RomRepository(documentAccess, dao, StorageManager(folder.root), Dispatchers.Unconfined)
    }

    @Test
    fun `una rom nueva se da de alta con su titulo, codigo e icono`() = runTest {
        documentAccess.put(URI, DsRomFixtures.validRomBytes(title = "MARIOKART", gameCode = "ATSE"), "mariokart.nds")

        val result = repository.import(URI)

        val imported = (result as RomImportResult.Imported).rom
        assertEquals("MARIOKART", imported.internalTitle)
        assertEquals("ATSE", imported.gameCode)
        assertNotNull(imported.icon)
        assertEquals(32 * 32, imported.icon!!.size)
        assertTrue(documentAccess.hasReadPermission(URI))
    }

    @Test
    fun `volver a importar el mismo contenido no duplica, solo actualiza la ubicacion`() = runTest {
        val rom = DsRomFixtures.validRomBytes()
        documentAccess.put(URI, rom, "primero.nds")
        val first = (repository.import(URI) as RomImportResult.Imported).rom

        documentAccess.put(OTHER_URI, rom, "segundo.nds")
        val result = repository.import(OTHER_URI)

        val again = (result as RomImportResult.AlreadyInLibrary).rom
        assertEquals(first.id, again.id)
        assertEquals("segundo.nds", again.fileName)
        assertEquals(OTHER_URI, again.documentUri)

        repository.observeLibrary().test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `un archivo que no es una rom de DS se rechaza como no reconocido`() = runTest {
        documentAccess.put(URI, ByteArray(4096) { it.toByte() }, "no-es-una-rom.nds")

        assertEquals(RomImportResult.UnrecognizedRom, repository.import(URI))
    }

    @Test
    fun `un documento sin contenido da un resultado ilegible, no una excepcion`() = runTest {
        // No se llama a documentAccess.put: el proveedor no entrega ningún flujo.
        assertEquals(RomImportResult.Unreadable, repository.import(URI))
    }

    @Test
    fun `una rom dentro de un zip se importa igual que una suelta`() = runTest {
        val rom = DsRomFixtures.validRomBytes(gameCode = "BSME")
        documentAccess.put(URI, zipOf("Juego.nds" to rom), "Juego.zip")

        val result = repository.import(URI)

        assertEquals("BSME", (result as RomImportResult.Imported).rom.gameCode)
    }

    @Test
    fun `un zip sin ninguna nds adentro se rechaza con su propio motivo`() = runTest {
        documentAccess.put(URI, zipOf("leeme.txt" to byteArrayOf(1)), "descarga.zip")

        assertEquals(RomImportResult.NoRomInZip, repository.import(URI))
    }

    @Test
    fun `un zip con varias nds se rechaza sin elegir por el usuario`() = runTest {
        documentAccess.put(
            URI,
            zipOf("uno.nds" to DsRomFixtures.validRomBytes(), "dos.nds" to DsRomFixtures.validRomBytes(gameCode = "ZZZZ")),
            "coleccion.zip",
        )

        assertEquals(RomImportResult.MultipleRomsInZip(2), repository.import(URI))
    }

    @Test
    fun `mover el archivo original marca la entrada como no disponible sin perder la partida`() = runTest {
        documentAccess.put(URI, DsRomFixtures.validRomBytes(), "juego.nds")
        val imported = (repository.import(URI) as RomImportResult.Imported).rom

        documentAccess.revokePermission(URI)

        repository.observeLibrary().test {
            val entry = awaitItem().single()
            assertFalse(entry.isAvailable)
            cancelAndIgnoreRemainingEvents()
        }
        // La fila sigue en la biblioteca: no se borró nada al perder el acceso.
        assertNotNull(repository.findById(imported.id))
    }

    @Test
    fun `readRomBytes descomprime la unica entrada de un zip`() = runTest {
        val rom = DsRomFixtures.validRomBytes()
        documentAccess.put(URI, zipOf("Juego.nds" to rom), "Juego.zip")
        val imported = (repository.import(URI) as RomImportResult.Imported).rom

        val bytes = repository.readRomBytes(imported)

        assertTrue(rom.contentEquals(bytes))
    }

    @Test
    fun `quitar una rom sin borrar partidas conserva sus archivos en disco`() = runTest {
        documentAccess.put(URI, DsRomFixtures.validRomBytes(), "juego.nds")
        val imported = (repository.import(URI) as RomImportResult.Imported).rom
        val storage = StorageManager(folder.root)
        val markerFile = storage.batteryFile(imported.id).apply { writeText("save-data") }

        repository.remove(imported.id, deleteSaves = false)

        assertNull(repository.findById(imported.id))
        assertTrue(markerFile.exists())
        assertFalse(documentAccess.hasReadPermission(URI))
    }

    @Test
    fun `quitar una rom borrando partidas borra tambien sus archivos`() = runTest {
        documentAccess.put(URI, DsRomFixtures.validRomBytes(), "juego.nds")
        val imported = (repository.import(URI) as RomImportResult.Imported).rom
        val storage = StorageManager(folder.root)
        val markerFile = storage.batteryFile(imported.id).apply { writeText("save-data") }

        repository.remove(imported.id, deleteSaves = true)

        assertFalse(markerFile.exists())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private companion object {
        const val URI = "content://documents/tree/primary/juego.nds"
        const val OTHER_URI = "content://documents/tree/primary/otra-copia.nds"
    }
}
