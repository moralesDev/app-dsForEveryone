package com.moralesDev.dsForEveryone.ui.game

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.opengl.GLSurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.emulator.FrameBuffer
import com.moralesDev.dsForEveryone.emulator.layout.ScreenPlacement
import com.moralesDev.dsForEveryone.emulator.render.DsGLRenderer

/** GL ES: el búfer del core es una textura y se pintan dos quads (docs/pantallas.md). */
@Composable
internal actual fun GameVideo(
    frame: () -> FrameBuffer?,
    placement: ScreenPlacement?,
    filter: ImageFilter,
    modifier: Modifier,
) {
    val currentFrame by rememberUpdatedState(frame)
    val renderer = remember { DsGLRenderer(frameProvider = { currentFrame()?.byteBuffer }) }
    LaunchedEffect(placement) { placement?.let(renderer::setPlacement) }
    LaunchedEffect(filter) { renderer.setFilter(filter) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            GLSurfaceView(context).apply {
                setEGLContextClientVersion(2)
                setRenderer(renderer)
                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            }
        },
        onRelease = { it.onPause() },
    )
}

/** El resto de la app sigue en vertical por manifiesto; sólo esta pantalla la libera. */
@Composable
internal actual fun FreeOrientationWhileGamepadConnected(gamepadConnected: Boolean) {
    val context = LocalContext.current
    DisposableEffect(gamepadConnected) {
        if (!gamepadConnected) return@DisposableEffect onDispose {}
        val activity = context.findActivity()
        val previousOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose { activity.requestedOrientation = previousOrientation }
    }
}

private tailrec fun Context.findActivity(): Activity = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> error("No se encontró ninguna Activity en la cadena de contextos")
}
