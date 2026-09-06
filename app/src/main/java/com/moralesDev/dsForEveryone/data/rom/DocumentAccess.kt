package com.moralesDev.dsForEveryone.data.rom

import android.content.ContentResolver
import android.content.Intent
import android.provider.OpenableColumns
import androidx.core.net.toUri
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
