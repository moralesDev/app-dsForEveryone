package com.moralesDev.dsForEveryone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import com.moralesDev.dsForEveryone.data.storage.StorageManager
import com.moralesDev.dsForEveryone.data.save.SaveArchive
import com.moralesDev.dsForEveryone.di.startKoinIos
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.ios_pending_body
import com.moralesDev.dsForEveryone.resources.ios_pending_settings
import com.moralesDev.dsForEveryone.resources.ios_pending_title
import com.moralesDev.dsForEveryone.ui.App
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.navigation.PlatformScreens
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import org.jetbrains.compose.resources.stringResource
import org.koin.mp.KoinPlatform
import platform.UIKit.UIViewController

/**
 * La app de iOS: lo mismo que pinta `MainActivity` en Android, en un
 * `UIViewController` que la app de Xcode (ios/app/) pone como raíz.
 *
 * Las pantallas que todavía no son comunes —biblioteca, juego, mapeo de
 * mandos— enseñan un aviso; desde el de la biblioteca se puede ir a
 * Ajustes, que ya funciona entera salvo los selectores de archivos.
 */
fun MainViewController(): UIViewController {
    startKoinIos()
    // Lo mismo que hace DsEmuApplication en Android antes de nada.
    KoinPlatform.getKoin().get<SaveArchive>().recoverInterrupted()
    return ComposeUIViewController {
        App(
            screens = PlatformScreens(
                library = { onOpenSettings, _ -> PendingOnIos(onOpenSettings) },
                game = { onExit -> PendingOnIos(onExit) },
                gamepadMapping = { onBack -> PendingOnIos(onBack) },
            ),
        )
    }
}

@Composable
private fun PendingOnIos(onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.ios_pending_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(Res.string.ios_pending_body),
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.6f),
            textAlign = TextAlign.Center,
        )
        AccentButton(text = stringResource(Res.string.ios_pending_settings), onClick = onAction)
    }
}
