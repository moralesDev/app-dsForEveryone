package com.moralesDev.dsForEveryone.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.moralesDev.dsForEveryone.data.cover.CoverRepository
import com.moralesDev.dsForEveryone.data.db.DsEmuDatabase
import com.moralesDev.dsForEveryone.data.db.RomDao
import com.moralesDev.dsForEveryone.data.rom.ContentResolverDocumentAccess
import com.moralesDev.dsForEveryone.data.rom.DocumentAccess
import com.moralesDev.dsForEveryone.data.rom.RomRepository
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import com.moralesDev.dsForEveryone.emulator.core.StubEmulatorCore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

    @Provides
    @Singleton
    fun provideStorageManager(@ApplicationContext context: Context): StorageManager =
        StorageManager(context.filesDir)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DsEmuDatabase =
        Room.databaseBuilder(context, DsEmuDatabase::class.java, DsEmuDatabase.NAME).build()

    @Provides
    fun provideRomDao(database: DsEmuDatabase): RomDao = database.romDao()

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideDocumentAccess(contentResolver: ContentResolver): DocumentAccess =
        ContentResolverDocumentAccess(contentResolver)

    @Provides
    @Singleton
    fun provideRomRepository(
        documentAccess: DocumentAccess,
        romDao: RomDao,
        storageManager: StorageManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): RomRepository = RomRepository(documentAccess, romDao, storageManager, ioDispatcher)

    @Provides
    @Singleton
    fun provideCoverRepository(
        contentResolver: ContentResolver,
        storageManager: StorageManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CoverRepository = CoverRepository(contentResolver, storageManager, ioDispatcher)

    /**
     * Único punto donde se decide qué core usa la aplicación.
     *
     * `StubEmulatorCore` sigue en el proyecto una vez llegue el core nativo
     * (fase 4): es el doble con el que se prueba sin NDK.
     */
    @Provides
    @Singleton
    fun provideEmulatorCore(): EmulatorCore = StubEmulatorCore()

    /**
     * El controlador es único en la aplicación: la sesión de juego debe
     * sobrevivir a rotaciones y a que la pantalla se recomponga, y sólo puede
     * haber un hilo de emulación vivo.
     */
    @Provides
    @Singleton
    fun provideEmulatorController(core: EmulatorCore): EmulatorController = EmulatorController(core)
}
