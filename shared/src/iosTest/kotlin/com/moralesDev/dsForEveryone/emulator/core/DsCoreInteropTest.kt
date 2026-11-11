package com.moralesDev.dsForEveryone.emulator.core

import com.moralesDev.dsForEveryone.emulator.core.native.DS_FRAME_PIXELS
import com.moralesDev.dsForEveryone.emulator.core.native.DS_LOAD_INVALID_ROM
import com.moralesDev.dsForEveryone.emulator.core.native.DS_LOAD_OK
import com.moralesDev.dsForEveryone.emulator.core.native.ds_copy_frame_rgba
import com.moralesDev.dsForEveryone.emulator.core.native.ds_core_version
import com.moralesDev.dsForEveryone.emulator.core.native.ds_create
import com.moralesDev.dsForEveryone.emulator.core.native.ds_destroy
import com.moralesDev.dsForEveryone.emulator.core.native.ds_load
import com.moralesDev.dsForEveryone.emulator.core.native.ds_run_frame
import com.moralesDev.dsForEveryone.emulator.core.native.ds_set_audio_muted
import com.moralesDev.dsForEveryone.emulator.core.native.ds_state_size
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * El core de verdad (ds_core + melonDS, sin JIT) llamado desde Kotlin/Native
 * en el simulador de iOS: la prueba de que cinterop, las bibliotecas
 * estáticas y el enlace con libc++ encajan. Lo que hace el core lo prueban
 * ya Android y la app de prueba del iPad; aquí sólo se comprueba que se
 * llega a él y responde.
 */
@OptIn(ExperimentalForeignApi::class)
class DsCoreInteropTest {

    @Test
    fun `la version es la que se graba en los save states`() {
        assertEquals("1.1", ds_core_version()?.toKString())
    }

    /**
     * Una ROM vacía se rechaza en ds_core. Lo que no se rechaza es «cualquier
     * cosa que no sea una ROM»: melonDS acepta como cartucho hasta 16 bytes
     * de basura (rellena lo que falta). En la app nunca le llega basura,
     * porque la biblioteca valida la cabecera antes de dar de alta una ROM.
     */
    @Test
    fun `una ROM vacia se rechaza sin romper nada`() {
        val emu = assertNotNull(ds_create())
        val empty = ByteArray(1)
        val result = empty.usePinned { ds_load(emu, it.addressOf(0).reinterpret<UByteVar>(), 0u, null, 0u) }
        assertEquals(DS_LOAD_INVALID_ROM.toInt(), result)
        ds_destroy(emu)
    }

    @Test
    fun `una ROM minima carga y corre fotogramas`() {
        val emu = assertNotNull(ds_create())
        ds_set_audio_muted(emu, true)
        // 1 MB de ceros con un título: no hace nada, pero el core la acepta
        // como cartucho y ejecuta (ceros son instrucciones ARM inofensivas).
        val rom = ByteArray(1024 * 1024)
        "INTEROPTEST".encodeToByteArray().copyInto(rom)
        val result = rom.usePinned { ds_load(emu, it.addressOf(0).reinterpret<UByteVar>(), rom.size.convert(), null, 0u) }
        assertEquals(DS_LOAD_OK.toInt(), result)

        repeat(60) { ds_run_frame(emu) }

        val frame = ByteArray(DS_FRAME_PIXELS.toInt() * 4)
        val copied = frame.usePinned { ds_copy_frame_rgba(emu, it.addressOf(0).reinterpret<UByteVar>(), frame.size.convert()) }
        assertTrue(copied)
        assertTrue(ds_state_size(emu) > 0u)
        ds_destroy(emu)
    }
}
