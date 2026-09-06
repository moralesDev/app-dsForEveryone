package com.moralesDev.dsForEveryone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.ui.MainViewModel
import com.moralesDev.dsForEveryone.ui.locale.ProvideAppLanguage
import com.moralesDev.dsForEveryone.ui.navigation.AppNavHost
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.DsEmuTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity de la aplicación. El manifiesto fija vertical por defecto;
 * `GameScreen` (fase 2) será la única pantalla que la libere en tiempo de
 * ejecución para poder rotar durante la partida. El resto de la app —
 * biblioteca, ajustes, bienvenida— no está pensada para horizontal.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            DsEmuTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Background),
                ) {
                    val settings by viewModel.settings.collectAsStateWithLifecycle()

                    // Hasta que no se sabe el idioma y si toca el onboarding no
                    // se pinta nada: sólo el fondo, que ya es el definitivo.
                    settings?.let { current ->
                        ProvideAppLanguage(current.language) {
                            AppNavHost(
                                skipWelcome = current.skipWelcome,
                                onWelcomeFinished = viewModel::onWelcomeFinished,
                            )
                        }
                    }
                }
            }
        }
    }
}
