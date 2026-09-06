package com.moralesDev.dsForEveryone.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroup
import com.moralesDev.dsForEveryone.emulator.layout.ControlLayout
import com.moralesDev.dsForEveryone.emulator.layout.ControlLayoutProfile
import com.moralesDev.dsForEveryone.emulator.layout.LayoutRect
import com.moralesDev.dsForEveryone.emulator.layout.LayoutSize
import com.moralesDev.dsForEveryone.emulator.layout.ScreenLayout
import com.moralesDev.dsForEveryone.emulator.layout.ScreenPlacement
import com.moralesDev.dsForEveryone.emulator.layout.TouchRouter
import com.moralesDev.dsForEveryone.emulator.render.DsGLRenderer
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.game.controls.ActionButtons
import com.moralesDev.dsForEveryone.ui.game.controls.BlowButton
import com.moralesDev.dsForEveryone.ui.game.controls.DirectionalPad
import com.moralesDev.dsForEveryone.ui.game.controls.ShoulderButton
import com.moralesDev.dsForEveryone.ui.game.controls.SystemButton
import com.moralesDev.dsForEveryone.ui.theme.Background

/**
 * Pantalla de juego (entregable 2.6): las dos pantallas con [DsGLRenderer],
 * la superficie táctil sobre la pantalla inferior y los siete grupos de
 * mandos colocados por [ControlLayout], todo contra `StubEmulatorCore`.
 *
 * La pantalla táctil es un puntero más, no un caso especial: no hay ninguna
 * lógica de exclusividad entre mandos y dedo, porque Compose ya entrega cada
 * puntero al nodo sobre el que bajó. Lo que se pinta pulsado sale siempre de
 * la máscara real de [com.moralesDev.dsForEveryone.emulator.InputManager],
 * nunca de estado local del composable.
 *
 * La ROM real, la biblioteca y el sistema de partidas llegan con la fase 3,
 * sobre esta misma pantalla.
 */
@Composable
fun GameScreen(onExit: () -> Unit) {
    val viewModel: GameViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pressedMask by viewModel.pressedMask.collectAsStateWithLifecycle()
    val micActive by viewModel.micNoiseActive.collectAsStateWithLifecycle()
    var isMenuOpen by remember { mutableStateOf(false) }

    FreeOrientation()

    BackHandler {
        if (isMenuOpen) {
            isMenuOpen = false
            viewModel.resume()
        } else {
            isMenuOpen = true
            viewModel.pause()
        }
    }

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

    val controlProfile = remember(placement, availableSize) {
        val current = placement ?: return@remember null
        val side = minOf(availableSize.width, availableSize.height) * CONTROL_GROUP_SIZE_FRACTION
        ControlLayout.defaultProfile(
            available = availableSize,
            touchScreen = current.touchScreen,
            groupSize = LayoutSize(side, side),
            opacity = CONTROL_OPACITY,
        )
    }

    val renderer = remember { DsGLRenderer(frameProvider = { viewModel.currentFrame }) }
    LaunchedEffect(placement) { placement?.let(renderer::setPlacement) }
    LaunchedEffect(settings.imageFilter) { renderer.setFilter(settings.imageFilter) }

    val density = LocalDensity.current
    val touchOffsetPx = with(density) { settings.touchOffsetDp.dp.toPx() }

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

        placement?.let { current ->
            TouchSurface(
                placement = current,
                touchMode = settings.touchMode,
                touchOffsetPx = touchOffsetPx,
                onTouch = viewModel::onTouch,
            )
        }

        controlProfile?.let { profile ->
            ControlGroups(
                profile = profile,
                pressedMask = pressedMask,
                micActive = micActive,
                onButton = viewModel::onButton,
                onDirections = viewModel::onDirections,
                onBlow = viewModel::onMicNoise,
                density = density,
            )
        }

        GlassIconButton(
            icon = Icons.Filled.Menu,
            contentDescription = stringResource(R.string.cd_menu),
            onClick = {
                isMenuOpen = true
                viewModel.pause()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(12.dp),
        )

        if (isMenuOpen) {
            PauseMenu(
                activeLayoutMode = activeMode,
                onLayoutModeChange = { mode -> viewModel.setLayoutMode(mode, isPortrait) },
                touchMode = settings.touchMode,
                onTouchModeChange = viewModel::setTouchMode,
                imageFilter = settings.imageFilter,
                onImageFilterChange = viewModel::setImageFilter,
                onResume = {
                    isMenuOpen = false
                    viewModel.resume()
                },
                onExit = {
                    viewModel.pause()
                    onExit()
                },
            )
        }
    }
}

/**
 * Superficie invisible exactamente sobre [ScreenPlacement.touchScreen].
 *
 * Traduce cada posición a coordenadas de la DS con [TouchRouter] y la escribe
 * en [com.moralesDev.dsForEveryone.emulator.InputManager] a través de
 * [onTouch]. En [TouchMode.OFFSET] el punto de contacto real se sitúa
 * [touchOffsetPx] por encima del dedo, para no taparlo con la yema.
 */
@Composable
private fun TouchSurface(
    placement: ScreenPlacement,
    touchMode: TouchMode,
    touchOffsetPx: Float,
    onTouch: (Int, Int, Boolean) -> Unit,
) {
    val screen = placement.touchScreen
    if (screen.width <= 0f || screen.height <= 0f) return

    val density = LocalDensity.current
    val (offsetX, offsetY, boxWidth, boxHeight) = with(density) {
        listOf(screen.left.toDp(), screen.top.toDp(), screen.width.toDp(), screen.height.toDp())
    }

    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .size(boxWidth, boxHeight)
            .pointerInput(placement, touchMode, touchOffsetPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    reportTouch(down.position, screen, touchMode, touchOffsetPx, placement, onTouch)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            reportTouch(change.position, screen, touchMode, touchOffsetPx, placement, onTouch)
                        }
                    } finally {
                        onTouch(0, 0, false)
                    }
                }
            },
    )
}

/**
 * [localPosition] está en coordenadas locales de la superficie táctil (0,0 en
 * su propia esquina superior izquierda); se traslada al hueco disponible
 * completo sumando [screen] antes de pasar por [TouchRouter], que espera esas
 * unidades. Un punto fuera de la pantalla táctil —posible al arrastrar el
 * dedo más allá del borde— da `null` y se reporta como dedo levantado.
 */
private fun reportTouch(
    localPosition: Offset,
    screen: LayoutRect,
    touchMode: TouchMode,
    touchOffsetPx: Float,
    placement: ScreenPlacement,
    onTouch: (Int, Int, Boolean) -> Unit,
) {
    val globalX = screen.left + localPosition.x
    val globalY = screen.top + localPosition.y - if (touchMode == TouchMode.OFFSET) touchOffsetPx else 0f

    val point = TouchRouter.map(globalX, globalY, placement)
    if (point != null) onTouch(point.x, point.y, true) else onTouch(0, 0, false)
}

@Composable
private fun ControlGroups(
    profile: ControlLayoutProfile,
    pressedMask: Int,
    micActive: Boolean,
    onButton: (DsButton, Boolean) -> Unit,
    onDirections: (Set<DsButton>) -> Unit,
    onBlow: (Boolean) -> Unit,
    density: Density,
) {
    profile.groups.forEach { (group, placement) ->
        val bounds = placement.bounds
        if (bounds.width <= 0f || bounds.height <= 0f) return@forEach

        val (offsetX, offsetY, boxWidth, boxHeight) = with(density) {
            listOf(bounds.left.toDp(), bounds.top.toDp(), bounds.width.toDp(), bounds.height.toDp())
        }
        val groupModifier = Modifier
            .offset(offsetX, offsetY)
            .size(boxWidth, boxHeight)

        when (group) {
            ControlGroup.DIRECTIONAL_PAD -> DirectionalPad(
                pressedMask = pressedMask,
                opacity = placement.opacity,
                haptic = true,
                onDirections = onDirections,
                modifier = groupModifier,
            )

            ControlGroup.ACTION_BUTTONS -> ActionButtons(
                pressedMask = pressedMask,
                opacity = placement.opacity,
                haptic = true,
                onPress = onButton,
                modifier = groupModifier,
            )

            ControlGroup.L -> ShoulderButton(
                button = DsButton.L,
                label = "L",
                pressedMask = pressedMask,
                opacity = placement.opacity,
                haptic = true,
                onPress = onButton,
                modifier = groupModifier,
            )

            ControlGroup.R -> ShoulderButton(
                button = DsButton.R,
                label = "R",
                pressedMask = pressedMask,
                opacity = placement.opacity,
                haptic = true,
                onPress = onButton,
                modifier = groupModifier,
            )

            // START/SELECT: literal, sin traducir. Son la leyenda física del
            // botón, igual que A/B/X/Y/L/R: ningún juego real las traduce.
            ControlGroup.START -> SystemButton(
                button = DsButton.START,
                label = "START",
                pressedMask = pressedMask,
                opacity = placement.opacity,
                haptic = true,
                onPress = onButton,
                modifier = groupModifier,
            )

            ControlGroup.SELECT -> SystemButton(
                button = DsButton.SELECT,
                label = "SELECT",
                pressedMask = pressedMask,
                opacity = placement.opacity,
                haptic = true,
                onPress = onButton,
                modifier = groupModifier,
            )

            ControlGroup.BLOW -> BlowButton(
                active = micActive,
                opacity = placement.opacity,
                haptic = true,
                contentDescription = stringResource(R.string.cd_blow),
                onBlow = onBlow,
                modifier = groupModifier,
            )
        }
    }
}

/**
 * Libera la orientación mientras esta pantalla esté visible; el resto de la
 * app sigue en vertical por manifiesto.
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

/** Lado de cada grupo de mandos, como fracción del lado menor del hueco disponible. */
private const val CONTROL_GROUP_SIZE_FRACTION = 0.20f
private const val CONTROL_OPACITY = 0.85f
