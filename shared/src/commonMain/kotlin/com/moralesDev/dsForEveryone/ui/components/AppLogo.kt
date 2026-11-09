package com.moralesDev.dsForEveryone.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.moralesDev.dsForEveryone.ui.theme.Accent300
import com.moralesDev.dsForEveryone.ui.theme.Accent400
import com.moralesDev.dsForEveryone.ui.theme.Inter
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.app_logo
import com.moralesDev.dsForEveryone.resources.app_name
import org.jetbrains.compose.resources.painterResource

/**
 * La marca: el logo de DsForEveryone (art/logo.png, que también es el icono
 * de la app en Android y en iOS; ver tools/make-icons.swift).
 *
 * Es una imagen, no un dibujo: hasta noviembre de 2026 era una consola de
 * contorno pintada con Canvas en los colores del tema. Se usa a tamaños de
 * 19 dp (cabecera de la biblioteca) a 112 dp (arranque); a los más pequeños
 * el texto del logo no se lee, pero la figura sí, que es lo que identifica.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 64.dp) {
    Image(
        painter = painterResource(Res.drawable.app_logo),
        contentDescription = null,
        modifier = modifier.size(size),
    )
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
