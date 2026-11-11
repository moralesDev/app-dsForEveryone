package com.moralesDev.dsForEveryone.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.data.settings.AppLanguage
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.data.settings.DirectionalControl
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.components.GlassSwitch
import com.moralesDev.dsForEveryone.ui.components.SectionLabel
import com.moralesDev.dsForEveryone.ui.components.SegmentedPair
import com.moralesDev.dsForEveryone.ui.components.SettingsDivider
import com.moralesDev.dsForEveryone.ui.components.SettingsGroup
import com.moralesDev.dsForEveryone.ui.components.SettingsRow
import com.moralesDev.dsForEveryone.ui.archive.PendingArchiveImport
import com.moralesDev.dsForEveryone.ui.resolve
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Danger
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import com.moralesDev.dsForEveryone.ui.files.rememberCreateDocument
import com.moralesDev.dsForEveryone.ui.files.rememberOpenDocument
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock
import org.koin.compose.viewmodel.koinViewModel
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.cd_back
import com.moralesDev.dsForEveryone.resources.directional_dpad
import com.moralesDev.dsForEveryone.resources.directional_joystick
import com.moralesDev.dsForEveryone.resources.filter_sharp
import com.moralesDev.dsForEveryone.resources.filter_smooth
import com.moralesDev.dsForEveryone.resources.nav_open_about
import com.moralesDev.dsForEveryone.resources.pa_touch_mode
import com.moralesDev.dsForEveryone.resources.scr_settings_title
import com.moralesDev.dsForEveryone.resources.se_about_hint
import com.moralesDev.dsForEveryone.resources.se_archive_export
import com.moralesDev.dsForEveryone.resources.se_archive_export_hint
import com.moralesDev.dsForEveryone.resources.se_archive_import
import com.moralesDev.dsForEveryone.resources.se_archive_import_hint
import com.moralesDev.dsForEveryone.resources.se_archive_replace
import com.moralesDev.dsForEveryone.resources.se_archive_replace_body
import com.moralesDev.dsForEveryone.resources.se_archive_replace_title
import com.moralesDev.dsForEveryone.resources.se_directional
import com.moralesDev.dsForEveryone.resources.se_directional_hint
import com.moralesDev.dsForEveryone.resources.se_filter
import com.moralesDev.dsForEveryone.resources.se_filter_hint
import com.moralesDev.dsForEveryone.resources.se_gamepad
import com.moralesDev.dsForEveryone.resources.se_gamepad_hint
import com.moralesDev.dsForEveryone.resources.se_language
import com.moralesDev.dsForEveryone.resources.se_language_en
import com.moralesDev.dsForEveryone.resources.se_language_es
import com.moralesDev.dsForEveryone.resources.se_language_hint
import com.moralesDev.dsForEveryone.resources.se_screens
import com.moralesDev.dsForEveryone.resources.se_screens_hint
import com.moralesDev.dsForEveryone.resources.se_section_about
import com.moralesDev.dsForEveryone.resources.se_section_app
import com.moralesDev.dsForEveryone.resources.se_section_image
import com.moralesDev.dsForEveryone.resources.se_section_input
import com.moralesDev.dsForEveryone.resources.se_section_saves
import com.moralesDev.dsForEveryone.resources.se_touch_distance
import com.moralesDev.dsForEveryone.resources.se_touch_distance_hint
import com.moralesDev.dsForEveryone.resources.se_touch_hint
import com.moralesDev.dsForEveryone.resources.se_touch_long
import com.moralesDev.dsForEveryone.resources.se_touch_medium
import com.moralesDev.dsForEveryone.resources.se_touch_short
import com.moralesDev.dsForEveryone.resources.se_welcome
import com.moralesDev.dsForEveryone.resources.se_welcome_hint
import com.moralesDev.dsForEveryone.resources.st_cancel
import com.moralesDev.dsForEveryone.resources.touch_direct
import com.moralesDev.dsForEveryone.resources.touch_offset

/**
 * Los ajustes de la aplicación: lo que no depende de la partida que se esté
 * jugando.
 *
 * La regla que decide qué entra aquí y qué no es la de siempre —**no hay
 * ajustes decorativos**— más una segunda: lo que se cambia *mientras se juega*
 * vive en el menú de pausa, no aquí. Por eso la velocidad de emulación, las
 * ranuras y los trucos no aparecen: se tocan con la partida delante. El filtro
 * y el modo de toque sí están en los dos sitios a propósito, porque son el
 * mismo valor —cambiarlo aquí cambia el del juego abierto— y hay quien los
 * quiere dejar puestos antes de empezar.
 *
 * Las tres filas que navegan (disposición, mando, acerca de) llevan a
 * pantallas que ya existían; lo que cambia es que ahora se llega a ellas desde
 * una lista de ajustes y no desde tres botones sueltos en el centro.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenLayoutEditor: () -> Unit,
    onOpenGamepadMapping: () -> Unit,
) {
    val viewModel: SettingsViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val archiveMessage by viewModel.message.collectAsStateWithLifecycle()
    val pendingArchiveImport by viewModel.pendingArchiveImport.collectAsStateWithLifecycle()
    val archiveBusy by viewModel.archiveBusy.collectAsStateWithLifecycle()

    val archiveExportPicker = rememberCreateDocument(
        mimeType = "application/zip",
        onResult = viewModel::exportAllSaves,
    )
    val archiveImportPicker = rememberOpenDocument(onResult = viewModel::onArchiveImportPicked)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(Res.string.scr_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // ------------------------------------------------ Aplicación

        SectionLabel(
            text = stringResource(Res.string.se_section_app),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(Res.string.se_language),
                description = stringResource(Res.string.se_language_hint),
            ) {
                SegmentedPair(
                    first = stringResource(Res.string.se_language_es),
                    second = stringResource(Res.string.se_language_en),
                    firstSelected = settings.language == AppLanguage.SPANISH,
                    onSelect = { spanish ->
                        viewModel.setLanguage(
                            if (spanish) AppLanguage.SPANISH else AppLanguage.ENGLISH,
                        )
                    },
                )
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.se_welcome),
                description = stringResource(Res.string.se_welcome_hint),
            ) {
                GlassSwitch(
                    checked = !settings.skipWelcome,
                    onCheckedChange = viewModel::setShowWelcome,
                )
            }
        }

        // ------------------------------------------------ Imagen

        SectionLabel(
            text = stringResource(Res.string.se_section_image),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(Res.string.se_filter),
                description = stringResource(Res.string.se_filter_hint),
            ) {
                SegmentedPair(
                    first = stringResource(Res.string.filter_sharp),
                    second = stringResource(Res.string.filter_smooth),
                    firstSelected = settings.imageFilter == ImageFilter.SHARP,
                    onSelect = { sharp ->
                        viewModel.setImageFilter(if (sharp) ImageFilter.SHARP else ImageFilter.SMOOTH)
                    },
                )
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.se_screens),
                description = stringResource(Res.string.se_screens_hint),
                onClick = onOpenLayoutEditor,
            ) {
                NavigationChevron()
            }
        }

        // ------------------------------------------------ Entrada

        SectionLabel(
            text = stringResource(Res.string.se_section_input),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(Res.string.se_directional),
                description = stringResource(Res.string.se_directional_hint),
            ) {
                SegmentedPair(
                    first = stringResource(Res.string.directional_joystick),
                    second = stringResource(Res.string.directional_dpad),
                    firstSelected = settings.directionalControl == DirectionalControl.JOYSTICK,
                    onSelect = { joystick ->
                        viewModel.setDirectionalControl(
                            if (joystick) DirectionalControl.JOYSTICK else DirectionalControl.DPAD,
                        )
                    },
                )
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.pa_touch_mode),
                description = stringResource(Res.string.se_touch_hint),
            ) {
                SegmentedPair(
                    first = stringResource(Res.string.touch_direct),
                    second = stringResource(Res.string.touch_offset),
                    firstSelected = settings.touchMode == TouchMode.DIRECT,
                    onSelect = { direct ->
                        viewModel.setTouchMode(if (direct) TouchMode.DIRECT else TouchMode.OFFSET)
                    },
                )
            }
            // Sólo hay algo que graduar si el punto de contacto está
            // desplazado: en modo Directo esta fila no ajustaría nada, y un
            // ajuste que no hace nada no se enseña.
            if (settings.touchMode == TouchMode.OFFSET) {
                SettingsDivider()
                SettingsRow(
                    label = stringResource(Res.string.se_touch_distance),
                    description = stringResource(Res.string.se_touch_distance_hint),
                ) {
                    OffsetChips(
                        selected = settings.touchOffsetDp,
                        onSelect = viewModel::setTouchOffsetDp,
                    )
                }
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(Res.string.se_gamepad),
                description = stringResource(Res.string.se_gamepad_hint),
                onClick = onOpenGamepadMapping,
            ) {
                NavigationChevron()
            }
        }

        // ------------------------------------------------ Partidas

        SectionLabel(
            text = stringResource(Res.string.se_section_saves),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            val pending = pendingArchiveImport
            if (pending != null) {
                // La confirmación ocupa el sitio de las dos filas, dentro de la
                // propia pantalla: sin diálogos, que abrirían otra raíz de
                // composición adonde no llega el idioma elegido.
                ArchiveImportConfirmation(
                    pending = pending,
                    onConfirm = viewModel::confirmArchiveImport,
                    onCancel = viewModel::cancelArchiveImport,
                )
            } else {
                SettingsRow(
                    label = stringResource(Res.string.se_archive_export),
                    description = stringResource(Res.string.se_archive_export_hint),
                    onClick = {
                        if (!archiveBusy) archiveExportPicker(suggestedBackupFileName())
                    },
                ) {
                    NavigationChevron()
                }
                SettingsDivider()
                SettingsRow(
                    label = stringResource(Res.string.se_archive_import),
                    description = stringResource(Res.string.se_archive_import_hint),
                    // Cualquier tipo: los proveedores no siempre etiquetan un .zip como tal.
                    onClick = { if (!archiveBusy) archiveImportPicker() },
                ) {
                    NavigationChevron()
                }
            }
        }
        archiveMessage?.let { message ->
            Text(
                text = message.resolve(),
                style = MaterialTheme.typography.bodySmall,
                color = Accent200,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // ------------------------------------------------ Acerca de

        SectionLabel(
            text = stringResource(Res.string.se_section_about),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(Res.string.nav_open_about),
                description = stringResource(Res.string.se_about_hint),
                onClick = onOpenAbout,
            ) {
                NavigationChevron()
            }
        }
    }
}

/**
 * Las tres distancias del lápiz desplazado, en dp. Tres valores y no un
 * deslizador: lo que se busca es «que mi dedo no tape el punto», y para eso
 * basta con corto, medio y largo. Un deslizador pediría precisión que nadie
 * tiene sobre su propia yema.
 */
private val OFFSET_CHOICES = listOf(16f, 24f, 32f)

@Composable
private fun OffsetChips(selected: Float, onSelect: (Float) -> Unit) {
    // El valor guardado puede no ser ninguno de los tres (viene de DataStore,
    // y antes de esta pantalla nadie lo escribía): se marca el más cercano en
    // vez de dejar la fila sin ninguno seleccionado.
    val closest = OFFSET_CHOICES.minByOrNull { kotlin.math.abs(it - selected) }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        OFFSET_CHOICES.forEachIndexed { index, value ->
            val isSelected = value == closest
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Accent.copy(alpha = 0.30f) else Glass)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                Text(
                    text = stringResource(OFFSET_LABELS[index]),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Accent200 else textAlpha(0.6f),
                )
            }
        }
    }
}

private val OFFSET_LABELS = listOf(
    Res.string.se_touch_short,
    Res.string.se_touch_medium,
    Res.string.se_touch_long,
)

@Composable
private fun ArchiveImportConfirmation(
    pending: PendingArchiveImport,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 13.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.se_archive_replace_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(
                Res.string.se_archive_replace_body,
                pending.gameCount,
                pending.replacedCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = textAlpha(0.58f),
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConfirmationChip(
                text = stringResource(Res.string.se_archive_replace),
                color = Danger,
                onClick = onConfirm,
            )
            ConfirmationChip(
                text = stringResource(Res.string.st_cancel),
                color = MaterialTheme.colorScheme.onSurface,
                onClick = onCancel,
            )
        }
    }
}

@Composable
private fun ConfirmationChip(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Glass)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

/** `dsforeveryone-saves-AAAA-MM-DD.zip`: la fecha distingue una copia de la siguiente. */
private fun suggestedBackupFileName(): String =
    "dsforeveryone-saves-${Clock.System.todayIn(TimeZone.currentSystemDefault())}.zip"

/** La punta de flecha de las filas que llevan a otra pantalla. */
@Composable
private fun NavigationChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = textAlpha(0.35f),
        modifier = Modifier.padding(end = 2.dp),
    )
}
