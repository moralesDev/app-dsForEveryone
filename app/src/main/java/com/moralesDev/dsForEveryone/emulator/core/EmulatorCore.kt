package com.moralesDev.dsForEveryone.emulator.core

import android.os.ParcelFileDescriptor
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Frontera entre la aplicación y el core de emulación.
 *
 * Toda la aplicación (UI, partidas, biblioteca) habla únicamente con esta
 * interfaz. Detrás puede haber un core nativo (melonDS o DeSmuME, fase 4) o un
 * doble de pruebas, y nada más del proyecto cambia al sustituir uno por otro.
 *
 * Hereda la de `app-emulador` y añade lo propio de la DS: [setTouch] y
 * [setMicNoise]. Ver docs/integracion-core.md, sección 3, para la
 * correspondencia exacta con el lado nativo.
 *
 * ## Contrato de hilos
 *
 * - [load], [unload], [runFrame], [setKeys], [setTouch], [setMicNoise],
 *   [stateSize], [serializeState], [deserializeState], [readBattery],
 *   [consumeBatteryDirty], [captureFrame] y [captureFrameInto] se llaman
 *   **siempre** desde el hilo de emulación, nunca desde el hilo principal.
 * - [attachSurface] es la excepción: la llama el hilo principal, y **debe
 *   bloquear** hasta que el core deje de escribir en la superficie anterior,
 *   si es que escribe en alguna (ver su documentación).
 */
interface EmulatorCore {

    /**
     * Identificador del core sin versión: `melonds`, `desmume`, `stub`.
     * Deliberadamente sin traducir: se escribe en cada save state.
     */
    val coreName: String

    /**
     * Versión de ese core: `1.0.0`. Va separada del nombre porque son dos
     * comprobaciones distintas — un estado de otro core es un archivo ajeno;
     * uno del mismo core en otra versión es un archivo propio que ya no se
     * sabe leer.
     */
    val coreVersion: String

    /** Nombre legible del core, para diagnóstico y para la pantalla de ajustes. */
    val name: String get() = "$coreName-$coreVersion"

    /**
     * Carga una ROM y, si existe, su partida de batería previa.
     *
     * @param rom contenido completo del archivo .nds.
     * @param batterySave contenido de `battery.sav`, o `null` la primera vez.
     * @return el resultado de la carga; sólo [CoreLoadResult.Success] deja el
     *   core en estado utilizable.
     */
    fun load(rom: ByteArray, batterySave: ByteArray?): CoreLoadResult

    /**
     * Como [load], pero a partir de un descriptor abierto sobre el archivo
     * real de la ROM en vez de un `ByteArray` ya copiado. Existe para ROMs
     * sueltas (no dentro de un `.zip`) que pueden pesar más de lo que cabe
     * cómodo en el heap de Java —ver `RomRepository.openRomForCore`—: cargar
     * un cartucho de 128 MB con [load] exige tenerlo entero en un único
     * `ByteArray` de Kotlin, y eso es lo que agotaba el heap.
     *
     * La implementación por defecto simplemente lee el descriptor entero y
     * delega en [load]: es lo que basta para [StubEmulatorCore], donde el
     * tamaño de la ROM nunca es un problema real. Un core que sí lo sea
     * sobrescribe este método para mapear el archivo en el lado nativo, sin
     * que llegue a existir un `ByteArray` con la ROM completa.
     *
     * Se queda con la propiedad de [fd] y lo cierra siempre, tenga éxito o
     * no la carga.
     */
    fun loadFromDescriptor(
        fd: ParcelFileDescriptor,
        length: Long,
        batterySave: ByteArray?,
    ): CoreLoadResult {
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        return load(bytes, batterySave)
    }

    /** Libera la ROM y todos los recursos nativos asociados. Idempotente. */
    fun unload()

    /** Avanza exactamente un fotograma. */
    fun runFrame()

    /** Fija la máscara de botones (ver [com.moralesDev.dsForEveryone.emulator.DsButton.bit]) para el siguiente fotograma. */
    fun setKeys(mask: Int)

    /**
     * Estado de la pantalla táctil para el siguiente fotograma.
     *
     * @param x,y coordenada de la DS, 0..255 y 0..191. Se ignoran si [down]
     *   es `false`. Mientras el dedo esté abajo se llama en **cada**
     *   fotograma, no sólo cuando la posición cambia (ver docs/entrada.md).
     */
    fun setTouch(x: Int, y: Int, down: Boolean)

    /**
     * Ruido blanco simulado en la entrada de micrófono, mientras el botón de
     * soplar esté pulsado. Sin `RECORD_AUDIO`: ver docs/entrada.md.
     */
    fun setMicNoise(active: Boolean)

    /**
     * Sustituye el conjunto de claves activo por [entries] entero — no hay
     * actualización incremental, se manda la lista completa cada vez que
     * cambia algo (añadir, activar/desactivar, borrar). Devuelve, para cada
     * entrada de [entries] en el mismo orden, por qué se rechazó, o `null` si
     * se aceptó.
     *
     * Implementación por defecto: acepta todas sin más. Es lo que basta a
     * [StubEmulatorCore], donde no hay ningún motor de claves que emular.
     */
    fun applyCheats(entries: List<CheatEntry>): List<CheatRejection?> = entries.map { null }

    /**
     * Ver el contrato de hilos en la documentación de la interfaz.
     *
     * En la DS la presentación normal no pasa por aquí: el framebuffer se lee
     * con [captureFrame] y se sube como textura (fase 2.5), no se dibuja
     * directamente sobre una `Surface` como en `app-emulador`. Esta llamada
     * se conserva para un eventual camino de render 3D por GL directo a
     * superficie (fase 4); un core que no lo use puede limitarse a guardar la
     * referencia.
     */
    fun attachSurface(surface: Surface?)

    /**
     * Bytes que ocupa el estado de la ROM cargada ahora mismo, o 0 si no hay
     * ninguna o el core no está disponible.
     */
    fun stateSize(): Int

    /** Serializa el estado completo de la máquina, o `null` si no se puede. */
    fun serializeState(): ByteArray?

    /** Restaura un estado previamente serializado por este mismo core. */
    fun deserializeState(state: ByteArray): Boolean

    /**
     * Contenido actual de la savedata del cartucho (EEPROM/Flash), o `null`
     * si el juego no usa guardado de batería.
     */
    fun readBattery(): ByteArray?

    /**
     * Indica si la partida de batería cambió desde la última consulta y pone
     * el indicador a cero. Existe para no reescribir en disco 60 veces por
     * segundo.
     */
    fun consumeBatteryDirty(): Boolean

    /**
     * Copia del framebuffer actual: 256×384, la pantalla superior en las
     * filas 0–191 y la inferior en las 192–383. `null` si todavía no se ha
     * generado ninguno.
     *
     * En la DS este es el único camino de vídeo: lo usa tanto la miniatura de
     * los save states como, cada fotograma, el renderizador GL (fase 2.5)
     * para subirlo como textura.
     */
    fun captureFrame(): IntArray?

    /**
     * Como [captureFrame], pero escribiendo los bytes ya en orden R,G,B,A
     * directamente en [buffer] (asignado y reutilizado por quien llama, con
     * espacio para 256×384 píxeles de 4 bytes) en vez de devolver un
     * `IntArray` nuevo.
     *
     * Existe sólo para el camino caliente —una llamada por fotograma, 60
     * veces por segundo, desde [com.moralesDev.dsForEveryone.emulator.EmulatorController]—,
     * donde reservar un array nuevo cada vez llenaba el heap de Java de
     * basura sin necesidad (era además la sospecha real detrás del
     * `OutOfMemoryError` de la fase 4.C, ver docs/estabilizacion-core.md).
     * [captureFrame] se queda para usos infrecuentes (la miniatura de un save
     * state), donde esa reserva no importa.
     *
     * @return `false` si no hay fotograma que copiar (nada cargado todavía) o
     *   si [buffer] no es válido; en ese caso no se escribe nada.
     */
    fun captureFrameInto(buffer: ByteBuffer): Boolean {
        val frame = captureFrame() ?: return false
        buffer.clear()
        for (pixel in frame) {
            buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
            buffer.put(((pixel shr 8) and 0xFF).toByte()) // G
            buffer.put((pixel and 0xFF).toByte()) // B
            buffer.put(((pixel shr 24) and 0xFF).toByte()) // A
        }
        buffer.position(0)
        return true
    }
}

/** Resultado de [EmulatorCore.load]. */
sealed interface CoreLoadResult {
    data object Success : CoreLoadResult

    /** El archivo no es una ROM de DS reconocible. */
    data object InvalidRom : CoreLoadResult

    /** El core no está disponible en esta compilación. */
    data object CoreUnavailable : CoreLoadResult

    data class Failure(val message: String) : CoreLoadResult
}
