package com.moralesDev.dsForEveryone.data.hash

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * SHA-256 y CRC32 los implementa cada plataforma (MessageDigest y CRC32 de
 * Java en Android, CommonCrypto y zlib en iOS). Estas pruebas corren en las
 * dos y comparan contra valores de referencia publicados: si alguna de las
 * dos diera otra cosa, la misma ROM tendría identidades distintas en Android
 * y en iOS, y una partida exportada en uno no se reconocería en el otro.
 */
class HashesTest {

    @Test
    fun `sha256 de la cadena vacia`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun `sha256 de abc — vector de FIPS 180-2`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".encodeToByteArray()),
        )
    }

    @Test
    fun `sha256 de un millon de a — vector largo de FIPS 180-2`() {
        assertEquals(
            "cdc76e5c9914fb9281a1c7e284d73e67f1809a48a497200e046d39ccc7112cd0",
            sha256Hex(ByteArray(1_000_000) { 'a'.code.toByte() }),
        )
    }

    @Test
    fun `sha256 por bloques da lo mismo que de una vez`() {
        val data = ByteArray(200_003) { (it * 31 + 7).toByte() }
        val hash = Sha256()
        var offset = 0
        for (size in listOf(1, 0, 63, 64, 65, 4096, 100_000)) {
            hash.update(data, offset, size)
            offset += size
        }
        hash.update(data, offset, data.size - offset)
        assertEquals(sha256Hex(data), hash.digest().toHex())
    }

    @Test
    fun `sha256 respeta offset y length`() {
        val data = "xxabcyy".encodeToByteArray()
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256().apply { update(data, 2, 3) }.digest().toHex(),
        )
    }

    @Test
    fun `crc32 de la cadena vacia es cero`() {
        assertEquals(0L, Crc32().value)
    }

    @Test
    fun `crc32 de 123456789 — valor de comprobacion del CRC-32 de zip`() {
        assertEquals(0xCBF43926L, Crc32().apply { update("123456789".encodeToByteArray()) }.value)
    }

    @Test
    fun `crc32 es sin signo aunque pase de 2 elevado a 31`() {
        // "The quick brown fox jumps over the lazy dog" → 0x414FA339; y uno por
        // encima de 0x7FFFFFFF, para que un signo mal tratado se notara.
        assertEquals(
            0x414FA339L,
            Crc32().apply { update("The quick brown fox jumps over the lazy dog".encodeToByteArray()) }.value,
        )
        assertEquals(0xCBF43926L, Crc32().apply { update("123456789".encodeToByteArray()) }.value)
    }

    @Test
    fun `crc32 por bloques da lo mismo que de una vez`() {
        val data = ByteArray(100_001) { (it * 17 + 3).toByte() }
        val whole = Crc32().apply { update(data) }.value
        val pieces = Crc32().apply {
            update(data, 0, 10)
            update(data, 10, 0)
            update(data, 10, 50_000)
            update(data, 50_010, data.size - 50_010)
        }.value
        assertEquals(whole, pieces)
    }

    @Test
    fun `hexadecimal en minusculas y con ceros a la izquierda`() {
        assertEquals("00017f80ff", byteArrayOf(0, 1, 0x7F, 0x80.toByte(), 0xFF.toByte()).toHex())
    }

    @Test
    fun `un sha256 ya cerrado no admite mas datos`() {
        val hash = Sha256()
        hash.digest()
        // En Android MessageDigest se reinicia tras digest(); en iOS el
        // contexto ya no existe. El contrato común es «uno por cálculo», y lo
        // que no puede pasar es que en iOS se escriba en memoria liberada.
        if (isIos) assertFailsWith<IllegalStateException> { hash.update(byteArrayOf(1)) }
    }
}
