package com.moralesDev.dsForEveryone.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.Accent200
import com.moralesDev.dsForEveryone.ui.theme.Accent400
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.GlassStrong
import com.moralesDev.dsForEveryone.ui.theme.Neutral600
import com.moralesDev.dsForEveryone.ui.theme.Surface
import com.moralesDev.dsForEveryone.ui.theme.textAlpha

/**
 * Piezas del sistema visual Nocturne compartidas por todas las pantallas.
 *
 * El material es siempre el mismo: vidrio traslúcido (relleno al 7–12 % de
 * blanco, borde de 1 px) sobre fondo oscuro, y una única acción de acento por
 * pantalla dibujada en contorno, nunca rellena. Al pulsar, todo encoge
 * ligeramente en vez de cambiar de tono: es el gesto que da sensación física.
 */

/** Rótulo de sección en versalitas espaciadas, en color de acento. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = Accent400,
        modifier = modifier,
    )
}

/** Acción principal: contorno de acento, 50 dp de alto. Una por pantalla. */
@Composable
fun AccentButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 50.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, label = "accent-scale")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) Accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, Accent, RoundedCornerShape(12.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Accent200, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.titleSmall, color = Accent200)
    }
}

/** Botón de vidrio cuadrado con icono: ajustes, volver, cerrar. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    cornerRadius: Dp = 11.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, label = "glass-icon-scale")
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(shape)
            .background(if (pressed) GlassStrong else Glass)
            .border(1.dp, GlassBorder, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = textAlpha(0.75f),
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Contenedor de filas de ajustes: tarjeta de superficie con separadores finos. */
@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .border(1.dp, Color(0x0FE9E9ED), RoundedCornerShape(14.dp)),
    ) {
        content()
    }
}

@Composable
fun SettingsDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x0FE9E9ED)),
    )
}

/**
 * Fila de ajuste: etiqueta a la izquierda, control a la derecha.
 *
 * [onClick] la convierte en navegable y añade realimentación al pulsar.
 * [description] añade una segunda línea para los ajustes cuyo nombre no basta
 * para saber qué hacen.
 */
@Composable
fun SettingsRow(
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = textAlpha(0.44f),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing()
    }
}

/**
 * Interruptor del diseño: cápsula de 38 × 22 con perilla de 15.
 * No se usa el `Switch` de Material porque su tamaño y su color de pista no
 * encajan con el resto de controles de vidrio.
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val knobOffset by animateDpAsState(if (checked) 18.dp else 3.dp, label = "switch-knob")

    Box(
        modifier = modifier
            .width(38.dp)
            .height(22.dp)
            .clip(CircleShape)
            .background(if (checked) Accent.copy(alpha = 0.40f) else Glass)
            .border(
                width = 1.dp,
                color = if (checked) Accent.copy(alpha = 0.60f) else GlassBorder,
                shape = CircleShape,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(start = knobOffset)
                .align(Alignment.CenterStart)
                .size(15.dp)
                .clip(CircleShape)
                .background(if (checked) Accent100 else Neutral600),
        )
    }
}

/** Control segmentado de dos opciones: relación de aspecto, filtro, idioma. */
@Composable
fun SegmentedPair(
    first: String,
    second: String,
    firstSelected: Boolean,
    onSelect: (firstChosen: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0x4D000000))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SegmentedOption(first, firstSelected) { onSelect(true) }
        SegmentedOption(second, !firstSelected) { onSelect(false) }
    }
}

@Composable
private fun SegmentedOption(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) Accent.copy(alpha = 0.22f) else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) Accent100 else textAlpha(0.45f),
        )
    }
}

/**
 * Campo de texto del sistema Nocturne: mismo vidrio y borde que el resto de
 * superficies. Se usa tanto para el código de una clave (varias líneas) como
 * para su nombre (una sola), variando [minLines] y [singleLine].
 *
 * No hay foco visual propio más allá del borde de vidrio de siempre: pedir
 * atención con un halo de acento sería tratar de escribir como la acción
 * principal de la pantalla, y aquí no lo es.
 */
@Composable
fun GlassTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty() && placeholder != null) {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = textAlpha(0.34f),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(Accent200),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Píldora de estado que acompaña a cada juego de la biblioteca. */
@Composable
fun Tag(
    text: String,
    icon: ImageVector,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (highlighted) Accent.copy(alpha = 0.18f) else Glass)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) Accent200 else textAlpha(0.55f),
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = text,
            style = LocalTextStyle.current.copy(fontSize = MaterialTheme.typography.labelSmall.fontSize),
            color = if (highlighted) Accent200 else textAlpha(0.55f),
        )
    }
}
