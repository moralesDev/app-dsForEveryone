package com.moralesDev.dsForEveryone.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.moralesDev.dsForEveryone.data.rom.RomInfo

/**
 * Fila de la biblioteca. Guarda metadatos y la referencia al documento, nunca
 * el contenido de la ROM ni el de las partidas: los binarios viven en disco
 * (ver `StorageManager`), no en la base.
 */
@Entity(tableName = "roms")
data class RomEntity(
    /** SHA-256 del contenido de la ROM. */
    @PrimaryKey val id: String,
    val documentUri: String,
    val fileName: String,
    val displayName: String,
    val internalTitle: String,
    val gameCode: String,
    /**
     * El icono de 32×32 del banner, ya como píxeles ARGB empaquetados (ver
     * [toIconBytes]/[toIconPixels]), o `null` si el cartucho no traía banner
     * o el suyo estaba dañado. Es la portada por defecto (fase 3.3); el
     * usuario puede sustituirla a mano, pero eso vive en `CoverRepository`,
     * no aquí.
     */
    val icon: ByteArray?,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastPlayedAt: Long?,
) {
    // Room no usa equals/hashCode, pero un ByteArray en un data class los
    // rompe (compara por referencia) para cualquiera que sí los use, como las
    // pruebas de JVM.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RomEntity) return false
        return id == other.id &&
            documentUri == other.documentUri &&
            fileName == other.fileName &&
            displayName == other.displayName &&
            internalTitle == other.internalTitle &&
            gameCode == other.gameCode &&
            (icon?.contentEquals(other.icon) ?: (other.icon == null)) &&
            sizeBytes == other.sizeBytes &&
            addedAt == other.addedAt &&
            lastPlayedAt == other.lastPlayedAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentUri.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + internalTitle.hashCode()
        result = 31 * result + gameCode.hashCode()
        result = 31 * result + (icon?.contentHashCode() ?: 0)
        result = 31 * result + sizeBytes.hashCode()
        result = 31 * result + addedAt.hashCode()
        result = 31 * result + (lastPlayedAt?.hashCode() ?: 0)
        return result
    }
}

fun RomEntity.toRomInfo(isAvailable: Boolean = true) = RomInfo(
    id = id,
    documentUri = documentUri,
    fileName = fileName,
    displayName = displayName,
    internalTitle = internalTitle,
    gameCode = gameCode,
    icon = icon?.toIconPixels(),
    sizeBytes = sizeBytes,
    addedAt = addedAt,
    lastPlayedAt = lastPlayedAt,
    isAvailable = isAvailable,
)

/** Cuatro bytes por píxel (ARGB), en el orden en que los entrega [IntArray.iterator]. */
fun IntArray.toIconBytes(): ByteArray {
    val bytes = ByteArray(size * 4)
    for (index in indices) {
        val pixel = this[index]
        val offset = index * 4
        bytes[offset] = (pixel shr 24).toByte()
        bytes[offset + 1] = (pixel shr 16).toByte()
        bytes[offset + 2] = (pixel shr 8).toByte()
        bytes[offset + 3] = pixel.toByte()
    }
    return bytes
}

/** Inversa de [IntArray.toIconBytes]. */
fun ByteArray.toIconPixels(): IntArray {
    val pixels = IntArray(size / 4)
    for (index in pixels.indices) {
        val offset = index * 4
        pixels[index] = ((this[offset].toInt() and 0xFF) shl 24) or
            ((this[offset + 1].toInt() and 0xFF) shl 16) or
            ((this[offset + 2].toInt() and 0xFF) shl 8) or
            (this[offset + 3].toInt() and 0xFF)
    }
    return pixels
}
