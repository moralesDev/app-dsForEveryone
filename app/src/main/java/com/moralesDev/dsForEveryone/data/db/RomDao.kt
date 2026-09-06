package com.moralesDev.dsForEveryone.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RomDao {

    /** Los jugados recientemente primero; el resto, por orden de incorporación. */
    @Query(
        """
        SELECT * FROM roms
        ORDER BY CASE WHEN lastPlayedAt IS NULL THEN 1 ELSE 0 END,
                 lastPlayedAt DESC,
                 addedAt DESC
        """,
    )
    fun observeAll(): Flow<List<RomEntity>>

    @Query("SELECT * FROM roms WHERE id = :romId")
    suspend fun findById(romId: String): RomEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rom: RomEntity)

    /**
     * Al volver a elegir una ROM que ya estaba en la biblioteca sólo se refresca
     * su ubicación: el identificador es el mismo, así que la partida se conserva
     * aunque el archivo haya cambiado de sitio.
     */
    @Query("UPDATE roms SET documentUri = :uri, fileName = :fileName WHERE id = :romId")
    suspend fun updateLocation(romId: String, uri: String, fileName: String)

    @Query("UPDATE roms SET lastPlayedAt = :timestamp WHERE id = :romId")
    suspend fun markPlayed(romId: String, timestamp: Long)

    @Query("UPDATE roms SET displayName = :name WHERE id = :romId")
    suspend fun rename(romId: String, name: String)

    @Query("DELETE FROM roms WHERE id = :romId")
    suspend fun delete(romId: String)
}
