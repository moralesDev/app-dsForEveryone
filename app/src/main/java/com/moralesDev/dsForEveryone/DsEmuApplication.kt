package com.moralesDev.dsForEveryone

import android.app.Application
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class DsEmuApplication : Application() {

    @Inject
    lateinit var saveArchive: SaveArchive

    override fun onCreate() {
        super.onCreate()
        // Antes de que nada pueda abrir un juego: si una importación de
        // partidas murió a mitad de reemplazar una carpeta, la de antes tiene
        // que estar en su sitio cuando el juego la busque. Son un par de
        // `rename` sobre `saves/`, no trabajo que justifique ir a otro hilo.
        saveArchive.recoverInterrupted()
    }
}
