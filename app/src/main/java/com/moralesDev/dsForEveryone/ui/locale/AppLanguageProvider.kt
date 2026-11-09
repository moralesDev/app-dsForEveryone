package com.moralesDev.dsForEveryone.ui.locale

import android.content.res.Configuration
import android.os.LocaleList
import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.os.ConfigurationCompat
import com.moralesDev.dsForEveryone.data.settings.AppLanguage
import java.util.Locale


/**
 * Cambia el idioma de la aplicación en caliente, sin tocar los ajustes del
 * sistema ni reiniciar la Activity.
 *
 * `stringResource` resuelve contra `LocalResources`, que a su vez se calcula a
 * partir de `LocalContext` y `LocalConfiguration`. Sustituyendo esos dos por un
 * contexto con la configuración regional elegida, todo el árbol de Compose
 * vuelve a leer los textos del idioma nuevo en la siguiente recomposición.
 *
 * Se envuelve el contexto en un [ContextThemeWrapper] en lugar de usar
 * `createConfigurationContext`, que devuelve un `ContextImpl` suelto. La
 * diferencia importa: `ViewModelProvider` y lo que cuelga de la Activity se
 * localizan subiendo por la cadena de `ContextWrapper`, y con el contexto
 * suelto esa cadena se rompe. (Lo descubrió `hiltViewModel()`, que fallaba;
 * hoy los ViewModels los da Koin, pero la cadena tiene que seguir entera.)
 *
 * La configuración de partida para el `ContextThemeWrapper` es la del sistema
 * ([LocalConfiguration] ambiental, la que Compose actualiza sola), no la del
 * contexto recordado: la Activity sobrevive a los cambios de configuración
 * declarados (rotación incluida, ver `AndroidManifest.xml`), así que `context`
 * no cambia de identidad al rotar. Si `remember` sólo dependiera de `language`
 * y `context`, el envoltorio se calcularía una vez al arrancar —en vertical— y
 * todo el árbol, `GameScreen` incluido, seguiría leyendo esa orientación
 * congelada para siempre.
 */
@Composable
fun ProvideAppLanguage(
    language: AppLanguage,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemConfiguration = LocalConfiguration.current
    // La Configuration del sistema siempre trae al menos un locale; no hace
    // falta (ni conviene) un `?: Locale.getDefault()` de repuesto para algo
    // que no puede pasar.
    val systemLocale = ConfigurationCompat.getLocales(systemConfiguration)[0]!!

    // Los textos son de Compose Multiplatform (docs/ios.md, iOS-3 paso 3), y su
    // stringResource no lee LocalConfiguration: elige el idioma por
    // `Locale.current`, que en Android sale de `Locale.getDefault()`. Por eso
    // el idioma de la app pasa también a ser el del proceso, con la región
    // real del teléfono (para formatos de números y fechas cercanos a los
    // del sistema). Lo sustituido en LocalContext/LocalConfiguration sigue
    // haciendo falta para lo que aún lee recursos de Android.
    remember(language, systemLocale) {
        Locale.setDefault(
            Locale.Builder().setLanguage(language.tag).setRegion(systemLocale.country).build(),
        )
    }

    val localizedContext = remember(language, context, systemConfiguration) {
        val configuration = Configuration(systemConfiguration).apply {
            setLocales(LocaleList(Locale.forLanguageTag(language.tag)))
        }
        ContextThemeWrapper(context, /* themeResId = */ 0).apply {
            applyOverrideConfiguration(configuration)
        }
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalSystemLocale provides systemLocale,
    ) {
        // `Locale.current` no es estado de Compose: cambiarlo no recompone a
        // nadie. La `key` rehace la interfaz cuando cambia el idioma para que
        // cada stringResource vuelva a leerlo. Lo que no se pierde con ello
        // —la pantalla en la que se está, sus ViewModels— vive por encima:
        // el NavController lo crea MainActivity fuera de esta función.
        key(language) { content() }
    }
}
