package com.moralesDev.dsForEveryone.ui.game.layout

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroup
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroupPlacement
import com.moralesDev.dsForEveryone.emulator.layout.ControlLayout
import com.moralesDev.dsForEveryone.emulator.layout.LayoutEngine
import com.moralesDev.dsForEveryone.emulator.layout.LayoutInsets
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode
import com.moralesDev.dsForEveryone.emulator.layout.LayoutRect
import com.moralesDev.dsForEveryone.emulator.layout.LayoutSize
import com.moralesDev.dsForEveryone.emulator.layout.ScreenLayout
import com.moralesDev.dsForEveryone.emulator.layout.ScreenPlacement
import com.moralesDev.dsForEveryone.emulator.layout.inset
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.game.CONTROLS_CEILING_BELOW_CENTER_MM
import com.moralesDev.dsForEveryone.ui.game.CONTROLS_INWARD_SHIFT_MM
import com.moralesDev.dsForEveryone.ui.game.LayoutModeChips
import com.moralesDev.dsForEveryone.ui.game.SYSTEM_BUTTONS_BELOW_SCREEN_MM
import com.moralesDev.dsForEveryone.ui.game.rememberPxPerMm
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.GlassStrong
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import org.koin.compose.viewmodel.koinViewModel

/**
 * Editor de disposición (entregable 2.7): arrastra a mano las dos pantallas
 * —el hueco entre ellas, o el tamaño de la secundaria en «Principal +
 * secundaria»— y los siete grupos de mandos, con un perfil independiente por
 * orientación.
 *
 * La vista previa usa exactamente [ScreenLayout.resolve] y
 * [ControlLayout.resolveProfile], las mismas funciones que
 * [com.moralesDev.dsForEveryone.ui.game.GameScreen]: si algún día el editor y
 * el juego colocan distinto, es un error en una de las dos pantallas, nunca
 * una discrepancia de diseño.
 *
 * Este destino sólo se alcanza desde Configuración, nunca desde una partida en
 * marcha, así que «sólo se abre con la emulación detenida» se cumple por
 * construcción del grafo de navegación (ver `AppNavHost`) y no hace falta
 * comprobarlo aquí.
 */
@Composable
fun LayoutEditorScreen(onBack: () -> Unit) {
    val viewModel: LayoutEditorViewModel = koinViewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val density = LocalDensity.current

    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val activeMode = if (isPortrait) settings.portraitLayoutMode else settings.landscapeLayoutMode
    val customControlLayout = if (isPortrait) settings.portraitControlLayout else settings.landscapeControlLayout

    var availableSize by remember { mutableStateOf(LayoutSize(0f, 0f)) }
    var selectedGroup by remember { mutableStateOf<ControlGroup?>(null) }

    // El hueco y la escala de la secundaria se arrastran en directo: el valor
    // vive aquí mientras dura el gesto y sólo se persiste al soltar, para no
    // escribir en DataStore en cada fotograma de un arrastre.
    var isDraggingScreens by remember { mutableStateOf(false) }
    var gapState by remember { mutableFloatStateOf(settings.screenGap) }
    var scaleState by remember { mutableFloatStateOf(settings.secondaryScale) }
    LaunchedEffect(settings.screenGap) { if (!isDraggingScreens) gapState = settings.screenGap }
    LaunchedEffect(settings.secondaryScale) { if (!isDraggingScreens) scaleState = settings.secondaryScale }

    // Mismos márgenes que usa GameScreen —ver ese archivo—: un único hueco
    // utilizable, la ventana menos las cuatro barras del sistema, para las
    // pantallas y para los mandos.
    val systemBars = WindowInsets.systemBars
    val insets = LayoutInsets(
        left = systemBars.getLeft(density, LocalLayoutDirection.current).toFloat(),
        top = systemBars.getTop(density).toFloat(),
        right = systemBars.getRight(density, LocalLayoutDirection.current).toFloat(),
        bottom = systemBars.getBottom(density).toFloat(),
    )
    val usableArea = remember(availableSize, insets) {
        LayoutRect(0f, 0f, availableSize.width, availableSize.height).inset(insets)
    }

    // A este hueco es al que `gap` es relativo dentro de ScreenLayout, así que
    // la barra de arrastre tiene que traducir sus píxeles con esta misma
    // medida y no con la del contenedor entero.
    val screensAvailable = usableArea.size

    val placement = remember(activeMode, availableSize, insets, gapState, scaleState, settings.screensSwapped, isPortrait) {
        if (availableSize.width > 0f && availableSize.height > 0f) {
            ScreenLayout.resolve(
                mode = activeMode,
                available = availableSize,
                gap = gapState,
                secondaryScale = scaleState,
                swapped = settings.screensSwapped,
                insets = insets,
                // En vertical los mandos ocupan del centro hacia abajo.
                upperHalf = isPortrait,
            )
        } else {
            null
        }
    }

    val minTouchTargetPx = with(density) { ControlLayout.MIN_TOUCH_TARGET_DP.dp.toPx() }

    // Las mismas medidas físicas que GameScreen: si no, el editor colocaría
    // distinto que el juego.
    val pxPerMm = rememberPxPerMm()
    val ceilingBelowCenterPx = CONTROLS_CEILING_BELOW_CENTER_MM * pxPerMm.y
    val inwardShiftPx = CONTROLS_INWARD_SHIFT_MM * pxPerMm.x
    val systemBelowScreenPx = SYSTEM_BUTTONS_BELOW_SCREEN_MM * pxPerMm.y
    val controlProfile = remember(
        placement,
        usableArea,
        customControlLayout,
        minTouchTargetPx,
        isPortrait,
        ceilingBelowCenterPx,
        inwardShiftPx,
        systemBelowScreenPx,
    ) {
        val current = placement ?: return@remember null
        ControlLayout.resolveProfile(
            custom = customControlLayout,
            area = usableArea,
            touchScreen = current.touchScreen,
            minTouchTarget = minTouchTargetPx,
            opacity = ControlLayout.DEFAULT_OPACITY,
            overlay = isPortrait,
            ceiling = ControlLayout.overlayCeiling(current, usableArea, belowCenter = ceilingBelowCenterPx),
            inwardShift = inwardShiftPx,
            systemTop = ControlLayout.lowerScreenBottom(current)?.plus(systemBelowScreenPx),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .onSizeChanged { size -> availableSize = LayoutSize(size.width.toFloat(), size.height.toFloat()) }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { selectedGroup = null })
            },
    ) {
        placement?.let { current ->
            ScreenPreview(
                rect = current.topScreen,
                label = stringResource(R.string.le_screen_top),
                density = density,
            )
            ScreenPreview(
                rect = current.touchScreen,
                label = stringResource(R.string.le_screen_touch),
                density = density,
                highlighted = true,
            )

            if (activeMode == LayoutMode.STACKED || activeMode == LayoutMode.SIDE_BY_SIDE) {
                GapHandle(
                    mode = activeMode,
                    placement = current,
                    available = screensAvailable,
                    density = density,
                    onDragStart = { isDraggingScreens = true },
                    onDragDelta = { deltaFraction -> gapState = (gapState + deltaFraction).coerceIn(0f, 1f) },
                    onDragEnd = {
                        isDraggingScreens = false
                        viewModel.setScreenGap(gapState)
                    },
                )
            }
            if (activeMode == LayoutMode.MAIN_AND_SECONDARY) {
                SecondaryScaleHandle(
                    placement = current,
                    density = density,
                    onDragStart = { isDraggingScreens = true },
                    onDragDelta = { deltaScale ->
                        scaleState = (scaleState + deltaScale).coerceIn(MIN_SECONDARY_SCALE, MAX_SECONDARY_SCALE)
                    },
                    onDragEnd = {
                        isDraggingScreens = false
                        viewModel.setSecondaryScale(scaleState)
                    },
                )
            }
        }

        controlProfile?.let { profile ->
            // En vertical los mandos van encima de la táctil a propósito (ver
            // ControlLayout.overlayProfile): ahí la táctil no bloquea nada.
            val touchBlocker = placement?.touchScreen?.takeUnless { isPortrait }
            // Y en vertical, nunca por encima de la pantalla de arriba: todo
            // lo que queda sobre el techo de overlayCeiling es zona vedada.
            val ceilingBlocker = placement?.takeIf { isPortrait }?.let { current ->
                val ceiling = ControlLayout.overlayCeiling(current, usableArea, belowCenter = ceilingBelowCenterPx)
                LayoutRect(usableArea.left, usableArea.top, usableArea.right, ceiling)
                    .takeIf { it.height > 0f }
            }

            profile.groups.forEach { (group, groupPlacement) ->
                key(group) {
                    val blockers = (profile.groups - group).values.map { it.bounds } + listOfNotNull(touchBlocker, ceilingBlocker)
                    EditableGroupBox(
                        group = group,
                        placement = groupPlacement,
                        isSelected = selectedGroup == group,
                        area = usableArea,
                        blockers = blockers,
                        density = density,
                        onSelect = { selectedGroup = group },
                        onCommit = { newBounds ->
                            viewModel.setControlGroup(
                                portrait = isPortrait,
                                base = ControlLayout.toFractions(profile, usableArea),
                                group = group,
                                placement = ControlLayout.fractionOf(
                                    bounds = newBounds,
                                    area = usableArea,
                                    opacity = groupPlacement.opacity,
                                ),
                            )
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.cd_back),
                onClick = onBack,
            )
            Text(
                text = stringResource(
                    if (isPortrait) R.string.le_title_portrait else R.string.le_title_landscape,
                ),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            GlassIconButton(
                icon = Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.le_reset),
                onClick = {
                    selectedGroup = null
                    viewModel.resetControlLayout(isPortrait)
                },
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val selected = selectedGroup
            val profile = controlProfile
            if (selected != null && profile != null) {
                OpacityPanel(
                    group = selected,
                    opacity = profile.groups.getValue(selected).opacity,
                    onOpacityChange = { newOpacity ->
                        viewModel.setControlGroup(
                            portrait = isPortrait,
                            base = ControlLayout.toFractions(profile, usableArea),
                            group = selected,
                            placement = ControlLayout.fractionOf(
                                bounds = profile.groups.getValue(selected).bounds,
                                area = usableArea,
                                opacity = newOpacity,
                            ),
                        )
                    },
                )
            } else {
                Text(
                    text = stringResource(R.string.le_hint),
                    style = MaterialTheme.typography.labelMedium,
                    color = textAlpha(0.55f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LayoutModeChips(
                        selected = activeMode,
                        onSelect = { mode -> viewModel.setLayoutMode(mode, isPortrait) },
                    )
                    GlassIconButton(
                        icon = Icons.Filled.SwapVert,
                        contentDescription = stringResource(R.string.le_swap),
                        onClick = { viewModel.setScreensSwapped(!settings.screensSwapped) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ScreenPreview(
    rect: LayoutRect,
    label: String,
    density: Density,
    highlighted: Boolean = false,
) {
    if (rect.width <= 0f || rect.height <= 0f) return
    val (offsetX, offsetY, boxWidth, boxHeight) = with(density) {
        listOf(rect.left.toDp(), rect.top.toDp(), rect.width.toDp(), rect.height.toDp())
    }
    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .size(boxWidth, boxHeight)
            .clip(RoundedCornerShape(6.dp))
            .background(if (highlighted) Accent.copy(alpha = 0.10f) else Glass)
            .border(1.dp, if (highlighted) Accent.copy(alpha = 0.45f) else GlassBorder, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textAlpha(0.5f),
        )
    }
}

/**
 * Barra arrastrable entre las dos pantallas, para el hueco. La primera y la
 * segunda se determinan por posición física (no por [ScreenPlacement.touchScreen]
 * ni por `swapped`), así que funciona igual con las pantallas intercambiadas.
 */
@Composable
private fun GapHandle(
    mode: LayoutMode,
    placement: ScreenPlacement,
    available: LayoutSize,
    density: Density,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val isVertical = mode == LayoutMode.STACKED
    val (first, second) = if (isVertical) {
        if (placement.topScreen.top <= placement.touchScreen.top) {
            placement.topScreen to placement.touchScreen
        } else {
            placement.touchScreen to placement.topScreen
        }
    } else {
        if (placement.topScreen.left <= placement.touchScreen.left) {
            placement.topScreen to placement.touchScreen
        } else {
            placement.touchScreen to placement.topScreen
        }
    }
    if (first.width <= 0f || second.width <= 0f) return

    val axisLength = if (isVertical) available.height else available.width
    if (axisLength <= 0f) return

    val center = if (isVertical) (first.bottom + second.top) / 2f else (first.right + second.left) / 2f

    val (offsetX, offsetY, boxWidth, boxHeight) = with(density) {
        if (isVertical) {
            listOf(
                first.left.toDp(),
                (center - HANDLE_THICKNESS_PX / 2f).toDp(),
                first.width.toDp(),
                HANDLE_THICKNESS_PX.toDp(),
            )
        } else {
            listOf(
                (center - HANDLE_THICKNESS_PX / 2f).toDp(),
                first.top.toDp(),
                HANDLE_THICKNESS_PX.toDp(),
                first.height.toDp(),
            )
        }
    }

    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .size(boxWidth, boxHeight)
            .clip(RoundedCornerShape(3.dp))
            .background(Accent.copy(alpha = 0.55f))
            .pointerInput(mode) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    // Al ser un hueco SIMÉTRICO entre las dos pantallas, arrastrar la
                    // barra una distancia `d` cambia el hueco total en `2d`.
                    val delta = if (isVertical) dragAmount.y else dragAmount.x
                    onDragDelta((2f * delta) / axisLength)
                }
            },
    )
}

/**
 * Manejador en la esquina superior izquierda de la pantalla secundaria (modo
 * «Principal + secundaria»): arrastrarlo hacia el centro la agranda, hacia
 * fuera la reduce. La esquina opuesta está anclada a la principal por
 * construcción (ver [ScreenLayout]), así que no hace falta tratarla aquí.
 */
@Composable
private fun SecondaryScaleHandle(
    placement: ScreenPlacement,
    density: Density,
    onDragStart: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val topArea = placement.topScreen.width * placement.topScreen.height
    val touchArea = placement.touchScreen.width * placement.touchScreen.height
    val main = if (topArea >= touchArea) placement.topScreen else placement.touchScreen
    val secondary = if (topArea >= touchArea) placement.touchScreen else placement.topScreen
    if (main.width <= 0f || secondary.width <= 0f) return

    val (offsetX, offsetY) = with(density) {
        (secondary.left - HANDLE_SIZE_PX / 2f).toDp() to (secondary.top - HANDLE_SIZE_PX / 2f).toDp()
    }
    val handleSize = with(density) { HANDLE_SIZE_PX.toDp() }

    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .size(handleSize)
            .clip(CircleShape)
            .background(Accent)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                ) { change, dragAmount ->
                    change.consume()
                    // Arrastrar hacia la esquina superior izquierda (dx, dy negativos)
                    // agranda la secundaria: por eso el signo se invierte aquí.
                    onDragDelta(-dragAmount.x / main.width)
                }
            },
    )
}

/**
 * Un grupo de mandos arrastrable y redimensionable. [blockers] son los
 * rectángulos con los que no puede solaparse: los otros seis grupos y, en
 * horizontal, la pantalla táctil. En vertical los mandos van encima de la
 * táctil por diseño, y la pantalla superior no entra nunca: solaparla está
 * permitido porque no es interactiva (ver [ControlLayout]).
 */
@Composable
private fun EditableGroupBox(
    group: ControlGroup,
    placement: ControlGroupPlacement,
    isSelected: Boolean,
    area: LayoutRect,
    blockers: List<LayoutRect>,
    density: Density,
    onSelect: () -> Unit,
    onCommit: (LayoutRect) -> Unit,
) {
    var isDragging by remember(group) { mutableStateOf(false) }
    var workingBounds by remember(group) { mutableStateOf(placement.bounds) }
    LaunchedEffect(placement.bounds) { if (!isDragging) workingBounds = placement.bounds }

    val minSide = minOf(area.width, area.height) * MIN_GROUP_SIZE_FRACTION
    val maxSide = minOf(area.width, area.height) * MAX_GROUP_SIZE_FRACTION

    val (offsetX, offsetY, boxWidth, boxHeight) = with(density) {
        listOf(
            workingBounds.left.toDp(),
            workingBounds.top.toDp(),
            workingBounds.width.toDp(),
            workingBounds.height.toDp(),
        )
    }
    val handleSize = with(density) { HANDLE_SIZE_PX.toDp() }

    Box(
        modifier = Modifier
            .offset(offsetX, offsetY)
            .size(boxWidth, boxHeight)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Accent.copy(alpha = 0.24f) else GlassStrong.copy(alpha = GlassStrong.alpha * placement.opacity))
            .border(1.dp, if (isSelected) Accent else GlassBorder, RoundedCornerShape(10.dp))
            .pointerInput(group, area, blockers) {
                detectDragGestures(
                    onDragStart = { isDragging = true; onSelect() },
                    onDragEnd = { isDragging = false; onCommit(workingBounds) },
                    onDragCancel = { isDragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    val candidate = workingBounds.translated(dragAmount.x, dragAmount.y).clampedWithin(area)
                    if (blockers.none { LayoutEngine.overlaps(candidate, it) }) workingBounds = candidate
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(group.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            color = textAlpha(0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(4.dp),
        )

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(Accent)
                    .pointerInput(group, area, blockers) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false; onCommit(workingBounds) },
                            onDragCancel = { isDragging = false },
                        ) { change, dragAmount ->
                            change.consume()
                            val candidate = workingBounds
                                .resizedFromBottomRight(dragAmount.x, dragAmount.y, minSide, maxSide, area)
                                .clampedWithin(area)
                            if (blockers.none { LayoutEngine.overlaps(candidate, it) }) workingBounds = candidate
                        }
                    },
            )
        }
    }
}

@Composable
private fun OpacityPanel(
    group: ControlGroup,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(group.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            color = textAlpha(0.7f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.le_opacity),
                style = MaterialTheme.typography.labelSmall,
                color = textAlpha(0.5f),
            )
            Slider(
                value = opacity,
                onValueChange = onOpacityChange,
                valueRange = MIN_GROUP_OPACITY..1f,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
        }
    }
}

private fun ControlGroup.labelRes(): Int = when (this) {
    ControlGroup.DIRECTIONAL_PAD -> R.string.le_group_dpad
    ControlGroup.ACTION_BUTTONS -> R.string.le_group_action
    ControlGroup.L -> R.string.le_group_l
    ControlGroup.R -> R.string.le_group_r
    ControlGroup.START -> R.string.le_group_start
    ControlGroup.SELECT -> R.string.le_group_select
    ControlGroup.BLOW -> R.string.le_group_blow
}

private fun LayoutRect.translated(dx: Float, dy: Float): LayoutRect =
    LayoutRect(left + dx, top + dy, right + dx, bottom + dy)

private fun LayoutRect.resizedFromBottomRight(
    dx: Float,
    dy: Float,
    minSide: Float,
    maxSide: Float,
    area: LayoutRect,
): LayoutRect {
    val newWidth = (width + dx).coerceIn(minSide, minOf(maxSide, area.right - left))
    val newHeight = (height + dy).coerceIn(minSide, minOf(maxSide, area.bottom - top))
    return LayoutRect(left, top, left + newWidth, top + newHeight)
}

private fun LayoutRect.clampedWithin(area: LayoutRect): LayoutRect {
    val w = width.coerceAtMost(area.width)
    val h = height.coerceAtMost(area.height)
    val newLeft = left.coerceIn(area.left, (area.right - w).coerceAtLeast(area.left))
    val newTop = top.coerceIn(area.top, (area.bottom - h).coerceAtLeast(area.top))
    return LayoutRect(newLeft, newTop, newLeft + w, newTop + h)
}

private const val HANDLE_THICKNESS_PX = 28f
private const val HANDLE_SIZE_PX = 34f
private const val MIN_GROUP_SIZE_FRACTION = 0.08f
private const val MAX_GROUP_SIZE_FRACTION = 0.5f
private const val MIN_GROUP_OPACITY = 0.2f
private const val MIN_SECONDARY_SCALE = 0.1f
private const val MAX_SECONDARY_SCALE = 0.9f
