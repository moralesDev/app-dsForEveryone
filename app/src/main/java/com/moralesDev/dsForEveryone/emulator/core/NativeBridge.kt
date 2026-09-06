package com.moralesDev.dsForEveryone.emulator.core

import android.util.Log

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
    external fun nativeLoad(handle: Long, rom: ByteArray): Int

    external fun nativeUnload(handle: Long)

    external fun nativeRunFrame(handle: Long)

    external fun nativeCaptureFrame(handle: Long): IntArray?

    external fun nativeSetKeys(handle: Long, mask: Int)

    external fun nativeSetTouch(handle: Long, x: Int, y: Int, down: Boolean)

    external fun nativeSetMicNoise(handle: Long, active: Boolean)

    external fun nativeStateSize(handle: Long): Int

    external fun nativeSerializeState(handle: Long): ByteArray?

    external fun nativeDeserializeState(handle: Long, state: ByteArray): Boolean

    external fun nativeReadBattery(handle: Long): ByteArray?

    external fun nativeConsumeBatteryDirty(handle: Long): Boolean

    external fun nativeCoreVersion(): String
}
