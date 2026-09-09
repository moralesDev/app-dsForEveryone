package com.moralesDev.dsForEveryone.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.moralesDev.dsForEveryone.data.cheats.CheatsManager
import com.moralesDev.dsForEveryone.data.cover.CoverRepository
import com.moralesDev.dsForEveryone.data.db.DsEmuDatabase
import com.moralesDev.dsForEveryone.data.db.RomDao
import com.moralesDev.dsForEveryone.data.rom.ContentResolverDocumentAccess
import com.moralesDev.dsForEveryone.data.rom.DocumentAccess
import com.moralesDev.dsForEveryone.data.rom.RomRepository
import com.moralesDev.dsForEveryone.data.save.BatterySaveManager
import com.moralesDev.dsForEveryone.data.save.SaveManager
import com.moralesDev.dsForEveryone.data.save.SaveStateManager
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.core.DsEmulatorCore
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadInputRouter
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    @ApplicationScope
    fun provideApplicationScope(
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher)

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
    fun provideGamepadManager(@ApplicationContext context: Context): GamepadManager =
        GamepadManager(context)

    @Provides
    @Singleton
    fun provideGamepadInputRouter(
        settingsRepository: SettingsRepository,
        @ApplicationScope applicationScope: CoroutineScope,
    ): GamepadInputRouter = GamepadInputRouter(settingsRepository, applicationScope)

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

    @Provides
    @Singleton
    fun provideBatterySaveManager(
        storageManager: StorageManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): BatterySaveManager = BatterySaveManager(storageManager, ioDispatcher)

    @Provides
    @Singleton
    fun provideCheatsManager(
        storageManager: StorageManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): CheatsManager = CheatsManager(storageManager, ioDispatcher)

    @Provides
    @Singleton
    fun provideSaveStateManager(
        storageManager: StorageManager,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): SaveStateManager = SaveStateManager(storageManager, ioDispatcher)

    @Provides
    @Singleton
    fun provideSaveManager(
        batterySaveManager: BatterySaveManager,
        saveStateManager: SaveStateManager,
        storageManager: StorageManager,
    ): SaveManager = SaveManager(batterySaveManager, saveStateManager, storageManager)

    /**
     * Único punto donde se decide qué core usa la aplicación.
     *
     * `StubEmulatorCore` sigue en el proyecto tras llegar el core nativo: es
     * el doble con el que se prueba sin NDK.
     */
    @Provides
    @Singleton
    fun provideEmulatorCore(): EmulatorCore = DsEmulatorCore()

    /**
     * El controlador es único en la aplicación: la sesión de juego debe
     * sobrevivir a rotaciones y a que la pantalla se recomponga, y sólo puede
     * haber un hilo de emulación vivo.
     */
    @Provides
    @Singleton
    fun provideEmulatorController(core: EmulatorCore): EmulatorController = EmulatorController(core)
}
