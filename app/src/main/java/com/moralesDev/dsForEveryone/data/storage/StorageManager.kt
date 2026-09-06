package com.moralesDev.dsForEveryone.data.storage

import java.io.File

/**
 * Única autoridad sobre dónde vive cada archivo de la aplicación.
 *
 * Todo cuelga del almacenamiento interno (`filesDir`), que es privado del
 * proceso: ninguna otra aplicación puede leer ni corromper las partidas, y no
 * hace falta ningún permiso.
 *
 * Recibe la carpeta ya resuelta en lugar del `Context` del que sale. Es lo que
 * permite ejercitar el sistema de partidas completo en pruebas de JVM, sobre
 * una carpeta temporal y sin emulador de Android de por medio.
 *
 * La disposición en disco es:
 *
 * ```
 * filesDir/
 * ├── covers/
 * │   └── <sha256 de la ROM>.webp
 * ├── system/
 * │   └── <bios7.bin, firmware.bin, ...>   (lo que pida el core elegido)
 * └── saves/
 *     ├── <sha256 de la ROM>/
 *     │   ├── battery.sav
 *     │   ├── cheats.json
 *     │   └── states/
 *     │       ├── slot_1.state   (contenedor E4FS: metadatos + estado)
 *     │       ├── slot_1.png     (miniatura)
 *     │       ├── auto.state     (respaldo automático, ranura del sistema)
 *     │       └── auto.png
 *     └── <sha256 de otra ROM>/
 * ```
 *
 * La carpeta de cada juego se nombra con el hash del contenido de la ROM,
 * nunca con el nombre del archivo. Renombrar el archivo no separa a un juego
 * de su partida, y dos archivos distintos no pueden colisionar en la misma
 * carpeta.
 *
 * `system/` es distinta a propósito: BIOS y firmware no pertenecen a ningún
 * juego, los usa el core con cualquier ROM, y por eso viven fuera de `saves/`
 * y no se tocan al borrar los datos de un juego concreto.
 */
class StorageManager(filesDirectory: File) {

    private val savesRoot = File(filesDirectory, "saves")
    private val coversRoot = File(filesDirectory, "covers")
    private val systemRoot = File(filesDirectory, "system")

    fun romDirectory(romId: String): File =
        File(savesRoot, romId).also { it.mkdirs() }

    fun batteryFile(romId: String): File =
        File(romDirectory(romId), BATTERY_FILE)

    fun cheatsFile(romId: String): File =
        File(romDirectory(romId), CHEATS_FILE)

    fun statesDirectory(romId: String): File =
        File(romDirectory(romId), STATES_DIR).also { it.mkdirs() }

    fun stateFile(romId: String, slot: Int): File =
        File(statesDirectory(romId), "${slotName(slot)}.state")

    fun stateThumbnailFile(romId: String, slot: Int): File =
        File(statesDirectory(romId), "${slotName(slot)}.png")

    /**
     * El respaldo automático es la ranura 0 y se llama `auto`, no `slot_0`.
     *
     * Tener un nombre propio y no un número más es deliberado: deja a la vista,
     * en el propio disco, que ese archivo lo escribe el sistema y las ranuras
     * numeradas son del usuario.
     */
    private fun slotName(slot: Int): String =
        if (slot == AUTO_SLOT) "auto" else "slot_$slot"

    /**
     * Portada elegida por el usuario, si la puso. Sin ella, la biblioteca usa
     * la extraída del banner de la ROM (fase 3) sin pasar por aquí.
     *
     * Vive fuera de `saves/` a propósito: no es una partida. Quien quita un
     * juego «conservando las partidas» espera recuperarlas al volver a añadirlo,
     * y mezclar aquí una imagen de interfaz sólo confundiría qué se guarda y qué
     * se borra. Cuelga del hash de la ROM igual que todo lo demás, así que
     * renombrar el archivo no separa al juego de su portada.
     */
    fun coverFile(romId: String): File =
        File(coversRoot.also { it.mkdirs() }, "$romId.webp")

    /**
     * Archivo de sistema (BIOS o firmware) que el core elegido necesite.
     *
     * El nombre lo decide quien llama: no hay una lista fija aquí porque
     * depende del core (fase 4). Compartido entre todas las ROMs, así que
     * [deleteRomData] nunca lo toca.
     */
    fun systemFile(name: String): File =
        File(systemRoot.also { it.mkdirs() }, name)

    /**
     * Borra todos los datos de una ROM. Se usa al quitarla de la biblioteca
     * eligiendo borrar también las partidas: entonces no debe quedar rastro,
     * portada incluida. Los archivos de `system/` no son de ningún juego y no
     * se tocan.
     */
    fun deleteRomData(romId: String) {
        File(savesRoot, romId).deleteRecursively()
        coverFile(romId).delete()
    }

    fun usedBytes(romId: String): Long =
        File(savesRoot, romId).walkBottomUp().filter(File::isFile).sumOf(File::length)

    companion object {
        /**
         * Número de ranura del respaldo automático. Las del usuario son 1..5, así
         * que el 0 estaba libre y encaja en el campo `slot` del contenedor sin
         * tocar el formato.
         */
        const val AUTO_SLOT = 0

        private const val BATTERY_FILE = "battery.sav"
        private const val CHEATS_FILE = "cheats.json"
        private const val STATES_DIR = "states"
    }
}
