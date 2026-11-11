package com.moralesDev.dsForEveryone.emulator.core

import android.os.ParcelFileDescriptor

/**
 * Un core que sabe cargar la ROM a partir de un descriptor abierto sobre el
 * archivo real, en vez de un `ByteArray` ya copiado. Sólo Android: existe
 * para ROMs sueltas (no dentro de un `.zip`) que pueden pesar más de lo que
 * cabe cómodo en el heap de Java —ver `RomRepository.openRomForCore`—:
 * cargar un cartucho de 128 MB con [EmulatorCore.load] exige tenerlo entero
 * en un único `ByteArray` de Kotlin, y eso es lo que agotaba el heap.
 *
 * Lo implementa el core nativo, que mapea el archivo en el lado C++ sin que
 * llegue a existir un `ByteArray` con la ROM completa. Un core que no lo
 * implemente (el stub, el de las pruebas) recibe la ROM leída entera: ver
 * `EmulatorController.openFromDescriptor`.
 */
interface DescriptorLoadingCore : EmulatorCore {
    /** Se queda con la propiedad de [fd] y lo cierra siempre, tenga éxito o no la carga. */
    fun loadFromDescriptor(fd: ParcelFileDescriptor, length: Long, batterySave: ByteArray?): CoreLoadResult
}
