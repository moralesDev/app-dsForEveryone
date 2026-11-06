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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.R
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
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

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
    val viewModel: SettingsViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

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
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.scr_settings_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 12.dp),
            )
        }

        // ------------------------------------------------ Aplicación

        SectionLabel(
            text = stringResource(R.string.se_section_app),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(R.string.se_language),
                description = stringResource(R.string.se_language_hint),
            ) {
                SegmentedPair(
                    first = stringResource(R.string.se_language_es),
                    second = stringResource(R.string.se_language_en),
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
                label = stringResource(R.string.se_welcome),
                description = stringResource(R.string.se_welcome_hint),
            ) {
                GlassSwitch(
                    checked = !settings.skipWelcome,
                    onCheckedChange = viewModel::setShowWelcome,
                )
            }
        }

        // ------------------------------------------------ Imagen

        SectionLabel(
            text = stringResource(R.string.se_section_image),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(R.string.se_filter),
                description = stringResource(R.string.se_filter_hint),
            ) {
                SegmentedPair(
                    first = stringResource(R.string.filter_sharp),
                    second = stringResource(R.string.filter_smooth),
                    firstSelected = settings.imageFilter == ImageFilter.SHARP,
                    onSelect = { sharp ->
                        viewModel.setImageFilter(if (sharp) ImageFilter.SHARP else ImageFilter.SMOOTH)
                    },
                )
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.se_screens),
                description = stringResource(R.string.se_screens_hint),
                onClick = onOpenLayoutEditor,
            ) {
                NavigationChevron()
            }
        }

        // ------------------------------------------------ Entrada

        SectionLabel(
            text = stringResource(R.string.se_section_input),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(R.string.se_directional),
                description = stringResource(R.string.se_directional_hint),
            ) {
                SegmentedPair(
                    first = stringResource(R.string.directional_joystick),
                    second = stringResource(R.string.directional_dpad),
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
                label = stringResource(R.string.pa_touch_mode),
                description = stringResource(R.string.se_touch_hint),
            ) {
                SegmentedPair(
                    first = stringResource(R.string.touch_direct),
                    second = stringResource(R.string.touch_offset),
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
                    label = stringResource(R.string.se_touch_distance),
                    description = stringResource(R.string.se_touch_distance_hint),
                ) {
                    OffsetChips(
                        selected = settings.touchOffsetDp,
                        onSelect = viewModel::setTouchOffsetDp,
                    )
                }
            }
            SettingsDivider()
            SettingsRow(
                label = stringResource(R.string.se_gamepad),
                description = stringResource(R.string.se_gamepad_hint),
                onClick = onOpenGamepadMapping,
            ) {
                NavigationChevron()
            }
        }

        // ------------------------------------------------ Acerca de

        SectionLabel(
            text = stringResource(R.string.se_section_about),
            modifier = Modifier.padding(top = 8.dp),
        )
        SettingsGroup {
            SettingsRow(
                label = stringResource(R.string.nav_open_about),
                description = stringResource(R.string.se_about_hint),
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
    R.string.se_touch_short,
    R.string.se_touch_medium,
    R.string.se_touch_long,
)

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
