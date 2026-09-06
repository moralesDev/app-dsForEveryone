package com.moralesDev.dsForEveryone.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Doble en memoria de [RomDao], para ejercitar `RomRepository` en JVM sin Room. */
class FakeRomDao : RomDao {

    private val rows = MutableStateFlow<Map<String, RomEntity>>(emptyMap())

    override fun observeAll(): Flow<List<RomEntity>> = rows.map { it.values.toList() }

    override suspend fun findById(romId: String): RomEntity? = rows.value[romId]

    override suspend fun upsert(rom: RomEntity) {
        rows.update { it + (rom.id to rom) }
    }

    override suspend fun updateLocation(romId: String, uri: String, fileName: String) {
        rows.update { current ->
            val existing = current[romId] ?: return@update current
            current + (romId to existing.copy(documentUri = uri, fileName = fileName))
        }
    }

    override suspend fun markPlayed(romId: String, timestamp: Long) {
        rows.update { current ->
            val existing = current[romId] ?: return@update current
            current + (romId to existing.copy(lastPlayedAt = timestamp))
        }
    }

    override suspend fun rename(romId: String, name: String) {
        rows.update { current ->
            val existing = current[romId] ?: return@update current
            current + (romId to existing.copy(displayName = name))
        }
    }

    override suspend fun delete(romId: String) {
        rows.update { it - romId }
    }
}
