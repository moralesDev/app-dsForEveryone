package com.moralesDev.dsForEveryone.data.save

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Defensa del proyecto hermano contra el bug de `coreVersion` (ver CLAUDE.md
 * de `app-emulador`, «Actualizar mGBA»): en aquel proyecto, `mGBA` llegó a
 * grabar en cada save state la rama y el commit de `app-emulador` en vez de
 * su propia versión, porque `version.cmake` interrogaba a git y encontraba el
 * `.git` de la aplicación al no tener `third_party/mgba` uno propio.
 * melonDS 1.1 no tiene ese defecto (ver docs/integracion-core.md, sección 6:
 * su versión es un literal en CMakeLists.txt, no algo que `git describe`
 * pueda ensuciar), pero el principio se mantiene: lo que se graba es
 * `BRIDGE_CORE_VERSION`, una constante del puente, nunca un símbolo del core.
 *
 * El fixture es un `slot_1.state` real: una ranura grabada en un Motorola
 * edge 30 con el puente de la fase 4.4, contra una ROM sintética
 * ([com.moralesDev.dsForEveryone.data.rom.DsRomFixtures], generada y
 * exportada con
 * [com.moralesDev.dsForEveryone.data.rom.SyntheticRomExporter]) — nunca con
 * una ROM del usuario (ver CLAUDE.md, protocolo de pruebas en dispositivo).
 *
 * Ojo con lo que esta clase NO prueba: corre en `app/src/test/`, JVM puro sin
 * NDK (`unitTests.isReturnDefaultValues` en `app/build.gradle.kts`). El
 * fixture es un archivo congelado en el momento en que se capturó; estas
 * pruebas nunca llaman a `NativeBridge.nativeCoreVersion()` ni cargan
 * `libdsemu.so`, así que no detectarían que el bug apareciera en un build
 * futuro — sólo confirman que el *formato del contenedor* sigue
 * decodificándose igual. Quien vigila el `.so` real en cada build es la tarea
 * de Gradle `verifyCoreVersionDebug`/`verifyCoreVersionRelease`
 * (`app/build.gradle.kts`), que sí inspecciona el objeto compilado.
 *
 * El estado pesa ~19 MB sin comprimir: es el tamaño real de un save state de
 * melonDS (VRAM, RAM principal, los búferes del GPU3D aunque el juego no use
 * 3D...), muy por encima de los ~400 KB de mGBA. Es una cifra a tener en
 * cuenta para la fase 4.5 (cinco ranuras por juego pesan de verdad) y está
 * documentada en integracion-core.md, sección 8.
 */
class GoldenSaveStateTest {

    private val bytes = javaClass.getResourceAsStream("/save/golden-melonds-1.1.state")
        ?.use { it.readBytes() }
        ?: error("No se encontró el fixture dorado en resources/save/")

    private val identity = StateIdentity(
        romSha256 = "2e450af5f1bb0029800087ccab9e7a78867706bd6ad67de8891161d3ef455a22",
        romCrc32 = 3452841973L,
        coreName = "melonds",
        coreVersion = "1.1",
        stateSize = bytes.size - 104, // headerBytes real del fixture
    )

    @Test
    fun `el fixture dorado se decodifica como valido`() {
        val decoded = SaveStateContainer.decode(bytes)

        assertTrue("Se esperaba un estado válido y se obtuvo $decoded", decoded is DecodedState.Valid)
    }

    @Test
    fun `coreVersion del fixture es exactamente 1-1, no un simbolo del core`() {
        val decoded = SaveStateContainer.decode(bytes) as DecodedState.Valid

        assertEquals("melonds", decoded.header.coreName)
        assertEquals("1.1", decoded.header.coreVersion)
    }

    @Test
    fun `el fixture pasa las nueve comprobaciones contra su propia identidad`() {
        val decoded = SaveStateContainer.decode(bytes) as DecodedState.Valid

        assertNull(identity.reject(decoded.header))
    }
}
