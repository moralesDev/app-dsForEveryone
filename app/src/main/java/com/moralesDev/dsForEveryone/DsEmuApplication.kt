package com.moralesDev.dsForEveryone

import android.app.Application
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.di.appModule
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DsEmuApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Koin arranca antes que nada: todo lo demás se pide a él.
        startKoin {
            androidContext(this@DsEmuApplication)
            modules(appModule)
        }

        // Antes de que nada pueda abrir un juego: si una importación de
        // partidas murió a mitad de reemplazar una carpeta, la de antes tiene
        // que estar en su sitio cuando el juego la busque. Son un par de
        // `rename` sobre `saves/`, no trabajo que justifique ir a otro hilo.
        get<SaveArchive>().recoverInterrupted()
    }
}
