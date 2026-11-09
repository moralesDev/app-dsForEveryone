package com.moralesDev.dsForEveryone.data.hash

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256_CTX
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreCrypto.CC_SHA256_Final
import platform.CoreCrypto.CC_SHA256_Init
import platform.CoreCrypto.CC_SHA256_Update
import platform.zlib.crc32

/**
 * SHA-256 con CommonCrypto. El contexto vive en memoria nativa, fuera del
 * recolector: se reserva al crear el objeto y se libera en [digest], que
 * por contrato se llama una vez por cálculo.
 */
@OptIn(ExperimentalForeignApi::class)
actual class Sha256 actual constructor() {
    private var context: CC_SHA256_CTX? = nativeHeap.alloc<CC_SHA256_CTX>().also { CC_SHA256_Init(it.ptr) }

    actual fun update(bytes: ByteArray, offset: Int, length: Int) {
        val ctx = checkNotNull(context) { "Sha256 ya entregó su resumen" }
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
        // addressOf(offset) con offset == size estaría fuera del array.
        if (length == 0) return
        bytes.usePinned { pinned ->
            CC_SHA256_Update(ctx.ptr, pinned.addressOf(offset), length.convert())
        }
    }

    actual fun digest(): ByteArray {
        val ctx = checkNotNull(context) { "Sha256 ya entregó su resumen" }
        val out = ByteArray(CC_SHA256_DIGEST_LENGTH)
        out.usePinned { pinned ->
            CC_SHA256_Final(pinned.addressOf(0).reinterpret<UByteVar>(), ctx.ptr)
        }
        nativeHeap.free(ctx)
        context = null
        return out
    }
}

/** CRC32 de zlib, el mismo algoritmo que `java.util.zip.CRC32`. */
@OptIn(ExperimentalForeignApi::class)
actual class Crc32 actual constructor() {
    private var crc: ULong = 0u

    actual fun update(bytes: ByteArray, offset: Int, length: Int) {
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size)
        if (length == 0) return
        bytes.usePinned { pinned ->
            crc = crc32(crc.convert(), pinned.addressOf(offset).reinterpret<UByteVar>(), length.convert()).convert()
        }
    }

    actual val value: Long get() = (crc and 0xFFFFFFFFu).toLong()
}
