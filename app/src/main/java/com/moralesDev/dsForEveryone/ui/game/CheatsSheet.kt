package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.emulator.core.CheatEntry
import com.moralesDev.dsForEveryone.ui.components.GlassSwitch
import com.moralesDev.dsForEveryone.ui.components.GlassTextArea
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.Neutral800
import com.moralesDev.dsForEveryone.ui.theme.ScrimColor
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Hoja de claves, con el mismo esqueleto que [SaveStateSheet]: sube desde
 * abajo, sin `Dialog` ni `Popup`, y absorbe el toque dentro de la tarjeta para
 * que tocar fuera cierre en vez de calar.
 *
 * A diferencia de `app-emulador` (mGBA trae tres formatos ambiguos que hay
 * que resolver) aquí no hace falta preguntar nada al pegar un código: melonDS
 * sólo entiende Action Replay. El único dato que pide es el propio código y,
 * si se quiere, un nombre. El nombre en blanco no se manda vacío: quien llama
 * ya le puso un «Clave N» antes de persistirlo, así que aquí sólo hay que
 * mostrar [CheatEntry.label] tal cual.
 *
 * El aviso de respaldo no promete un efecto que no se puede ver: la hoja vive
 * dentro del menú de pausa, con la emulación detenida, y activar una clave no
 * pinta nada distinto hasta que se reanuda.
 */
@Composable
fun CheatsSheet(
    gameTitle: String,
    cheats: List<CheatEntry>,
    maxEntries: Int,
    hasAnySaveState: Boolean,
    onAdd: (code: String, label: String) -> Unit,
    onToggle: (id: String, enabled: Boolean) -> Unit,
    onDelete: (id: String) -> Unit,
    onBackupNow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isAdding by remember { mutableStateOf(false) }
    var draftCode by remember { mutableStateOf("") }
    var draftLabel by remember { mutableStateOf("") }

    fun closeForm() {
        isAdding = false
        draftCode = ""
        draftLabel = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            // Sube desde abajo: sin esto, en horizontal, el aviso de respaldo y
            // el formulario de añadir quedarían debajo de la barra de
            // navegación del sistema (mismo motivo que SaveStateSheet).
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 22.dp),
        ) {
            Box(
                Modifier
                    .padding(bottom = 14.dp)
                    .align(Alignment.CenterHorizontally)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Neutral800),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringResource(R.string.ch_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = textAlpha(0.45f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .clickable(onClick = onDismiss)
                        .padding(2.dp),
                )
            }
            Text(
                text = gameTitle,
                style = MaterialTheme.typography.bodySmall,
                color = textAlpha(0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            BackupNotice(
                hasAnySaveState = hasAnySaveState,
                onBackupNow = onBackupNow,
                modifier = Modifier.padding(top = 16.dp),
            )

            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (cheats.isEmpty() && !isAdding) {
                    Text(
                        text = stringResource(R.string.ch_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = textAlpha(0.42f),
                    )
                }
                cheats.forEach { entry ->
                    CheatRow(
                        entry = entry,
                        onToggle = { onToggle(entry.id, it) },
                        onDelete = { onDelete(entry.id) },
                    )
                }

                if (isAdding) {
                    // Se resuelve aquí, en composición, no dentro del callback:
                    // stringResource sólo se puede llamar en contexto @Composable,
                    // y el ViewModel no puede construir texto traducido por su
                    // cuenta (ver CLAUDE.md).
                    val fallbackLabel = stringResource(R.string.ch_name_fallback, cheats.size + 1)
                    AddCheatForm(
                        code = draftCode,
                        label = draftLabel,
                        onCodeChange = { draftCode = it },
                        onLabelChange = { draftLabel = it },
                        onCancel = ::closeForm,
                        onSubmit = {
                            onAdd(draftCode, draftLabel.ifBlank { fallbackLabel })
                            closeForm()
                        },
                    )
                } else if (cheats.size < maxEntries) {
                    AddCheatButton(onClick = { isAdding = true })
                } else {
                    Text(
                        text = stringResource(R.string.ch_limit_reached, maxEntries),
                        style = MaterialTheme.typography.labelMedium,
                        color = textAlpha(0.40f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupNotice(
    hasAnySaveState: Boolean,
    onBackupNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    if (!hasAnySaveState) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0x1FE0A030))
                .border(1.dp, Color(0x33E0A030), shape)
                .padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = Color(0xFFE0A030),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.ch_notice_full_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Text(
                text = stringResource(R.string.ch_notice_full_body),
                style = MaterialTheme.typography.bodySmall,
                color = textAlpha(0.60f),
                modifier = Modifier.padding(top = 4.dp),
            )
            BackupNowButton(onBackupNow, modifier = Modifier.padding(top = 10.dp))
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Glass)
                .border(1.dp, GlassBorder, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.ch_notice_compact),
                style = MaterialTheme.typography.labelMedium,
                color = textAlpha(0.55f),
                modifier = Modifier.weight(1f),
            )
            BackupNowButton(onBackupNow)
        }
    }
}

@Composable
private fun BackupNowButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(shape)
            .border(1.dp, Accent.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.ch_notice_backup_cta),
            style = MaterialTheme.typography.bodySmall,
            color = Accent200,
        )
    }
}

@Composable
private fun CheatRow(entry: CheatEntry, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Color(0x12E9E9ED), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = entry.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Filled.DeleteOutline,
            contentDescription = stringResource(R.string.cd_delete_cheat, entry.label),
            tint = textAlpha(0.38f),
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onDelete)
                .padding(8.dp),
        )
        GlassSwitch(checked = entry.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun AddCheatButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = textAlpha(0.65f),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.ch_add),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun AddCheatForm(
    code: String,
    label: String,
    onCodeChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassTextArea(
            value = code,
            onValueChange = onCodeChange,
            placeholder = stringResource(R.string.ch_code_placeholder),
            minLines = 3,
        )
        GlassTextArea(
            value = label,
            onValueChange = onLabelChange,
            placeholder = stringResource(R.string.ch_name_placeholder),
            singleLine = true,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FormButton(
                label = stringResource(R.string.ch_cancel),
                tint = MaterialTheme.colorScheme.onSurface,
                filled = false,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            FormButton(
                label = stringResource(R.string.ch_submit),
                tint = Accent200,
                filled = true,
                enabled = code.isNotBlank(),
                onClick = onSubmit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FormButton(
    label: String,
    tint: Color,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (filled) tint.copy(alpha = 0.16f) else Glass)
            .border(1.dp, if (filled) tint.copy(alpha = 0.55f) else GlassBorder, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (!enabled) textAlpha(0.30f) else if (filled) tint else MaterialTheme.colorScheme.onSurface,
        )
    }
}
