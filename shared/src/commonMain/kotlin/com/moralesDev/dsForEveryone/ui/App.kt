package com.moralesDev.dsForEveryone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.moralesDev.dsForEveryone.ui.locale.ProvideAppLanguage
import com.moralesDev.dsForEveryone.ui.navigation.AppNavHost
import com.moralesDev.dsForEveryone.ui.navigation.PlatformScreens
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.DsEmuTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * La aplicación entera, la misma en Android (MainActivity) y en iOS
 * (MainViewController): tema, idioma y navegación. Lo único que cambia de
 * una a otra son las pantallas que todavía no son comunes, [screens].
 */
@Composable
fun App(screens: PlatformScreens) {
    DsEmuTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
        ) {
            val viewModel: MainViewModel = koinViewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            // Por encima de ProvideAppLanguage a propósito: al cambiar de
            // idioma la interfaz se rehace (ver ProvideAppLanguage), y la
            // navegación —en qué pantalla se está— tiene que sobrevivir.
            val navController = rememberNavController()

            // Hasta que no se sabe el idioma y si toca el onboarding no
            // se pinta nada: sólo el fondo, que ya es el definitivo.
            settings?.let { current ->
                ProvideAppLanguage(current.language) {
                    AppNavHost(
                        screens = screens,
                        navController = navController,
                        skipWelcome = current.skipWelcome,
                        onWelcomeFinished = viewModel::onWelcomeFinished,
                    )
                }
            }
        }
    }
}
