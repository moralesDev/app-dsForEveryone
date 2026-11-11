package com.moralesDev.dsForEveryone.ui.navigation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moralesDev.dsForEveryone.ui.about.AboutScreen
import com.moralesDev.dsForEveryone.ui.components.LoadingVeil
import com.moralesDev.dsForEveryone.ui.game.layout.LayoutEditorScreen
import com.moralesDev.dsForEveryone.ui.settings.SettingsScreen
import com.moralesDev.dsForEveryone.ui.splash.SplashScreen
import com.moralesDev.dsForEveryone.ui.welcome.WelcomeScreen

object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val LAYOUT_EDITOR = "layout_editor"
    const val GAMEPAD_MAPPING = "gamepad_mapping"

    private const val GAME_ROM_ID_ARG = "romId"
    const val GAME = "game?$GAME_ROM_ID_ARG={$GAME_ROM_ID_ARG}"

    /** [romId] es el SHA-256 de la ROM elegida en la biblioteca. */
    fun game(romId: String): String = "game?$GAME_ROM_ID_ARG=$romId"
}

/**
 * Las pantallas que todavía no son comunes (docs/ios.md, iOS-3 paso 4): las
 * pone cada plataforma. En Android, las de verdad; en iOS, mientras no
 * estén hechas, un aviso. Cuando pasen al módulo común, este hueco
 * desaparece y [AppNavHost] las llama directamente, como al resto.
 */
class PlatformScreens(
    val library: @Composable (onOpenSettings: () -> Unit, onOpenGame: (romId: String) -> Unit) -> Unit,
    val game: @Composable (onExit: () -> Unit) -> Unit,
    val gamepadMapping: @Composable (onBack: () -> Unit) -> Unit,
)

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
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppNavHost(
    screens: PlatformScreens,
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
                screens.library(
                    {
                        transitions.navigateWithVeil { navController.navigate(Routes.SETTINGS) }
                    },
                    // Sin velo: la pantalla de juego trae su propio «Cargando
                    // ROM» (ver GameViewModel).
                    { romId -> navController.navigate(Routes.game(romId)) },
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
                    onOpenGamepadMapping = {
                        transitions.navigateWithVeil { navController.navigate(Routes.GAMEPAD_MAPPING) }
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

            composable(Routes.GAMEPAD_MAPPING) {
                screens.gamepadMapping { transitions.navigateWithVeil { navController.popBackStack() } }
            }

            composable(
                Routes.GAME,
                arguments = listOf(navArgument("romId") { type = NavType.StringType; nullable = true }),
            ) {
                screens.game {
                    transitions.navigateWithVeil(ScreenTransitions.LEAVING_GAME_MS) {
                        navController.popBackStack(Routes.LIBRARY, inclusive = false)
                    }
                }
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
                currentRoute in setOf(Routes.SETTINGS, Routes.ABOUT, Routes.LAYOUT_EDITOR, Routes.GAMEPAD_MAPPING),
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
