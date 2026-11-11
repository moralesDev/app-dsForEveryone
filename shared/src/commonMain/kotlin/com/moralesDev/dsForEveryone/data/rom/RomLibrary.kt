package com.moralesDev.dsForEveryone.data.rom

import com.moralesDev.dsForEveryone.ui.files.PickedDocument
import kotlinx.coroutines.flow.Flow

/**
 * La biblioteca de juegos tal como la usa su pantalla: listar, añadir,
 * renombrar, quitar. Común a Android e iOS (docs/ios.md, iOS-3).
 *
 * Cada plataforma la implementa a su manera, porque lo difícil no es esto
 * sino **dónde vive la ROM**: no se copia a la app, se recuerda dónde está.
 * En Android, `RomRepository` con el `Uri` de SAF y su permiso persistente;
 * en iOS, un marcador de seguridad («bookmark») del archivo. Entregar la ROM
 * al core no está aquí: es de cada plataforma y de su core.
 */
interface RomLibrary {
    fun observeLibrary(): Flow<List<RomInfo>>

    suspend fun findById(romId: String): RomInfo?

    /** Da de alta el documento elegido: un `.nds` suelto o un `.zip` con uno dentro. */
    suspend fun import(document: PickedDocument): RomImportResult

    suspend fun markPlayed(romId: String)

    suspend fun rename(romId: String, displayName: String)

    /**
     * Quita el juego de la biblioteca. [deleteSaves] es una decisión explícita
     * del usuario: por defecto las partidas se conservan, para que volver a
     * añadir la misma ROM las recupere intactas.
     */
    suspend fun remove(romId: String, deleteSaves: Boolean)
}

sealed interface RomImportResult {
    data class Imported(val rom: RomInfo) : RomImportResult
    data class AlreadyInLibrary(val rom: RomInfo) : RomImportResult

    /** El archivo se pudo leer pero su cabecera no es la de un cartucho de DS reconocible. */
    data object UnrecognizedRom : RomImportResult
    data object Unreadable : RomImportResult

    /** El `.zip` no trae ninguna entrada `.nds` reconocible. */
    data object NoRomInZip : RomImportResult

    /** El `.zip` trae más de una ROM; no se elige por el usuario, se rechaza. */
    data class MultipleRomsInZip(val count: Int) : RomImportResult

    data object EncryptedZip : RomImportResult
    data object CorruptZip : RomImportResult

    /** La ROM descomprimida supera `RomZipReader.MAX_UNCOMPRESSED_BYTES`. */
    data object RomTooLarge : RomImportResult
}

