package com.moralesDev.dsForEveryone.data.rom

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

/**
 * Localiza y calcula la identidad de la ROM de DS dentro de un `.zip`, sólo
 * con `java.util.zip` y sin acceso aleatorio: el Storage Access Framework sólo
 * garantiza un `InputStream` secuencial.
 *
 * No decide por el nombre del `.zip` ni por el de sus entradas más que para
 * reconocer la extensión `.nds`: el contenido se valida después con la
 * cabecera del cartucho, exactamente igual que un archivo suelto.
 *
 * Enumerar las entradas y extraer la elegida se hacen **en la misma pasada**,
 * no en dos abriendo el zip dos veces: `ZipInputStream.closeEntry()` sobre una
 * entrada aún no leída la descomprime igual para poder saltarla, así que
 * separar «contar» de «extraer» descomprimiría la ROM elegida dos veces.
 */
object RomZipReader {

    /**
     * Cuánto puede pesar como mucho una ROM ya descomprimida. Los cartuchos
     * de DS normales no pasan de 256 MB (2 Gbit); los DSi mejorados llegan a
     * 1 GB (8 Gbit). El margen es para no rechazar por poco un volcado atípico
     * sin abrir la puerta a un `.zip` pensado para agotar la memoria.
     */
    const val MAX_UNCOMPRESSED_BYTES = 1024L * 1024 * 1024

    private const val ZIP_SIGNATURE_SIZE = 4

    /** La única extensión que vale la pena mirar dentro de un `.zip`; el contenido decide el resto. */
    private val ROM_EXTENSIONS = listOf(".nds")

    sealed interface Resolution {
        data class Single(val fingerprint: RomFingerprint) : Resolution
        data class Multiple(val count: Int) : Resolution
        data object None : Resolution
        data object Encrypted : Resolution
        data object Corrupt : Resolution
        data object TooLarge : Resolution
    }

    /**
     * Firma de cabecera local de un `.zip` (o de un `.zip` vacío). Basta con
     * los cuatro primeros bytes; no hay que fiarse del nombre del archivo,
     * que el proveedor de documentos puede mentir.
     */
    fun isZip(header: ByteArray): Boolean =
        header.size >= ZIP_SIGNATURE_SIZE &&
            header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
            (header[2] == 0x03.toByte() || header[2] == 0x05.toByte()) &&
            (header[3] == 0x04.toByte() || header[3] == 0x06.toByte())

    /**
     * Recorre el `.zip` una sola vez: calcula la identidad de la primera
     * entrada `.nds` que encuentra mientras sigue buscando si hay otra
     * después. Si aparece una segunda, el resultado de la primera se
     * descarta —el caso ya se rechaza igual—, pero eso sólo cuesta de más en
     * el caso raro, nunca en el habitual de una sola ROM.
     *
     * Ignora carpetas y basura de compresores (`__MACOSX/`, archivos que
     * empiezan por `.`).
     */
    fun resolveRom(open: () -> InputStream): Resolution =
        when (val outcome = scanForSingleRomEntry(open, RomIdentity::read)) {
            is ScanOutcome.Ready -> Resolution.Single(outcome.value)
            is ScanOutcome.Multiple -> Resolution.Multiple(outcome.count)
            ScanOutcome.None -> Resolution.None
            is ScanOutcome.Failed -> outcome.resolution
        }

    /**
     * Bytes ya descomprimidos de la única entrada de ROM de un `.zip` que ya
     * pasó por [resolveRom] al darlo de alta. Se usa al cargar la partida, no
     * al darla de alta: aquí ya no hace falta distinguir «varias» de
     * «ninguna» con un mensaje propio para el usuario, sólo devolver los
     * bytes listos para el core o `null` si el `.zip` ya no es el que se
     * validó entonces.
     */
    fun extractRom(open: () -> InputStream): ByteArray? =
        (scanForSingleRomEntry(open) { it.readBytes() } as? ScanOutcome.Ready)?.value

    /**
     * Como [extractRom], pero escribe la ROM descomprimida en flujo a
     * [destination] en vez de construir un `ByteArray`: para una ROM grande
     * dentro de un `.zip` (Pokémon HeartGold, 128 MB, agotó el heap de Java
     * así — ver `docs/estabilizacion-core.md`) el `ByteArray` es justo lo que
     * hay que evitar. `true` si encontró y copió la única entrada de ROM;
     * `destination` queda sin tocar (o a medio escribir, según cuándo falló)
     * en cualquier otro caso, así que quien llame no debe fiarse del archivo
     * si esto devuelve `false`.
     */
    fun extractRomToFile(open: () -> InputStream, destination: File): Boolean =
        scanForSingleRomEntry(open) { input -> FileOutputStream(destination).use(input::copyTo) } is ScanOutcome.Ready

    /**
     * El recorrido de verdad, compartido por [resolveRom] y [extractRom]:
     * una sola pasada por las entradas, aplicando [onMatch] a la primera que
     * cuente como ROM mientras se sigue mirando si hay otra después.
     */
    private fun <T> scanForSingleRomEntry(
        open: () -> InputStream,
        onMatch: (InputStream) -> T,
    ): ScanOutcome<T> {
        var value: T? = null
        var matchCount = 0
        try {
            ZipInputStream(open()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (isEligibleRomEntry(entry)) {
                        matchCount++
                        if (matchCount == 1) {
                            value = onMatch(LimitedInputStream(zip, MAX_UNCOMPRESSED_BYTES))
                        }
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: RomTooLargeException) {
            return ScanOutcome.Failed(Resolution.TooLarge)
        } catch (error: IOException) {
            return ScanOutcome.Failed(classifyError(error))
        }

        return when (matchCount) {
            0 -> ScanOutcome.None
            1 -> ScanOutcome.Ready(checkNotNull(value))
            else -> ScanOutcome.Multiple(matchCount)
        }
    }

    private sealed interface ScanOutcome<out T> {
        data class Ready<T>(val value: T) : ScanOutcome<T>
        data class Multiple(val count: Int) : ScanOutcome<Nothing>
        data object None : ScanOutcome<Nothing>
        data class Failed(val resolution: Resolution) : ScanOutcome<Nothing>
    }

    /**
     * A qué se debe un fallo al leer un `.zip`: cifrado o, cualquier otra
     * cosa, dañado. `java.util.zip` no expone si una entrada está cifrada más
     * que a través del mensaje de la excepción: no hay una bandera pública
     * para preguntarlo de antemano.
     */
    fun classifyError(error: IOException): Resolution =
        if (error is ZipException && error.looksEncrypted()) Resolution.Encrypted else Resolution.Corrupt

    private fun isEligibleRomEntry(entry: ZipEntry): Boolean {
        if (entry.isDirectory) return false
        val name = entry.name
        if (name.startsWith("__MACOSX/")) return false
        val fileName = name.substringAfterLast('/')
        if (fileName.startsWith(".")) return false
        return ROM_EXTENSIONS.any { fileName.endsWith(it, ignoreCase = true) }
    }

    private fun ZipException.looksEncrypted(): Boolean =
        message?.contains("encrypt", ignoreCase = true) == true

    /** Corta la lectura con [RomTooLargeException] en cuanto se supera [limit]. */
    private class LimitedInputStream(
        private val delegate: InputStream,
        private val limit: Long,
    ) : InputStream() {
        private var readSoFar = 0L

        override fun read(): Int {
            val byte = delegate.read()
            if (byte >= 0) count(1)
            return byte
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val read = delegate.read(b, off, len)
            if (read > 0) count(read)
            return read
        }

        private fun count(read: Int) {
            readSoFar += read
            if (readSoFar > limit) throw RomTooLargeException(readSoFar)
        }

        // No cierra `delegate`: es el propio ZipInputStream, que sigue
        // haciendo falta para las entradas siguientes.
        override fun close() = Unit
    }
}

/** La entrada del `.zip` supera el tope permitido para una ROM de DS. */
class RomTooLargeException(sizeBytes: Long) :
    IOException("ROM descomprimida de $sizeBytes bytes supera el tope de ${RomZipReader.MAX_UNCOMPRESSED_BYTES}")
