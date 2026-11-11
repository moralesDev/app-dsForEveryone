package com.moralesDev.dsForEveryone.data.hash

/**
 * SHA-256 incremental: la identidad de cada ROM y la integridad de cada save
 * state (ver RomIdentity y SaveStateContainer).
 *
 * Se declara aquí y lo implementa cada plataforma con lo que ya trae y está
 * optimizado: `MessageDigest` en Android, CommonCrypto en iOS. Reescribirlo
 * en Kotlin común sería más lento justo donde importa —una ROM de DS pasa
 * de 500 MB y se recorre entera al darla de alta—, y el resultado tiene que
 * ser idéntico byte a byte en las dos: lo comprueban las pruebas de
 * commonTest con vectores conocidos, en la JVM y en iOS.
 */
expect class Sha256() {
    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)

    /** El resumen de 32 bytes. Deja el objeto inservible: uno por cálculo. */
    fun digest(): ByteArray
}

/**
 * CRC32 incremental (el de zip/zlib, polinomio 0xEDB88320): la comprobación
 * rápida que acompaña al SHA-256 en los save states. `java.util.zip.CRC32`
 * en Android, la de zlib en iOS: el mismo algoritmo.
 */
expect class Crc32() {
    fun update(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset)

    /** El valor como entero sin signo de 32 bits, en un Long. */
    val value: Long
}

/** SHA-256 de [bytes], en hexadecimal en minúsculas. */
fun sha256Hex(bytes: ByteArray): String = Sha256().apply { update(bytes) }.digest().toHex()

/** Hexadecimal en minúsculas, dos dígitos por byte: lo mismo que daba `"%02x".format` en la JVM. */
fun ByteArray.toHex(): String {
    val out = StringBuilder(size * 2)
    for (byte in this) {
        val value = byte.toInt() and 0xFF
        out.append(HEX_DIGITS[value ushr 4]).append(HEX_DIGITS[value and 0x0F])
    }
    return out.toString()
}

private const val HEX_DIGITS = "0123456789abcdef"
