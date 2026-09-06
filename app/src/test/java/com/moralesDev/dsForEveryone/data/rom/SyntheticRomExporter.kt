package com.moralesDev.dsForEveryone.data.rom

import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.zip.CRC32

/**
 * Utilidad de desarrollo, no una prueba de verdad: escribe a disco la ROM
 * sintética de [DsRomFixtures] para probarla en el Motorola Edge 30 (`adb
 * push` + importarla en la app), junto con su SHA-256 y su CRC32 para poder
 * construir un [com.moralesDev.dsForEveryone.data.save.StateIdentity] de
 * prueba a mano.
 *
 * Existe porque las pruebas en dispositivo de este proyecto se hacen siempre
 * con una ROM libre, nunca con las del usuario (ver CLAUDE.md), y generar esa
 * ROM a mano en cada fase era trabajo repetido. Vive en `src/test/` a
 * propósito: corre en JVM, nunca se empaqueta en el APK y ningún código de
 * `src/main/` la referencia.
 *
 * Se ejecuta a mano cuando hace falta, no como parte de la batería normal:
 *
 * ```
 * .\gradlew.bat testDebugUnitTest --tests "*SyntheticRomExporter" -i
 * ```
 */
class SyntheticRomExporter {

    @Test
    fun exportar() {
        val bytes = DsRomFixtures.validRomBytes()
        val out = File("build/synthetic-rom/sample.nds")
        out.parentFile?.mkdirs()
        out.writeBytes(bytes)

        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val crc32 = CRC32().apply { update(bytes) }.value

        println("Escrito: ${out.absolutePath} (${bytes.size} bytes)")
        println("sha256=$sha256")
        println("crc32=$crc32 (0x${crc32.toString(16)})")
    }
}
