package com.moralesDev.dsForEveryone.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent300
import com.moralesDev.dsForEveryone.ui.theme.Accent400
import com.moralesDev.dsForEveryone.ui.theme.Accent700
import com.moralesDev.dsForEveryone.ui.theme.Inter
import com.moralesDev.dsForEveryone.ui.theme.ScreenBezel
import com.moralesDev.dsForEveryone.ui.theme.TextPrimary
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.app_name

/**
 * La marca: una consola de contorno con su pantalla, su cruceta y sus dos
 * botones.
 *
 * Se dibuja en vez de empaquetarse como recurso vectorial para que tome los
 * colores del tema y quede nítida a cualquier tamaño, desde los 19 dp de la
 * cabecera de la biblioteca hasta los 112 dp del arranque.
 *
 * Las medidas son las del diseño, en un lienzo de 64 unidades. Los grosores de
 * trazo se escalan con el tamaño pero con un mínimo: por debajo de cierto punto
 * una línea proporcional desaparecería y el dibujo se rompería.
 *
 * Hereda la geometría de `app-emulador`: una sola pantalla, no dos. La DS
 * tiene su propia carcasa de dos pantallas en `ScreenLayout` (fase 2); esta
 * marca es la identidad de la aplicación, no una vista previa del hardware.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Canvas(modifier = modifier.size(size)) {
        val unit = this.size.minDimension / 64f
        fun stroke(width: Float, minimumDp: Float) =
            (width * unit).coerceAtLeast(minimumDp.dp.toPx())

        // Carcasa.
        drawRoundRect(
            color = Accent,
            topLeft = Offset(5 * unit, 5 * unit),
            size = Size(54 * unit, 54 * unit),
            cornerRadius = CornerRadius(16 * unit),
            style = Stroke(width = stroke(1.8f, 1.4f)),
        )

        // Pantalla.
        drawRoundRect(
            color = ScreenBezel,
            topLeft = Offset(14 * unit, 16 * unit),
            size = Size(36 * unit, 18 * unit),
            cornerRadius = CornerRadius(4.5f * unit),
        )
        drawRoundRect(
            color = Accent700,
            topLeft = Offset(14 * unit, 16 * unit),
            size = Size(36 * unit, 18 * unit),
            cornerRadius = CornerRadius(4.5f * unit),
            style = Stroke(width = stroke(1.1f, 0.8f)),
        )

        // Cruceta.
        val crossStroke = stroke(2.6f, 1.6f)
        drawLine(
            color = TextPrimary,
            start = Offset(18 * unit, 45 * unit),
            end = Offset(27 * unit, 45 * unit),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = TextPrimary,
            start = Offset(22.5f * unit, 40.5f * unit),
            end = Offset(22.5f * unit, 49.5f * unit),
            strokeWidth = crossStroke,
            cap = StrokeCap.Round,
        )

        // Botones A y B.
        drawCircle(Accent, radius = 3.1f * unit, center = Offset(41.5f * unit, 42.5f * unit))
        drawCircle(Accent700, radius = 3.1f * unit, center = Offset(48.5f * unit, 47.5f * unit))
    }
}

/**
 * El nombre con «ForEveryone» en acento. Es la firma tipográfica de la marca y
 * aparece siempre así: en el arranque, en la presentación y en «Acerca de».
 */
@Composable
fun BrandWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 22.sp,
) {
    Text(
        text = buildAnnotatedString {
            append("Ds")
            withStyle(SpanStyle(color = Accent300)) { append("ForEveryone") }
        },
        style = TextStyle(
            fontFamily = Inter,
            fontWeight = FontWeight.Medium,
            fontSize = fontSize,
            letterSpacing = (-0.02).em,
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier,
    )
}

/**
 * Cabecera de marca de las pantallas de biblioteca: la consola en pequeño y el
 * nombre en versalitas. Va encima del título para que el usuario sepa siempre
 * en qué aplicación está sin robarle sitio al contenido.
 */
@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        AppLogo(size = 19.dp)
        Text(
            text = stringResource(Res.string.app_name).uppercase(),
            style = TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Normal,
                fontSize = 11.sp,
                letterSpacing = 0.1.em,
            ),
            color = Accent400,
        )
    }
}
