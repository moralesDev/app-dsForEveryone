package com.moralesDev.dsForEveryone.data.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El contenedor visto como lo que es: un analizador de archivos que vienen del
 * disco y en los que no se puede confiar.
 *
 * Casi todas estas pruebas describen un archivo estropeado a mano. Es a
 * propósito: la parte que importa del formato no es escribir bien, sino
 * negarse a leer mal.
 */
class SaveStateContainerTest {

    private val payload = ByteArray(512) { (it * 7).toByte() }

    private val identity = StateIdentity(
        romSha256 = "a".repeat(64),
        romCrc32 = 0xDEADBEEFL,
        coreName = "stub",
        coreVersion = "0",
        stateSize = 512,
    )

    private fun header(
        romSha256: String = identity.romSha256,
        romCrc32: Long = identity.romCrc32,
        coreName: String = identity.coreName,
        coreVersion: String = identity.coreVersion,
        slot: Int = 1,
        body: ByteArray = payload,
    ) = SaveStateHeader(
        containerVersion = SaveStateContainer.CONTAINER_VERSION,
        coreName = coreName,
        coreVersion = coreVersion,
        romSha256 = romSha256,
        romCrc32 = romCrc32,
        savedAt = 1_700_000_000_000L,
        slot = slot,
        stateSize = body.size,
        payloadSha256 = SaveStateContainer.sha256(body),
    )

    private fun encoded(body: ByteArray = payload) =
        SaveStateContainer.encode(header(body = body), body)

    @Test
    fun `lo que se escribe es lo que se lee`() {
        val original = header()
        val decoded = SaveStateContainer.decode(SaveStateContainer.encode(original, payload))

        assertEquals(DecodedState.Valid(original, payload), decoded)
    }

    @Test
    fun `el archivo empieza por la firma E4FS`() {
        val bytes = encoded()

        assertEquals("E4FS", String(bytes, 0, 4, Charsets.US_ASCII))
    }

    @Test
    fun `un archivo cualquiera no se confunde con un estado`() {
        // Lo que pasaría al renombrar una foto: hay bytes, pero no son esto.
        val reason = rejection(ByteArray(4096) { 0x42 })

        assertEquals(StateRejection.NotAContainer, reason)
    }

    @Test
    fun `un contenedor de otra version se rechaza en lugar de interpretarse`() {
        val bytes = encoded()
        // Los dos bytes que siguen a la firma son la versión del contenedor.
        bytes[4] = 0
        bytes[5] = 9

        assertEquals(StateRejection.UnsupportedContainer(9), rejection(bytes))
    }

    @Test
    fun `un archivo cortado a medias se detecta`() {
        val bytes = encoded()

        assertEquals(StateRejection.Truncated, rejection(bytes.copyOf(bytes.size - 100)))
    }

    @Test
    fun `un estado sin cabecera completa se detecta`() {
        // Corte tan temprano que ni siquiera hay cabecera que leer.
        assertEquals(StateRejection.Truncated, rejection(encoded().copyOf(20)))
    }

    @Test
    fun `un byte cambiado en el estado lo invalida`() {
        val bytes = encoded()
        val lastByte = bytes.size - 1
        bytes[lastByte] = (bytes[lastByte] + 1).toByte()

        assertEquals(StateRejection.Corrupt, rejection(bytes))
    }

    @Test
    fun `sobra basura al final y el archivo se rechaza`() {
        assertEquals(StateRejection.Corrupt, rejection(encoded() + ByteArray(16)))
    }

    @Test
    fun `un tamano de estado imposible no llega a reservar memoria`() {
        val bytes = encoded()
        // El campo stateSize, en el offset 24. Se pone a 2 GB.
        bytes[24] = 0x7F
        bytes[25] = 0xFF.toByte()
        bytes[26] = 0xFF.toByte()
        bytes[27] = 0xFF.toByte()

        assertEquals(StateRejection.Corrupt, rejection(bytes))
    }

    @Test
    fun `el estado de otro juego se rechaza aunque el archivo este intacto`() {
        val other = header(romSha256 = "b".repeat(64))

        assertEquals(StateRejection.OtherGame, identity.reject(other))
    }

    @Test
    fun `no basta con que coincida el hash, el CRC tambien`() {
        // El CRC es la segunda condición, no un adorno.
        val other = header(romCrc32 = 0x12345678L)

        assertEquals(StateRejection.OtherGame, identity.reject(other))
    }

    @Test
    fun `el estado de otro core se rechaza`() {
        val other = header(coreName = "otro")

        assertEquals(StateRejection.OtherCore("otro-0"), identity.reject(other))
    }

    @Test
    fun `el estado de otra version del mismo core se rechaza`() {
        // Un core real cambia el formato binario de su estado entre
        // versiones; un estado de la 0 no se puede dar por bueno en la 1.
        val other = header(coreVersion = "1")

        assertEquals(StateRejection.OtherCore("stub-1"), identity.reject(other))
    }

    @Test
    fun `un estado del tamano equivocado se rechaza antes de tocar el core`() {
        val other = header(body = ByteArray(256))

        assertEquals(
            StateRejection.UnexpectedSize(declared = 256, expected = 512),
            identity.reject(other),
        )
    }

    @Test
    fun `un estado que cuadra en todo se acepta`() {
        assertNull(identity.reject(header()))
    }

    @Test
    fun `la comparacion del hash de la ROM no distingue mayusculas`() {
        // El hash se escribe en minúsculas, pero un archivo escrito por otra
        // versión no tiene por qué haberlo hecho.
        val upper = identity.copy(romSha256 = identity.romSha256.uppercase())

        assertNull(upper.reject(header()))
    }

    @Test
    fun `la cabecera se puede leer sin cargar el estado entero`() {
        val bytes = encoded()
        val head = bytes.copyOf(SaveStateContainer.MAX_HEADER_BYTES)

        val peeked = SaveStateContainer.peek(head, bytes.size.toLong())

        assertTrue(peeked is PeekResult.Parsed)
        val parsed = (peeked as PeekResult.Parsed).header
        assertEquals("stub", parsed.coreName)
        assertEquals("0", parsed.coreVersion)
        assertEquals("stub-0", parsed.core)
        assertEquals(512, parsed.stateSize)
        assertEquals(1_700_000_000_000L, parsed.savedAt)
    }

    @Test
    fun `leer solo la cabecera tambien detecta un archivo cortado`() {
        val bytes = encoded()
        val head = bytes.copyOf(SaveStateContainer.MAX_HEADER_BYTES)

        // La cabecera está entera, pero el archivo declara más de lo que mide.
        val peeked = SaveStateContainer.peek(head, bytes.size - 1L)

        assertEquals(PeekResult.Rejected(StateRejection.Truncated), peeked)
    }

    private fun rejection(bytes: ByteArray): StateRejection {
        val decoded = SaveStateContainer.decode(bytes)
        assertTrue("Se esperaba un rechazo y se obtuvo $decoded", decoded is DecodedState.Rejected)
        return (decoded as DecodedState.Rejected).reason
    }
}
