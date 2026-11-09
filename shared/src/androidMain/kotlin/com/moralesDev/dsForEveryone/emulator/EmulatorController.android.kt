package com.moralesDev.dsForEveryone.emulator

import android.os.ParcelFileDescriptor
import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import com.moralesDev.dsForEveryone.emulator.core.DescriptorLoadingCore

/**
 * Como [EmulatorController.open], pero a partir de un descriptor de archivo
 * abierto sobre la ROM (ver [DescriptorLoadingCore]): la vía para ROMs
 * sueltas que pueden pesar más de lo que cabe cómodo en el heap de Java.
 * Sólo Android.
 *
 * Si el core no sabe cargar desde un descriptor, se lee el archivo entero y
 * se le da como bytes: es lo que basta para el stub y el core de las pruebas,
 * donde el tamaño de la ROM nunca es un problema real.
 */
suspend fun EmulatorController.openFromDescriptor(
    fd: ParcelFileDescriptor,
    length: Long,
    batterySave: ByteArray?,
): CoreLoadResult = finishOpen {
    when (val loader = core) {
        is DescriptorLoadingCore -> loader.loadFromDescriptor(fd, length, batterySave)
        else -> loader.load(ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }, batterySave)
    }
}
