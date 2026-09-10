package com.moralesDev.dsForEveryone.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.cheats.CheatsManager
import com.moralesDev.dsForEveryone.data.settings.EmulationSpeed
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroup
import com.moralesDev.dsForEveryone.emulator.layout.ControlLayout
import com.moralesDev.dsForEveryone.emulator.layout.ControlLayoutProfile
import com.moralesDev.dsForEveryone.emulator.layout.LayoutInsets
import com.moralesDev.dsForEveryone.emulator.layout.LayoutRect
import com.moralesDev.dsForEveryone.emulator.layout.LayoutSize
import com.moralesDev.dsForEveryone.emulator.layout.ScreenLayout
import com.moralesDev.dsForEveryone.emulator.layout.ScreenPlacement
import com.moralesDev.dsForEveryone.emulator.layout.TouchRouter
import com.moralesDev.dsForEveryone.emulator.layout.inset
import com.moralesDev.dsForEveryone.emulator.render.DsGLRenderer
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.game.controls.ActionButtons
import com.moralesDev.dsForEveryone.ui.game.controls.BlowButton
import com.moralesDev.dsForEveryone.ui.game.controls.DirectionalPad
import com.moralesDev.dsForEveryone.ui.game.controls.ShoulderButton
import com.moralesDev.dsForEveryone.ui.game.controls.SystemButton
import com.moralesDev.dsForEveryone.ui.resolve
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.GlassStrong
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

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
 * Vertical por defecto: encajar doce mandos táctiles en horizontal sin tapar
 * la táctil no compensa frente a lo simple que es jugar en vertical, así que
 * la orientación sigue bloqueada mientras no haya mando físico. Con un mando
 * Bluetooth o USB conectado (`GamepadManager`) esa cuenta cambia —no hace
 * falta pintar ningún botón en pantalla— y es entonces cuando
 * [FreeOrientationWhileGamepadConnected] libera la orientación y
 * `landscapeLayoutMode`/`landscapeControlLayout` de `EmulatorSettings` entran
 * en juego. Ver docs/entrada.md, «Mando físico».
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
    val isGamepadConnected by viewModel.isGamepadConnected.collectAsStateWithLifecycle()
    val isCheatsOpen by viewModel.isCheatsOpen.collectAsStateWithLifecycle()
    val cheats by viewModel.cheats.collectAsStateWithLifecycle()
    val hasAnySaveState by viewModel.hasAnySaveState.collectAsStateWithLifecycle()
    var isMenuOpen by remember { mutableStateOf(false) }

    FreeOrientationWhileGamepadConnected(isGamepadConnected)
    PauseWhileHidden(onHidden = viewModel::onScreenHidden, onShown = viewModel::onScreenShown)

    BackHandler {
        when {
            // La hoja de ranuras y la de trucos viven dentro del menú de
            // pausa (isMenuOpen sigue en true por debajo, ver más abajo):
            // cerrarlas vuelve a mostrar el menú, no reanuda la partida.
            slotAction != null -> viewModel.dismissSlotPicker()
            isCheatsOpen -> viewModel.closeCheats()
            isMenuOpen -> {
                isMenuOpen = false
                viewModel.resume()
            }
            else -> {
                isMenuOpen = true
                viewModel.pause()
            }
        }
    }

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val activeMode = if (isPortrait) settings.portraitLayoutMode else settings.landscapeLayoutMode

    val density = LocalDensity.current
    val minTouchTargetPx = with(density) { ControlLayout.MIN_TOUCH_TARGET_DP.dp.toPx() }

    // Las barras del sistema no consumen toques de la app, pero el sistema sí
    // se queda los suyos: lo que caiga debajo de una barra es inalcanzable,
    // se dibuje o no. Así que hay un único hueco utilizable —la ventana menos
    // las cuatro barras— y dentro de él van tanto las dos pantallas como los
    // mandos. Los cuatro lados y no sólo arriba y abajo: en horizontal, con
    // los tres botones, la barra de navegación se coloca a un lado.
    val systemBars = WindowInsets.systemBars
    val insets = LayoutInsets(
        left = systemBars.getLeft(density, LocalLayoutDirection.current).toFloat(),
        top = systemBars.getTop(density).toFloat(),
        right = systemBars.getRight(density, LocalLayoutDirection.current).toFloat(),
        bottom = systemBars.getBottom(density).toFloat(),
    )

    var availableSize by remember { mutableStateOf(LayoutSize(0f, 0f)) }
    val usableArea = remember(availableSize, insets) {
        LayoutRect(0f, 0f, availableSize.width, availableSize.height).inset(insets)
    }

    val placement = remember(
        activeMode,
        availableSize,
        insets,
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
                insets = insets,
            )
        } else {
            null
        }
    }

    val activeControlLayout = if (isPortrait) settings.portraitControlLayout else settings.landscapeControlLayout
    val controlProfile = remember(placement, usableArea, activeControlLayout, minTouchTargetPx) {
        val current = placement ?: return@remember null
        ControlLayout.resolveProfile(
            custom = activeControlLayout,
            area = usableArea,
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

        // Con mando físico conectado y en horizontal no hace falta pintar
        // ningún botón: el mando cubre los doce más el soplido (ver
        // docs/entrada.md, «Mando físico»). En vertical se mantienen —el
        // hueco vertical entre pantallas sigue siendo el mismo tenga o no
        // mando el usuario— y sin mando no cambia nada de lo de siempre.
        if (controlProfile != null && !(isGamepadConnected && !isPortrait)) {
            ControlGroups(
                profile = controlProfile,
                pressedMask = pressedMask,
                micActive = micActive,
                onButton = viewModel::onButton,
                onDirections = viewModel::onDirections,
                onBlow = viewModel::onMicNoise,
                density = density,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // El avance rápido se cambia sin pausar: pausar para acelerar una
            // conversación larga es exactamente lo que hace que nadie lo use.
            // El mismo ajuste está en el menú de pausa, y es el mismo valor.
            SpeedButton(speed = settings.speed, onClick = viewModel::cycleSpeed)

            GlassIconButton(
                icon = Icons.Filled.Menu,
                contentDescription = stringResource(R.string.cd_menu),
                onClick = {
                    isMenuOpen = true
                    viewModel.pause()
                },
            )
        }

        if (isMenuOpen) {
            PauseMenu(
                activeLayoutMode = activeMode,
                onLayoutModeChange = { mode -> viewModel.setLayoutMode(mode, isPortrait) },
                speed = settings.speed,
                onSpeedChange = viewModel::setSpeed,
                touchMode = settings.touchMode,
                onTouchModeChange = viewModel::setTouchMode,
                imageFilter = settings.imageFilter,
                onImageFilterChange = viewModel::setImageFilter,
                onChooseSlot = viewModel::chooseSlot,
                cheatsActive = cheats.count { it.enabled },
                cheatsTotal = cheats.size,
                // isMenuOpen se queda en true a propósito: Trucos se pinta
                // encima del menú de pausa, no en su lugar, igual que ya pasa
                // con la hoja de ranuras al elegir «Guardar partida» más
                // abajo. Así, al cerrar Trucos (con la X, tocando fuera, o
                // porque «Guardar estado» terminó de guardar) reaparece el
                // menú de pausa en vez de dejar la pantalla sin overlay.
                onCheats = viewModel::openCheats,
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

        if (isCheatsOpen) {
            CheatsSheet(
                gameTitle = rom?.displayName ?: stringResource(R.string.app_name),
                cheats = cheats,
                maxEntries = CheatsManager.MAX_ENTRIES,
                hasAnySaveState = hasAnySaveState,
                onAdd = viewModel::addCheat,
                onToggle = viewModel::toggleCheat,
                onDelete = viewModel::deleteCheat,
                onBackupNow = viewModel::openSaveFromCheats,
                onDismiss = viewModel::closeCheats,
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
 * El botón de avance rápido, al lado del de menú.
 *
 * No es un mando: no entra en [ControlLayout] ni en el editor de disposición
 * —son siete grupos y siguen siendo siete—, porque no se pulsa mientras se
 * juega sino que se deja puesto. Cada toque avanza el ciclo 1x → 1,5x → 2x →
 * 1x, y la velocidad actual se lee en el propio botón: con tres estados, un
 * icono encendido o apagado no distinguiría 1,5x de 2x.
 */
@Composable
private fun SpeedButton(speed: EmulationSpeed, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(11.dp)
    val background = when {
        speed.isFast -> Accent.copy(alpha = if (pressed) 0.42f else 0.30f)
        pressed -> GlassStrong
        else -> Glass
    }

    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, GlassBorder, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.FastForward,
            contentDescription = stringResource(R.string.cd_speed),
            tint = if (speed.isFast) Accent200 else textAlpha(0.75f),
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(speed.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = if (speed.isFast) Accent200 else textAlpha(0.6f),
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

/**
 * Detiene la emulación mientras la pantalla de juego no se ve, y la reanuda
 * al volver.
 *
 * `ON_STOP` y no `ON_PAUSE`: en pantalla dividida o con un diálogo del
 * sistema encima la partida se sigue viendo, y ahí pararla sería un fallo,
 * no un arreglo. Sin esto el hilo de emulación no se entera de nada —lo para
 * `onCleared`, que sólo llega al salir del juego de verdad— y el juego se
 * queda corriendo, y sonando, con la aplicación en segundo plano.
 */
@Composable
private fun PauseWhileHidden(onHidden: () -> Unit, onShown: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> onHidden()
                Lifecycle.Event.ON_START -> onShown()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

/**
 * Libera la orientación mientras haya un mando físico conectado y esta
 * pantalla esté visible; el resto de la app sigue en vertical por
 * manifiesto. Sin mando, la partida se queda en vertical: encajar doce
 * mandos táctiles en horizontal sin tapar la pantalla inferior no
 * compensaba (ver docs/entrada.md, «Mando físico»), y un mando físico es
 * justo lo que cambia esa cuenta.
 */
@Composable
private fun FreeOrientationWhileGamepadConnected(gamepadConnected: Boolean) {
    val context = LocalContext.current
    DisposableEffect(gamepadConnected) {
        if (!gamepadConnected) return@DisposableEffect onDispose {}
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

