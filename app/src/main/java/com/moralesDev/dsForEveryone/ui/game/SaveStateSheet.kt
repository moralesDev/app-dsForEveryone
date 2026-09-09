package com.moralesDev.dsForEveryone.ui.game

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.save.SaveStateManager
import com.moralesDev.dsForEveryone.data.save.SaveStateSlot
import com.moralesDev.dsForEveryone.data.save.StateRejection
import com.moralesDev.dsForEveryone.ui.components.SectionLabel
import com.moralesDev.dsForEveryone.ui.rememberSaveTimeFormatter
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Danger
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.Neutral700
import com.moralesDev.dsForEveryone.ui.theme.Neutral800
import com.moralesDev.dsForEveryone.ui.theme.ScrimColor
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hoja inferior de ranuras de estado.
 *
 * Las ranuras que se muestran son las de **esta** ROM: el `SaveStateManager`
 * sólo mira dentro de la carpeta que nombra su hash, así que no hay forma de
 * ver ni de pisar el estado de otro juego.
 *
 * Sube desde abajo en vez de aparecer en el centro porque es una lista que
 * puede crecer y porque el pulgar llega antes al borde inferior de la pantalla.
 *
 * Las dos acciones irreversibles —sobrescribir y borrar— piden confirmación, y
 * la piden aquí dentro, sustituyendo la lista. No se usa un diálogo del sistema:
 * una ventana emergente abre su propia raíz de composición, donde no llega el
 * idioma elegido en la aplicación.
 */
@Composable
fun SaveStateSheet(
    action: SlotAction,
    gameTitle: String,
    slots: List<SaveStateSlot>,
    autoSlot: SaveStateSlot?,
    pending: SlotConfirmation?,
    onSelect: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onRestoreAutoBackup: () -> Unit,
    onConfirm: () -> Unit,
    onCancelConfirmation: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ScrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // Con una confirmación en pantalla, tocar fuera la cancela en
                // lugar de cerrarlo todo: nunca cierra ejecutando la acción.
                onClick = { if (pending != null) onCancelConfirmation() else onDismiss() },
            )
            // La hoja sube desde abajo: sin esto, en horizontal, «cerrar» y el
            // botón de confirmar de ConfirmationPanel quedaban debajo de la
            // barra de navegación del sistema, sin forma de tocarlos.
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

            if (pending != null) {
                ConfirmationPanel(pending, onConfirm, onCancelConfirmation)
                return@Column
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = stringResource(
                        if (action == SlotAction.SAVE) R.string.st_save_title else R.string.st_load_title,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = textAlpha(0.45f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onDismiss),
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

            SectionLabel(
                text = stringResource(R.string.st_manual_section),
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                slots.forEach { slot ->
                    SlotRow(
                        slot = slot,
                        action = action,
                        onClick = { onSelect(slot.index) },
                        onDelete = { onDelete(slot.index) },
                    )
                }
            }

            // El respaldo va debajo y separado, no como una sexta ranura: no se
            // puede guardar en él y confundirlo con las del usuario invitaría a
            // intentarlo.
            SectionLabel(
                text = stringResource(R.string.st_auto_section),
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
            )
            AutoBackupRow(
                slot = autoSlot,
                onRestore = onRestoreAutoBackup,
                onDelete = { onDelete(SaveStateManager.AUTO_SLOT) },
            )
        }
    }
}

/**
 * El respaldo automático.
 *
 * Sólo ofrece restaurar y borrar. No hay «Guardar» porque esa ranura la escribe
 * el sistema: dejar el botón y desactivarlo sugeriría que en algún caso se
 * puede, y no lo hay.
 */
@Composable
private fun AutoBackupRow(
    slot: SaveStateSlot?,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatSavedAt = rememberSaveTimeFormatter()
    val shape = RoundedCornerShape(14.dp)
    val empty = slot == null || slot.isEmpty
    val restorable = slot?.isRestorable == true

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (empty) Color(0x08E9E9ED) else Surface)
            .border(1.dp, if (empty) Color(0x17E9E9ED) else Color(0x12E9E9ED), shape)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SlotThumbnail(slot ?: EMPTY_AUTO_SLOT)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.st_auto_last),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val problem = slot?.problem
            val savedAt = slot?.savedAt
            when {
                problem != null -> ProblemLabel(problem, savedAt)

                savedAt == null -> Text(
                    text = stringResource(R.string.st_auto_never),
                    style = MaterialTheme.typography.labelMedium,
                    color = textAlpha(0.34f),
                    modifier = Modifier.padding(top = 2.dp),
                )

                else -> Text(
                    text = formatSavedAt(savedAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = textAlpha(0.48f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        if (!empty) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.cd_delete_auto),
                tint = textAlpha(0.38f),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onDelete)
                    .padding(8.dp),
            )
        }

        SlotPillButton(
            label = stringResource(R.string.st_auto_restore),
            enabled = restorable,
            accent = false,
            onClick = onRestore,
        )
    }
}

/** Ranura vacía de mentira, sólo para que la miniatura pinte su marcador. */
private val EMPTY_AUTO_SLOT = SaveStateSlot(
    index = SaveStateManager.AUTO_SLOT,
    savedAt = null,
    sizeBytes = 0,
    thumbnailPath = null,
    core = null,
    problem = null,
)

/**
 * Sustituye la lista mientras se pregunta. Ocupar el mismo sitio, y no
 * superponerse, deja claro que la elección anterior está en suspenso.
 */
@Composable
private fun ConfirmationPanel(
    pending: SlotConfirmation,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val destructive = pending is SlotConfirmation.Delete
    val title = when (pending) {
        is SlotConfirmation.Overwrite -> stringResource(R.string.st_overwrite_title, pending.slot)
        is SlotConfirmation.Delete -> stringResource(R.string.st_delete_title, pending.slot)
    }
    val body = stringResource(
        if (destructive) R.string.st_delete_body else R.string.st_overwrite_body,
    )

    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.60f),
            modifier = Modifier.padding(top = 6.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SheetButton(
                label = stringResource(R.string.st_cancel),
                tint = MaterialTheme.colorScheme.onSurface,
                filled = false,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            SheetButton(
                label = stringResource(
                    if (destructive) R.string.st_delete else R.string.st_replace,
                ),
                tint = if (destructive) Danger else Accent200,
                filled = true,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SheetButton(
    label: String,
    tint: Color,
    filled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(if (filled) tint.copy(alpha = 0.16f) else Glass)
            .border(1.dp, if (filled) tint.copy(alpha = 0.55f) else GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (filled) tint else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SlotRow(
    slot: SaveStateSlot,
    action: SlotAction,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    // Cargar sólo tiene sentido si hay algo restaurable; guardar, siempre.
    val enabled = if (action == SlotAction.SAVE) true else slot.isRestorable
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (slot.isEmpty) Color(0x08E9E9ED) else Surface)
            .border(1.dp, if (slot.isEmpty) Color(0x17E9E9ED) else Color(0x12E9E9ED), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SlotThumbnail(slot)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.st_slot, slot.index),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            SlotSubtitle(slot)
        }

        // Sólo las ranuras ocupadas se pueden vaciar, y también las averiadas:
        // es la única forma de recuperar una ranura que ya no sirve.
        if (!slot.isEmpty) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.cd_delete_state, slot.index),
                tint = textAlpha(0.38f),
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .clickable(onClick = onDelete)
                    .padding(8.dp),
            )
        }

        SlotActionButton(slot, action, enabled, onClick)
    }
}

@Composable
private fun SlotSubtitle(slot: SaveStateSlot) {
    val formatSavedAt = rememberSaveTimeFormatter()
    val problem = slot.problem
    when {
        slot.isEmpty -> Text(
            text = stringResource(R.string.st_empty),
            style = MaterialTheme.typography.labelMedium,
            color = textAlpha(0.34f),
            modifier = Modifier.padding(top = 2.dp),
        )

        problem != null -> ProblemLabel(problem, savedAt = slot.savedAt)

        else -> Text(
            text = formatSavedAt(slot.savedAt!!),
            style = MaterialTheme.typography.labelMedium,
            color = textAlpha(0.48f),
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/**
 * Por qué una ranura no sirve, y cuándo se creó si aún se puede saber.
 *
 * La fecha se saca de la cabecera, que se lee aunque el contenido esté dañado.
 * Cuando está disponible se muestra completa —fecha y hora— por la misma razón
 * que en las demás: una hora suelta no distingue un estado de otro.
 */
@Composable
private fun ProblemLabel(problem: StateRejection, savedAt: Long? = null) {
    val formatSavedAt = rememberSaveTimeFormatter()
    Column(modifier = Modifier.padding(top = 3.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = Danger,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = stringResource(problem.shortLabel),
                style = MaterialTheme.typography.labelMedium,
                color = Danger,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (savedAt != null) {
            Text(
                text = formatSavedAt(savedAt),
                style = MaterialTheme.typography.labelMedium,
                color = textAlpha(0.34f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SlotActionButton(
    slot: SaveStateSlot,
    action: SlotAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val label = when {
        action == SlotAction.LOAD -> stringResource(R.string.st_load)
        slot.isEmpty -> stringResource(R.string.st_save)
        else -> stringResource(R.string.st_overwrite)
    }
    // La ranura vacía en modo guardar es la única acción de acento de la hoja:
    // es lo que el usuario busca cuando abre esta pantalla.
    val isAccent = action == SlotAction.SAVE && slot.isEmpty

    SlotPillButton(label = label, enabled = enabled, accent = isAccent, onClick = onClick)
}

@Composable
private fun SlotPillButton(
    label: String,
    enabled: Boolean,
    accent: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(if (accent) Color.Transparent else Glass)
            .border(
                width = 1.dp,
                color = if (accent) Accent.copy(alpha = 0.55f) else GlassBorder,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = when {
                !enabled -> textAlpha(0.30f)
                accent -> Accent200
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun SlotThumbnail(slot: SaveStateSlot) {
    val path = slot.thumbnailPath
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .width(58.dp)
            .height(39.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF0B0D16)),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                imageVector = if (slot.isEmpty) Icons.Filled.Add else Icons.Filled.Image,
                contentDescription = null,
                tint = Neutral700,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
