package com.moralesDev.dsForEveryone.data.rom

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
