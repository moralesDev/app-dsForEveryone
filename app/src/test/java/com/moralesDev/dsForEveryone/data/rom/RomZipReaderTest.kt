package com.moralesDev.dsForEveryone.data.rom

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * `RomZipReader` sobre `.zip` construidos en la propia prueba: nunca se
 * decide por el nombre del archivo, sólo por lo que hay dentro.
 */
class RomZipReaderTest {

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

    private fun open(bytes: ByteArray): () -> InputStream = { ByteArrayInputStream(bytes) }

    @Test
    fun `reconoce la firma de un zip sin mirar el nombre`() {
        val bytes = zipOf("game.nds" to byteArrayOf(1, 2, 3))

        assertTrue(RomZipReader.isZip(bytes.copyOf(4)))
    }

    @Test
    fun `una nds suelta no tiene firma de zip`() {
        val fakeRom = ByteArray(200) { it.toByte() }

        assertFalse(RomZipReader.isZip(fakeRom.copyOf(4)))
    }

    @Test
    fun `una unica entrada nds se resuelve con su huella`() {
        val content = ByteArray(1024) { it.toByte() }
        val bytes = zipOf("Pokemon.nds" to content)

        val resolution = RomZipReader.resolveRom(open(bytes))

        assertEquals(
            RomIdentity.read(ByteArrayInputStream(content)).id,
            (resolution as RomZipReader.Resolution.Single).fingerprint.id,
        )
    }

    @Test
    fun `ignora carpetas, ocultos y basura de macOS`() {
        val content = ByteArray(100) { it.toByte() }
        val bytes = zipOf(
            "carpeta/" to ByteArray(0),
            "__MACOSX/._Pokemon.nds" to byteArrayOf(1),
            ".oculto.nds" to byteArrayOf(1),
            "carpeta/Pokemon.nds" to content,
        )

        val resolution = RomZipReader.resolveRom(open(bytes)) as RomZipReader.Resolution.Single

        assertEquals(RomIdentity.read(ByteArrayInputStream(content)).id, resolution.fingerprint.id)
    }

    @Test
    fun `no distingue mayusculas en la extension`() {
        val bytes = zipOf("Pokemon.NDS" to ByteArray(10))

        assertTrue(RomZipReader.resolveRom(open(bytes)) is RomZipReader.Resolution.Single)
    }

    @Test
    fun `varias entradas nds se reportan como multiples, no se elige por el usuario`() {
        val bytes = zipOf("one.nds" to byteArrayOf(1), "two.nds" to byteArrayOf(2))

        assertEquals(RomZipReader.Resolution.Multiple(2), RomZipReader.resolveRom(open(bytes)))
    }

    @Test
    fun `sin ninguna entrada de rom se distingue de un zip vacio`() {
        val withOtherFiles = zipOf("readme.txt" to byteArrayOf(1), "portada.png" to byteArrayOf(2))
        val empty = zipOf()

        assertEquals(RomZipReader.Resolution.None, RomZipReader.resolveRom(open(withOtherFiles)))
        assertEquals(RomZipReader.Resolution.None, RomZipReader.resolveRom(open(empty)))
    }

    @Test
    fun `un zip con datos comprimidos danados no lanza sin controlar`() {
        // Cabecera local y nombre intactos —para que se reconozca como .nds
        // de verdad—; lo que se destroza son los bytes comprimidos que le
        // siguen, así que la rotura aparece al intentar descomprimirlos, no al
        // leer la cabecera. Contenido pseudoaleatorio para que no comprima a
        // casi nada y sobren bytes de sobra que corromper.
        val name = "game.nds"
        val content = ByteArray(2_000) { index -> ((index * 31 + 7) and 0xFF).toByte() }
        val bytes = zipOf(name to content)
        val dataOffset = LOCAL_HEADER_SIZE + name.length
        val corrupted = bytes.copyOf().also { corrupted ->
            for (index in dataOffset until dataOffset + 20) {
                corrupted[index] = corrupted[index].inc()
            }
        }

        assertEquals(RomZipReader.Resolution.Corrupt, RomZipReader.resolveRom(open(corrupted)))
    }

    @Test
    fun `una entrada cifrada se distingue de una danada`() {
        val encrypted = encryptedZipHeader(entryName = "game.nds", data = byteArrayOf(1, 2, 3, 4))

        assertEquals(RomZipReader.Resolution.Encrypted, RomZipReader.resolveRom(open(encrypted)))
    }

    @Test
    fun `una entrada que de verdad supera el tope se rechaza sin cargarla entera igual`() {
        // El tope es de 1 GiB: escribir esa cantidad de una vez tumbaría la
        // propia prueba por memoria. Se escribe en trozos de ceros -que
        // comprimen a casi nada- para que el .zip resultante sea pequeño de
        // verdad y sólo LimitedInputStream vea el tamaño real al descomprimir.
        val bytes = zipOfOversizedEntry("game.nds", RomZipReader.MAX_UNCOMPRESSED_BYTES + 1)

        assertEquals(RomZipReader.Resolution.TooLarge, RomZipReader.resolveRom(open(bytes)))
    }

    private fun zipOfOversizedEntry(name: String, totalBytes: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(1 shl 20)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(name))
            var written = 0L
            while (written < totalBytes) {
                val toWrite = minOf(chunk.size.toLong(), totalBytes - written).toInt()
                zip.write(chunk, 0, toWrite)
                written += toWrite
            }
            zip.closeEntry()
        }
        return out.toByteArray()
    }

    /**
     * `extractRom` es lo que usa `RomRepository.readRomBytes` al cargar la
     * partida —no al darla de alta—, así que se entregan los bytes tal cual,
     * sin el mensaje distinto por caso que sí necesita `resolveRom`.
     */
    @Test
    fun `extractRom devuelve el contenido ya descomprimido de la unica entrada`() {
        val content = ByteArray(500) { it.toByte() }
        val bytes = zipOf("other.txt" to byteArrayOf(9), "game.nds" to content)

        val extracted = RomZipReader.extractRom(open(bytes))

        assertTrue(content.contentEquals(extracted))
    }

    @Test
    fun `extractRom no elige entre varias entradas`() {
        val bytes = zipOf("one.nds" to byteArrayOf(1), "two.nds" to byteArrayOf(2))

        assertEquals(null, RomZipReader.extractRom(open(bytes)))
    }

    @Test
    fun `extractRom no inventa una rom si no hay ninguna`() {
        val bytes = zipOf("readme.txt" to byteArrayOf(1))

        assertEquals(null, RomZipReader.extractRom(open(bytes)))
    }

    /**
     * Cabecera local de un `.zip` a mano, con el bit 0 de la bandera de
     * propósito general puesto (cifrado). `ZipOutputStream` no puede escribir
     * entradas cifradas —Java no lo soporta—, así que es la única forma de
     * ejercitar esta rama: `ZipInputStream` la rechaza al leer la cabecera,
     * antes de intentar descomprimir nada.
     */
    private fun encryptedZipHeader(entryName: String, data: ByteArray): ByteArray {
        val nameBytes = entryName.toByteArray(Charsets.US_ASCII)
        val buffer = ByteBuffer.allocate(30 + nameBytes.size + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0x04034b50) // firma de cabecera local
        buffer.putShort(20) // versión necesaria
        buffer.putShort(0x0001) // bandera: bit 0 = cifrado
        buffer.putShort(0) // método: almacenado
        buffer.putShort(0) // hora
        buffer.putShort(0) // fecha
        buffer.putInt(0) // crc32
        buffer.putInt(data.size) // tamaño comprimido
        buffer.putInt(data.size) // tamaño descomprimido
        buffer.putShort(nameBytes.size.toShort())
        buffer.putShort(0) // longitud del campo extra
        buffer.put(nameBytes)
        buffer.put(data)
        return buffer.array()
    }

    private companion object {
        /** Cabecera local de un `.zip`: firma + 12 campos fijos de 2/4 bytes, sin el nombre. */
        const val LOCAL_HEADER_SIZE = 30
    }
}
