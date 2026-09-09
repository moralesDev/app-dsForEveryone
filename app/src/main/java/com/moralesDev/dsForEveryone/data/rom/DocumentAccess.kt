package com.moralesDev.dsForEveryone.data.rom

import android.content.ContentResolver
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.net.toUri
import java.io.File
import java.io.InputStream

/**
 * Lo mínimo que [RomRepository] necesita del Storage Access Framework.
 *
 * Existe como interfaz —y no como `ContentResolver` directo— por una sola
 * razón: `ContentResolver` no se puede instanciar en una prueba de JVM, y el
 * plan exige cobertura de pruebas para `RomRepository` desde el principio. Con
 * esta frontera, las pruebas sustituyen [DocumentAccess] por un doble en
 * memoria y ejercitan la lógica de verdad (deduplicado, disponibilidad,
 * permisos persistentes) sin tocar Android.
 */
interface DocumentAccess {
    fun openInputStream(uri: String): InputStream?

    /**
     * Descriptor de archivo abierto sobre el documento, para ROMs sueltas que
     * no conviene copiar a memoria (ver `RomRepository.openRomForCore`).
     * `null` si el proveedor no lo permite; quien llame cae entonces a
     * [openInputStream].
     */
    fun openFileDescriptor(uri: String): ParcelFileDescriptor?

    /**
     * Descriptor sobre un archivo ya en disco, no un documento SAF: la vía de
     * emergencia de `RomRepository.openRomForCore` cuando [openFileDescriptor]
     * da `null` — copia el documento a un archivo temporal propio y necesita
     * un descriptor real sobre *ese* archivo para entregárselo al core.
     * `null` si tampoco eso se puede abrir, caso en el que la ROM no llega a
     * cargar en vez de copiarse entera a un `ByteArray`.
     */
    fun openLocalFileDescriptor(file: File): ParcelFileDescriptor?

    fun displayNameOf(uri: String): String?

    /** `false` si el proveedor no concede permiso persistente; la ROM sigue jugable en esta sesión. */
    fun takePersistableReadPermission(uri: String): Boolean
    fun releasePersistableReadPermission(uri: String)

    /** Si sigue habiendo permiso de lectura persistente sobre [uri]. */
    fun hasReadPermission(uri: String): Boolean
}

class ContentResolverDocumentAccess(
    private val contentResolver: ContentResolver,
) : DocumentAccess {

    override fun openInputStream(uri: String): InputStream? =
        contentResolver.openInputStream(uri.toUri())

    override fun openFileDescriptor(uri: String): ParcelFileDescriptor? =
        contentResolver.openFileDescriptor(uri.toUri(), "r")

    override fun openLocalFileDescriptor(file: File): ParcelFileDescriptor? =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

    override fun displayNameOf(uri: String): String? =
        contentResolver.query(uri.toUri(), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }

    override fun takePersistableReadPermission(uri: String): Boolean = try {
        contentResolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    } catch (error: SecurityException) {
        false
    }

    override fun releasePersistableReadPermission(uri: String) {
        try {
            contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (error: SecurityException) {
            // No había permiso persistente que liberar.
        }
    }

    override fun hasReadPermission(uri: String): Boolean =
        contentResolver.persistedUriPermissions.any { it.isReadPermission && it.uri.toString() == uri }
}
