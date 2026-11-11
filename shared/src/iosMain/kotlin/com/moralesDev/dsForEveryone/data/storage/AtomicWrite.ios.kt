package com.moralesDev.dsForEveryone.data.storage

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import okio.IOException
import okio.Path
import platform.posix.F_FULLFSYNC
import platform.posix.O_CREAT
import platform.posix.O_TRUNC
import platform.posix.O_WRONLY
import platform.posix.close
import platform.posix.errno
import platform.posix.fcntl
import platform.posix.fsync
import platform.posix.open
import platform.posix.strerror
import platform.posix.write
import kotlinx.cinterop.toKString

/**
 * En los aparatos de Apple `fsync` sólo garantiza que los datos han llegado
 * a la unidad, que puede tenerlos aún en su caché; `F_FULLFSYNC` obliga a
 * escribirlos de verdad. Es lo que hace falta para que el `rename` de
 * [writeAtomically] no llegue al disco antes que el contenido. Si el sistema
 * de archivos no lo admite, se cae a `fsync`.
 */
@OptIn(ExperimentalForeignApi::class)
internal actual fun writeDurably(path: Path, bytes: ByteArray) {
    val fd = open(path.toString(), O_WRONLY or O_CREAT or O_TRUNC, 0x1A4) // 0644
    if (fd < 0) throw IOException("No se pudo abrir $path: ${strerror(errno)?.toKString()}")
    try {
        var written = 0
        bytes.usePinned { pinned ->
            while (written < bytes.size) {
                val result = write(fd, pinned.addressOf(written), (bytes.size - written).convert())
                if (result < 0) throw IOException("No se pudo escribir $path: ${strerror(errno)?.toKString()}")
                written += result.toInt()
            }
        }
        if (fcntl(fd, F_FULLFSYNC) != 0 && fsync(fd) != 0) {
            throw IOException("No se pudo sincronizar $path: ${strerror(errno)?.toKString()}")
        }
    } finally {
        close(fd)
    }
}
