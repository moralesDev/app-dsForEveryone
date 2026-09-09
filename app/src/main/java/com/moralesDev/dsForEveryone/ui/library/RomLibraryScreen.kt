package com.moralesDev.dsForEveryone.ui.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.rom.DsBanner
import com.moralesDev.dsForEveryone.data.rom.RomInfo
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.components.BrandHeader
import com.moralesDev.dsForEveryone.ui.components.GlassIconButton
import com.moralesDev.dsForEveryone.ui.resolve
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Danger
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.Neutral600
import com.moralesDev.dsForEveryone.ui.theme.Neutral700
import com.moralesDev.dsForEveryone.ui.theme.ScrimColor
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Juegos que se muestran por página de la biblioteca. */
private const val PAGE_SIZE = 5

/**
 * Biblioteca de juegos.
 *
 * El alta pasa por el selector de documentos del sistema, no por una lista de
 * archivos propia: es la única vía en Android moderno que da acceso duradero a
 * un archivo del usuario sin pedir permiso sobre todo su almacenamiento. La
 * aplicación no trae ni descarga ningún juego.
 *
 * Sin «continuar partida» todavía: depende del sistema de partidas (fase 3.4).
 * Cada fila tiene un único camino, «Abrir», hasta entonces.
 */
@Composable
fun RomLibraryScreen(
    onOpen: (RomInfo) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: RomLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val contentResolver = LocalContext.current.contentResolver

    // Los proveedores de documentos rara vez declaran un MIME para .nds, así
    // que se aceptan todos y es la cabecera del archivo la que decide si vale.
    val romPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::onRomPicked,
    )
    val pickRom = { romPicker.launch(arrayOf("*/*")) }

    // Selector de fotos del sistema. Da acceso **sólo** a la imagen elegida y
    // por eso la aplicación sigue sin declarar ni un permiso de almacenamiento:
    // no hace falta pedir la galería entera para poner una portada.
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = viewModel::onImagePicked,
    )
    val pickCover = {
        imagePicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // A qué juego pertenece el resultado lo dice el estado del ViewModel
    // ([RomOptionsState]), no una variable de esta pantalla: el selector de
    // documentos es otra aplicación y puede recomponer la biblioteca al volver.
    val savExportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri ->
            uiState.romOptions?.rom?.id?.let { romId -> viewModel.exportSav(romId, contentResolver, uri) }
        },
    )
    val savImportPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uiState.romOptions?.rom?.id?.let { romId ->
                viewModel.onSavImportPicked(romId, contentResolver, uri)
            }
        },
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        if (uiState.isEmpty) {
            EmptyLibrary(onAdd = { pickRom() }, modifier = Modifier.weight(1f))
        } else {
            LibraryHeader(
                query = uiState.query,
                onQueryChange = viewModel::onQueryChange,
                onOpenSettings = onOpenSettings,
            )

            LibraryList(
                roms = uiState.roms,
                query = uiState.query,
                hasNoResults = uiState.hasNoResults,
                onOpen = { rom -> viewModel.onOpen(rom.id); onOpen(rom) },
                onOptions = viewModel::onRomOptionsRequested,
                onEditCover = viewModel::onCoverTapped,
                modifier = Modifier.weight(1f),
            )

            Box(modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 22.dp)) {
                AccentButton(
                    text = stringResource(R.string.lib_add),
                    icon = Icons.Filled.Add,
                    onClick = { pickRom() },
                )
            }
        }
    }

    uiState.message?.let { message ->
        val text = message.resolve()
        LibraryToast(text = text, onDismiss = viewModel::consumeMessage)
    }

    uiState.coverAction?.let { action ->
        when (action) {
            is CoverAction.Choose -> CoverOptionsSheet(
                gameName = action.rom.displayName,
                hasCover = action.hasCover,
                onPick = { pickCover() },
                onDelete = { viewModel.requestCoverDeletion(action.rom) },
                onDismiss = viewModel::dismissCoverAction,
            )

            is CoverAction.Crop -> CoverCropSheet(
                gameName = action.rom.displayName,
                image = action.image,
                isSaving = uiState.isProcessingCover,
                onSave = { zoom, centerX, centerY ->
                    viewModel.saveCover(action.rom, action.image, zoom, centerX, centerY)
                },
                onDismiss = viewModel::dismissCoverAction,
            )

            is CoverAction.ConfirmDelete -> CoverDeleteSheet(
                gameName = action.rom.displayName,
                onConfirm = { viewModel.deleteCover(action.rom) },
                onDismiss = viewModel::dismissCoverAction,
            )
        }
    }

    uiState.romOptions?.let { options ->
        RomOptionsSheet(
            rom = options.rom,
            hasBatterySave = options.hasBatterySave,
            pendingSavImport = uiState.pendingSavImport,
            pendingSavDelete = uiState.pendingSavDelete,
            onExtractSav = {
                savExportPicker.launch(suggestedSavFileName(options.rom.fileName))
            },
            onImportSav = { savImportPicker.launch(arrayOf("*/*")) },
            onConfirmSavImport = viewModel::confirmSavImport,
            onCancelSavImport = viewModel::cancelSavImport,
            onDeleteSav = viewModel::requestSavDeletion,
            onConfirmSavDelete = viewModel::confirmSavDelete,
            onCancelSavDelete = viewModel::cancelSavDelete,
            onKeep = { viewModel.remove(options.rom.id, deleteSaves = false) },
            onDeleteAll = { viewModel.remove(options.rom.id, deleteSaves = true) },
            onDismiss = viewModel::dismissRomOptions,
        )
    }
}

/** `<nombre original sin extensión>.sav`, para que otros emuladores lo reconozcan. */
private fun suggestedSavFileName(originalFileName: String): String =
    originalFileName.substringBeforeLast('.', originalFileName) + ".sav"

@Composable
private fun LibraryHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                BrandHeader()
                Text(
                    text = stringResource(R.string.lib_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }
            GlassIconButton(
                icon = Icons.Filled.Tune,
                contentDescription = stringResource(R.string.cd_settings),
                onClick = onOpenSettings,
            )
        }

        SearchField(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

/**
 * El buscador de la biblioteca.
 *
 * El campo es el dueño de su texto **y de su cursor**. Darle el `query` del
 * ViewModel tal cual haría que cada pulsación diera un rodeo asíncrono —flujo
 * de la base de datos, `combine`, `collectAsStateWithLifecycle`— y volviera un
 * fotograma más tarde: el campo leería ese texto retrasado como un cambio
 * venido de fuera y devolvería el cursor al principio.
 *
 * El estado con selección vive aquí y sólo viaja en un sentido: hacia el
 * ViewModel, que se limita a filtrar. Lo que vuelva de él ya no toca el cursor.
 */
@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier) {
    var field by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(query))
    }
    // Al recrearse la pantalla el texto vuelve del `rememberSaveable`, pero el
    // filtro del ViewModel puede haber arrancado de cero: se le repite una vez.
    LaunchedEffect(Unit) {
        if (field.text != query) onQueryChange(field.text)
    }

    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = textAlpha(0.40f),
            modifier = Modifier.size(16.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (field.text.isEmpty()) {
                Text(
                    text = stringResource(R.string.lib_search),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textAlpha(0.40f),
                )
            }
            BasicTextField(
                value = field,
                onValueChange = {
                    field = it
                    onQueryChange(it.text)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground,
                ),
                cursorBrush = SolidColor(Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * La lista de juegos, paginada de [PAGE_SIZE] en [PAGE_SIZE].
 *
 * La página se reinicia con [query] como llave de un `remember` normal, no
 * [rememberSaveable]: además de resetear al filtrar, así se pierde al volver
 * de una partida. La biblioteca es la misma entrada del grafo de navegación
 * antes y después de jugar, así que sin esto `rememberSaveable` restauraría
 * la página en la que se había quedado la última vez, en vez de arrancar
 * siempre en la primera al salir del juego.
 */
@Composable
private fun LibraryList(
    roms: List<LibraryItem>,
    query: String,
    hasNoResults: Boolean,
    onOpen: (RomInfo) -> Unit,
    onOptions: (LibraryItem) -> Unit,
    onEditCover: (LibraryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    var page by remember(query) { mutableIntStateOf(0) }
    val pageCount = ((roms.size - 1) / PAGE_SIZE + 1).coerceAtLeast(1)
    val currentPage = page.coerceIn(0, pageCount - 1)
    val pageItems = roms.drop(currentPage * PAGE_SIZE).take(PAGE_SIZE)

    Column(modifier = modifier) {
        Box(modifier = Modifier.weight(1f)) {
            if (hasNoResults) {
                Text(
                    text = stringResource(R.string.lib_no_results, query),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textAlpha(0.50f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 40.dp, vertical = 40.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(pageItems, key = { it.rom.id }) { item ->
                        RomRow(
                            item = item,
                            onOpen = { onOpen(item.rom) },
                            onOptions = { onOptions(item) },
                            onEditCover = { onEditCover(item) },
                        )
                    }
                }
            }

            // Degradado que funde la lista con lo que venga debajo (el
            // paginador o el botón fijo de abajo).
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(28.dp)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Background)),
                    ),
            )
        }

        if (pageCount > 1) {
            PageControls(
                page = currentPage,
                pageCount = pageCount,
                onPrevious = { page = currentPage - 1 },
                onNext = { page = currentPage + 1 },
            )
        }
    }
}

@Composable
private fun PageControls(page: Int, pageCount: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PageButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.cd_page_previous),
            enabled = page > 0,
            onClick = onPrevious,
        )
        Text(
            text = stringResource(R.string.lib_page_indicator, page + 1, pageCount),
            style = MaterialTheme.typography.labelMedium,
            color = textAlpha(0.55f),
        )
        PageButton(
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.cd_page_next),
            enabled = page < pageCount - 1,
            onClick = onNext,
        )
    }
}

@Composable
private fun PageButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(if (enabled) Glass else Color(0x08E9E9ED))
            .border(1.dp, if (enabled) GlassBorder else Color(0x0FE9E9ED), shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) textAlpha(0.75f) else textAlpha(0.28f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Un juego de la biblioteca. */
@Composable
private fun RomRow(
    item: LibraryItem,
    onOpen: () -> Unit,
    onOptions: () -> Unit,
    onEditCover: () -> Unit,
) {
    val rom = item.rom
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Surface)
            .border(1.dp, Color(0x12E9E9ED), shape)
            .padding(11.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            Cover(
                rom = rom,
                coverPath = item.coverPath,
                coverVersion = item.coverVersion,
                onClick = onEditCover,
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rom.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (rom.isAvailable) {
                        "${relativeWhen(rom.lastPlayedAt)} · ${formatSize(rom.sizeBytes)}"
                    } else {
                        stringResource(R.string.lib_unavailable)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (rom.isAvailable) textAlpha(0.48f) else Danger,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }

            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.cd_rom_options, rom.displayName),
                tint = textAlpha(0.32f),
                modifier = Modifier
                    .size(20.dp)
                    .clickable(onClick = onOptions),
            )
        }

        if (!rom.isAvailable) return@Column

        RomAction(
            text = stringResource(R.string.lib_open),
            icon = Icons.Filled.PlayArrow,
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )
    }
}

@Composable
private fun RomAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .height(34.dp)
            .clip(shape)
            .background(Accent.copy(alpha = 0.16f))
            .border(1.dp, Accent.copy(alpha = 0.50f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Accent200, modifier = Modifier.size(13.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Accent200,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * La portada del juego, y el botón para cambiarla.
 *
 * Sin portada personalizada se muestra el icono extraído del banner del
 * propio cartucho (fase 3.1/3.2) en vez de un marcador genérico: es una de
 * las mejoras que este proyecto tiene gratis frente al de GBA, que no tiene
 * ninguna portada automática. `FilterQuality.None` mantiene el pixel art del
 * icono nítido al ampliarlo, igual que el modo «Nítido» de la pantalla de
 * juego.
 *
 * Se toca la propia imagen en lugar de añadir un botón aparte. Un botón grande
 * junto al título rompería la tarjeta por una acción que se usa una vez por
 * juego; el lápiz en la esquina basta para que se vea que la imagen responde.
 */
@Composable
private fun Cover(
    rom: RomInfo,
    coverPath: String?,
    coverVersion: Long,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    // La clave incluye la versión: la ruta no cambia al reemplazar la imagen, y
    // sin esto seguiría en pantalla la portada vieja.
    val customCover by produceState<ImageBitmap?>(null, coverPath, coverVersion) {
        value = coverPath?.let {
            withContext(Dispatchers.IO) {
                runCatching { BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val bannerIcon = remember(rom.id, rom.icon) {
        rom.icon
            ?.takeIf { it.size == DsBanner.ICON_SIZE * DsBanner.ICON_SIZE }
            ?.let { pixels ->
                Bitmap.createBitmap(pixels, DsBanner.ICON_SIZE, DsBanner.ICON_SIZE, Bitmap.Config.ARGB_8888)
                    .asImageBitmap()
            }
    }

    val label = stringResource(
        if (coverPath != null) R.string.cd_cover_custom else R.string.cd_cover_default,
        rom.displayName,
    )

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B1E2E), Color(0xFF121422))))
            .border(1.dp, Color(0x12E9E9ED), shape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        when {
            customCover != null -> Image(
                bitmap = customCover!!,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            bannerIcon != null -> Image(
                bitmap = bannerIcon,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize().padding(6.dp),
            )

            else -> Icon(
                imageVector = if (rom.isAvailable) Icons.Filled.Image else Icons.Filled.WarningAmber,
                contentDescription = null,
                tint = if (rom.isAvailable) Neutral700 else Danger,
                modifier = Modifier.size(18.dp),
            )
        }

        // Marca de que se puede tocar. Va en la esquina y sobre un disco oscuro
        // para que se lea igual encima de una portada clara o de una oscura.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xCC0B0D16)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                tint = textAlpha(0.75f),
                modifier = Modifier.size(9.dp),
            )
        }
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 22.dp, vertical = 26.dp)) {
        BrandHeader()
        Text(
            text = stringResource(R.string.lib_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 7.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, Neutral700, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = Neutral600,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = stringResource(R.string.em_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 14.dp),
            )
            Text(
                text = stringResource(R.string.em_body),
                style = MaterialTheme.typography.bodyMedium,
                color = textAlpha(0.52f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).width(230.dp),
            )
        }

        AccentButton(
            text = stringResource(R.string.em_cta),
            icon = Icons.Filled.Add,
            onClick = onAdd,
        )
    }
}

@Composable
private fun LibraryToast(text: String, onDismiss: () -> Unit) {
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
 * Opciones de un juego: extraer, importar o borrar su partida de batería, y
 * quitarlo de la biblioteca.
 *
 * Se dibuja dentro de la propia pantalla y no como diálogo de Material, por dos
 * razones: el sistema visual no usa diálogos en ningún sitio, y una ventana
 * emergente abre su propia raíz de composición, donde no llega el idioma
 * elegido en la aplicación y los textos volverían al del sistema.
 *
 * Importar y borrar sustituyen el contenido por una confirmación —igual que al
 * sobrescribir o borrar un save state— porque las dos son irreversibles y un
 * `.sav` de DS no lleva ninguna identidad dentro: a diferencia de los save
 * states, aquí no hay forma de comprobar que el archivo importado sea de este
 * juego, así que la única red de seguridad es poder empezar en blanco otra vez.
 */
@Composable
private fun RomOptionsSheet(
    rom: RomInfo,
    hasBatterySave: Boolean,
    pendingSavImport: PendingSavImport?,
    pendingSavDelete: Boolean,
    onExtractSav: () -> Unit,
    onImportSav: () -> Unit,
    onConfirmSavImport: () -> Unit,
    onCancelSavImport: () -> Unit,
    onDeleteSav: () -> Unit,
    onConfirmSavDelete: () -> Unit,
    onCancelSavDelete: () -> Unit,
    onKeep: () -> Unit,
    onDeleteAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val confirming = pendingSavImport != null || pendingSavDelete
    val onCancelConfirmation = if (pendingSavImport != null) onCancelSavImport else onCancelSavDelete

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                // Con una confirmación en pantalla, tocar fuera la cancela en vez
                // de cerrarlo todo: nunca cierra ejecutando la acción destructiva.
                onClick = { if (confirming) onCancelConfirmation() else onDismiss() },
            )
            .padding(22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(22.dp),
        ) {
            if (pendingSavImport != null || pendingSavDelete) {
                val title: String
                val body: String
                val confirmLabel: String
                val onConfirm: () -> Unit
                if (pendingSavImport != null) {
                    title = stringResource(R.string.lib_sav_replace_title, rom.displayName)
                    body = stringResource(R.string.lib_sav_replace_body)
                    confirmLabel = stringResource(R.string.st_replace)
                    onConfirm = onConfirmSavImport
                } else {
                    title = stringResource(R.string.lib_sav_delete_title, rom.displayName)
                    body = stringResource(R.string.lib_sav_delete_body)
                    confirmLabel = stringResource(R.string.lib_sav_delete)
                    onConfirm = onConfirmSavDelete
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = textAlpha(0.58f),
                    modifier = Modifier.padding(top = 8.dp),
                )
                Column(
                    modifier = Modifier.padding(top = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SheetAction(text = confirmLabel, onClick = onConfirm, contentColor = Danger)
                    SheetAction(
                        text = stringResource(R.string.st_cancel),
                        onClick = onCancelConfirmation,
                    )
                }
                return@Column
            }

            Text(
                text = stringResource(R.string.lib_options_title, rom.displayName),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.lib_remove_body),
                style = MaterialTheme.typography.bodySmall,
                color = textAlpha(0.58f),
                modifier = Modifier.padding(top = 8.dp),
            )

            Column(
                modifier = Modifier.padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetAction(text = stringResource(R.string.lib_remove_keep), onClick = onKeep)
                SheetAction(
                    text = stringResource(R.string.lib_remove_delete),
                    onClick = onDeleteAll,
                    contentColor = Danger,
                )
                SheetAction(
                    text = stringResource(R.string.lib_sav_extract),
                    onClick = onExtractSav,
                    enabled = hasBatterySave,
                )
                SheetAction(text = stringResource(R.string.lib_sav_import), onClick = onImportSav)
                SheetAction(
                    text = stringResource(R.string.lib_sav_delete),
                    onClick = onDeleteSav,
                    contentColor = Danger,
                    enabled = hasBatterySave,
                )
                SheetAction(text = stringResource(R.string.st_cancel), onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SheetAction(
    text: String,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassBorder, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) contentColor else textAlpha(0.30f),
        )
    }
}

/**
 * Fecha de la última partida en lenguaje corriente. Es lo que el usuario
 * reconoce de un vistazo; la fecha exacta no le dice nada.
 */
@Composable
private fun relativeWhen(timestamp: Long?): String {
    if (timestamp == null) return stringResource(R.string.when_never)

    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(timestamp).atZone(zone)
    val days = ChronoUnit.DAYS.between(moment.toLocalDate(), LocalDate.now(zone))

    return when {
        days <= 0L -> stringResource(R.string.when_today, TIME_FORMATTER.format(moment))
        days == 1L -> stringResource(R.string.when_yesterday)
        days < 7L -> stringResource(R.string.when_days_ago, days.toInt())
        days < 14L -> stringResource(R.string.when_last_week)
        else -> DATE_FORMATTER.format(moment)
    }
}

/** Las ROMs de DS pueden pasar de 1 GB (cartuchos DSi); MB solo se queda corto rápido. */
private fun formatSize(bytes: Long): String {
    val megabytes = bytes / (1024f * 1024f)
    return if (megabytes >= 1024f) {
        String.format(Locale.getDefault(), "%.1f GB", megabytes / 1024f)
    } else {
        String.format(Locale.getDefault(), "%.0f MB", megabytes)
    }
}

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
