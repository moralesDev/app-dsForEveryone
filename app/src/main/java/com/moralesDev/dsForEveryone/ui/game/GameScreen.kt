package com.moralesDev.dsForEveryone.ui.game

import android.content.res.Configuration
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
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
import com.moralesDev.dsForEveryone.emulator.layout.shiftedDown
import com.moralesDev.dsForEveryone.emulator.render.DsGLRenderer
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.game.controls.ActionButtons
import com.moralesDev.dsForEveryone.ui.game.controls.BlowButton
import com.moralesDev.dsForEveryone.ui.game.controls.DirectionalPad
import com.moralesDev.dsForEveryone.ui.game.controls.ShoulderButton
import com.moralesDev.dsForEveryone.ui.game.controls.SystemButton
import com.moralesDev.dsForEveryone.ui.resolve
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.Surface

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
 *
 * Sólo vertical por ahora: esta pantalla ya no libera la orientación (antes
 * lo hacía para `LayoutMode.SIDE_BY_SIDE`), porque encajar doce mandos
 * táctiles en horizontal sin tapar la táctil no compensa frente a lo simple
 * que es jugar en vertical. `landscapeLayoutMode`/`landscapeControlLayout` en
 * `EmulatorSettings` y la rama horizontal de `ControlLayout.defaultProfile`
 * se quedan sin usar pero no se borran: cuando haya mandos USB no hará falta
 * pintar ningún botón en pantalla, y ahí sí compensará jugar en horizontal.
 */
@Composable
fun GameScreen(onExit: () -> Unit) {
    val viewModel: GameViewModel = hiltViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val pressedMask by viewModel.pressedMask.collectAsStateWithLifecycle()
    val micActive by viewModel.micNoiseActive.collectAsStateWithLifecycle()
    val rom by viewModel.rom.collectAsStateWithLifecycle()
    val slotAction by viewModel.slotAction.collectAsStateWithLifecycle()
    val stateSlots by viewModel.stateSlots.collectAsStateWithLifecycle()
    val autoSlot by viewModel.autoSlot.collectAsStateWithLifecycle()
    val pendingConfirmation by viewModel.pendingConfirmation.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var isMenuOpen by remember { mutableStateOf(false) }

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

    val density = LocalDensity.current
    val minTouchTargetPx = with(density) { ControlLayout.MIN_TOUCH_TARGET_DP.dp.toPx() }

    // Ni la barra de estado ni la de navegación consumen touches para la
    // app, pero por motivos distintos hay que dejarles hueco: la de estado
    // taparía parte del dibujo de la pantalla superior si empezara en
    // y = 0, así que las dos pantallas se calculan en el hueco que queda
    // por debajo y se corren hacia abajo esa misma distancia. La de
    // navegación no tapa dibujo (las pantallas no llegan tan abajo), pero sí
    // haría inalcanzable cualquier mando que cayera ahí, así que a los
    // mandos —más abajo— se les da el hueco completo menos ese margen.
    val topInsetPx = WindowInsets.systemBars.getTop(density).toFloat()
    val bottomInsetPx = WindowInsets.systemBars.getBottom(density).toFloat()

    var availableSize by remember { mutableStateOf(LayoutSize(0f, 0f)) }
    val placement = remember(
        activeMode,
        availableSize,
        topInsetPx,
        settings.screenGap,
        settings.secondaryScale,
        settings.screensSwapped,
    ) {
        if (availableSize.width > 0f && availableSize.height > 0f) {
            val screensAvailable = LayoutSize(availableSize.width, (availableSize.height - topInsetPx).coerceAtLeast(0f))
            val raw = ScreenLayout.resolve(
                mode = activeMode,
                available = screensAvailable,
                gap = settings.screenGap,
                secondaryScale = settings.secondaryScale,
                swapped = settings.screensSwapped,
            )
            ScreenPlacement(
                topScreen = raw.topScreen.shiftedDown(topInsetPx),
                touchScreen = raw.touchScreen.shiftedDown(topInsetPx),
            )
        } else {
            null
        }
    }

    val controlAvailableSize = LayoutSize(availableSize.width, (availableSize.height - bottomInsetPx).coerceAtLeast(0f))

    val activeControlLayout = if (isPortrait) settings.portraitControlLayout else settings.landscapeControlLayout
    val controlProfile = remember(placement, controlAvailableSize, activeControlLayout, minTouchTargetPx) {
        val current = placement ?: return@remember null
        ControlLayout.resolveProfile(
            custom = activeControlLayout,
            available = controlAvailableSize,
            touchScreen = current.touchScreen,
            minTouchTarget = minTouchTargetPx,
            opacity = ControlLayout.DEFAULT_OPACITY,
        )
    }

    val renderer = remember { DsGLRenderer(frameProvider = { viewModel.currentFrame }) }
    LaunchedEffect(placement) { placement?.let(renderer::setPlacement) }
    LaunchedEffect(settings.imageFilter) { renderer.setFilter(settings.imageFilter) }

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
                onChooseSlot = viewModel::chooseSlot,
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

        slotAction?.let { action ->
            SaveStateSheet(
                action = action,
                gameTitle = rom?.displayName ?: stringResource(R.string.app_name),
                slots = stateSlots,
                autoSlot = autoSlot,
                pending = pendingConfirmation,
                onSelect = viewModel::onSlotChosen,
                onDelete = viewModel::requestSlotDeletion,
                onRestoreAutoBackup = viewModel::restoreAutoBackup,
                onConfirm = viewModel::acceptConfirmation,
                onCancelConfirmation = viewModel::cancelConfirmation,
                onDismiss = viewModel::dismissSlotPicker,
            )
        }

        message?.let { pending ->
            GameToast(text = pending.resolve(), onDismiss = viewModel::consumeMessage)
        }
    }
}

@Composable
private fun GameToast(text: String, onDismiss: () -> Unit) {
    LaunchedEffect(text) {
        kotlinx.coroutines.delay(2_600)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 22.dp, vertical = 92.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Surface)
                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
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

