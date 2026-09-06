package com.moralesDev.dsForEveryone.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.emulator.layout.LayoutSize
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode
import com.moralesDev.dsForEveryone.emulator.layout.ScreenLayout
import com.moralesDev.dsForEveryone.emulator.render.DsGLRenderer
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.components.GlassSwitch
import com.moralesDev.dsForEveryone.ui.components.SegmentedPair
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import kotlinx.coroutines.delay

/**
 * Pantalla de prueba del renderizador GL (entregable 2.5): monta
 * [DsGLRenderer] contra `StubEmulatorCore`, con un selector de disposición,
 * el intercambio de pantallas y el filtro de imagen, para poder comprobar en
 * el dispositivo que los dos rectángulos del stub se ven, que cambiar de
 * disposición no recrea nada y que rotar no deja nada pegado.
 *
 * La pantalla de juego de verdad —ROM real, mandos y táctil, menú de
 * pausa— es el entregable 2.6, que sustituye esta por completo.
 */
@Composable
fun GameScreen(onExit: () -> Unit) {
    val viewModel: GameViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val emulatedFps by viewModel.framesPerSecond.collectAsStateWithLifecycle()

    FreeOrientation()

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val activeMode = if (isPortrait) settings.portraitLayoutMode else settings.landscapeLayoutMode

    var availableSize by remember { mutableStateOf(LayoutSize(0f, 0f)) }
    val placement = remember(
        activeMode,
        availableSize,
        settings.screenGap,
        settings.secondaryScale,
        settings.screensSwapped,
    ) {
        if (availableSize.width > 0f && availableSize.height > 0f) {
            ScreenLayout.resolve(
                mode = activeMode,
                available = availableSize,
                gap = settings.screenGap,
                secondaryScale = settings.secondaryScale,
                swapped = settings.screensSwapped,
            )
        } else {
            null
        }
    }

    val renderer = remember { DsGLRenderer(frameProvider = { viewModel.currentFrame }) }
    var presentedFps by remember { mutableIntStateOf(0) }

    LaunchedEffect(renderer) {
        while (true) {
            delay(POLL_PRESENTED_FPS_MS)
            presentedFps = renderer.presentedFramesPerSecond()
        }
    }

    LaunchedEffect(placement) { placement?.let(renderer::setPlacement) }
    LaunchedEffect(settings.imageFilter) { renderer.setFilter(settings.imageFilter) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .onSizeChanged { size ->
                availableSize = LayoutSize(size.width.toFloat(), size.height.toFloat())
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                GLSurfaceView(context).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                }
            },
            onRelease = { it.onPause() },
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onExit,
            )
            Text(
                text = stringResource(R.string.dbg_fps, emulatedFps, presentedFps),
                style = MaterialTheme.typography.labelMedium,
                color = textAlpha(0.7f),
            )
            ModeSelector(
                selected = activeMode,
                onSelect = { viewModel.setLayoutMode(it, isPortrait) },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SegmentedPair(
                    first = stringResource(R.string.filter_sharp),
                    second = stringResource(R.string.filter_smooth),
                    firstSelected = settings.imageFilter == ImageFilter.SHARP,
                    onSelect = { sharp ->
                        viewModel.setImageFilter(if (sharp) ImageFilter.SHARP else ImageFilter.SMOOTH)
                    },
                )
                Text(
                    text = stringResource(R.string.dbg_swap),
                    style = MaterialTheme.typography.bodySmall,
                    color = textAlpha(0.7f),
                )
                GlassSwitch(
                    checked = settings.screensSwapped,
                    onCheckedChange = viewModel::setScreensSwapped,
                )
            }
        }
    }
}

@Composable
private fun ModeSelector(selected: LayoutMode, onSelect: (LayoutMode) -> Unit) {
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
                    .padding(horizontal = 8.dp, vertical = 5.dp),
            ) {
                Text(
                    text = stringResource(mode.labelRes()),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) Accent100 else textAlpha(0.6f),
                )
            }
        }
    }
}

private fun LayoutMode.labelRes(): Int = when (this) {
    LayoutMode.STACKED -> R.string.dbg_mode_stacked
    LayoutMode.SIDE_BY_SIDE -> R.string.dbg_mode_side_by_side
    LayoutMode.TOP_ONLY -> R.string.dbg_mode_top_only
    LayoutMode.BOTTOM_ONLY -> R.string.dbg_mode_bottom_only
    LayoutMode.MAIN_AND_SECONDARY -> R.string.dbg_mode_main_secondary
}

/**
 * Libera la orientación mientras esta pantalla esté visible; el resto de la
 * app sigue en vertical por manifiesto. Adelantado desde el entregable 2.6
 * —que la formaliza como pieza reutilizable— porque sin esto no hay forma de
 * comprobar en el dispositivo que rotar no deja nada pegado.
 */
@Composable
private fun FreeOrientation() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose { activity.requestedOrientation = previousOrientation }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No se encontró ninguna Activity en la cadena de contextos")
}

private const val POLL_PRESENTED_FPS_MS = 500L
