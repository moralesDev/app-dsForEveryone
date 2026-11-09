package com.moralesDev.dsForEveryone.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.moralesDev.dsForEveryone.data.db.DsEmuDatabase
import com.moralesDev.dsForEveryone.data.rom.GameTitles
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.ui.MainViewModel
import com.moralesDev.dsForEveryone.ui.game.layout.LayoutEditorViewModel
import com.moralesDev.dsForEveryone.ui.settings.SettingsViewModel
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLocale
import platform.Foundation.NSUserDomainMask
import platform.Foundation.languageCode
import platform.Foundation.preferredLanguages

/**
 * Lo que se inyecta en iOS: el equivalente de `AppModule` de Android, sólo
 * con lo que usan las pantallas que ya son comunes (docs/ios.md, iOS-3
 * paso 5). Crecerá con la biblioteca y el juego.
 *
 * Todo vive en `Application Support/files/`, que es la carpeta privada de la
 * app que el sistema incluye en las copias de seguridad, y la misma
 * disposición que `filesDir` en Android (ver StorageManager).
 */
private val iosModule = module {

    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }

    single { filesDirectory() }

    single { StorageManager(get<Path>()) }

    single {
        PreferenceDataStoreFactory.createWithPath(
            produceFile = { get<Path>() / "datastore" / SettingsRepository.FILE_NAME },
        )
    }

    single {
        SettingsRepository(
            dataStore = get(),
            // El idioma del sistema, sin lo que la app haya fijado en
            // AppleLanguages (ver ProvideAppLanguage.ios.kt): para eso está
            // la lista de preferencias, no la primera de la app.
            systemLanguageTag = { (NSLocale.preferredLanguages.firstOrNull() as? String)?.substringBefore('-') },
        )
    }

    single {
        Room.databaseBuilder<DsEmuDatabase>(name = (get<Path>() / DsEmuDatabase.NAME).toString())
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    single { SaveArchive(get(), get(IoDispatcher)) }

    // Ajustes sólo necesita los títulos para el manifiesto de una copia:
    // salen de la base de datos, sin pasar por el repositorio de ROMs.
    single {
        val database = get<DsEmuDatabase>()
        GameTitles { database.romDao().observeAll().first().associate { it.id to it.displayName } }
    }

    viewModel { MainViewModel(get()) }
    viewModel { SettingsViewModel(get(), get(), get()) }
    viewModel { LayoutEditorViewModel(get()) }
}

/** `Application Support/files/`, creada si no existe. */
@OptIn(ExperimentalForeignApi::class)
private fun filesDirectory(): Path {
    val url = NSFileManager.defaultManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask)
        .first() as platform.Foundation.NSURL
    val root = url.path!!.toPath() / "files"
    FileSystem.SYSTEM.createDirectories(root)
    return root
}

private var started = false

/** Arranca Koin una sola vez. Lo llama MainViewController. */
fun startKoinIos() {
    if (started) return
    started = true
    startKoin { modules(iosModule) }
}
