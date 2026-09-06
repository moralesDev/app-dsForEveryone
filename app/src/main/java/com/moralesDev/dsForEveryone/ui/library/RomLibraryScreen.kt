package com.moralesDev.dsForEveryone.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.components.BrandHeader
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Ocupante provisional del destino de biblioteca. La biblioteca real —Room,
 * SAF, cabecera y banner de la ROM— llega en la fase 3; de momento sólo deja
 * ver que la navegación funciona y da acceso a Configuración.
 *
 * [onOpenGame] es igual de provisional: hasta que exista una biblioteca de
 * verdad (fase 3) es la única forma de llegar a la pantalla de juego, que en
 * la fase 2 monta el renderizador GL contra `StubEmulatorCore` (entregable
 * 2.5) en vez de abrir ninguna ROM real.
 */
@Composable
fun RomLibraryScreen(onOpenSettings: () -> Unit, onOpenGame: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        BrandHeader()
        Text(
            text = stringResource(R.string.scr_library_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.scr_library_body),
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.62f),
        )
        AccentButton(
            text = stringResource(R.string.nav_open_game_debug),
            onClick = onOpenGame,
        )
        AccentButton(
            text = stringResource(R.string.nav_open_settings),
            onClick = onOpenSettings,
        )
    }
}
