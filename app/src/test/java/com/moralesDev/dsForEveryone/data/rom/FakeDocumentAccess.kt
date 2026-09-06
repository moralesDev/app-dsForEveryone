package com.moralesDev.dsForEveryone.data.rom

import android.os.ParcelFileDescriptor
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Doble en memoria de [DocumentAccess], para ejercitar [RomRepository] en JVM
 * sin ningún `ContentResolver` real.
 */
class FakeDocumentAccess : DocumentAccess {

    private val documents = mutableMapOf<String, ByteArray>()
    private val displayNames = mutableMapOf<String, String>()
    private val readPermissions = mutableSetOf<String>()

    /** Si es `true`, [takePersistableReadPermission] falla como haría un proveedor sin permiso persistente. */
    var denyPersistablePermission = false

    fun put(uri: String, bytes: ByteArray, displayName: String? = null) {
        documents[uri] = bytes
        if (displayName != null) displayNames[uri] = displayName
    }

    /** Simula que el usuario borró o movió el archivo: sigue "existiendo" el permiso, pero no el contenido. */
    fun removeContent(uri: String) {
        documents.remove(uri)
    }

    fun revokePermission(uri: String) {
        readPermissions.remove(uri)
    }

    override fun openInputStream(uri: String): InputStream? = documents[uri]?.let { ByteArrayInputStream(it) }

    /**
     * Siempre `null`: `ParcelFileDescriptor` no se puede construir en una
     * prueba de JVM sin Robolectric, que este proyecto no usa. La vía de
     * descriptor de `RomRepository.openRomForCore` queda sin poder probarse
     * de extremo a extremo aquí a propósito; lo que sí prueba
     * `RomRepositoryTest` es que, cuando esto devuelve `null`, el método no
     * revienta y se limita a no ofrecer esa vía.
     */
    override fun openFileDescriptor(uri: String): ParcelFileDescriptor? = null

    override fun displayNameOf(uri: String): String? = displayNames[uri]

    override fun takePersistableReadPermission(uri: String): Boolean {
        if (denyPersistablePermission) return false
        readPermissions += uri
        return true
    }

    override fun releasePersistableReadPermission(uri: String) {
        readPermissions -= uri
    }

    override fun hasReadPermission(uri: String): Boolean = uri in readPermissions
}
