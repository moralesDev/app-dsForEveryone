package com.moralesDev.dsForEveryone.emulator.core

import android.util.Log
import java.nio.ByteBuffer

/**
 * Declaración de los métodos nativos del puente.
 *
 * Es una capa deliberadamente tonta: sólo declara la frontera con C++. Toda
 * la traducción a los conceptos de la aplicación ocurre en [DsEmulatorCore], y
 * ninguna otra clase del proyecto habla con esta.
 *
 * `System.loadLibrary` puede fallar —una compilación sin la ABI del
 * dispositivo, por ejemplo—, y ese fallo no debe tumbar la aplicación: se
 * registra y [isAvailable] queda en `false`, de modo que el core informe de
 * que no está disponible en lugar de reventar.
 */
internal object NativeBridge {

    val isAvailable: Boolean = try {
        System.loadLibrary("dsemu")
        true
    } catch (error: UnsatisfiedLinkError) {
        Log.e("NativeBridge", "No se pudo cargar libdsemu.so", error)
        false
    }

    external fun nativeCreate(): Long

    /** @return 0 correcto, 1 ROM no válida, 2 fallo de inicialización. */
    external fun nativeLoad(handle: Long, rom: ByteArray, batterySave: ByteArray?): Int

    /**
     * Como [nativeLoad], pero mapea la ROM en memoria en el lado nativo en
     * vez de recibirla copiada en un `ByteArray`: es la vía para ROMs sueltas
     * que pueden pesar más de lo que cabe cómodo en el heap de Java (ver
     * RomRepository.openRomForCore).
     *
     * Se queda con la propiedad de [fd] —debe venir de
     * `ParcelFileDescriptor.detachFd()`— y lo cierra siempre él mismo, tenga
     * éxito o no.
     *
     * @return 0 correcto, 1 ROM no válida, 2 fallo de inicialización.
     */
    external fun nativeLoadFd(handle: Long, fd: Int, length: Long, batterySave: ByteArray?): Int

    external fun nativeUnload(handle: Long)

    external fun nativeRunFrame(handle: Long)

    external fun nativeCaptureFrame(handle: Long): IntArray?

    /**
     * Escribe el fotograma actual, ya en bytes R,G,B,A, dentro de [buffer]
     * (debe ser directo, con espacio para 256×384×4 bytes) en vez de devolver
     * un array nuevo. Ver [EmulatorCore.captureFrameInto].
     *
     * @return `false` si no hay nada cargado o [buffer] no es válido.
     */
    external fun nativeCaptureFrameInto(handle: Long, buffer: ByteBuffer): Boolean

    external fun nativeSetKeys(handle: Long, mask: Int)

    external fun nativeSetTouch(handle: Long, x: Int, y: Int, down: Boolean)

    external fun nativeSetMicNoise(handle: Long, active: Boolean)

    /** Ver [EmulatorCore.setAudioMuted]: el core sigue produciendo muestras, sólo que no se entregan. */
    external fun nativeSetAudioMuted(handle: Long, muted: Boolean)

    /** Ver [EmulatorCore.setLidClosed]. Sólo se llama cuando el estado cambia: abrir dispara una IRQ. */
    external fun nativeSetLidClosed(handle: Long, closed: Boolean)

    external fun nativeStateSize(handle: Long): Int

    external fun nativeSerializeState(handle: Long): ByteArray?

    external fun nativeDeserializeState(handle: Long, state: ByteArray): Boolean

    external fun nativeReadBattery(handle: Long): ByteArray?

    external fun nativeConsumeBatteryDirty(handle: Long): Boolean

    /**
     * Sustituye el conjunto de claves Action Replay activo por [lines]/[enabled]
     * enteros —índices paralelos, un elemento de cada por entrada—; cada
     * elemento de [lines] son las líneas de esa clave unidas por `'\n'`.
     *
     * @return por cada entrada, en el mismo orden: `""` si se aceptó, o
     *   `"L<n>"` si la línea `n` (0-based) no tiene forma de código Action
     *   Replay válido. `null` si no hay ninguna ROM cargada.
     */
    external fun nativeApplyCheats(handle: Long, lines: Array<String>, enabled: BooleanArray): Array<String>?

    external fun nativeCoreVersion(): String
}
