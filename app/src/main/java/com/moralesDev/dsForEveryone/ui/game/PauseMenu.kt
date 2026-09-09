package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode
import com.moralesDev.dsForEveryone.ui.components.SectionLabel
import com.moralesDev.dsForEveryone.ui.components.SegmentedPair
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Danger
import com.moralesDev.dsForEveryone.ui.theme.Divider
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.ScrimColor
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Menú de pausa, superpuesto al juego.
 *
 * Mientras está abierto la emulación está detenida de verdad —quien lo
 * muestra pausa antes el controlador—, no simplemente tapada.
 *
 * Respecto a `app-emulador`: con dos filas propias de esta fase —disposición
 * de pantallas y modo de toque—, cambiables aquí mismo sin salir del juego.
 * `onCheats` abre [CheatsSheet] **encima** de este menú, no en su lugar —el
 * menú se queda «abierto» por debajo (mismo criterio que ya usan las ranuras
 * de guardado) para que, al cerrar Trucos, el menú de pausa reaparezca solo
 * en vez de dejar la pantalla sin ningún overlay.
 */
@Composable
fun PauseMenu(
    activeLayoutMode: LayoutMode,
    onLayoutModeChange: (LayoutMode) -> Unit,
    touchMode: TouchMode,
    onTouchModeChange: (TouchMode) -> Unit,
    imageFilter: ImageFilter,
    onImageFilterChange: (ImageFilter) -> Unit,
    onChooseSlot: (SlotAction) -> Unit,
    cheatsActive: Int,
    cheatsTotal: Int,
    onCheats: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ScrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onResume,
            )
            // Antes de acolchar visualmente: en horizontal, sin esto, la
            // tarjeta se mide contra el alto completo de la pantalla,
            // incluido lo que tapa la barra de navegación del sistema.
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        // En horizontal la tarjeta puede no caber entera: sin tope de altura
        // ni scroll, «Cargar partida» y «Salir» —al final de la columna—
        // quedaban fuera de la pantalla y no había forma de llegar a ellos.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .clip(RoundedCornerShape(18.dp))
                .background(Surface)
                // Absorbe el toque para que pulsar dentro de la tarjeta no cierre.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionLabel(stringResource(R.string.pa_title))

            ResumeButton(onResume, modifier = Modifier.padding(top = 10.dp))

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.pa_layout),
                    style = MaterialTheme.typography.labelMedium,
                    color = textAlpha(0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LayoutModeChips(selected = activeLayoutMode, onSelect = onLayoutModeChange)
            }

            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.pa_touch_mode),
                    style = MaterialTheme.typography.labelMedium,
                    color = textAlpha(0.55f),
                )
                SegmentedPair(
                    first = stringResource(R.string.touch_direct),
                    second = stringResource(R.string.touch_offset),
                    firstSelected = touchMode == TouchMode.DIRECT,
                    onSelect = { direct ->
                        onTouchModeChange(if (direct) TouchMode.DIRECT else TouchMode.OFFSET)
                    },
                )
            }

            Column(
                modifier = Modifier.padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.se_filter),
                    style = MaterialTheme.typography.labelMedium,
                    color = textAlpha(0.55f),
                )
                SegmentedPair(
                    first = stringResource(R.string.filter_sharp),
                    second = stringResource(R.string.filter_smooth),
                    firstSelected = imageFilter == ImageFilter.SHARP,
                    onSelect = { sharp ->
                        onImageFilterChange(if (sharp) ImageFilter.SHARP else ImageFilter.SMOOTH)
                    },
                )
            }

            Box(
                Modifier
                    .padding(horizontal = 4.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider),
            )

            MenuRow(
                label = stringResource(R.string.pa_save),
                icon = Icons.Filled.Save,
                onClick = { onChooseSlot(SlotAction.SAVE) },
            )
            MenuRow(
                label = stringResource(R.string.pa_load),
                icon = Icons.Filled.SettingsBackupRestore,
                onClick = { onChooseSlot(SlotAction.LOAD) },
            )
            MenuRow(
                label = stringResource(R.string.pa_cheats),
                icon = Icons.Filled.FlashOn,
                trailing = stringResource(R.string.pa_cheats_count, cheatsActive, cheatsTotal),
                onClick = onCheats,
            )

            Box(
                Modifier
                    .padding(horizontal = 4.dp, vertical = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider),
            )

            MenuRow(
                label = stringResource(R.string.pa_exit),
                icon = Icons.AutoMirrored.Filled.Logout,
                onClick = onExit,
                contentColor = Danger,
            )
        }
    }
}

@Composable
private fun ResumeButton(onResume: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(Accent.copy(alpha = 0.16f))
            .border(1.dp, Accent.copy(alpha = 0.55f), shape)
            .clickable(onClick = onResume)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Accent100,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.pa_resume),
            style = MaterialTheme.typography.titleSmall,
            color = Accent100,
        )
    }
}

@Composable
private fun MenuRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (contentColor == Danger) Danger else textAlpha(0.55f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = textAlpha(0.42f),
            )
        }
    }
}

/** `internal`: la reutiliza también el editor de disposición (fase 2.7), en `ui.game.layout`. */
@Composable
internal fun LayoutModeChips(selected: LayoutMode, onSelect: (LayoutMode) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LayoutMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Accent.copy(alpha = 0.30f) else Glass)
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Accent200 else textAlpha(0.6f),
                )
            }
        }
    }
}

internal fun LayoutMode.labelRes(): Int = when (this) {
    LayoutMode.STACKED -> R.string.layout_stacked
    LayoutMode.SIDE_BY_SIDE -> R.string.layout_side_by_side
    LayoutMode.TOP_ONLY -> R.string.layout_top_only
    LayoutMode.BOTTOM_ONLY -> R.string.layout_bottom_only
    LayoutMode.MAIN_AND_SECONDARY -> R.string.layout_main_secondary
}
