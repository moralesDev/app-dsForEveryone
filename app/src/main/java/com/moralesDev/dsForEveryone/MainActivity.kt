package com.moralesDev.dsForEveryone

import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadInputRouter
import com.moralesDev.dsForEveryone.ui.App
import com.moralesDev.dsForEveryone.ui.game.GameScreen
import com.moralesDev.dsForEveryone.ui.game.gamepad.GamepadMappingScreen
import com.moralesDev.dsForEveryone.ui.library.RomLibraryScreen
import com.moralesDev.dsForEveryone.ui.navigation.PlatformScreens
import org.koin.android.ext.android.inject

/**
 * Única Activity de la aplicación. El manifiesto fija vertical por defecto;
 * `GameScreen` (fase 2) será la única pantalla que la libere en tiempo de
 * ejecución para poder rotar durante la partida. El resto de la app —
 * biblioteca, ajustes, bienvenida— no está pensada para horizontal.
 */
class MainActivity : ComponentActivity() {

    private val gamepadInputRouter: GamepadInputRouter by inject()

    /**
     * Punto más alto posible para interceptar un `KeyEvent` de mando, antes
     * de que llegue al foco de Compose. Ver `GamepadInputRouter.dispatch` —
     * fuera de la partida y de la pantalla de mapeo no consume nada, así que
     * la navegación normal con teclado/mando no cambia.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        gamepadInputRouter.dispatch(event) || super.dispatchKeyEvent(event)

    override fun onCreate(savedInstanceState: Bundle?) {
        // `enableEdgeToEdge()` sin argumentos elige iconos claros u oscuros
        // según el modo día/noche del sistema, pero `DsEmuTheme` sólo tiene
        // un esquema —oscuro— sin importar ese ajuste: con el sistema en
        // modo claro, la llamada por defecto pintaba iconos oscuros que se
        // volvían invisibles sobre el fondo, siempre oscuro, de la app.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            App(
                screens = PlatformScreens(
                    library = { onOpenSettings, onOpenGame ->
                        RomLibraryScreen(onOpenSettings = onOpenSettings, onOpen = { rom -> onOpenGame(rom.id) })
                    },
                    game = { onExit -> GameScreen(onExit = onExit) },
                    gamepadMapping = { onBack -> GamepadMappingScreen(onBack = onBack) },
                ),
            )
        }
    }
}
