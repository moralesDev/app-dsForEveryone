package com.moralesDev.dsForEveryone.ui.about

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.ab_core_label
import com.moralesDev.dsForEveryone.resources.ab_core_link
import com.moralesDev.dsForEveryone.resources.ab_source_link
import com.moralesDev.dsForEveryone.resources.cd_back
import com.moralesDev.dsForEveryone.resources.scr_about_body
import com.moralesDev.dsForEveryone.resources.scr_about_title

/** Repositorio de melonDS, el core de emulación que empotra esta aplicación. */
private const val MELONDS_SOURCE_URL = "https://github.com/melonDS-emu/melonDS"

/**
 * Repositorio de esta aplicación. Al enlazar melonDS (GPL-3.0-or-later) todo
 * el APK —interfaz, sistema de partidas, puente incluidos— se distribuye bajo
 * la misma licencia; este enlace es cómo se cumple la obligación de entregar
 * el código fuente a quien recibe el binario (ver docs/licencia.md).
 */
private const val APP_SOURCE_URL = "https://github.com/moralesDev/app-dsForEveryone"

/**
 * Acerca de: versión, aviso del core y los dos enlaces de código fuente que
 * exige distribuir un APK con GPL-3.0 dentro (ver docs/licencia.md, «Lo que
 * hay que dejar hecho en el repositorio»). No es cortesía, es la obligación
 * de informar al usuario de sus derechos.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

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
            contentDescription = stringResource(Res.string.cd_back),
            onClick = onBack,
        )
        Text(
            text = stringResource(Res.string.scr_about_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.scr_about_body),
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.62f),
        )
        Text(
            text = stringResource(Res.string.ab_core_label),
            style = MaterialTheme.typography.labelMedium,
            color = textAlpha(0.45f),
        )
        AccentButton(
            text = stringResource(Res.string.ab_source_link),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, APP_SOURCE_URL.toUri())) },
        )
        AccentButton(
            text = stringResource(Res.string.ab_core_link),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, MELONDS_SOURCE_URL.toUri())) },
        )
    }
}
