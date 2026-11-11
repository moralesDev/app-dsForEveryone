package com.moralesDev.dsForEveryone.data.zip

import okio.FileSystem
import okio.Path.Companion.toOkioPath
import okio.sink
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.random.Random

/**
 * El zip común (ZipWriter, ZipArchiveReader) contra el de Java, que es el
 * que usaba la app hasta iOS-3 y con el que están hechas las copias de
 * partidas que ya tienen los usuarios. En las dos direcciones:
 *
 * - lo que escribe ZipWriter lo lee `java.util.zip` (en orden y por el
 *   directorio central), igual que cualquier otra herramienta;
 * - lo que escribía `ZipOutputStream` lo lee ZipArchiveReader, incluidas
 *   las entradas con «descriptor de datos» (tamaños después del contenido),
 *   las sin comprimir y las carpetas.
 */
class ZipInteropTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val entries = linkedMapOf(
        "dsforeveryone-saves.json" to """{"format":"x"}""".toByteArray(),
        "saves/abc/battery.sav" to Random(1).nextBytes(512 * 1024),
        "saves/abc/states/slot_1.state" to ByteArray(300_000) { (it % 7).toByte() },
        "saves/abc/vacío.bin" to ByteArray(0),
    )

    @Test
    fun `lo que escribe ZipWriter lo lee ZipInputStream entrada a entrada`() {
        val bytes = writeWithZipWriter()
        val read = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                read[entry.name] = zip.readBytes()
            }
        }
        assertSame(entries, read)
    }

    @Test
    fun `lo que escribe ZipWriter lo lee ZipFile por el directorio central`() {
        val file = folder.newFile("nuevo.zip").apply { writeBytes(writeWithZipWriter()) }
        ZipFile(file).use { zip ->
            val read = linkedMapOf<String, ByteArray>()
            for (entry in zip.entries()) read[entry.name] = zip.getInputStream(entry).readBytes()
            assertSame(entries, read)
        }
    }

    @Test
    fun `lo que escribia ZipOutputStream lo lee ZipArchiveReader`() {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("carpeta/"))
            zip.closeEntry()
            for ((name, data) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
            // Una entrada sin comprimir, que ZipOutputStream exige declarar entera.
            val stored = "guardado tal cual".toByteArray()
            zip.putNextEntry(
                ZipEntry("sin-comprimir.txt").apply {
                    method = ZipEntry.STORED
                    size = stored.size.toLong()
                    crc = java.util.zip.CRC32().apply { update(stored) }.value
                },
            )
            zip.write(stored)
            zip.closeEntry()
        }
        val file = folder.newFile("antiguo.zip").apply { writeBytes(output.toByteArray()) }

        ZipArchiveReader(FileSystem.SYSTEM, file.toOkioPath()).use { reader ->
            val names = reader.entries.map { it.name }
            assertEquals(listOf("carpeta/") + entries.keys + "sin-comprimir.txt", names)
            val read = linkedMapOf<String, ByteArray>()
            for (entry in reader.entries) {
                if (entry.isDirectory || entry.name == "sin-comprimir.txt") continue
                read[entry.name] = reader.read(entry, Int.MAX_VALUE)!!
            }
            assertSame(entries, read)
            assertArrayEquals(
                "guardado tal cual".toByteArray(),
                reader.read(reader.entries.last(), Int.MAX_VALUE),
            )
        }
    }

    private fun writeWithZipWriter(): ByteArray {
        val output = ByteArrayOutputStream()
        ZipWriter(output.sink(), modifiedAtMillis = 1_700_000_000_000L).use { zip ->
            for ((name, data) in entries) zip.writeEntry(name, data)
        }
        return output.toByteArray()
    }

    private fun assertSame(expected: Map<String, ByteArray>, actual: Map<String, ByteArray>) {
        assertEquals(expected.keys.toList(), actual.keys.toList())
        for ((name, data) in expected) assertArrayEquals(name, data, actual.getValue(name))
    }
}
