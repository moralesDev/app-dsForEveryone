package com.moralesDev.dsForEveryone.data.save

import com.moralesDev.dsForEveryone.data.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * La copia de partidas sobre carpetas de verdad.
 *
 * Dos «móviles» —dos `filesDir` temporales distintos— para que exportar en
 * uno e importar en otro sea literalmente eso. Las copias malas se fabrican a
 * mano entrada a entrada: lo que importa del formato no es escribir bien, sino
 * negarse a leer mal, y negarse **sin tocar `saves/`**.
 */
class SaveArchiveTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var phoneA: Phone
    private lateinit var phoneB: Phone

    private val gameA = "a".repeat(64)
    private val gameB = "b".repeat(64)

    private class Phone(root: File) {
        val storage = StorageManager(root)
        val states = SaveStateManager(storage, Dispatchers.Unconfined)
        val battery = BatterySaveManager(storage, Dispatchers.Unconfined)
        val archive = SaveArchive(storage, Dispatchers.Unconfined, clock = { 1_700_000_000_000L })
    }

    @Before
    fun setUp() {
        phoneA = Phone(folder.newFolder("a"))
        phoneB = Phone(folder.newFolder("b"))
    }

    private fun identity(game: String) = StateIdentity(game, 0x1234L, "stub", "0", STATE_BYTES)

    private fun payload(seed: Int) = ByteArray(STATE_BYTES) { (it * 7 + seed).toByte() }

    private suspend fun Phone.saveState(game: String, slot: Int, seed: Int) =
        assertTrue(states.write(game, slot, payload(seed), frame = null, identity(game)))

    private fun stateBytes(game: String, slot: Int, seed: Int = 1): ByteArray {
        val data = payload(seed)
        val header = SaveStateHeader(
            containerVersion = SaveStateContainer.CONTAINER_VERSION,
            coreName = "stub",
            coreVersion = "0",
            romSha256 = game,
            romCrc32 = 0x1234L,
            savedAt = 1L,
            slot = slot,
            stateSize = data.size,
            payloadSha256 = SaveStateContainer.sha256(data),
        )
        return SaveStateContainer.encode(header, data)
    }

    private suspend fun Phone.exportAll(): Pair<ArchiveExportResult, ByteArray> {
        val output = ByteArrayOutputStream()
        val result = archive.export(output, titles = mapOf(gameA to "Juego A"))
        return result to output.toByteArray()
    }

    private suspend fun Phone.prepare(bytes: ByteArray): ArchivePrepareResult =
        archive.prepare { ByteArrayInputStream(bytes) }

    private suspend fun Phone.prepareReady(bytes: ByteArray): PreparedImport {
        val result = prepare(bytes)
        assertTrue("Se esperaba Ready y llegó $result", result is ArchivePrepareResult.Ready)
        return (result as ArchivePrepareResult.Ready).prepared
    }

    private fun Phone.file(path: String) = File(storage.savesDirectory(), path)

    private fun Phone.snapshot(): Map<String, List<Byte>> =
        storage.savesDirectory().walkTopDown().filter(File::isFile).associate {
            it.relativeTo(storage.savesDirectory()).path to it.readBytes().toList()
        }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            for ((name, bytes) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun manifest(
        vararg games: String,
        format: String = SaveArchive.FORMAT,
        version: Int = SaveArchive.FORMAT_VERSION,
    ): Pair<String, ByteArray> {
        val list = games.joinToString(",") { """{"id":"$it"}""" }
        return SaveArchive.MANIFEST to
            """{"format":"$format","version":$version,"exportedAt":1,"games":[$list]}""".toByteArray()
    }

    private val sav = "partida".toByteArray()

    // ------------------------------------------------------------ ida y vuelta

    @Test
    fun `exportar en un movil e importar en otro deja las mismas partidas byte a byte`() = runTest {
        phoneA.battery.write(gameA, sav)
        phoneA.saveState(gameA, 1, seed = 1)
        phoneA.saveState(gameA, SaveStateManager.AUTO_SLOT, seed = 2)
        phoneA.file("$gameA/cheats.json").writeText("""{"entries":[]}""")
        phoneA.file("$gameA/states/slot_1.png").writeBytes(PNG + byteArrayOf(1, 2, 3))
        phoneA.saveState(gameB, 3, seed = 3)

        val (result, bytes) = phoneA.exportAll()
        assertEquals(ArchiveExportResult.Exported(games = 2, skippedFiles = 0), result)

        val prepared = phoneB.prepareReady(bytes)
        assertEquals(setOf(gameA, gameB), prepared.games.map { it.id }.toSet())
        assertEquals("Juego A", prepared.games.single { it.id == gameA }.title)
        assertTrue(prepared.games.none { it.replacesExisting })

        assertEquals(CommitOutcome(imported = 2, failed = 0), phoneB.archive.commit(prepared))
        assertEquals(phoneA.snapshot(), phoneB.snapshot())

        // Y lo importado se puede restaurar, no sólo está ahí.
        assertEquals(
            StateReadResult.Loaded(payload(1)),
            phoneB.states.read(gameA, 1, identity(gameA)),
        )
    }

    @Test
    fun `exportar un solo juego no arrastra a los demas`() = runTest {
        phoneA.saveState(gameA, 1, seed = 1)
        phoneA.saveState(gameB, 1, seed = 2)

        val output = ByteArrayOutputStream()
        val result = phoneA.archive.export(output, emptyMap(), onlyRomId = gameB)

        assertEquals(ArchiveExportResult.Exported(games = 1, skippedFiles = 0), result)
        val names = entryNames(output.toByteArray())
        assertEquals(SaveArchive.MANIFEST, names.first())
        assertTrue(names.drop(1).all { it.startsWith("saves/$gameB/") })
    }

    @Test
    fun `sin partidas no hay nada que exportar`() = runTest {
        val (result, _) = phoneA.exportAll()
        assertEquals(ArchiveExportResult.NothingToExport, result)
    }

    @Test
    fun `un battery sav vacio no viaja ni cuenta como danado`() = runTest {
        phoneA.file("$gameA/battery.sav").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(0)) }
        phoneA.saveState(gameA, 1, seed = 1)

        val (result, bytes) = phoneA.exportAll()

        assertEquals(ArchiveExportResult.Exported(games = 1, skippedFiles = 0), result)
        assertFalse(entryNames(bytes).any { it.endsWith("battery.sav") })
    }

    @Test
    fun `exportar deja fuera lo que importar rechazaria y la copia sigue siendo importable`() = runTest {
        phoneA.saveState(gameA, 1, seed = 1)
        phoneA.saveState(gameA, 2, seed = 2)
        // Un corte de corriente a mitad de una escritura antigua.
        val slot2 = phoneA.file("$gameA/states/slot_2.state")
        slot2.writeBytes(slot2.readBytes().copyOf(100))

        val (result, bytes) = phoneA.exportAll()

        assertEquals(ArchiveExportResult.Exported(games = 1, skippedFiles = 1), result)
        assertFalse(entryNames(bytes).any { it.endsWith("slot_2.state") })
        phoneB.prepareReady(bytes)
    }

    @Test
    fun `los estados de otra version del core viajan`() = runTest {
        val old = StateIdentity(gameA, 0x1234L, "melonDS", "0.9", STATE_BYTES)
        phoneA.states.write(gameA, 1, payload(1), frame = null, old)

        val (result, bytes) = phoneA.exportAll()

        assertEquals(ArchiveExportResult.Exported(games = 1, skippedFiles = 0), result)
        phoneB.prepareReady(bytes)
    }

    // ------------------------------------------------------------ reemplazo

    @Test
    fun `preparar no toca saves y descartar tampoco`() = runTest {
        phoneA.saveState(gameA, 1, seed = 1)
        val (_, bytes) = phoneA.exportAll()
        phoneB.saveState(gameA, 4, seed = 9)
        val before = phoneB.snapshot()

        val prepared = phoneB.prepareReady(bytes)
        assertTrue(prepared.games.single().replacesExisting)
        assertEquals(before, phoneB.snapshot())

        phoneB.archive.discard(prepared)
        assertEquals(before, phoneB.snapshot())
        assertTrue(phoneB.storage.importStagingDirectory().listFiles().isNullOrEmpty())
    }

    @Test
    fun `importar reemplaza la carpeta entera del juego y no toca a los demas`() = runTest {
        phoneA.saveState(gameA, 1, seed = 1)
        val (_, bytes) = phoneA.exportAll()

        phoneB.saveState(gameA, 4, seed = 9)
        phoneB.battery.write(gameA, "vieja".toByteArray())
        phoneB.saveState(gameB, 2, seed = 5)
        val gameBBefore = phoneB.file("$gameB/states/slot_2.state").readBytes()

        phoneB.archive.commit(phoneB.prepareReady(bytes))

        // Reemplazar, no fusionar: lo que no venía en la copia ya no está.
        assertFalse(phoneB.file("$gameA/states/slot_4.state").exists())
        assertFalse(phoneB.file("$gameA/battery.sav").exists())
        assertTrue(phoneB.file("$gameA/states/slot_1.state").exists())
        assertArrayEquals(gameBBefore, phoneB.file("$gameB/states/slot_2.state").readBytes())
        assertFalse(phoneB.storage.savesDirectory().list()!!.any { it.startsWith(".replaced-") })
    }

    @Test
    fun `importar desde la hoja de un juego toma solo ese juego de una copia completa`() = runTest {
        phoneA.saveState(gameA, 1, seed = 1)
        phoneA.saveState(gameB, 1, seed = 2)
        val (_, bytes) = phoneA.exportAll()

        val outcome = phoneB.archive.commit(phoneB.prepareReady(bytes), onlyRomId = gameB)

        assertEquals(CommitOutcome(imported = 1, failed = 0), outcome)
        assertTrue(phoneB.file("$gameB/states/slot_1.state").exists())
        assertFalse(phoneB.file(gameA).exists())
    }

    // ------------------------------------------------------------ rechazos

    private suspend fun assertRejected(expected: ArchivePrepareResult, bytes: ByteArray) {
        phoneB.saveState(gameA, 4, seed = 9)
        val before = phoneB.snapshot()

        assertEquals(expected, phoneB.prepare(bytes))

        assertEquals("Un rechazo no puede tocar saves/", before, phoneB.snapshot())
        assertTrue(
            "Un rechazo no deja nada preparado",
            phoneB.storage.importStagingDirectory().listFiles().isNullOrEmpty(),
        )
    }

    @Test
    fun `un archivo que no es zip no es una copia`() = runTest {
        assertRejected(ArchivePrepareResult.NotABackup, "hola".toByteArray())
    }

    @Test
    fun `un zip sin manifiesto delante no es una copia`() = runTest {
        assertRejected(
            ArchivePrepareResult.NotABackup,
            zip("saves/$gameA/battery.sav" to sav, manifest(gameA)),
        )
    }

    @Test
    fun `un manifiesto de otro formato no es una copia`() = runTest {
        assertRejected(
            ArchivePrepareResult.NotABackup,
            zip(manifest(gameA, format = "otra-app"), "saves/$gameA/battery.sav" to sav),
        )
    }

    @Test
    fun `un manifiesto que no es json no es una copia`() = runTest {
        assertRejected(
            ArchivePrepareResult.NotABackup,
            zip(SaveArchive.MANIFEST to "{roto".toByteArray(), "saves/$gameA/battery.sav" to sav),
        )
    }

    @Test
    fun `una copia de una version mas nueva se rechaza diciendolo`() = runTest {
        assertRejected(
            ArchivePrepareResult.NewerFormat(2),
            zip(manifest(gameA, version = 2), "saves/$gameA/battery.sav" to sav),
        )
    }

    @Test
    fun `una ruta que intenta salir de saves se rechaza`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameA/../../../evil.sav" to sav),
        )
        assertFalse(File(folder.root, "evil.sav").exists())
    }

    @Test
    fun `cualquier entrada fuera de la lista se rechaza`() = runTest {
        for (name in listOf(
            "saves/${gameA.uppercase()}/battery.sav",
            "saves/$gameA/states/slot_6.state",
            "saves/$gameA/states/slot_0.state",
            "saves/$gameA/battery.sav.tmp",
            "/saves/$gameA/battery.sav",
            "covers/$gameA.webp",
        )) {
            assertRejected(ArchivePrepareResult.Corrupt, zip(manifest(gameA), name to sav))
        }
    }

    @Test
    fun `una entrada duplicada se rechaza`() = runTest {
        // ZipOutputStream no deja escribir duplicados; se fabrica cambiando
        // el nombre de la segunda entrada en los bytes ya escritos.
        val bytes = zip(
            manifest(gameA),
            "saves/$gameA/battery.sav" to sav,
            "saves/$gameA/battery.saw" to sav,
        )
        val tampered = String(bytes, Charsets.ISO_8859_1)
            .replace("battery.saw", "battery.sav")
            .toByteArray(Charsets.ISO_8859_1)
        assertRejected(ArchivePrepareResult.Corrupt, tampered)
    }

    @Test
    fun `un estado de otro juego dentro de la carpeta de este se rechaza`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameA/states/slot_1.state" to stateBytes(gameB, 1)),
        )
    }

    @Test
    fun `un estado cuya ranura no es la de su nombre se rechaza`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameA/states/slot_2.state" to stateBytes(gameA, 1)),
        )
    }

    @Test
    fun `un estado truncado se rechaza`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameA/states/slot_1.state" to stateBytes(gameA, 1).copyOf(120)),
        )
    }

    @Test
    fun `un estado con un bit cambiado en el payload se rechaza`() = runTest {
        val state = stateBytes(gameA, 1)
        state[state.size - 1] = (state[state.size - 1].toInt() xor 1).toByte()
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameA/states/slot_1.state" to state),
        )
    }

    @Test
    fun `basta un archivo malo para rechazar la copia entera`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(
                manifest(gameA, gameB),
                "saves/$gameA/states/slot_1.state" to stateBytes(gameA, 1),
                "saves/$gameB/cheats.json" to "no es json".toByteArray(),
            ),
        )
    }

    @Test
    fun `una miniatura que no es png se rechaza`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameA/states/slot_1.png" to "GIF89a".toByteArray()),
        )
    }

    @Test
    fun `un battery sav vacio dentro de la copia se rechaza`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameA/battery.sav" to ByteArray(0)),
        )
    }

    @Test
    fun `un manifiesto que no coincide con lo que trae la copia se rechaza`() = runTest {
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA, gameB), "saves/$gameA/battery.sav" to sav),
        )
        assertRejected(
            ArchivePrepareResult.Corrupt,
            zip(manifest(gameA), "saves/$gameB/battery.sav" to sav),
        )
        assertRejected(ArchivePrepareResult.Corrupt, zip(manifest()))
    }

    @Test
    fun `una entrada mas grande que su tope se rechaza sin creerse lo que declara el zip`() = runTest {
        assertRejected(
            ArchivePrepareResult.TooLarge,
            zip(manifest(gameA), "saves/$gameA/battery.sav" to ByteArray(SaveArchive.MAX_BATTERY_BYTES + 1)),
        )
    }

    // ------------------------------------------------------------ recuperación

    @Test
    fun `si el proceso murio entre los dos rename la carpeta anterior vuelve`() = runTest {
        phoneB.saveState(gameA, 1, seed = 1)
        val before = phoneB.snapshot()
        val saves = phoneB.storage.savesDirectory()
        assertTrue(File(saves, gameA).renameTo(File(saves, ".replaced-$gameA")))

        phoneB.archive.recoverInterrupted()

        assertEquals(before, phoneB.snapshot())
    }

    @Test
    fun `si el proceso murio tras colocar la nueva sobra la vieja y la preparacion`() = runTest {
        phoneB.saveState(gameA, 1, seed = 1)
        val saves = phoneB.storage.savesDirectory()
        File(saves, ".replaced-$gameA/states").mkdirs()
        File(saves, ".replaced-$gameA/states/slot_5.state").writeBytes(byteArrayOf(1))
        File(phoneB.storage.importStagingDirectory(), "import-x/$gameA").mkdirs()
        val placed = phoneB.snapshot().filterKeys { it.startsWith(gameA) }

        phoneB.archive.recoverInterrupted()

        assertEquals(placed, phoneB.snapshot())
        assertFalse(phoneB.storage.importStagingDirectory().exists())
    }

    private fun entryNames(bytes: ByteArray): List<String> =
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name }.toList()
        }

    private companion object {
        const val STATE_BYTES = 4096
        val PNG = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
    }
}
