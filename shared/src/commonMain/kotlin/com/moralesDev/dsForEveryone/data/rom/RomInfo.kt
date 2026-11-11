package com.moralesDev.dsForEveryone.data.rom

/**
 * Una ROM tal y como la ve la aplicación. Modelo de dominio: no sabe nada de
 * Room, de `Uri`s ni de columnas.
 */
data class RomInfo(
    /** SHA-256 del contenido. Identificador primario en toda la aplicación. */
    val id: String,
    /** URI del documento SAF. La ROM no se copia: se referencia. */
    val documentUri: String,
    val fileName: String,
    /** Nombre que se muestra en la biblioteca (editable por el usuario). */
    val displayName: String,
    val internalTitle: String,
    val gameCode: String,
    /** 32×32 píxeles ARGB del banner, o `null` si el cartucho no traía uno legible. */
    val icon: IntArray?,
    val sizeBytes: Long,
    val addedAt: Long,
    val lastPlayedAt: Long?,
    /**
     * `false` cuando el archivo original ya no es accesible: el usuario lo
     * movió, lo borró o desmontó la tarjeta. La entrada se conserva —y con ella
     * las partidas— pero no se puede jugar hasta volver a localizarla.
     */
    val isAvailable: Boolean = true,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RomInfo) return false
        return id == other.id &&
            documentUri == other.documentUri &&
            fileName == other.fileName &&
            displayName == other.displayName &&
            internalTitle == other.internalTitle &&
            gameCode == other.gameCode &&
            (icon?.contentEquals(other.icon) ?: (other.icon == null)) &&
            sizeBytes == other.sizeBytes &&
            addedAt == other.addedAt &&
            lastPlayedAt == other.lastPlayedAt &&
            isAvailable == other.isAvailable
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
        result = 31 * result + isAvailable.hashCode()
        return result
    }
}
