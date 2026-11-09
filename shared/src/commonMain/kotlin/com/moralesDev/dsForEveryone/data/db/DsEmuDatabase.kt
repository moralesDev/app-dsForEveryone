package com.moralesDev.dsForEveryone.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/**
 * La base de datos de la biblioteca. Vive en el módulo común desde iOS-3
 * (docs/ios.md): Room es multiplataforma, y la misma definición sirve para
 * Android y para iOS. El esquema exportado está en `shared/schemas/`.
 */
@Database(
    entities = [RomEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(DsEmuDatabaseConstructor::class)
abstract class DsEmuDatabase : RoomDatabase() {
    abstract fun romDao(): RomDao

    companion object {
        const val NAME = "dsemu.db"
    }
}

/**
 * Lo pide Room fuera de Android, donde no puede crear la base por reflexión:
 * el `actual` de cada plataforma lo genera KSP. No se escribe a mano.
 */
@Suppress("KotlinNoActualForExpect")
expect object DsEmuDatabaseConstructor : RoomDatabaseConstructor<DsEmuDatabase> {
    override fun initialize(): DsEmuDatabase
}
