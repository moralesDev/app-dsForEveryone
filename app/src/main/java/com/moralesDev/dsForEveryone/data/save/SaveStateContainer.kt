package com.moralesDev.dsForEveryone.data.save

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.security.MessageDigest

/**
 * Formato de archivo de los save states de DsForEveryone.
 *
 * El estado en sí lo produce el core y **no se reinterpreta**: es una
 * secuencia de bytes opaca. Lo que aporta este contenedor es todo lo que hace
 * falta para decidir, antes de entregárselo al core, si ese estado puede
 * restaurarse aquí y ahora. Sin esa capa el único filtro sería el tamaño, y
 * dos juegos distintos producen estados del mismo tamaño: cargar el de otra
 * partida entraría sin protestar y dejaría la máquina en un estado imposible.
 *
 * ```
 *  offset  bytes  campo
 *  0       4      "E4FS"
 *  4       2      containerVersion
 *  6       2      headerBytes        (todo lo que hay antes del payload)
 *  8       8      savedAt            (epoch en milisegundos)
 *  16      1      slot
 *  17      1      flags              (reservado, 0)
 *  18      2      reservado          (0)
 *  20      4      romCrc32
 *  24      4      stateSize
 *  28      32     romSha256          (bytes crudos)
 *  60      32     payloadSha256      (bytes crudos)
 *  92      1+n    coreName           (longitud + UTF-8)
 *  93+n    1+m    coreVersion        (longitud + UTF-8)
 *  headerBytes    payload            (estado del core, stateSize bytes)
 * ```
 *
 * Los enteros van en orden de red (big-endian), que es lo que escriben `DataOutputStream`
 * y `DataInputStream` sin configurar nada, así que no hay forma de que las dos
 * mitades del código discrepen.
 *
 * `headerBytes` es la pieza que permite que el formato crezca: una versión
 * futura puede añadir campos al final de la cabecera y el payload se seguirá
 * encontrando en su sitio. Hoy no se usa para leer versiones futuras —esas se
 * rechazan a propósito, ver [decode]— pero sí como comprobación de que el
 * archivo es coherente consigo mismo.
 *
 * La versión del contenedor y la del core son cosas distintas. Esta la decide
 * la aplicación cuando cambia la disposición del archivo; la del core la
 * decide el core elegido (fase 4) cuando cambia el formato binario de su
 * estado interno. Un archivo puede ser perfectamente legible
 * ([CONTAINER_VERSION] correcta) y aun así contener un payload que este core
 * no sabe restaurar.
 */
object SaveStateContainer {

    /** Firma del archivo. Cuatro caracteres ASCII: `E4FS`. */
    val MAGIC = byteArrayOf(0x45, 0x34, 0x46, 0x53)

    const val CONTAINER_VERSION = 1

    /**
     * Un estado de DS puede pesar bastante más que uno de GBA: hay dos CPUs,
     * memoria de vídeo 3D y WRAM compartida de por medio. El tope existe para
     * no reservar memoria a partir de un número que viene del disco: ver la
     * nota sobre datos no confiables en [decode].
     */
    const val MAX_STATE_BYTES = 32 * 1024 * 1024

    /** Bytes de cabecera anteriores a las dos cadenas de longitud variable. */
    private const val FIXED_HEADER_BYTES = 92
    private const val DIGEST_BYTES = 32

    fun encode(header: SaveStateHeader, payload: ByteArray): ByteArray {
        require(payload.size == header.stateSize) {
            "El payload mide ${payload.size} y la cabecera declara ${header.stateSize}"
        }

        val coreName = header.coreName.toByteArray(Charsets.UTF_8)
        val coreVersion = header.coreVersion.toByteArray(Charsets.UTF_8)
        require(coreName.size <= 0xFF && coreVersion.size <= 0xFF) {
            "El identificador del core no cabe en el contenedor"
        }
        val headerBytes = FIXED_HEADER_BYTES + 1 + coreName.size + 1 + coreVersion.size

        val output = ByteArrayOutputStream(headerBytes + payload.size)
        DataOutputStream(output).use { out ->
            out.write(MAGIC)
            out.writeShort(header.containerVersion)
            out.writeShort(headerBytes)
            out.writeLong(header.savedAt)
            out.writeByte(header.slot)
            out.writeByte(0) // flags
            out.writeShort(0) // reservado
            out.writeInt(header.romCrc32.toInt())
            out.writeInt(header.stateSize)
            out.write(header.romSha256.hexToBytes(DIGEST_BYTES))
            out.write(header.payloadSha256.hexToBytes(DIGEST_BYTES))
            out.writeByte(coreName.size)
            out.write(coreName)
            out.writeByte(coreVersion.size)
            out.write(coreVersion)
            out.write(payload)
        }
        return output.toByteArray()
    }

    /**
     * Interpreta un archivo completo.
     *
     * Todo lo que llega aquí se trata como dato no confiable, aunque lo haya
     * escrito la propia aplicación: un archivo puede quedar a medias por un
     * corte de corriente, o venir de otra versión, o simplemente no ser un save
     * state. Ninguna cifra leída del archivo se usa para reservar memoria antes
     * de comprobar que cuadra con el tamaño real.
     */
    fun decode(bytes: ByteArray): DecodedState {
        val parsed = when (val result = readHeader(bytes)) {
            null -> return DecodedState.Rejected(StateRejection.NotAContainer)
            is HeaderResult.Rejected -> return DecodedState.Rejected(result.reason)
            is HeaderResult.Parsed -> result
        }

        // Sólo ahora, con el tamaño ya contrastado contra el del archivo, se
        // copia el payload.
        val payload = bytes.copyOfRange(
            parsed.headerBytes,
            parsed.headerBytes + parsed.header.stateSize,
        )
        if (sha256(payload) != parsed.header.payloadSha256) {
            return DecodedState.Rejected(StateRejection.Corrupt)
        }
        return DecodedState.Valid(parsed.header, payload)
    }

    /**
     * Lee sólo la cabecera, para poder listar las cinco ranuras sin cargar en
     * memoria el estado completo por cada una.
     *
     * No comprueba la integridad del payload —eso exige leerlo entero— así que
     * una ranura que aparezca correcta en la lista todavía puede resultar dañada
     * al abrirla. Es el reparto que interesa: el listado se abre constantemente
     * desde el menú de pausa y la comprobación cara se paga una sola vez, justo
     * cuando se va a restaurar.
     *
     * @param declaredLength tamaño real del archivo en disco.
     */
    fun peek(head: ByteArray, declaredLength: Long): PeekResult =
        when (val result = readHeader(head, declaredLength)) {
            null -> PeekResult.Rejected(StateRejection.NotAContainer)
            is HeaderResult.Rejected -> PeekResult.Rejected(result.reason)
            is HeaderResult.Parsed -> PeekResult.Parsed(result.header)
        }

    /**
     * Cuántos bytes hay que leer del archivo para que [peek] pueda trabajar.
     * La cabecera es de tamaño variable, pero acotado.
     */
    const val MAX_HEADER_BYTES = FIXED_HEADER_BYTES + 1 + 0xFF + 1 + 0xFF

    /**
     * @param totalLength longitud del archivo completo; por omisión, la del
     *   array, que es lo correcto cuando se ha leído entero.
     */
    private fun readHeader(bytes: ByteArray, totalLength: Long = bytes.size.toLong()): HeaderResult? {
        if (bytes.size < MAGIC.size || !bytes.startsWith(MAGIC)) return null

        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                input.readInt() // la firma, ya comprobada

                val containerVersion = input.readUnsignedShort()
                if (containerVersion != CONTAINER_VERSION) {
                    return HeaderResult.Rejected(StateRejection.UnsupportedContainer(containerVersion))
                }

                val headerBytes = input.readUnsignedShort()
                if (headerBytes < FIXED_HEADER_BYTES + 2 || headerBytes > MAX_HEADER_BYTES) {
                    return HeaderResult.Rejected(StateRejection.Corrupt)
                }

                val savedAt = input.readLong()
                val slot = input.readUnsignedByte()
                input.readUnsignedByte() // flags
                input.readUnsignedShort() // reservado
                val romCrc32 = input.readInt().toLong() and 0xFFFFFFFFL
                val stateSize = input.readInt()
                if (stateSize <= 0 || stateSize > MAX_STATE_BYTES) {
                    return HeaderResult.Rejected(StateRejection.Corrupt)
                }

                val romSha256 = ByteArray(DIGEST_BYTES).also(input::readFully).toHex()
                val payloadSha256 = ByteArray(DIGEST_BYTES).also(input::readFully).toHex()
                val coreName = input.readShortString()
                val coreVersion = input.readShortString()

                // La cabecera debe medir exactamente lo que declaró, y el archivo
                // exactamente cabecera + payload. Cualquier otra cosa es un
                // archivo alterado o escrito a medias.
                val consumed = FIXED_HEADER_BYTES + 1 + coreName.toByteArray(Charsets.UTF_8).size +
                    1 + coreVersion.toByteArray(Charsets.UTF_8).size
                if (consumed != headerBytes) {
                    return HeaderResult.Rejected(StateRejection.Corrupt)
                }
                val expectedLength = headerBytes.toLong() + stateSize
                if (totalLength < expectedLength) {
                    return HeaderResult.Rejected(StateRejection.Truncated)
                }
                if (totalLength > expectedLength) {
                    return HeaderResult.Rejected(StateRejection.Corrupt)
                }

                HeaderResult.Parsed(
                    header = SaveStateHeader(
                        containerVersion = containerVersion,
                        coreName = coreName,
                        coreVersion = coreVersion,
                        romSha256 = romSha256,
                        romCrc32 = romCrc32,
                        savedAt = savedAt,
                        slot = slot,
                        stateSize = stateSize,
                        payloadSha256 = payloadSha256,
                    ),
                    headerBytes = headerBytes,
                )
            }
        } catch (_: EOFException) {
            HeaderResult.Rejected(StateRejection.Truncated)
        } catch (_: IOException) {
            HeaderResult.Rejected(StateRejection.Corrupt)
        }
    }

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun DataInputStream.readShortString(): String {
        val length = readUnsignedByte()
        val raw = ByteArray(length)
        readFully(raw)
        return String(raw, Charsets.UTF_8)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(expectedBytes: Int): ByteArray {
        require(length == expectedBytes * 2) { "Se esperaba un hash de $expectedBytes bytes" }
        return ByteArray(expectedBytes) { substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    private sealed interface HeaderResult {
        data class Parsed(val header: SaveStateHeader, val headerBytes: Int) : HeaderResult
        data class Rejected(val reason: StateRejection) : HeaderResult
    }
}

/** Metadatos que viajan junto al estado dentro del contenedor. */
data class SaveStateHeader(
    val containerVersion: Int,
    /** Core que generó el payload, sin versión: p. ej. `stub`. */
    val coreName: String,
    /** Versión de ese core. */
    val coreVersion: String,
    /** SHA-256 del contenido de la ROM, en hexadecimal. */
    val romSha256: String,
    val romCrc32: Long,
    val savedAt: Long,
    val slot: Int,
    val stateSize: Int,
    /** SHA-256 del payload, para detectar corrupción. */
    val payloadSha256: String,
) {
    /** Identificador legible del core, como el que muestra Configuración. */
    val core: String get() = "$coreName-$coreVersion"
}

/**
 * Lo que debe coincidir para que un estado pueda restaurarse en la sesión que
 * está corriendo ahora mismo.
 */
data class StateIdentity(
    val romSha256: String,
    val romCrc32: Long,
    val coreName: String,
    val coreVersion: String,
    /** Bytes que el core espera hoy para esta ROM. */
    val stateSize: Int,
) {
    /**
     * Comprueba una cabecera contra esta sesión.
     *
     * @return el motivo del rechazo, o `null` si el estado es restaurable.
     */
    fun reject(header: SaveStateHeader): StateRejection? = when {
        // Primero el juego: es el error que de verdad comete la gente, y el que
        // produce el destrozo más difícil de entender si pasa.
        !header.romSha256.equals(romSha256, ignoreCase = true) -> StateRejection.OtherGame
        header.romCrc32 != romCrc32 -> StateRejection.OtherGame
        header.coreName != coreName -> StateRejection.OtherCore(header.core)
        header.coreVersion != coreVersion -> StateRejection.OtherCore(header.core)
        header.stateSize != stateSize ->
            StateRejection.UnexpectedSize(header.stateSize, stateSize)
        else -> null
    }
}

/** Por qué un archivo de estado no puede restaurarse. */
sealed interface StateRejection {
    /** No empieza por la firma: no es un save state de esta aplicación. */
    data object NotAContainer : StateRejection

    /** El contenedor es de una versión que esta aplicación no sabe leer. */
    data class UnsupportedContainer(val version: Int) : StateRejection

    /** El archivo se quedó a medias. */
    data object Truncated : StateRejection

    /** Los bytes no coinciden con su hash, o la cabecera no es coherente. */
    data object Corrupt : StateRejection

    /** El estado pertenece a otro juego. */
    data object OtherGame : StateRejection

    /** Lo generó otro core, u otra versión del mismo core. */
    data class OtherCore(val core: String) : StateRejection

    /** El core actual espera un estado de otro tamaño. */
    data class UnexpectedSize(val declared: Int, val expected: Int) : StateRejection
}

/** Resultado de leer un archivo de estado completo. */
sealed interface DecodedState {
    data class Valid(val header: SaveStateHeader, val payload: ByteArray) : DecodedState {
        // `payload` es un array: sin esto, dos contenedores con el mismo estado
        // no se considerarían iguales y las pruebas mentirían.
        override fun equals(other: Any?): Boolean =
            other is Valid && header == other.header && payload.contentEquals(other.payload)

        override fun hashCode(): Int = 31 * header.hashCode() + payload.contentHashCode()
    }

    data class Rejected(val reason: StateRejection) : DecodedState
}

/** Resultado de leer sólo la cabecera de un archivo de estado. */
sealed interface PeekResult {
    data class Parsed(val header: SaveStateHeader) : PeekResult
    data class Rejected(val reason: StateRejection) : PeekResult
}
