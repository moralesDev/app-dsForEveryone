package com.moralesDev.dsForEveryone.data.zip

import okio.Buffer
import okio.FileSystem
import okio.Path
import okio.use
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El zip común en las dos plataformas: en iOS comprime y descomprime el
 * zlib del sistema a través de Okio, y es la única prueba que lo ejercita
 * allí. La compatibilidad con `java.util.zip` la comprueba ZipInteropTest,
 * en `:app`.
 */
class ZipRoundTripTest {
    private val fileSystem = FileSystem.SYSTEM
    private lateinit var root: Path

    @BeforeTest
    fun createRoot() {
        root = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "dsforeveryone-zip-${Random.nextLong().toULong()}"
        fileSystem.createDirectories(root)
    }

    @AfterTest
    fun deleteRoot() {
        fileSystem.deleteRecursively(root, mustExist = false)
    }

    private fun write(entries: List<Pair<String, ByteArray>>): Path {
        val path = root / "copia.zip"
        ZipWriter(fileSystem.sink(path), modifiedAtMillis = 1_700_000_000_000L).use { zip ->
            for ((name, data) in entries) zip.writeEntry(name, data)
        }
        return path
    }

    @Test
    fun `ida y vuelta — mismos nombres en el mismo orden y mismos bytes`() {
        val entries = listOf(
            "primero.json" to "{}".encodeToByteArray(),
            "saves/a/battery.sav" to Random(7).nextBytes(200_000),
            "saves/a/repetitivo.bin" to ByteArray(1_000_000) { 3 },
            "vacío" to ByteArray(0),
        )
        ZipArchiveReader(fileSystem, write(entries)).use { zip ->
            assertEquals(entries.map { it.first }, zip.entries.map { it.name })
            for ((entry, expected) in zip.entries.zip(entries)) {
                assertContentEquals(expected.second, zip.read(entry, Int.MAX_VALUE))
            }
        }
    }

    @Test
    fun `leer con tope devuelve null en cuanto lo descomprimido pasa`() {
        ZipArchiveReader(fileSystem, write(listOf("grande" to ByteArray(100_000)))).use { zip ->
            assertNull(zip.read(zip.entries.single(), 99_999))
            assertEquals(100_000, zip.read(zip.entries.single(), 100_000)!!.size)
        }
    }

    @Test
    fun `un archivo que no empieza como zip no tiene entradas`() {
        val path = root / "hola.zip"
        fileSystem.write(path) { writeUtf8("hola") }
        ZipArchiveReader(fileSystem, path).use { zip -> assertTrue(zip.entries.isEmpty()) }
    }

    @Test
    fun `un zip sin final del directorio central se rechaza`() {
        val path = write(listOf("a" to ByteArray(10)))
        val bytes = fileSystem.read(path) { readByteArray() }
        fileSystem.write(path) { write(bytes, 0, bytes.size - 22) }
        assertFailsWith<ZipFormatException> { ZipArchiveReader(fileSystem, path).close() }
    }

    @Test
    fun `un byte cambiado en lo comprimido se nota`() {
        val path = write(listOf("datos" to Random(3).nextBytes(10_000)))
        val bytes = fileSystem.read(path) { readByteArray() }
        // Dentro de los datos comprimidos: tras la cabecera local (30 + nombre).
        bytes[30 + "datos".length + 50] = (bytes[30 + "datos".length + 50].toInt() xor 0x55).toByte()
        fileSystem.write(path) { write(bytes) }
        ZipArchiveReader(fileSystem, path).use { zip ->
            assertFailsWith<okio.IOException> { zip.read(zip.entries.single(), Int.MAX_VALUE) }
        }
    }

    @Test
    fun `fecha de MS-DOS — 2023-11-14 22-13-20 UTC`() {
        val (date, time) = toDosDateTime(1_700_000_000_000L)
        assertEquals(((2023 - 1980) shl 9) or (11 shl 5) or 14, date)
        assertEquals((22 shl 11) or (13 shl 5) or (20 / 2), time)
    }

    @Test
    fun `el escritor no deja el sink a medias si no hay entradas`() {
        val buffer = Buffer()
        ZipWriter(buffer, modifiedAtMillis = 0L).close()
        // Sólo el final del directorio central: 22 bytes.
        assertEquals(22L, buffer.size)
    }
}
