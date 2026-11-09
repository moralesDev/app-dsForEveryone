package com.moralesDev.dsForEveryone.data.storage

import okio.FileSystem
import okio.Path
import kotlin.random.Random

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
 * Vive en el módulo común desde iOS-3 (docs/ios.md) y trabaja con rutas de
 * Okio (`*Path`). En Android, el código que aún usa `java.io.File` —y las
 * pruebas— entra por las funciones de siempre (`batteryFile`, `stateFile`…),
 * que están en `StorageFiles.android.kt` y convierten la ruta.
 *
 * La disposición en disco es:
 *
 * ```
 * filesDir/
 * ├── covers/
 * │   └── <sha256 de la ROM>.webp
 * ├── system/
 * │   └── <bios7.bin, firmware.bin, ...>   (lo que pida el core elegido)
 * ├── scratch/
 * │   └── rom_scratch*.nds   (vive segundos: se borra al abrir su descriptor)
 * ├── import-staging/        (copia de partidas validada, pendiente de confirmar)
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
class StorageManager(filesDirectory: Path) {

    private val fileSystem = FileSystem.SYSTEM

    private val savesRoot = filesDirectory / "saves"
    private val coversRoot = filesDirectory / "covers"
    private val systemRoot = filesDirectory / "system"
    private val scratchRoot = filesDirectory / "scratch"
    private val importStagingRoot = filesDirectory / "import-staging"

    /**
     * La carpeta que contiene una carpeta por juego. Sólo la recorre la copia
     * de partidas (`SaveArchive`), que necesita ver todos los juegos a la vez;
     * el resto del código entra siempre por el id de una ROM concreta.
     */
    fun savesPath(): Path = savesRoot

    /**
     * Dónde se prepara una importación antes de confirmarla. Cuelga de
     * `filesDir` igual que `saves/` —mismo sistema de archivos—, que es lo que
     * permite reemplazar la carpeta de un juego con un `rename` y no con una
     * copia que podría quedarse a medias.
     */
    fun importStagingPath(): Path = importStagingRoot

    /** Si el juego tiene algún archivo guardado, sin crear su carpeta para averiguarlo. */
    fun hasRomData(romId: String): Boolean = filesUnder(savesRoot / romId).any()

    fun romPath(romId: String): Path =
        (savesRoot / romId).also(fileSystem::createDirectories)

    fun batteryPath(romId: String): Path = romPath(romId) / BATTERY_FILE

    fun cheatsPath(romId: String): Path = romPath(romId) / CHEATS_FILE

    fun statesPath(romId: String): Path =
        (romPath(romId) / STATES_DIR).also(fileSystem::createDirectories)

    fun statePath(romId: String, slot: Int): Path =
        statesPath(romId) / "${slotName(slot)}.state"

    fun stateThumbnailPath(romId: String, slot: Int): Path =
        statesPath(romId) / "${slotName(slot)}.png"

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
    fun coverPath(romId: String): Path =
        coversRoot.also(fileSystem::createDirectories) / "$romId.webp"

    /**
     * Archivo de sistema (BIOS o firmware) que el core elegido necesite.
     *
     * El nombre lo decide quien llama: no hay una lista fija aquí porque
     * depende del core (fase 4). Compartido entre todas las ROMs, así que
     * [deleteRomData] nunca lo toca.
     */
    fun systemPath(name: String): Path =
        systemRoot.also(fileSystem::createDirectories) / name

    /**
     * Borra todos los datos de una ROM. Se usa al quitarla de la biblioteca
     * eligiendo borrar también las partidas: entonces no debe quedar rastro,
     * portada incluida. Los archivos de `system/` no son de ningún juego y no
     * se tocan.
     */
    fun deleteRomData(romId: String) {
        fileSystem.deleteRecursively(savesRoot / romId, mustExist = false)
        fileSystem.delete(coverPath(romId), mustExist = false)
    }

    fun usedBytes(romId: String): Long =
        filesUnder(savesRoot / romId).sumOf { fileSystem.metadataOrNull(it)?.size ?: 0L }

    /**
     * Archivo temporal para la copia de emergencia de una ROM suelta cuando
     * el proveedor SAF no entrega un descriptor directo (ver
     * `RomRepository.openRomForCore`). Nunca queda ahí: quien lo escribe lo
     * borra apenas abre un `ParcelFileDescriptor` sobre él —el inodo sigue
     * vivo mientras ese descriptor esté abierto—, así que esta carpeta no
     * guarda ninguna copia duradera de ninguna ROM.
     *
     * Como `File.createTempFile`: un nombre que no existía, y el archivo ya
     * creado (vacío) al volver, para que dos llamadas no se pisen.
     */
    fun newRomScratchPath(): Path {
        fileSystem.createDirectories(scratchRoot)
        while (true) {
            val candidate = scratchRoot / "rom_scratch${Random.nextLong().toULong()}.nds"
            try {
                fileSystem.write(candidate, mustCreate = true) {}
                return candidate
            } catch (_: okio.IOException) {
                // Ya existía (o lo creó otro a la vez): otro nombre.
                if (!fileSystem.exists(candidate)) throw IllegalStateException("No se pudo crear $candidate")
            }
        }
    }

    /** Los archivos (no carpetas) bajo [directory], a cualquier profundidad; nada si no existe. */
    private fun filesUnder(directory: Path): Sequence<Path> =
        if (!fileSystem.exists(directory)) {
            emptySequence()
        } else {
            fileSystem.listRecursively(directory).filter { fileSystem.metadataOrNull(it)?.isRegularFile == true }
        }

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
