package com.moralesDev.dsForEveryone.data.save

import com.moralesDev.dsForEveryone.data.storage.StorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import com.moralesDev.dsForEveryone.data.storage.batteryFile
import com.moralesDev.dsForEveryone.data.storage.stateFile
import com.moralesDev.dsForEveryone.data.storage.statesDirectory

/**
 * El sistema de ranuras sobre disco de verdad.
 *
 * Se ejercita contra una carpeta temporal en lugar de simular el sistema de
 * archivos: lo que se quiere comprobar —que los juegos no se mezclan, que
 * sobrescribir reemplaza y borrar borra sólo lo suyo— son afirmaciones sobre
 * archivos, y un doble de pruebas las daría por buenas por construcción.
 */
class SaveStateManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var storage: StorageManager
    private lateinit var manager: SaveStateManager

    private val gameA = "a".repeat(64)
    private val gameB = "b".repeat(64)

    private val identityA = StateIdentity(gameA, 0x1111_1111L, "stub", "0", STATE_BYTES)
    private val identityB = StateIdentity(gameB, 0x2222_2222L, "stub", "0", STATE_BYTES)

    @Before
    fun setUp() {
        storage = StorageManager(folder.root)
        // El trabajo de este gestor es bloqueante de verdad —abre, lee y renombra
        // archivos—, así que se ejecuta en el sitio en lugar de simular un reloj.
        manager = SaveStateManager(storage, Dispatchers.Unconfined)
    }

    private fun state(seed: Int) = ByteArray(STATE_BYTES) { (it + seed).toByte() }

    @Test
    fun `una ranura recien guardada se puede recuperar tal cual`() = runTest {
        val original = state(1)

        assertTrue(manager.write(gameA, 1, original, frame = null, identityA))

        val read = manager.read(gameA, 1, identityA)
        assertEquals(StateReadResult.Loaded(original), read)
    }

    @Test
    fun `una ranura sin guardar esta vacia`() = runTest {
        assertEquals(StateReadResult.Empty, manager.read(gameA, 3, identityA))

        val slots = manager.slots(gameA, identityA)
        assertTrue(slots.all { it.isEmpty })
        assertTrue(slots.none { it.isRestorable })
    }

    @Test
    fun `hay cinco ranuras y son independientes entre si`() = runTest {
        repeat(SaveStateManager.SLOT_COUNT) { index ->
            manager.write(gameA, index + 1, state(index), frame = null, identityA)
        }

        val slots = manager.slots(gameA, identityA)
        assertEquals(SaveStateManager.SLOT_COUNT, slots.size)
        assertEquals((1..5).toList(), slots.map { it.index })
        assertTrue(slots.all { it.isRestorable })

        // Cada una devuelve lo suyo, no lo de la vecina.
        repeat(SaveStateManager.SLOT_COUNT) { index ->
            assertEquals(
                StateReadResult.Loaded(state(index)),
                manager.read(gameA, index + 1, identityA),
            )
        }
    }

    @Test
    fun `dos juegos no comparten ranuras`() = runTest {
        manager.write(gameA, 1, state(10), frame = null, identityA)
        manager.write(gameB, 1, state(20), frame = null, identityB)

        assertEquals(StateReadResult.Loaded(state(10)), manager.read(gameA, 1, identityA))
        assertEquals(StateReadResult.Loaded(state(20)), manager.read(gameB, 1, identityB))
        // Y la ranura 2 de cada uno sigue vacía: nadie ha escrito en el vecino.
        assertEquals(StateReadResult.Empty, manager.read(gameA, 2, identityA))
        assertEquals(StateReadResult.Empty, manager.read(gameB, 2, identityB))
    }

    @Test
    fun `el estado de un juego no se puede cargar en otro`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA)

        // Se copia a mano el archivo de A en la carpeta de B, que es justo lo
        // que la ruta por hash impide y lo que la validación tiene que atrapar
        // si alguna vez ocurriera de otro modo.
        storage.stateFile(gameA, 1).copyTo(storage.stateFile(gameB, 1))

        assertEquals(
            StateReadResult.Rejected(StateRejection.OtherGame),
            manager.read(gameB, 1, identityB),
        )
    }

    @Test
    fun `una ranura de otro juego aparece marcada en el listado`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA)
        storage.stateFile(gameA, 1).copyTo(storage.stateFile(gameB, 1))

        val slot = manager.slots(gameB, identityB).first()

        // Ocupada pero inservible: ni vacía ni restaurable.
        assertFalse(slot.isEmpty)
        assertFalse(slot.isRestorable)
        assertEquals(StateRejection.OtherGame, slot.problem)
    }

    @Test
    fun `un estado de otra version del core se rechaza`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA)

        val newerCore = identityA.copy(coreVersion = "1")

        assertEquals(
            StateReadResult.Rejected(StateRejection.OtherCore("stub-0")),
            manager.read(gameA, 1, newerCore),
        )
    }

    @Test
    fun `un estado del tamano equivocado se rechaza`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA)

        val otherSize = identityA.copy(stateSize = STATE_BYTES * 2)

        assertEquals(
            StateReadResult.Rejected(
                StateRejection.UnexpectedSize(STATE_BYTES, STATE_BYTES * 2),
            ),
            manager.read(gameA, 1, otherSize),
        )
    }

    @Test
    fun `un archivo alterado se detecta y no llega al core`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA)

        val file = storage.stateFile(gameA, 1)
        val bytes = file.readBytes()
        bytes[bytes.size - 1] = (bytes[bytes.size - 1] + 1).toByte()
        file.writeBytes(bytes)

        assertEquals(
            StateReadResult.Rejected(StateRejection.Corrupt),
            manager.read(gameA, 1, identityA),
        )
    }

    @Test
    fun `un archivo sin el contenedor no se interpreta como estado`() = runTest {
        // Lo que quedaría de un archivo ajeno, o de un bug que escribiera en crudo.
        storage.stateFile(gameA, 1).writeBytes(state(1))

        assertEquals(
            StateReadResult.Rejected(StateRejection.NotAContainer),
            manager.read(gameA, 1, identityA),
        )

        // Y aparece ocupada y averiada, no vacía: si apareciera vacía, el primer
        // guardado la pisaría sin preguntar.
        val slot = manager.slots(gameA, identityA).first()
        assertFalse(slot.isEmpty)
        assertEquals(StateRejection.NotAContainer, slot.problem)
    }

    @Test
    fun `sobrescribir reemplaza el contenido y no deja restos`() = runTest {
        manager.write(gameA, 2, state(1), frame = null, identityA)
        manager.write(gameA, 2, state(99), frame = null, identityA)

        assertEquals(StateReadResult.Loaded(state(99)), manager.read(gameA, 2, identityA))
        // La escritura atómica pasa por un temporal; no puede quedarse ahí.
        val leftovers = storage.statesDirectory(gameA).listFiles().orEmpty()
            .filter { it.name.endsWith(".tmp") }
        assertTrue("Quedó un temporal: $leftovers", leftovers.isEmpty())
    }

    @Test
    fun `borrar una ranura no toca las demas ni la partida del juego`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA)
        manager.write(gameA, 2, state(2), frame = null, identityA)
        val battery = storage.batteryFile(gameA).apply { writeBytes(ByteArray(64) { 7 }) }

        manager.delete(gameA, 1)

        assertEquals(StateReadResult.Empty, manager.read(gameA, 1, identityA))
        assertEquals(StateReadResult.Loaded(state(2)), manager.read(gameA, 2, identityA))
        assertTrue(battery.exists())
        assertEquals(64, battery.length())
    }

    @Test
    fun `borrar una ranura vacia no falla`() = runTest {
        manager.delete(gameA, 4)

        assertEquals(StateReadResult.Empty, manager.read(gameA, 4, identityA))
    }

    @Test
    fun `una ranura fuera de rango no se acepta`() = runTest {
        // El número de ranura no viene del usuario, pero un fallo aquí
        // escribiría fuera del esquema de archivos y conviene que sea ruidoso.
        // El 0 sí es válido: es la ranura del respaldo automático.
        assertThrows { manager.write(gameA, -1, state(1), frame = null, identityA) }
        assertThrows { manager.write(gameA, 6, state(1), frame = null, identityA) }
        assertThrows { manager.delete(gameA, 9) }
    }

    @Test
    fun `el listado describe la ranura sin cargar el estado entero`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA, savedAt = 1_700_000_000_000L)

        val slot = manager.slots(gameA, identityA).first()

        assertEquals(1, slot.index)
        assertEquals(1_700_000_000_000L, slot.savedAt)
        assertEquals("stub-0", slot.core)
        assertTrue("El archivo debe pesar más que el estado", slot.sizeBytes > STATE_BYTES)
        assertNull(slot.problem)
        assertTrue(slot.isRestorable)
        // Sin fotograma no hay miniatura, y la ranura sigue siendo válida.
        assertNull(slot.thumbnailPath)
    }

    @Test
    fun `sin sesion se describen las ranuras pero no se juzgan`() = runTest {
        manager.write(gameA, 1, state(1), frame = null, identityA)

        val slot = manager.slots(gameA, identity = null).first()

        assertNotNull(slot.savedAt)
        assertNull(slot.problem)
    }

    private inline fun assertThrows(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        throw AssertionError("Se esperaba IllegalArgumentException")
    }

    private companion object {
        const val STATE_BYTES = 1024
    }
}
