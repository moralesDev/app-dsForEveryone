package com.moralesDev.dsForEveryone.ui.game.layout

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
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Ocupante provisional del destino de editor de disposición. El editor real
 * —arrastrar las dos pantallas y los siete grupos de mandos sobre
 * `LayoutEngine`— es el entregable 2.7, y sólo se abrirá con la emulación
 * detenida.
 */
@Composable
fun LayoutEditorScreen(onBack: () -> Unit) {
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
            text = stringResource(R.string.scr_layout_editor_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.scr_layout_editor_body),
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.62f),
        )
    }
}
