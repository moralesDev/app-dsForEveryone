package com.moralesDev.dsForEveryone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Ocupante provisional del destino de configuración. Los ajustes reales de la
 * DS (disposición de pantallas, filtro, modo de toque) llegan con la UI de
 * cada uno según avancen las fases; de momento sólo enlaza con «Acerca de» y
 * con el editor de disposición para que el grafo de navegación sea completo.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLayoutEditor: () -> Unit,
    onOpenGamepadMapping: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        GlassIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            onClick = onBack,
        )
        Text(
            text = stringResource(R.string.scr_settings_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.scr_settings_body),
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.62f),
        )
        AccentButton(
            text = stringResource(R.string.nav_open_layout_editor),
            onClick = onOpenLayoutEditor,
        )
        AccentButton(
            text = stringResource(R.string.nav_open_gamepad_mapping),
            onClick = onOpenGamepadMapping,
        )
        AccentButton(
            text = stringResource(R.string.nav_open_about),
            onClick = onOpenAbout,
        )
    }
}
