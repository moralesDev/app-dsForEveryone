package com.moralesDev.dsForEveryone.di

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.moralesDev.dsForEveryone.data.save.ThumbnailEncoder
import java.io.File
import okio.Path
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

/**
 * Lo que Hilt comprobaba al compilar y Koin no: que cada cosa que se pide
 * está declarada en [appModule]. Recorre todas las definiciones y los
 * constructores de lo que construyen, y falla si alguna dependencia no tiene
 * de dónde salir. Sin esta prueba, un olvido se descubriría al abrir la
 * pantalla en el móvil.
 *
 * Los tipos de [extraTypes] los pone el sistema, no el módulo: el `Context`
 * lo da `androidContext()`, el `File` es `filesDir`, y el `SavedStateHandle`
 * lo pone la entrada de navegación de cada ViewModel. `Function0` es el
 * `systemLanguageTag` de SettingsRepository, una lambda escrita en el propio
 * módulo y no algo que se pida a Koin. `Path` es `filesDir` en la forma de
 * Okio con la que trabaja StorageManager, y `ThumbnailEncoder` es el
 * codificador de miniaturas de Android, que el módulo pone tal cual.
 */
class AppModuleTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `todas las dependencias del modulo tienen de donde salir`() {
        appModule.verify(
            extraTypes = listOf(
                Context::class,
                File::class,
                SavedStateHandle::class,
                Function0::class,
                Path::class,
                ThumbnailEncoder::class,
            ),
        )
    }
}
