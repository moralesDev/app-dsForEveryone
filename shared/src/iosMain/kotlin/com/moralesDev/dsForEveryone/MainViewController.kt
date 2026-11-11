package com.moralesDev.dsForEveryone

import androidx.compose.ui.window.ComposeUIViewController
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.di.startKoinIos
import com.moralesDev.dsForEveryone.ui.App
import com.moralesDev.dsForEveryone.ui.game.GameScreen
import com.moralesDev.dsForEveryone.ui.game.gamepad.GamepadMappingScreen
import com.moralesDev.dsForEveryone.ui.library.RomLibraryScreen
import com.moralesDev.dsForEveryone.ui.navigation.PlatformScreens
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/**
 * La app de iOS: lo mismo que pinta `MainActivity` en Android, en un
 * `UIViewController` que la app de Xcode (ios/app/) pone como raíz.
 */
fun MainViewController(): UIViewController {
    startKoinIos()
    // Lo mismo que hace DsEmuApplication en Android antes de nada.
    KoinPlatform.getKoin().get<SaveArchive>().recoverInterrupted()
    return ComposeUIViewController {
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
