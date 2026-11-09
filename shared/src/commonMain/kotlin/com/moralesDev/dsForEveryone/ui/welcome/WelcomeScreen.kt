package com.moralesDev.dsForEveryone.ui.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moralesDev.dsForEveryone.ui.components.AccentButton
import com.moralesDev.dsForEveryone.ui.components.AppLogo
import com.moralesDev.dsForEveryone.ui.components.BrandWordmark
import com.moralesDev.dsForEveryone.ui.theme.Accent
import com.moralesDev.dsForEveryone.ui.theme.Accent100
import com.moralesDev.dsForEveryone.ui.theme.Accent300
import com.moralesDev.dsForEveryone.ui.theme.Accent900
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.Glass
import com.moralesDev.dsForEveryone.ui.theme.GlassBorder
import com.moralesDev.dsForEveryone.ui.theme.textAlpha
import com.moralesDev.dsForEveryone.resources.Res
import com.moralesDev.dsForEveryone.resources.onb_body
import com.moralesDev.dsForEveryone.resources.onb_cta
import com.moralesDev.dsForEveryone.resources.onb_dont
import com.moralesDev.dsForEveryone.resources.onb_f1
import com.moralesDev.dsForEveryone.resources.onb_f1b
import com.moralesDev.dsForEveryone.resources.onb_f2
import com.moralesDev.dsForEveryone.resources.onb_f2b
import com.moralesDev.dsForEveryone.resources.onb_f3
import com.moralesDev.dsForEveryone.resources.onb_f3b
import com.moralesDev.dsForEveryone.resources.onb_next
import com.moralesDev.dsForEveryone.resources.onb_skip
import com.moralesDev.dsForEveryone.resources.onb_sys_body
import com.moralesDev.dsForEveryone.resources.onb_sys_f1
import com.moralesDev.dsForEveryone.resources.onb_sys_f1b
import com.moralesDev.dsForEveryone.resources.onb_sys_f2
import com.moralesDev.dsForEveryone.resources.onb_sys_f2b
import com.moralesDev.dsForEveryone.resources.onb_sys_title
import com.moralesDev.dsForEveryone.resources.onb_title

/**
 * Si el core elegido en la fase 4 necesita BIOS y firmware (melonDS los pide;
 * DeSmuME puede arrancar con BIOS de sustitución), el paso [OnboardingStep.SYSTEM_FILES]
 * se muestra; si no, la bienvenida se queda en un único paso, igual que en
 * `app-emulador`. De momento sólo está maquetado: la importación real de esos
 * archivos llega en la fase 4.5, y es entonces cuando esta bandera se ata a la
 * decisión de `docs/integracion-core.md` en vez de a una constante.
 */
const val SHOW_SYSTEM_FILES_ONBOARDING_STEP = true

private enum class OnboardingStep {
    INTRO,
    SYSTEM_FILES,
}

/**
 * Pantalla de bienvenida.
 *
 * Explica de qué va la aplicación, sin pedir permisos ni cuentas. Aparece en
 * cada arranque salvo que se marque «no volver a mostrar».
 *
 * El paso de archivos de sistema es condicional: puede que el core elegido no
 * los necesite en absoluto, y forzar un paso vacío sería peor que no tenerlo.
 */
@Composable
fun WelcomeScreen(
    onFinished: (skipWelcome: Boolean) -> Unit,
    showSystemFilesStep: Boolean = SHOW_SYSTEM_FILES_ONBOARDING_STEP,
) {
    var skipNextTime by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(OnboardingStep.INTRO) }

    when (step) {
        OnboardingStep.INTRO -> IntroStep(
            skipNextTime = skipNextTime,
            onSkipNextTimeChange = { skipNextTime = it },
            showsNextStep = showSystemFilesStep,
            onContinue = {
                if (showSystemFilesStep) {
                    step = OnboardingStep.SYSTEM_FILES
                } else {
                    onFinished(skipNextTime)
                }
            },
            onSkip = { onFinished(skipNextTime) },
        )

        OnboardingStep.SYSTEM_FILES -> SystemFilesStep(
            onContinue = { onFinished(skipNextTime) },
        )
    }
}

@Composable
private fun IntroStep(
    skipNextTime: Boolean,
    onSkipNextTimeChange: (Boolean) -> Unit,
    showsNextStep: Boolean,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = 26.dp, end = 26.dp, top = 34.dp, bottom = 26.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            AppLogo(size = 34.dp)
            BrandWordmark(fontSize = 14.5f.sp)
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(Res.string.onb_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.onb_body),
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.62f),
            modifier = Modifier.padding(top = 10.dp),
        )

        Column(
            modifier = Modifier.padding(top = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Feature(
                icon = Icons.Filled.FolderOpen,
                title = stringResource(Res.string.onb_f1),
                body = stringResource(Res.string.onb_f1b),
            )
            Feature(
                icon = Icons.Filled.TouchApp,
                title = stringResource(Res.string.onb_f2),
                body = stringResource(Res.string.onb_f2b),
            )
            Feature(
                icon = Icons.Filled.Save,
                title = stringResource(Res.string.onb_f3),
                body = stringResource(Res.string.onb_f3b),
            )
        }

        Spacer(Modifier.weight(1f))

        DontShowAgain(
            checked = skipNextTime,
            onCheckedChange = onSkipNextTimeChange,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        AccentButton(
            text = stringResource(if (showsNextStep) Res.string.onb_next else Res.string.onb_cta),
            onClick = onContinue,
        )

        Text(
            text = stringResource(Res.string.onb_skip),
            style = MaterialTheme.typography.bodySmall,
            color = textAlpha(0.45f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onSkip)
                .padding(vertical = 13.dp),
        )
    }
}

/**
 * Paso nuevo respecto a `app-emulador`: qué archivos de sistema hacen falta,
 * de dónde salen y que la aplicación no los distribuye. Sin lógica todavía —
 * la importación real por SAF llega en la fase 4.5—, sólo la explicación.
 */
@Composable
private fun SystemFilesStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(start = 26.dp, end = 26.dp, top = 34.dp, bottom = 26.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            AppLogo(size = 34.dp)
            BrandWordmark(fontSize = 14.5f.sp)
        }

        Spacer(Modifier.height(40.dp))

        Text(
            text = stringResource(Res.string.onb_sys_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(Res.string.onb_sys_body),
            style = MaterialTheme.typography.bodyMedium,
            color = textAlpha(0.62f),
            modifier = Modifier.padding(top = 10.dp),
        )

        Column(
            modifier = Modifier.padding(top = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Feature(
                icon = Icons.Filled.Memory,
                title = stringResource(Res.string.onb_sys_f1),
                body = stringResource(Res.string.onb_sys_f1b),
            )
            Feature(
                icon = Icons.Filled.CloudOff,
                title = stringResource(Res.string.onb_sys_f2),
                body = stringResource(Res.string.onb_sys_f2b),
            )
        }

        Spacer(Modifier.weight(1f))

        AccentButton(
            text = stringResource(Res.string.onb_cta),
            onClick = onContinue,
        )
    }
}

@Composable
private fun DontShowAgain(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassBorder, shape)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(19.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) Accent.copy(alpha = 0.55f) else Color.Transparent)
                .border(
                    width = 1.dp,
                    color = if (checked) Accent.copy(alpha = 0.70f) else Color(0x33E9E9ED),
                    shape = RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Accent100,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        Text(
            text = stringResource(Res.string.onb_dont),
            style = MaterialTheme.typography.bodySmall,
            color = textAlpha(0.72f),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Accent900),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Accent300,
                modifier = Modifier.size(17.dp),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = textAlpha(0.50f),
            )
        }
    }
}
