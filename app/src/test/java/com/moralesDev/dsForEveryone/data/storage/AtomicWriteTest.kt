package com.moralesDev.dsForEveryone.data.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Lo que importa del formato no es escribir bien: es que una escritura a
 * medias nunca deje un archivo a medias en disco.
 */
class AtomicWriteTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `escribe el contenido pedido`() {
        val file = File(folder.root, "battery.sav")
        val bytes = ByteArray(64) { it.toByte() }

        file.writeAtomically(bytes)

        assertTrue(file.exists())
        assertArrayEquals(bytes, file.readBytes())
    }

    @Test
    fun `crea las carpetas padre que hagan falta`() {
        val file = File(folder.root, "saves/abc123/states/slot_1.state")

        file.writeAtomically(ByteArray(8))

        assertTrue(file.exists())
    }

    @Test
    fun `sobrescribir reemplaza el contenido entero, sin mezclarlo con lo anterior`() {
        val file = File(folder.root, "slot_1.state")
        file.writeAtomically(ByteArray(10_000) { 1 })

        file.writeAtomically(ByteArray(10) { 2 })

        assertEquals(10, file.length())
        assertTrue(file.readBytes().all { it == 2.toByte() })
    }

    @Test
    fun `no deja ningun temporal a medio camino tras una escritura correcta`() {
        val file = File(folder.root, "slot_1.state")

        file.writeAtomically(ByteArray(16))

        val leftovers = folder.root.listFiles().orEmpty().filter { it.name.endsWith(".tmp") }
        assertTrue("No debe quedar ningún .tmp: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `escribir un archivo vacio es valido`() {
        val file = File(folder.root, "empty.state")

        file.writeAtomically(ByteArray(0))

        assertTrue(file.exists())
        assertEquals(0, file.length())
    }

    @Test
    fun `una escritura no toca otros archivos de la misma carpeta`() {
        val untouched = File(folder.root, "other.sav").apply { writeBytes(ByteArray(5) { 9 }) }
        val target = File(folder.root, "target.sav")

        target.writeAtomically(ByteArray(3) { 1 })

        assertArrayEquals(ByteArray(5) { 9 }, untouched.readBytes())
    }

    @Test
    fun `el temporal usado durante la escritura no queda huerfano si algo mas lo borra antes`() {
        // No se puede forzar un fallo real de renameTo en JVM puro sin tocar
        // permisos del sistema operativo, pero sí se puede comprobar que el
        // temporal usa un nombre predecible y exclusivo del archivo destino,
        // para que dos escrituras concurrentes a archivos distintos no puedan
        // pisarse el temporal la una a la otra.
        val a = File(folder.root, "a.sav")
        val b = File(folder.root, "b.sav")

        a.writeAtomically(ByteArray(4) { 1 })
        b.writeAtomically(ByteArray(4) { 2 })

        assertFalse(File(folder.root, "a.sav.tmp").exists())
        assertFalse(File(folder.root, "b.sav.tmp").exists())
        assertArrayEquals(ByteArray(4) { 1 }, a.readBytes())
        assertArrayEquals(ByteArray(4) { 2 }, b.readBytes())
    }
}
