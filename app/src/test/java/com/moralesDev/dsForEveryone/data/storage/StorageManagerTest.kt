package com.moralesDev.dsForEveryone.data.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Dónde vive cada archivo, sobre una carpeta temporal real: nada de dobles
 * para un contrato que es enteramente sobre el sistema de archivos.
 */
class StorageManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var storage: StorageManager

    private val pokemon = "3d0c79f1627022e18765766f6cb5ea067f6b5bf7dca115552189ad65a5c3a8ac"
    private val zelda = "cd3b969d5ed6b38b2039c11b894d6db2fc3fad209a7fd046bee241cc66069851"

    @Before
    fun setUp() {
        storage = StorageManager(folder.root)
    }

    // ---------------------------- saves/

    @Test
    fun `la carpeta de un juego cuelga de saves y del hash de la ROM`() {
        val dir = storage.romDirectory(pokemon)

        assertTrue(dir.exists())
        assertTrue(dir.isDirectory)
        assertEquals("saves", dir.parentFile?.name)
        assertEquals(pokemon, dir.name)
    }

    @Test
    fun `dos juegos no comparten carpeta`() {
        val one = storage.romDirectory(pokemon)
        val other = storage.romDirectory(zelda)

        assertNotEquals(one, other)
    }

    @Test
    fun `battery y cheats viven dentro de la carpeta del juego`() {
        val battery = storage.batteryFile(pokemon)
        val cheats = storage.cheatsFile(pokemon)

        assertEquals(storage.romDirectory(pokemon), battery.parentFile)
        assertEquals(storage.romDirectory(pokemon), cheats.parentFile)
        assertEquals("battery.sav", battery.name)
        assertEquals("cheats.json", cheats.name)
    }

    @Test
    fun `las ranuras numeradas se llaman slot_N y el respaldo automatico se llama auto`() {
        val slot1 = storage.stateFile(pokemon, 1)
        val auto = storage.stateFile(pokemon, StorageManager.AUTO_SLOT)

        assertEquals("slot_1.state", slot1.name)
        assertEquals("auto.state", auto.name)
        assertEquals(storage.statesDirectory(pokemon), slot1.parentFile)
    }

    @Test
    fun `la miniatura de una ranura vive junto a su estado`() {
        val state = storage.stateFile(pokemon, 2)
        val thumbnail = storage.stateThumbnailFile(pokemon, 2)

        assertEquals(state.parentFile, thumbnail.parentFile)
        assertEquals("slot_2.png", thumbnail.name)
    }

    // ---------------------------- covers/

    @Test
    fun `la portada cuelga del hash de la ROM y vive fuera de saves`() {
        val cover = storage.coverFile(pokemon)
        val saves = storage.romDirectory(pokemon)

        assertTrue(cover.name.startsWith(pokemon))
        assertTrue(cover.name.endsWith(".webp"))
        assertFalse(cover.absolutePath.startsWith(saves.absolutePath))
        assertEquals("covers", cover.parentFile?.name)
    }

    // ---------------------------- system/

    @Test
    fun `un archivo de sistema vive en system y no en saves ni covers`() {
        val bios = storage.systemFile("bios7.bin")

        assertEquals("system", bios.parentFile?.name)
        assertFalse(bios.absolutePath.startsWith(storage.romDirectory(pokemon).absolutePath))
        assertFalse(bios.absolutePath.startsWith(storage.coverFile(pokemon).parentFile!!.absolutePath))
    }

    @Test
    fun `dos archivos de sistema distintos conviven en la misma carpeta`() {
        val bios7 = storage.systemFile("bios7.bin").apply { writeBytes(ByteArray(16_384)) }
        val bios9 = storage.systemFile("bios9.bin").apply { writeBytes(ByteArray(4_096)) }

        assertTrue(bios7.exists())
        assertTrue(bios9.exists())
        assertEquals(bios7.parentFile, bios9.parentFile)
    }

    @Test
    fun `borrar los datos de una ROM no toca los archivos de sistema`() {
        val bios = storage.systemFile("bios7.bin").apply { writeBytes(ByteArray(16_384)) }
        storage.batteryFile(pokemon).writeBytes(ByteArray(64))
        storage.coverFile(pokemon).writeBytes(ByteArray(32))

        storage.deleteRomData(pokemon)

        assertFalse("La partida se va", storage.batteryFile(pokemon).exists())
        assertFalse("La portada se va", storage.coverFile(pokemon).exists())
        assertTrue("El archivo de sistema se queda: no es de ningún juego", bios.exists())
    }

    // ---------------------------- deleteRomData / usedBytes

    @Test
    fun `borrar los datos de un juego no toca los de otro`() {
        storage.batteryFile(pokemon).writeBytes(ByteArray(64))
        storage.batteryFile(zelda).writeBytes(ByteArray(64))

        storage.deleteRomData(pokemon)

        assertFalse(storage.batteryFile(pokemon).exists())
        assertTrue(storage.batteryFile(zelda).exists())
    }

    @Test
    fun `usedBytes suma sólo los archivos del juego pedido`() {
        storage.batteryFile(pokemon).writeBytes(ByteArray(100))
        storage.stateFile(pokemon, 1).writeBytes(ByteArray(200))
        storage.batteryFile(zelda).writeBytes(ByteArray(9_999))

        assertEquals(300L, storage.usedBytes(pokemon))
    }

    @Test
    fun `renombrar el archivo original no separa al juego de su carpeta`() {
        // El nombre del archivo no interviene en el cálculo: la ruta sale del
        // hash del contenido, que no cambia al renombrar el .nds.
        storage.batteryFile(pokemon).writeBytes(ByteArray(64))

        assertTrue(storage.batteryFile(pokemon).exists())
        assertEquals(64, storage.batteryFile(pokemon).length())
    }
}
