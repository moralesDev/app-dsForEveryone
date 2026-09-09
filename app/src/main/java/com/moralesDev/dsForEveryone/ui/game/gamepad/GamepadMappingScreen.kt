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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadTarget
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Divider
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Mapeo de botones del primer mando conectado: una fila por cada uno de los
 * trece objetivos de [GamepadTarget.ALL] (los doce de [DsButton] más el
 * soplido), con el botón físico asignado hoy y un «Reasignar» que entra en
 * modo captura — ver `GamepadMappingViewModel.beginCapture` y
 * `GamepadInputRouter`.
 */
@Composable
fun GamepadMappingScreen(onBack: () -> Unit) {
    val viewModel: GamepadMappingViewModel = hiltViewModel()
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
                    contentDescription = stringResource(R.string.cd_back),
                    onClick = onBack,
                )
                Text(
                    text = stringResource(R.string.gp_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }

            Text(
                text = device?.let { stringResource(R.string.gp_device_name, it.name) }
                    ?: stringResource(R.string.gp_no_gamepad),
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
                    text = stringResource(R.string.gp_reset),
                    onClick = viewModel::resetToDefault,
                )
            }
        }

        if (capturingTarget != null) {
            CaptureOverlay(onCancel = viewModel::cancelCapture)
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
private fun CaptureOverlay(onCancel: () -> Unit) {
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
                text = stringResource(R.string.gp_listening),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.gp_cancel),
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
            )
        }
    }
}

/** Nombre de un `keyCode` de Android sin el prefijo `KEYCODE_`: técnico a propósito, no se traduce. */
private fun keyCodeLabel(keyCode: Int): String =
    KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")

private fun GamepadTarget.labelRes(): Int = when (this) {
    is GamepadTarget.Button -> when (button) {
        DsButton.UP -> R.string.gp_target_up
        DsButton.DOWN -> R.string.gp_target_down
        DsButton.LEFT -> R.string.gp_target_left
        DsButton.RIGHT -> R.string.gp_target_right
        DsButton.A -> R.string.gp_target_a
        DsButton.B -> R.string.gp_target_b
        DsButton.X -> R.string.gp_target_x
        DsButton.Y -> R.string.gp_target_y
        DsButton.L -> R.string.gp_target_l
        DsButton.R -> R.string.gp_target_r
        DsButton.START -> R.string.gp_target_start
        DsButton.SELECT -> R.string.gp_target_select
    }
    GamepadTarget.Blow -> R.string.gp_target_blow
}
