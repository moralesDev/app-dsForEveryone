package com.moralesDev.dsForEveryone.emulator.core

import com.moralesDev.dsForEveryone.emulator.FrameBuffer

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
 *   [setAudioMuted], [setLidClosed],
 *   [stateSize], [serializeState], [deserializeState], [readBattery],
 *   [consumeBatteryDirty], [captureFrame] y [captureFrameInto] se llaman
 *   **siempre** desde el hilo de emulación, nunca desde el hilo principal.
 *
 * Desde iOS-4 (docs/ios.md) la interfaz es común: en Android la implementa
 * el puente JNI (`DsEmulatorCore`), en iOS `cinterop` sobre el mismo
 * ds_core. Se fue `attachSurface`, que ningún core usaba (el vídeo de la DS
 * sale siempre por [captureFrameInto]); cargar desde un descriptor de
 * archivo es sólo de Android (`DescriptorLoadingCore`).
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
     * Soplido simulado en la entrada de micrófono —una grabación de un
     * soplido real, en bucle— mientras el botón de soplar esté pulsado. Sin `RECORD_AUDIO`: ver docs/entrada.md.
     */
    fun setMicNoise(active: Boolean)

    /**
     * Cierra o abre la tapa de la consola.
     *
     * No es un botón: es el estado de la bisagra, que la DS expone como un bit
     * más de la entrada. Unos cuantos juegos lo usan como mecánica —cerrar la
     * tapa para estampar un símbolo, para que un personaje reaccione— y sin
     * ello no se puede avanzar.
     *
     * Se llama **sólo cuando el estado cambia**, nunca una vez por fotograma:
     * abrir la tapa dispara una interrupción en el core, y repetirla cada
     * fotograma sería un chaparrón de interrupciones que ningún hardware real
     * produce. Ver docs/entrada.md, «La tapa».
     *
     * Implementación por defecto: no hacer nada, que es lo que basta a un core
     * sin bisagra que emular como [StubEmulatorCore].
     */
    fun setLidClosed(closed: Boolean) {}

    /**
     * Silencia la salida de audio sin detenerla. Existe por el avance rápido:
     * a 2x el core produce el doble de muestras de las que el dispositivo
     * reproduce en el mismo tiempo, así que la única salida honesta es no
     * entregarlas —lo contrario suena a audio picado y con retraso creciente,
     * no a un juego acelerado—.
     *
     * Silenciar no es dejar de emular sonido: el core sigue generando sus
     * muestras y su estado interno sigue siendo el correcto, sólo que no
     * llegan al altavoz. Implementación por defecto: no hacer nada, que es lo
     * que basta a un core sin audio como [StubEmulatorCore].
     */
    fun setAudioMuted(muted: Boolean) {}

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
    fun captureFrameInto(buffer: FrameBuffer): Boolean {
        val frame = captureFrame() ?: return false
        buffer.fillFromArgb(frame)
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
