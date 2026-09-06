package com.moralesDev.dsForEveryone.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RomEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class DsEmuDatabase : RoomDatabase() {
    abstract fun romDao(): RomDao

    companion object {
        const val NAME = "dsemu.db"
    }
}
