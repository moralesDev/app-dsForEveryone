package com.moralesDev.dsForEveryone.data.storage

import okio.Path.Companion.toOkioPath
import java.io.File

/*
 * Puente temporal entre StorageManager, que desde iOS-3 trabaja con rutas de
 * Okio, y el código de Android que todavía usa `java.io.File`: exportar e
 * importar partidas (zip), las ROMs, las portadas, y las pruebas de `:app`.
 * Mismos nombres que tenían los métodos antes de pasar al módulo común, así
 * que ese código no cambia; sólo importa estas funciones. Se irán quitando a
 * medida que ese código pase también al módulo común.
 */

/** Como el constructor de antes: la carpeta como `File`. */
fun StorageManager(filesDirectory: File): StorageManager = StorageManager(filesDirectory.toOkioPath())

fun StorageManager.savesDirectory(): File = savesPath().toFile()

fun StorageManager.importStagingDirectory(): File = importStagingPath().toFile()

fun StorageManager.romDirectory(romId: String): File = romPath(romId).toFile()

fun StorageManager.batteryFile(romId: String): File = batteryPath(romId).toFile()

fun StorageManager.cheatsFile(romId: String): File = cheatsPath(romId).toFile()

fun StorageManager.statesDirectory(romId: String): File = statesPath(romId).toFile()

fun StorageManager.stateFile(romId: String, slot: Int): File = statePath(romId, slot).toFile()

fun StorageManager.stateThumbnailFile(romId: String, slot: Int): File =
    stateThumbnailPath(romId, slot).toFile()

fun StorageManager.coverFile(romId: String): File = coverPath(romId).toFile()

fun StorageManager.systemFile(name: String): File = systemPath(name).toFile()

fun StorageManager.newRomScratchFile(): File = newRomScratchPath().toFile()
