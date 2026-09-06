package com.moralesDev.dsForEveryone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moralesDev.dsForEveryone.ui.components.AppLogo
import com.moralesDev.dsForEveryone.ui.components.BrandWordmark
import com.moralesDev.dsForEveryone.ui.theme.Background
import com.moralesDev.dsForEveryone.ui.theme.DsEmuTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Única Activity de la aplicación. El manifiesto fija vertical por defecto;
 * `GameScreen` (fase 2) será la única pantalla que la libere en tiempo de
 * ejecución para poder rotar durante la partida.
 *
 * De momento pinta sólo el logotipo y el nombre centrados: la navegación
 * real llega en el siguiente entregable.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            DsEmuTheme {
                CenteredBrand()
            }
        }
    }
}

@Composable
private fun CenteredBrand() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        AppLogo(size = 96.dp)
        BrandWordmark()
    }
}
