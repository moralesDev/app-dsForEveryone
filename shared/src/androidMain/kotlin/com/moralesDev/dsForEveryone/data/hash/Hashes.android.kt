package com.moralesDev.dsForEveryone.data.hash

import java.security.MessageDigest
import java.util.zip.CRC32

actual class Sha256 actual constructor() {
    private val digest = MessageDigest.getInstance("SHA-256")

    actual fun update(bytes: ByteArray, offset: Int, length: Int) {
        digest.update(bytes, offset, length)
    }

    actual fun digest(): ByteArray = digest.digest()
}

actual class Crc32 actual constructor() {
    private val crc = CRC32()

    actual fun update(bytes: ByteArray, offset: Int, length: Int) {
        crc.update(bytes, offset, length)
    }

    actual val value: Long get() = crc.value
}
