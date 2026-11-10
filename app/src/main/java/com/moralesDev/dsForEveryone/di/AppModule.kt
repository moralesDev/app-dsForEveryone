package com.moralesDev.dsForEveryone.di

import androidx.core.os.ConfigurationCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import com.moralesDev.dsForEveryone.data.cheats.CheatsManager
import com.moralesDev.dsForEveryone.data.cover.CoverRepository
import com.moralesDev.dsForEveryone.data.cover.CoverStore
import com.moralesDev.dsForEveryone.data.db.DsEmuDatabase
import com.moralesDev.dsForEveryone.data.rom.ContentResolverDocumentAccess
import com.moralesDev.dsForEveryone.data.rom.DocumentAccess
import com.moralesDev.dsForEveryone.data.rom.GameTitles
import com.moralesDev.dsForEveryone.data.rom.RomLibrary
import com.moralesDev.dsForEveryone.data.rom.RomRepository
import com.moralesDev.dsForEveryone.data.save.BatterySaveManager
import com.moralesDev.dsForEveryone.data.save.BitmapThumbnailEncoder
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.data.save.SaveManager
import com.moralesDev.dsForEveryone.data.save.SaveStateManager
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.core.DsEmulatorCore
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import com.moralesDev.dsForEveryone.emulator.gamepad.AndroidGamepadSource
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadInputRouter
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadSource
import com.moralesDev.dsForEveryone.data.rom.AndroidGameRomOpener
import com.moralesDev.dsForEveryone.data.rom.GameRomOpener
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadManager
import com.moralesDev.dsForEveryone.ui.MainViewModel
import com.moralesDev.dsForEveryone.ui.game.GameViewModel
import com.moralesDev.dsForEveryone.ui.game.gamepad.GamepadMappingViewModel
import com.moralesDev.dsForEveryone.ui.game.layout.LayoutEditorViewModel
import com.moralesDev.dsForEveryone.ui.library.RomLibraryViewModel
import com.moralesDev.dsForEveryone.ui.settings.SettingsViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Todo lo que se inyecta en la aplicación, en un solo sitio.
 *
 * `single` es lo que en Hilt era `@Singleton`: una instancia para toda la
 * aplicación. `factory` es lo que no lo era (una por petición). Los
 * ViewModels se declaran con `viewModel` y las pantallas los piden con
 * `koinViewModel()`: Koin los guarda en el `ViewModelStore` de la entrada de
 * navegación, igual que hacía `hiltViewModel()`.
 *
 * A diferencia de Hilt, Koin resuelve en tiempo de ejecución: una
 * dependencia sin declarar no rompe la compilación, rompe al abrir la
 * pantalla. Por eso AppModuleTest verifica el módulo entero en la JVM.
 */
val appModule = module {

    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }

    single<CoroutineScope>(ApplicationScope) {
        CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(IoDispatcher))
    }

    // El almacén de ajustes: uno solo en todo el proceso (`single`), en el
    // mismo archivo que usaba el `preferencesDataStore` de antes, así que los
    // ajustes de cada usuario se conservan.
    single {
        val context = androidContext()
        PreferenceDataStoreFactory.create(
            produceFile = { context.filesDir.resolve("datastore/${SettingsRepository.FILE_NAME}") },
        )
    }

    single {
        val context = androidContext()
        SettingsRepository(
            dataStore = get(),
            systemLanguageTag = { ConfigurationCompat.getLocales(context.resources.configuration)[0]?.language },
        )
    }

    single { StorageManager(androidContext().filesDir) }

    single { GamepadManager(androidContext()) }

    single { GamepadInputRouter(get(), get(ApplicationScope)) }

    single<GamepadSource> { AndroidGamepadSource(get(), get()) }

    single {
        Room.databaseBuilder(androidContext(), DsEmuDatabase::class.java, DsEmuDatabase.NAME).build()
    }

    factory { get<DsEmuDatabase>().romDao() }

    factory { androidContext().contentResolver }

    single<DocumentAccess> { ContentResolverDocumentAccess(get()) }

    single { RomRepository(get(), get(), get(), get(IoDispatcher)) }

    // La misma instancia, vista como la biblioteca común (data/rom/RomLibrary).
    single<RomLibrary> { get<RomRepository>() }

    single<GameRomOpener> { AndroidGameRomOpener(get()) }

    single { CoverRepository(get(), get(), get(IoDispatcher)) }

    // La misma instancia, vista como las portadas comunes (data/cover/CoverStore).
    single<CoverStore> { get<CoverRepository>() }

    single { BatterySaveManager(get(), get(IoDispatcher)) }

    single { CheatsManager(get(), get(IoDispatcher)) }

    single { SaveStateManager(get(), get(IoDispatcher), BitmapThumbnailEncoder) }

    single { SaveManager(get(), get(), get()) }

    single { SaveArchive(get(), get(IoDispatcher)) }

    /**
     * Único punto donde se decide qué core usa la aplicación.
     *
     * `StubEmulatorCore` sigue en el proyecto tras llegar el core nativo: es
     * el doble con el que se prueba sin NDK.
     */
    single<EmulatorCore> { DsEmulatorCore() }

    /**
     * El controlador es único en la aplicación: la sesión de juego debe
     * sobrevivir a rotaciones y a que la pantalla se recomponga, y sólo puede
     * haber un hilo de emulación vivo.
     */
    single { EmulatorController(get()) }

    viewModel { MainViewModel(get()) }
    // Lo único que Ajustes necesita de la biblioteca: los títulos.
    single {
        val roms = get<RomRepository>()
        GameTitles { roms.observeLibrary().first().associate { it.id to it.displayName } }
    }

    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { RomLibraryViewModel(get(), get(), get(), get()) }
    viewModel { LayoutEditorViewModel(get()) }
    viewModel { GamepadMappingViewModel(get(), get(), get()) }

    // El último `get()` es el SavedStateHandle de la entrada de navegación:
    // Koin lo saca de la misma entrada que guarda el ViewModel, como Hilt.
    viewModel {
        GameViewModel(
            controller = get(),
            settingsRepository = get(),
            romLibrary = get(),
            romOpener = get(),
            saveManager = get(),
            cheatsManager = get(),
            gamepads = get(),
            applicationScope = get(ApplicationScope),
            savedStateHandle = get(),
        )
    }
}
