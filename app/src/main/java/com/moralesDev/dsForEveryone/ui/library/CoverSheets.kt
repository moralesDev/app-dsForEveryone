package com.moralesDev.dsForEveryone.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.data.cover.CoverImage
import com.moralesDev.dsForEveryone.data.cover.PickedImage
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Danger
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.ScrimColor
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import kotlin.math.min
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.cover_add
import com.moralesDev.dsForEveryone.resources.cover_add_title
import com.moralesDev.dsForEveryone.resources.cover_change
import com.moralesDev.dsForEveryone.resources.cover_change_title
import com.moralesDev.dsForEveryone.resources.cover_crop_body
import com.moralesDev.dsForEveryone.resources.cover_crop_square
import com.moralesDev.dsForEveryone.resources.cover_crop_title
import com.moralesDev.dsForEveryone.resources.cover_delete
import com.moralesDev.dsForEveryone.resources.cover_delete_body
import com.moralesDev.dsForEveryone.resources.cover_delete_title
import com.moralesDev.dsForEveryone.resources.cover_save
import com.moralesDev.dsForEveryone.resources.st_cancel
import com.moralesDev.dsForEveryone.resources.st_delete

/**
 * Qué hacer con la portada de un juego.
 *
 * Se dibuja dentro de la pantalla y no como diálogo de Material: una ventana
 * emergente abre su propia raíz de composición y sus textos se saldrían del
 * idioma elegido en la aplicación.
 */
@Composable
fun CoverOptionsSheet(
    gameName: String,
    hasCover: Boolean,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    CoverSheetFrame(
        title = stringResource(
            if (hasCover) Res.string.cover_change_title else Res.string.cover_add_title,
        ),
        subtitle = gameName,
        onDismiss = onDismiss,
    ) {
        CoverAction(
            text = stringResource(if (hasCover) Res.string.cover_change else Res.string.cover_add),
            icon = if (hasCover) Icons.Filled.SwapHoriz else Icons.Filled.AddPhotoAlternate,
            onClick = onPick,
        )
        if (hasCover) {
            CoverAction(
                text = stringResource(Res.string.cover_delete),
                icon = Icons.Filled.DeleteOutline,
                tint = Danger,
                onClick = onDelete,
            )
        }
        CoverAction(text = stringResource(Res.string.st_cancel), onClick = onDismiss)
    }
}

/** Confirmación antes de quitar la portada: recupera el icono del banner. */
@Composable
fun CoverDeleteSheet(gameName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    CoverSheetFrame(
        title = stringResource(Res.string.cover_delete_title),
        subtitle = gameName,
        body = stringResource(Res.string.cover_delete_body),
        onDismiss = onDismiss,
    ) {
        CoverAction(text = stringResource(Res.string.st_cancel), onClick = onDismiss)
        CoverAction(
            text = stringResource(Res.string.st_delete),
            icon = Icons.Filled.DeleteOutline,
            tint = Danger,
            onClick = onConfirm,
        )
    }
}

/**
 * Encuadre de la portada.
 *
 * La imagen se mueve y se acerca con los dedos dentro de un marco cuadrado, y lo
 * que quede dentro es la portada. No es un editor: no hay filtros, ni rotación,
 * ni relación libre. Sólo decidir qué parte se ve.
 *
 * El recorte **nunca deforma**. El lado del cuadrado sale del menor lado de la
 * imagen, así que una foto apaisada pierde los bordes en lugar de aplastarse.
 * Una imagen ya cuadrada no necesita tocar nada: se ve entera y basta con
 * guardar.
 *
 * Las tres cifras que definen el encuadre —acercamiento y centro— se envían tal
 * cual a `CoverImage.cropWindow`, que es donde vive la aritmética y donde se
 * comprueba en las pruebas.
 */
@Composable
fun CoverCropSheet(
    gameName: String,
    image: PickedImage,
    isSaving: Boolean,
    onSave: (zoom: Float, centerX: Float, centerY: Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var zoom by remember(image) { mutableFloatStateOf(1f) }
    var centerX by remember(image) { mutableFloatStateOf(0.5f) }
    var centerY by remember(image) { mutableFloatStateOf(0.5f) }

    val bitmap = remember(image) { image.bitmap.asImageBitmap() }

    CoverSheetFrame(
        title = stringResource(Res.string.cover_crop_title),
        subtitle = gameName,
        body = if (image.isSquare) {
            stringResource(Res.string.cover_crop_square)
        } else {
            stringResource(Res.string.cover_crop_body)
        },
        onDismiss = onDismiss,
    ) {
        // La vista previa **es** el recorte: pide el mismo cuadrado a
        // `CoverImage.cropWindow` que se usará al guardar y lo estira al marco.
        // No hay dos cálculos que puedan discrepar, así que lo que se ve aquí es
        // literalmente lo que se guarda.
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0B0D16))
                .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
                .pointerInput(image) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                        // El marco enseña un cuadrado de lado `menorLado / zoom`
                        // de la imagen, así que arrastrar el ancho entero del
                        // marco mueve el centro justo esa porción.
                        val visible = min(image.width, image.height) / zoom
                        val frame = size.width.toFloat().coerceAtLeast(1f)
                        centerX = (centerX - pan.x / frame * visible / image.width)
                            .coerceIn(0f, 1f)
                        centerY = (centerY - pan.y / frame * visible / image.height)
                            .coerceIn(0f, 1f)
                    }
                },
        ) {
            val window = CoverImage.cropWindow(image.width, image.height, zoom, centerX, centerY)
            drawImage(
                image = bitmap,
                srcOffset = IntOffset(window.left, window.top),
                srcSize = IntSize(window.side, window.side),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                filterQuality = FilterQuality.Medium,
            )
        }

        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CoverButton(
                text = stringResource(Res.string.st_cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            CoverButton(
                text = stringResource(Res.string.cover_save),
                accent = true,
                enabled = !isSaving,
                onClick = { onSave(zoom, centerX, centerY) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CoverSheetFrame(
    title: String,
    subtitle: String,
    body: String? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            )
            // La biblioteca aplica los márgenes del sistema a su columna, pero
            // esta hoja se dibuja por encima y fuera de ella: sin esto, el
            // último botón queda debajo de la barra de navegación.
            .windowInsetsPadding(WindowInsets.systemBars),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 22.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = textAlpha(0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (body != null) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    color = textAlpha(0.58f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun CoverAction(
    text: String,
    icon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = tint)
    }
}

@Composable
private fun CoverButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(if (accent) Accent.copy(alpha = 0.16f) else Glass)
            .border(1.dp, if (accent) Accent.copy(alpha = 0.55f) else GlassBorder, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = when {
                !enabled -> textAlpha(0.30f)
                accent -> Accent200
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Tope de acercamiento. Más allá la portada de 512 px se vería pastosa. */
private const val MAX_ZOOM = 4f
