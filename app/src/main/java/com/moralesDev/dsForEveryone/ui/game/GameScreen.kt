package com.moralesDev.dsForEveryone.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.data.cheats.CheatsManager
import com.moralesDev.dsForEveryone.data.settings.DirectionalControl
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
import com.moralesDev.dsForEveryone.ui.game.controls.GhostIcon
import com.moralesDev.dsForEveryone.ui.game.controls.GhostSurface
import com.moralesDev.dsForEveryone.ui.game.controls.ghostShadow
import com.moralesDev.dsForEveryone.ui.game.controls.ghostContentColor
import com.moralesDev.dsForEveryone.ui.game.controls.Joystick
import com.moralesDev.dsForEveryone.ui.game.controls.ShoulderButton
import com.moralesDev.dsForEveryone.ui.game.controls.SystemButton
import com.moralesDev.dsForEveryone.ui.resolve
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.ScreenBezel
import com.moralesDev.dsForEveryone.ui.theme.GlassOverVideo
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import kotlin.math.roundToInt
import org.koin.compose.viewmodel.koinViewModel
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.app_name
import com.moralesDev.dsForEveryone.resources.cd_blow
import com.moralesDev.dsForEveryone.resources.cd_hide_controls
import com.moralesDev.dsForEveryone.resources.cd_menu
import com.moralesDev.dsForEveryone.resources.cd_show_controls
import com.moralesDev.dsForEveryone.resources.lid_closed
import com.moralesDev.dsForEveryone.resources.lid_open_hint

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
    val viewModel: GameViewModel = koinViewModel()
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
    val lidClosed by viewModel.lidClosed.collectAsStateWithLifecycle()
    var isMenuOpen by remember { mutableStateOf(false) }
    // Sobrevive a rotar y a que el sistema recree la pantalla: quien aparta
    // los mandos para jugar con la táctil no quiere que vuelvan solos.
    var controlsHidden by rememberSaveable { mutableStateOf(false) }

    FreeOrientationWhileGamepadConnected(isGamepadConnected)
    PauseWhileHidden(onHidden = viewModel::onScreenHidden, onShown = viewModel::onScreenShown)

    BackHandler {
        when {
            // Con la tapa cerrada el juego está corriendo y el velo lo tapa
            // todo: atrás abre la tapa, que es lo único que se puede hacer
            // desde ahí, en vez de sacar del juego a ciegas.
            lidClosed -> viewModel.openLid()
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

    val isPortrait = rememberIsPortrait()
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
        isPortrait,
    ) {
        if (availableSize.width > 0f && availableSize.height > 0f) {
            ScreenLayout.resolve(
                mode = activeMode,
                available = availableSize,
                gap = settings.screenGap,
                secondaryScale = settings.secondaryScale,
                swapped = settings.screensSwapped,
                insets = insets,
                // En vertical los mandos ocupan del centro hacia abajo.
                upperHalf = isPortrait,
            )
        } else {
            null
        }
    }

    val activeControlLayout = if (isPortrait) settings.portraitControlLayout else settings.landscapeControlLayout
    // En vertical los mandos van encima de la pantalla inferior (ver
    // ControlLayout.overlayProfile); en horizontal, en una banda aparte.
    val pxPerMm = rememberPxPerMm()
    val ceilingBelowCenterPx = CONTROLS_CEILING_BELOW_CENTER_MM * pxPerMm.y
    val inwardShiftPx = CONTROLS_INWARD_SHIFT_MM * pxPerMm.x
    val systemBelowScreenPx = SYSTEM_BUTTONS_BELOW_SCREEN_MM * pxPerMm.y
    val controlProfile = remember(
        placement,
        usableArea,
        activeControlLayout,
        minTouchTargetPx,
        isPortrait,
        ceilingBelowCenterPx,
        inwardShiftPx,
        systemBelowScreenPx,
    ) {
        val current = placement ?: return@remember null
        ControlLayout.resolveProfile(
            custom = activeControlLayout,
            area = usableArea,
            touchScreen = current.touchScreen,
            minTouchTarget = minTouchTargetPx,
            opacity = ControlLayout.DEFAULT_OPACITY,
            overlay = isPortrait,
            // Nunca por encima de la pantalla de arriba: los mandos van
            // encima de la inferior, no de las dos.
            ceiling = ControlLayout.overlayCeiling(current, usableArea, belowCenter = ceilingBelowCenterPx),
            inwardShift = inwardShiftPx,
            systemTop = ControlLayout.lowerScreenBottom(current)?.plus(systemBelowScreenPx),
        )
    }

    // Apartar los mandos sólo existe en vertical, que es donde tapan las
    // pantallas, y sólo se llevan los que tapan alguna: Select y Start bajo
    // la pantalla inferior se quedan, y soplar no se va nunca. Si ninguno tapa nada —las pantallas en
    // la mitad de arriba—, no hay botón de apartar.
    val hideableGroups = remember(controlProfile, placement) {
        val profile = controlProfile ?: return@remember emptySet()
        val current = placement ?: return@remember emptySet()
        ControlLayout.groupsCoveringScreens(profile, current)
    }
    val canHideControls = isPortrait && hideableGroups.isNotEmpty()
    val hideProgress by animateFloatAsState(
        targetValue = if (canHideControls && controlsHidden) 1f else 0f,
        label = "controls-hide",
    )

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
                directionalControl = settings.directionalControl,
                hideProgress = hideProgress,
                hideableGroups = hideableGroups,
                windowWidth = availableSize.width,
                pressedMask = pressedMask,
                micActive = micActive,
                onButton = viewModel::onButton,
                onDirections = viewModel::onDirections,
                onBlow = viewModel::onMicNoise,
                density = density,
            )
        }

        // La velocidad, entre las dos pantallas cuando están apiladas en
        // vertical: a mano sin abrir el menú y sin tapar nada del juego. El
        // menú de pausa conserva el mismo ajuste para el resto de casos.
        if (isPortrait) {
            placement?.let(ControlLayout::stackedGap)?.let { (gapTop, gapBottom) ->
                SpeedBar(
                    centerY = (gapTop + gapBottom) / 2f,
                    centerX = usableArea.centerX,
                    selected = settings.speed,
                    onSelect = viewModel::setSpeed,
                    density = density,
                )
            }
        }

        // Apartar los mandos: abajo a la derecha, a la altura de Start (ver
        // ControlLayout.hideToggleBounds). No se aparta con ellos —es lo que
        // los devuelve— y por eso no es uno más de ControlGroups.
        if (canHideControls && controlProfile != null && !(isGamepadConnected && !isPortrait)) {
            HideControlsToggle(
                bounds = ControlLayout.hideToggleBounds(controlProfile, minTouchTargetPx, usableArea),
                hidden = controlsHidden,
                onToggle = { controlsHidden = !controlsHidden },
                density = density,
            )
        }

        // Un solo botón arriba, encima del juego: el de menú. El avance rápido
        // vivió aquí al lado y tapaba demasiado —dos pastillas sobre la
        // esquina de la pantalla superior—, así que se cambia sólo desde el
        // menú de pausa, que es donde ya estaba el mismo ajuste.
        GlassIconButton(
            icon = Icons.Filled.Menu,
            contentDescription = stringResource(Res.string.cd_menu),
            onClick = {
                isMenuOpen = true
                viewModel.pause()
            },
            // Debajo está el juego, no el fondo de la aplicación: con el
            // vidrio claro de siempre, un juego de fondo blanco borra el
            // botón. Ver GlassOverVideo.
            overVideo = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(12.dp),
        )

        if (isMenuOpen) {
            PauseMenu(
                activeLayoutMode = activeMode,
                onLayoutModeChange = { mode -> viewModel.setLayoutMode(mode, isPortrait) },
                speed = settings.speed,
                onSpeedChange = viewModel::setSpeed,
                screensSwapped = settings.screensSwapped,
                onScreensSwappedChange = viewModel::setScreensSwapped,
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
                onCloseLid = {
                    isMenuOpen = false
                    viewModel.closeLid()
                },
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
                gameTitle = rom?.displayName ?: stringResource(Res.string.app_name),
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
                gameTitle = rom?.displayName ?: stringResource(Res.string.app_name),
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

        if (lidClosed) {
            ClosedLidVeil(onOpen = viewModel::openLid)
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
 * Lo que se ve con la tapa cerrada: la consola bajada, no el juego detenido.
 *
 * La partida **sigue corriendo por debajo** —es justo el sentido de cerrar la
 * tapa: que el juego se entere y haga lo suyo—, así que esto no es un menú ni
 * una pausa, sólo tapa las dos pantallas como haría la carcasa. Absorbe todos
 * los toques: con la tapa bajada no se puede pulsar nada de la consola, y el
 * único gesto posible es volver a abrirla.
 */
@Composable
private fun ClosedLidVeil(onOpen: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBezel.copy(alpha = 0.94f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpen,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = null,
                tint = textAlpha(0.65f),
                modifier = Modifier.size(34.dp),
            )
            Text(
                text = stringResource(Res.string.lid_closed),
                style = MaterialTheme.typography.titleSmall,
                color = textAlpha(0.85f),
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = stringResource(Res.string.lid_open_hint),
                style = MaterialTheme.typography.labelMedium,
                color = textAlpha(0.5f),
                modifier = Modifier.padding(top = 6.dp),
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

/**
 * Los tres botones de velocidad —1x, 1,5x y 2x— en una fila centrada en
 * ([centerX], [centerY]), con el mismo aspecto transparente que los mandos.
 * El elegido se ve relleno. 1x es el valor por defecto de
 * [EmulationSpeed]; lo que se elija se guarda como cualquier otro ajuste.
 */
@Composable
private fun SpeedBar(
    centerY: Float,
    centerX: Float,
    selected: EmulationSpeed,
    onSelect: (EmulationSpeed) -> Unit,
    density: Density,
) {
    val (x, y) = with(density) { centerX.toDp() to centerY.toDp() }
    val totalWidth = SPEED_CHIP_WIDTH * EmulationSpeed.entries.size + SPEED_CHIP_SPACING * (EmulationSpeed.entries.size - 1)
    Row(
        horizontalArrangement = Arrangement.spacedBy(SPEED_CHIP_SPACING),
        modifier = Modifier.offset(x - totalWidth / 2, y - SPEED_CHIP_HEIGHT / 2),
    ) {
        EmulationSpeed.entries.forEach { speed ->
            GhostSurface(
                shape = RoundedCornerShape(50),
                pressed = speed == selected,
                opacity = ControlLayout.DEFAULT_OPACITY,
                modifier = Modifier
                    .size(SPEED_CHIP_WIDTH, SPEED_CHIP_HEIGHT)
                    .clickable { onSelect(speed) },
            ) {
                Text(
                    text = stringResource(speed.labelRes()),
                    style = MaterialTheme.typography.labelMedium.copy(
                        shadow = ghostShadow(ControlLayout.DEFAULT_OPACITY),
                    ),
                    color = ghostContentColor(ControlLayout.DEFAULT_OPACITY),
                )
            }
        }
    }
}

private val SPEED_CHIP_WIDTH = 46.dp
private val SPEED_CHIP_HEIGHT = 26.dp
private val SPEED_CHIP_SPACING = 8.dp

/**
 * El botón «>» / «<» que aparta los mandos o los devuelve. Con el mismo
 * aspecto que ellos —transparente, borde claro—, pero es un botón de la
 * aplicación, no de la DS: un clic, sin pulsación mantenida.
 */
@Composable
private fun HideControlsToggle(
    bounds: LayoutRect,
    hidden: Boolean,
    onToggle: () -> Unit,
    density: Density,
) {
    if (bounds.width <= 0f || bounds.height <= 0f) return
    val (offsetX, offsetY, boxWidth, boxHeight) = with(density) {
        listOf(bounds.left.toDp(), bounds.top.toDp(), bounds.width.toDp(), bounds.height.toDp())
    }
    GhostSurface(
        shape = CircleShape,
        pressed = false,
        opacity = ControlLayout.DEFAULT_OPACITY,
        modifier = Modifier
            .offset(offsetX, offsetY)
            .size(boxWidth, boxHeight)
            .clickable(onClick = onToggle),
    ) {
        GhostIcon(
            imageVector = if (hidden) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            opacity = ControlLayout.DEFAULT_OPACITY,
            contentDescription = stringResource(
                if (hidden) Res.string.cd_show_controls else Res.string.cd_hide_controls,
            ),
            tint = ghostContentColor(ControlLayout.DEFAULT_OPACITY),
            modifier = Modifier.fillMaxSize(0.8f),
        )
    }
}

/**
 * Los grupos de la mano izquierda. Al apartar los mandos salen por el borde
 * izquierdo; el resto, por el derecho.
 */
private val LEFT_HAND_GROUPS = setOf(
    ControlGroup.L,
    ControlGroup.BLOW,
    ControlGroup.DIRECTIONAL_PAD,
    ControlGroup.SELECT,
)

/**
 * @param hideProgress 0 con los mandos en su sitio, 1 del todo fuera. Del
 *   todo fuera, los grupos dejan de componerse: así el `finally` de cada
 *   mando suelta lo que estuviera pulsado y la pantalla inferior entera
 *   vuelve a ser de la táctil.
 * @param hideableGroups los únicos que se apartan; el resto no se mueve.
 */
@Composable
private fun ControlGroups(
    profile: ControlLayoutProfile,
    directionalControl: DirectionalControl,
    hideProgress: Float,
    hideableGroups: Set<ControlGroup>,
    windowWidth: Float,
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
        val progress = if (group in hideableGroups) hideProgress else 0f
        if (progress >= 1f) return@forEach
        // Lo justo para que el grupo quede entero fuera de la ventana por su lado.
        val slide = if (group in LEFT_HAND_GROUPS) -bounds.right else windowWidth - bounds.left
        val groupModifier = Modifier
            .offset { IntOffset((slide * progress).roundToInt(), 0) }
            .offset(offsetX, offsetY)
            .size(boxWidth, boxHeight)

        when (group) {
            ControlGroup.DIRECTIONAL_PAD -> when (directionalControl) {
                DirectionalControl.JOYSTICK -> Joystick(
                    pressedMask = pressedMask,
                    opacity = placement.opacity,
                    haptic = true,
                    onDirections = onDirections,
                    modifier = groupModifier,
                )

                DirectionalControl.DPAD -> DirectionalPad(
                    pressedMask = pressedMask,
                    opacity = placement.opacity,
                    haptic = true,
                    onDirections = onDirections,
                    modifier = groupModifier,
                )
            }

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
                contentDescription = stringResource(Res.string.cd_blow),
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

