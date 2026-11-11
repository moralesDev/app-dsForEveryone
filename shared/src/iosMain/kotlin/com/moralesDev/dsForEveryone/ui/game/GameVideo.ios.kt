package com.moralesDev.dsForEveryone.ui.game

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.emulator.DsVideo
import com.moralesDev.dsForEveryone.emulator.FrameBuffer
import com.moralesDev.dsForEveryone.emulator.layout.LayoutRect
import com.moralesDev.dsForEveryone.emulator.layout.ScreenPlacement
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.interpretCPointer
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.posix.memcpy
import kotlin.math.roundToInt

/**
 * Skia, el mismo lienzo con el que Compose pinta todo lo demás: el fotograma
 * se copia una vez por refresco de pantalla en un `Bitmap` propio y se pinta
 * en los dos rectángulos. «Nítido» es `FilterQuality.None` (vecino más
 * próximo) y «Suavizado», `Low` (bilineal): el mismo par que
 * `GL_NEAREST`/`GL_LINEAR` en Android.
 *
 * La copia es un `memcpy` directo a los píxeles del `Bitmap`, sin pasar por
 * un `ByteArray` intermedio ni reservar nada por fotograma.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun GameVideo(
    frame: () -> FrameBuffer?,
    placement: ScreenPlacement?,
    filter: ImageFilter,
    modifier: Modifier,
) {
    val bitmap = remember {
        Bitmap().apply {
            allocPixels(ImageInfo(DsVideo.WIDTH, DsVideo.HEIGHT, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap.close() } }
    val image = remember(bitmap) { bitmap.asComposeImageBitmap() }

    val currentFrame by rememberUpdatedState(frame)
    var presented by remember { mutableLongStateOf(0L) }
    LaunchedEffect(bitmap) {
        while (true) {
            withFrameNanos { now ->
                val source = currentFrame() ?: return@withFrameNanos
                val pixels = bitmap.peekPixels() ?: return@withFrameNanos
                source.bytes.usePinned { pinned ->
                    memcpy(interpretCPointer<ByteVar>(pixels.addr), pinned.addressOf(0), source.size.convert())
                }
                bitmap.notifyPixelsChanged()
                presented = now
            }
        }
    }

    val quality = when (filter) {
        ImageFilter.SHARP -> FilterQuality.None
        ImageFilter.SMOOTH -> FilterQuality.Low
    }
    Canvas(modifier) {
        // Leerlo es lo que repinta el lienzo en cada fotograma nuevo.
        if (presented == 0L) return@Canvas
        val current = placement ?: return@Canvas
        fun screen(rect: LayoutRect, sourceTop: Int) {
            if (rect.width <= 0f || rect.height <= 0f) return
            drawImage(
                image = image,
                srcOffset = IntOffset(0, sourceTop),
                srcSize = IntSize(DsVideo.WIDTH, DsVideo.HEIGHT / 2),
                dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                dstSize = IntSize(rect.width.roundToInt(), rect.height.roundToInt()),
                filterQuality = quality,
            )
        }
        screen(current.topScreen, sourceTop = 0)
        screen(current.touchScreen, sourceTop = DsVideo.HEIGHT / 2)
    }
}

/** De momento nada: la orientación en iPad la decide `Info.plist` (docs/ios.md). */
@Composable
internal actual fun FreeOrientationWhileGamepadConnected(gamepadConnected: Boolean) = Unit
