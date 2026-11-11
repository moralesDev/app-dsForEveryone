package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.emulator.FrameBuffer
import com.moralesDev.dsForEveryone.emulator.layout.ScreenPlacement

/**
 * Las dos pantallas de la DS: el último fotograma del core (256×384, R,G,B,A)
 * pintado en dos rectángulos, la mitad de arriba en
 * [ScreenPlacement.topScreen] y la de abajo en [ScreenPlacement.touchScreen].
 * Una pantalla de área cero no se pinta.
 *
 * Cada plataforma lo presenta con lo suyo: GL ES con `DsGLRenderer` en
 * Android (docs/pantallas.md) y Skia, el mismo lienzo de Compose, en iOS.
 * [frame] se consulta en cada fotograma de pantalla, no por recomposición.
 */
@Composable
internal expect fun GameVideo(
    frame: () -> FrameBuffer?,
    placement: ScreenPlacement?,
    filter: ImageFilter,
    modifier: Modifier = Modifier,
)

/**
 * Libera la orientación mientras haya un mando físico conectado y la
 * pantalla de juego esté visible. Sin mando, la partida se queda en vertical:
 * encajar doce mandos táctiles en horizontal sin tapar la pantalla inferior
 * no compensaba (ver docs/entrada.md, «Mando físico»), y un mando físico es
 * justo lo que cambia esa cuenta.
 */
@Composable
internal expect fun FreeOrientationWhileGamepadConnected(gamepadConnected: Boolean)
