package com.moralesDev.dsForEveryone.emulator.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.emulator.DsVideo
import com.moralesDev.dsForEveryone.emulator.layout.LayoutRect
import com.moralesDev.dsForEveryone.emulator.layout.ScreenPlacement
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Presenta el framebuffer del core como una textura y dos quads, uno por
 * pantalla. Sustituye el truco de `GbaSurfaceView` de `app-emulador` —el
 * tamaño del búfer *es* el filtro—, que no sobrevive a dos pantallas
 * colocables por separado. Ver docs/pantallas.md.
 *
 * Invariantes de este renderizador:
 * - El framebuffer de 256×384 se sube **una sola vez** por fotograma con
 *   `glTexSubImage2D`. La textura se crea una única vez en
 *   [onSurfaceCreated] y nunca se recrea ni cambia de tamaño.
 * - El filtro ([setFilter]) es un parámetro de textura (`GL_NEAREST` /
 *   `GL_LINEAR`), no una geometría: cambiarlo no toca la textura ni el búfer.
 * - Cambiar de disposición ([setPlacement]) o el tamaño de la superficie
 *   ([onSurfaceChanged]) sólo cambia el viewport y los dos rectángulos
 *   destino, nunca el framebuffer. Es la causa concreta del bug de rotación
 *   de `app-emulador` y aquí no puede reproducirse porque no hay ningún
 *   búfer que redimensionar.
 *
 * [frameProvider] se llama desde el propio hilo de GL en cada [onDrawFrame]:
 * debe ser una lectura barata y sin bloqueo (un campo compartido, no una
 * función `suspend`), igual que [com.moralesDev.dsForEveryone.emulator.InputManager]
 * para los mandos.
 */
class DsGLRenderer(
    private val frameProvider: () -> IntArray?,
) : GLSurfaceView.Renderer {

    @Volatile private var placement: ScreenPlacement? = null
    @Volatile private var filter: ImageFilter = ImageFilter.SHARP
    @Volatile private var surfaceWidth = 1
    @Volatile private var surfaceHeight = 1

    /** Fotogramas presentados por segundo: cuántas veces onDrawFrame llega a dibujar de verdad. */
    @Volatile private var presentedFps = 0

    private var program = 0
    private var textureId = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var textureUniform = 0
    private var appliedFilter: ImageFilter? = null

    private val pixelBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(DsVideo.WIDTH * DsVideo.HEIGHT * BYTES_PER_PIXEL)
            .order(ByteOrder.nativeOrder())

    private val vertexBuffer =
        ByteBuffer.allocateDirect(VERTICES_PER_QUAD * QUADS * FLOATS_PER_VERTEX * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

    private val presentedFramesInWindow = AtomicInteger(0)
    private var windowStartedAtNanos = System.nanoTime()

    /** Se llama desde cualquier hilo; el siguiente `onDrawFrame` recoge el valor. */
    fun setPlacement(newPlacement: ScreenPlacement) {
        placement = newPlacement
    }

    fun setFilter(newFilter: ImageFilter) {
        filter = newFilter
    }

    fun presentedFramesPerSecond(): Int = presentedFps

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        program = buildProgram()
        positionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        texCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        textureUniform = GLES20.glGetUniformLocation(program, "uTexture")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        appliedFilter = null

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
            DsVideo.WIDTH, DsVideo.HEIGHT, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        applyFilter()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width.coerceAtLeast(1)
        surfaceHeight = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        frameProvider()?.let(::uploadFrame)

        placement?.let { current ->
            applyFilter()
            updateGeometry(current)
            drawQuads()
        }

        trackPresentedFps()
    }

    /** Una sola subida por fotograma, al búfer ya asignado en [onSurfaceCreated]. */
    private fun uploadFrame(frame: IntArray) {
        pixelBuffer.clear()
        for (pixel in frame) {
            pixelBuffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            pixelBuffer.put(((pixel shr 8) and 0xFF).toByte()) // G
            pixelBuffer.put((pixel and 0xFF).toByte()) // B
            pixelBuffer.put(((pixel shr 24) and 0xFF).toByte()) // A
        }
        pixelBuffer.position(0)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexSubImage2D(
            GLES20.GL_TEXTURE_2D, 0, 0, 0,
            DsVideo.WIDTH, DsVideo.HEIGHT,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer,
        )
    }

    private fun applyFilter() {
        if (appliedFilter == filter) return
        appliedFilter = filter
        val glFilter = when (filter) {
            ImageFilter.SHARP -> GLES20.GL_NEAREST
            ImageFilter.SMOOTH -> GLES20.GL_LINEAR
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, glFilter)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, glFilter)
    }

    /** Recalcula los dos quads a partir de [ScreenPlacement]; nunca toca la textura. */
    private fun updateGeometry(current: ScreenPlacement) {
        vertexBuffer.clear()
        putQuad(current.topScreen, vTop = 0f, vBottom = 0.5f)
        putQuad(current.touchScreen, vTop = 0.5f, vBottom = 1f)
        vertexBuffer.position(0)
    }

    /** Una pantalla oculta (área cero, ver [ScreenPlacement]) se colapsa a un punto: no se ve nada de ella. */
    private fun putQuad(rect: LayoutRect, vTop: Float, vBottom: Float) {
        if (rect.width <= 0f || rect.height <= 0f) {
            repeat(VERTICES_PER_QUAD) { vertexBuffer.put(0f).put(0f).put(0f).put(0f) }
            return
        }
        val left = pixelToNdcX(rect.left)
        val right = pixelToNdcX(rect.right)
        val top = pixelToNdcY(rect.top)
        val bottom = pixelToNdcY(rect.bottom)

        // GL_TRIANGLE_STRIP: arriba-izq, arriba-der, abajo-izq, abajo-der.
        vertexBuffer.put(left).put(top).put(0f).put(vTop)
        vertexBuffer.put(right).put(top).put(1f).put(vTop)
        vertexBuffer.put(left).put(bottom).put(0f).put(vBottom)
        vertexBuffer.put(right).put(bottom).put(1f).put(vBottom)
    }

    private fun pixelToNdcX(x: Float) = (x / surfaceWidth) * 2f - 1f

    private fun pixelToNdcY(y: Float) = 1f - (y / surfaceHeight) * 2f

    private fun drawQuads() {
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glUniform1i(textureUniform, 0)

        vertexBuffer.position(0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer)

        vertexBuffer.position(2)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertexBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, VERTICES_PER_QUAD)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, VERTICES_PER_QUAD, VERTICES_PER_QUAD)

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(texCoordHandle)
    }

    private fun trackPresentedFps() {
        presentedFramesInWindow.incrementAndGet()
        val now = System.nanoTime()
        val elapsed = now - windowStartedAtNanos
        if (elapsed >= FPS_WINDOW_NANOS) {
            presentedFps = (presentedFramesInWindow.getAndSet(0) * 1_000_000_000L / elapsed).toInt()
            windowStartedAtNanos = now
        }
    }

    private fun buildProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SRC)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SRC)
        val programId = GLES20.glCreateProgram()
        GLES20.glAttachShader(programId, vertexShader)
        GLES20.glAttachShader(programId, fragmentShader)
        GLES20.glLinkProgram(programId)

        val linked = IntArray(1)
        GLES20.glGetProgramiv(programId, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) {
            Log.e(TAG, "Fallo al enlazar el programa: ${GLES20.glGetProgramInfoLog(programId)}")
        }
        return programId
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "Fallo al compilar el shader: ${GLES20.glGetShaderInfoLog(shader)}")
        }
        return shader
    }

    private companion object {
        const val TAG = "DsGLRenderer"
        const val BYTES_PER_PIXEL = 4
        const val VERTICES_PER_QUAD = 4
        const val QUADS = 2
        const val FLOATS_PER_VERTEX = 4 // x, y, u, v
        const val STRIDE_BYTES = FLOATS_PER_VERTEX * 4
        const val FPS_WINDOW_NANOS = 500_000_000L

        const val VERTEX_SHADER_SRC = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
                vTexCoord = aTexCoord;
            }
        """

        const val FRAGMENT_SHADER_SRC = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """
    }
}
