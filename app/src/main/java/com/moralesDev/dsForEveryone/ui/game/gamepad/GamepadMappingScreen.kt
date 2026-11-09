package com.moralesDev.dsForEveryone.ui.game.gamepad

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadTarget
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Divider
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import org.koin.compose.viewmodel.koinViewModel
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.cd_back
import com.moralesDev.dsForEveryone.resources.gp_cancel
import com.moralesDev.dsForEveryone.resources.gp_device_name
import com.moralesDev.dsForEveryone.resources.gp_leave_blank
import com.moralesDev.dsForEveryone.resources.gp_listening
import com.moralesDev.dsForEveryone.resources.gp_no_gamepad
import com.moralesDev.dsForEveryone.resources.gp_reset
import com.moralesDev.dsForEveryone.resources.gp_swap_hint
import com.moralesDev.dsForEveryone.resources.gp_target_a
import com.moralesDev.dsForEveryone.resources.gp_target_b
import com.moralesDev.dsForEveryone.resources.gp_target_blow
import com.moralesDev.dsForEveryone.resources.gp_target_down
import com.moralesDev.dsForEveryone.resources.gp_target_l
import com.moralesDev.dsForEveryone.resources.gp_target_left
import com.moralesDev.dsForEveryone.resources.gp_target_r
import com.moralesDev.dsForEveryone.resources.gp_target_right
import com.moralesDev.dsForEveryone.resources.gp_target_select
import com.moralesDev.dsForEveryone.resources.gp_target_start
import com.moralesDev.dsForEveryone.resources.gp_target_up
import com.moralesDev.dsForEveryone.resources.gp_target_x
import com.moralesDev.dsForEveryone.resources.gp_target_y
import com.moralesDev.dsForEveryone.resources.gp_title
import org.jetbrains.compose.resources.StringResource

/**
 * Mapeo de botones del primer mando conectado: una fila por cada uno de los
 * trece objetivos de [GamepadTarget.ALL] (los doce de [DsButton] más el
 * soplido), con el botón físico asignado hoy y un «Reasignar» que entra en
 * modo captura — ver `GamepadMappingViewModel.beginCapture` y
 * `GamepadInputRouter`.
 */
@Composable
fun GamepadMappingScreen(onBack: () -> Unit) {
    val viewModel: GamepadMappingViewModel = koinViewModel()
    val device by viewModel.connectedDevice.collectAsStateWithLifecycle()
    val mapping by viewModel.mapping.collectAsStateWithLifecycle()
    val capturingTarget by viewModel.capturingTarget.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize().background(Background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(Res.string.cd_back),
                    onClick = onBack,
                )
                Text(
                    text = stringResource(Res.string.gp_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            Text(
                text = device?.let { stringResource(Res.string.gp_device_name, it.name) }
                    ?: stringResource(Res.string.gp_no_gamepad),
                style = MaterialTheme.typography.bodyMedium,
                color = textAlpha(0.62f),
            )

            if (device != null) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(GamepadTarget.ALL) { target ->
                        GamepadTargetRow(
                            target = target,
                            keyCode = mapping.keyCodeFor(target),
                            isCapturing = capturingTarget == target,
                            onReassign = { viewModel.beginCapture(target) },
                        )
                    }
                }
                AccentButton(
                    text = stringResource(Res.string.gp_reset),
                    onClick = viewModel::resetToDefault,
                )
            }
        }

        if (capturingTarget != null) {
            CaptureOverlay(onCancel = viewModel::cancelCapture, onLeaveBlank = viewModel::leaveCapturingTargetBlank)
        }
    }
}

@Composable
private fun GamepadTargetRow(
    target: GamepadTarget,
    keyCode: Int?,
    isCapturing: Boolean,
    onReassign: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isCapturing) Accent.copy(alpha = 0.18f) else Surface)
            .clickable(onClick = onReassign)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(target.labelRes()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = keyCode?.let(::keyCodeLabel) ?: "—",
            style = MaterialTheme.typography.labelMedium,
            color = Accent,
        )
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Divider),
    )
}

@Composable
private fun CaptureOverlay(onCancel: () -> Unit, onLeaveBlank: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background.copy(alpha = 0.92f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.gp_listening),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            // Si el botón que se pulse ya está asignado a otro objetivo, los
            // dos se intercambian (GamepadMapping.with) — esta pista evita
            // que parezca que el otro se quedó sin botón por accidente.
            Text(
                text = stringResource(Res.string.gp_swap_hint),
                style = MaterialTheme.typography.bodySmall,
                color = textAlpha(0.6f),
            )
            Text(
                text = stringResource(Res.string.gp_leave_blank),
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLeaveBlank)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            Text(
                text = stringResource(Res.string.gp_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = textAlpha(0.6f),
            )
        }
    }
}

/** Nombre de un `keyCode` de Android sin el prefijo `KEYCODE_`: técnico a propósito, no se traduce. */
private fun keyCodeLabel(keyCode: Int): String =
    KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

private fun GamepadTarget.labelRes(): StringResource = when (this) {
    is GamepadTarget.Button -> when (button) {
        DsButton.UP -> Res.string.gp_target_up
        DsButton.DOWN -> Res.string.gp_target_down
        DsButton.LEFT -> Res.string.gp_target_left
        DsButton.RIGHT -> Res.string.gp_target_right
        DsButton.A -> Res.string.gp_target_a
        DsButton.B -> Res.string.gp_target_b
        DsButton.X -> Res.string.gp_target_x
        DsButton.Y -> Res.string.gp_target_y
        DsButton.L -> Res.string.gp_target_l
        DsButton.R -> Res.string.gp_target_r
        DsButton.START -> Res.string.gp_target_start
        DsButton.SELECT -> Res.string.gp_target_select
    }
    GamepadTarget.Blow -> Res.string.gp_target_blow
}
