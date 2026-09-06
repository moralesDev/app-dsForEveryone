package com.moralesDev.dsForEveryone.di

import android.content.Context
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.core.EmulatorCore
import com.moralesDev.dsForEveryone.emulator.core.StubEmulatorCore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)

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
