package com.moralesDev.dsForEveryone.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moralesDev.dsForEveryone.ui.about.AboutScreen
import com.moralesDev.dsForEveryone.ui.components.LoadingVeil
import com.moralesDev.dsForEveryone.ui.game.GameScreen
import com.moralesDev.dsForEveryone.ui.game.layout.LayoutEditorScreen
import com.moralesDev.dsForEveryone.ui.library.RomLibraryScreen
import com.moralesDev.dsForEveryone.ui.settings.SettingsScreen
import com.moralesDev.dsForEveryone.ui.splash.SplashScreen
import com.moralesDev.dsForEveryone.ui.welcome.WelcomeScreen

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LIBRARY = "library"
    const val GAME = "game"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val LAYOUT_EDITOR = "layout_editor"
}

/**
 * Grafo de navegación.
 *
 * Siempre se arranca por el splash. Es él quien decide a dónde seguir según
 * [skipWelcome], y sale de la pila al hacerlo: el botón atrás desde la
 * biblioteca cierra la aplicación, no vuelve a la presentación.
 *
 * ## Qué cambio de pantalla pasa por la carga y cuál no
 *
 * Casi todos pasan por [LoadingVeil], pero **no** los que ya enseñan otra cosa
 * mientras esperan; encadenar dos pantallas de espera es peor que no tener
 * ninguna:
 *
 * - Del arranque a donde sea: el arranque **es** una pantalla de espera, con la
 *   misma marca y su propia barra de progreso.
 * - De la biblioteca al juego: la pantalla de juego traerá su propio «Cargando
 *   ROM» a partir de la fase 3.
 *
 * El resto sí, y sobre todo **salir del juego**, que es el caso caro (fase 2).
 */
@Composable
fun AppNavHost(
    skipWelcome: Boolean,
    onWelcomeFinished: (skipNextTime: Boolean) -> Unit,
    navController: NavHostController = rememberNavController(),
    transitions: ScreenTransitions = rememberScreenTransitions(),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {

            composable(Routes.SPLASH) {
                SplashScreen(
                    onFinished = {
                        // Sin velo: el arranque ya lo era.
                        val next = if (skipWelcome) Routes.LIBRARY else Routes.WELCOME
                        navController.navigate(next) {
                            popUpTo(Routes.SPLASH) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.WELCOME) {
                WelcomeScreen(
                    onFinished = { skipNextTime ->
                        onWelcomeFinished(skipNextTime)
                        transitions.navigateWithVeil {
                            navController.navigate(Routes.LIBRARY) {
                                popUpTo(Routes.WELCOME) { inclusive = true }
                            }
                        }
                    },
                )
            }

            composable(Routes.LIBRARY) {
                RomLibraryScreen(
                    onOpenSettings = {
                        transitions.navigateWithVeil { navController.navigate(Routes.SETTINGS) }
                    },
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { transitions.navigateWithVeil { navController.popBackStack() } },
                    onOpenAbout = {
                        transitions.navigateWithVeil { navController.navigate(Routes.ABOUT) }
                    },
                    onOpenLayoutEditor = {
                        transitions.navigateWithVeil { navController.navigate(Routes.LAYOUT_EDITOR) }
                    },
                )
            }

            composable(Routes.ABOUT) {
                AboutScreen(
                    onBack = { transitions.navigateWithVeil { navController.popBackStack() } },
                )
            }

            composable(Routes.LAYOUT_EDITOR) {
                LayoutEditorScreen(
                    onBack = { transitions.navigateWithVeil { navController.popBackStack() } },
                )
            }

            composable(Routes.GAME) {
                GameScreen(
                    onExit = {
                        transitions.navigateWithVeil(ScreenTransitions.LEAVING_GAME_MS) {
                            navController.popBackStack(Routes.LIBRARY, inclusive = false)
                        }
                    },
                )
            }
        }

        // El botón atrás del sistema volvería sin pasar por la carga, y el
        // mismo camino se vería de dos maneras según con qué se haga. Se
        // intercepta aquí y no en cada pantalla porque la regla es del grafo,
        // no de ninguna de ellas. Va después del `NavHost` a propósito: el
        // último interesado en registrarse es el que atiende primero.
        //
        // La pantalla de juego no aparece: allí «atrás» abrirá el menú de
        // pausa (fase 2), que es lo que debe hacer.
        val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
        BackHandler(enabled = transitions.isRunning) {
            // Con el velo puesto no hay nada visible a lo que volver.
        }
        BackHandler(
            enabled = !transitions.isRunning &&
                currentRoute in setOf(Routes.SETTINGS, Routes.ABOUT, Routes.LAYOUT_EDITOR),
        ) {
            transitions.navigateWithVeil { navController.popBackStack() }
        }

        // Por encima del `NavHost`: es lo que permite que el desmontaje de la
        // pantalla de origen ocurra sin que se vea.
        if (transitions.isRunning) {
            LoadingVeil()
        }
    }
}
