package com.moralesDev.dsForEveryone.data.save

import com.moralesDev.dsForEveryone.data.storage.StorageManager

/**
 * Punto de entrada único a las partidas de una ROM.
 *
 * Reúne los dos sistemas de guardado —el del propio juego y el de ranuras— para
 * que la sesión de juego dependa de una sola pieza y no tenga que saber qué
 * archivo corresponde a qué. Cada método recibe el identificador de la ROM, que
 * es lo que garantiza el aislamiento entre juegos.
 *
 * Son dos cosas distintas y conviene no confundirlas:
 *
 * - La **partida de batería** pertenece al juego. Es lo que el cartucho escribe
 *   en su memoria cuando el jugador guarda desde el menú del propio juego, y es
 *   lo único que existiría en una consola de verdad.
 * - El **save state** pertenece al emulador. Es una fotografía de la máquina
 *   entera —dos CPUs, memoria, vídeo, sonido— en un instante cualquiera, y
 *   permite volver exactamente a ese instante.
 * - El **respaldo automático** es un save state más, con el mismo formato y las
 *   mismas comprobaciones, sólo que lo crea la aplicación sola y vive en su
 *   propia ranura. No sustituye a la partida del juego ni a las del usuario:
 *   los tres conviven.
 */
class SaveManager(
    private val batterySaves: BatterySaveManager,
    private val saveStates: SaveStateManager,
    private val storageManager: StorageManager,
) {

    /** Partida con la que debe arrancar la ROM, o `null` si es la primera vez. */
    suspend fun loadBattery(romId: String): ByteArray? = batterySaves.read(romId)

    suspend fun persistBattery(romId: String, data: ByteArray): Boolean =
        batterySaves.write(romId, data)

    fun hasBattery(romId: String): Boolean = batterySaves.exists(romId)

    /** Borra la partida de batería, dejando el juego como si no se hubiera guardado nunca. */
    suspend fun deleteBattery(romId: String): Boolean = batterySaves.delete(romId)

    suspend fun stateSlots(romId: String, identity: StateIdentity?): List<SaveStateSlot> =
        saveStates.slots(romId, identity)

    suspend fun autoSlot(romId: String, identity: StateIdentity?): SaveStateSlot =
        saveStates.autoSlot(romId, identity)

    /**
     * Guarda en una ranura del usuario.
     *
     * Rechaza el respaldo automático a propósito y no por descuido: es la
     * frontera que garantiza que ningún camino manual pueda pisarlo. Para eso
     * está [writeAutoBackup], al que sólo llega el sistema.
     */
    suspend fun writeState(
        romId: String,
        slot: Int,
        payload: ByteArray,
        frame: IntArray?,
        identity: StateIdentity,
    ): Boolean {
        require(slot in 1..SaveStateManager.SLOT_COUNT) {
            "El guardado manual no puede escribir en la ranura $slot"
        }
        return saveStates.write(romId, slot, payload, frame, identity)
    }

    /**
     * Guarda el respaldo automático. Toca `auto.state` y nada más: las cinco
     * ranuras del usuario quedan fuera de su alcance por construcción, porque el
     * número de ranura no es un parámetro.
     */
    suspend fun writeAutoBackup(
        romId: String,
        payload: ByteArray,
        frame: IntArray?,
        identity: StateIdentity,
    ): Boolean = saveStates.write(
        romId = romId,
        slot = SaveStateManager.AUTO_SLOT,
        payload = payload,
        frame = frame,
        identity = identity,
    )

    /** Cuándo se guardó por última vez, para la biblioteca. */
    suspend fun lastSaveSummary(
        romId: String,
        coreName: String,
        coreVersion: String,
    ): LastSaveSummary? = saveStates.lastSaveSummary(romId, coreName, coreVersion)

    suspend fun readState(romId: String, slot: Int, identity: StateIdentity): StateReadResult =
        saveStates.read(romId, slot, identity)

    suspend fun deleteState(romId: String, slot: Int) = saveStates.delete(romId, slot)

    fun usedBytes(romId: String): Long = storageManager.usedBytes(romId)
}
