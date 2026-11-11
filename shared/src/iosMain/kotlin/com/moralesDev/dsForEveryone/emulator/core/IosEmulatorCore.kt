package com.moralesDev.dsForEveryone.emulator.core

import com.moralesDev.dsForEveryone.emulator.FrameBuffer
import com.moralesDev.dsForEveryone.emulator.core.native.DS_FRAME_PIXELS
import com.moralesDev.dsForEveryone.emulator.core.native.DS_LOAD_INVALID_ROM
import com.moralesDev.dsForEveryone.emulator.core.native.DS_LOAD_OK
import cnames.structs.DsEmulator
import com.moralesDev.dsForEveryone.emulator.core.native.ds_apply_cheats
import com.moralesDev.dsForEveryone.emulator.core.native.ds_audio_ios_start
import com.moralesDev.dsForEveryone.emulator.core.native.ds_consume_battery_dirty
import com.moralesDev.dsForEveryone.emulator.core.native.ds_copy_frame_argb
import com.moralesDev.dsForEveryone.emulator.core.native.ds_copy_frame_rgba
import com.moralesDev.dsForEveryone.emulator.core.native.ds_core_version
import com.moralesDev.dsForEveryone.emulator.core.native.ds_create
import com.moralesDev.dsForEveryone.emulator.core.native.ds_deserialize_state
import com.moralesDev.dsForEveryone.emulator.core.native.ds_load
import com.moralesDev.dsForEveryone.emulator.core.native.ds_read_battery
import com.moralesDev.dsForEveryone.emulator.core.native.ds_run_frame
import com.moralesDev.dsForEveryone.emulator.core.native.ds_serialize_state
import com.moralesDev.dsForEveryone.emulator.core.native.ds_set_audio_muted
import com.moralesDev.dsForEveryone.emulator.core.native.ds_set_keys
import com.moralesDev.dsForEveryone.emulator.core.native.ds_set_lid_closed
import com.moralesDev.dsForEveryone.emulator.core.native.ds_set_mic_noise
import com.moralesDev.dsForEveryone.emulator.core.native.ds_set_touch
import com.moralesDev.dsForEveryone.emulator.core.native.ds_state_size
import com.moralesDev.dsForEveryone.emulator.core.native.ds_unload
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.asStableRef
import kotlinx.cinterop.convert
import kotlinx.cinterop.cstr
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.staticCFunction
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.size_t
import kotlin.native.concurrent.ThreadLocal

/**
 * El core en iOS: melonDS a través de ds_core (native/ds_core/ds_core.h),
 * llamado por cinterop. Es la pareja de `DsEmulatorCore` en Android, que
 * llama a la misma API en C por JNI: misma identidad ([coreName] y
 * [coreVersion], las que se graban en cada save state), mismos resultados.
 * Por eso una partida guardada en un sistema se carga en el otro.
 *
 * Sin JIT: Apple no deja ejecutar código generado en tiempo de ejecución. En
 * un iPad Air M2 el intérprete va a más de 4 veces la velocidad de una DS
 * (docs/ios.md, iOS-0).
 *
 * El emulador nativo se crea una sola vez y vive lo que vive el proceso,
 * como en Android, con su salida de audio (native/ios/ds_audio_ios.c).
 */
@OptIn(ExperimentalForeignApi::class)
class IosEmulatorCore : EmulatorCore {

    private val emu: CPointer<DsEmulator> = checkNotNull(ds_create()) { "ds_create devolvió null" }

    init {
        ds_audio_ios_start(emu)
    }

    override val coreName: String = "melonds"

    override val coreVersion: String = ds_core_version()?.toKString() ?: "no-disponible"

    override fun load(rom: ByteArray, batterySave: ByteArray?): CoreLoadResult {
        if (rom.isEmpty()) return CoreLoadResult.InvalidRom
        val result = rom.usePinned { romPin ->
            if (batterySave == null || batterySave.isEmpty()) {
                ds_load(emu, romPin.addressOf(0).reinterpret(), rom.size.convert(), null, 0u)
            } else {
                batterySave.usePinned { savePin ->
                    ds_load(
                        emu, romPin.addressOf(0).reinterpret(), rom.size.convert(),
                        savePin.addressOf(0).reinterpret(), batterySave.size.convert(),
                    )
                }
            }
        }
        return when (result) {
            DS_LOAD_OK.toInt() -> CoreLoadResult.Success
            DS_LOAD_INVALID_ROM.toInt() -> CoreLoadResult.InvalidRom
            else -> CoreLoadResult.Failure("melonDS no pudo inicializar la ROM.")
        }
    }

    override fun unload() = ds_unload(emu)

    override fun runFrame() = ds_run_frame(emu)

    override fun setKeys(mask: Int) = ds_set_keys(emu, mask.toUInt())

    override fun setTouch(x: Int, y: Int, down: Boolean) = ds_set_touch(emu, x, y, down)

    override fun setMicNoise(active: Boolean) = ds_set_mic_noise(emu, active)

    override fun setLidClosed(closed: Boolean) = ds_set_lid_closed(emu, closed)

    override fun setAudioMuted(muted: Boolean) = ds_set_audio_muted(emu, muted)

    override fun applyCheats(entries: List<CheatEntry>): List<CheatRejection?> = memScoped {
        if (entries.isEmpty()) {
            ds_apply_cheats(emu, null, null, 0u, null)
            return@memScoped emptyList()
        }
        val codes = allocArray<kotlinx.cinterop.CPointerVar<kotlinx.cinterop.ByteVar>>(entries.size)
        val rejected = allocArray<IntVar>(entries.size)
        entries.forEachIndexed { index, entry ->
            codes[index] = entry.lines.joinToString("\n").cstr.getPointer(this)
            rejected[index] = -1
        }
        // Un bool de C es un byte que vale 0 o 1: el array de Kotlin, fijado.
        val enabled = ByteArray(entries.size) { if (entries[it].enabled) 1 else 0 }
        enabled.usePinned {
            ds_apply_cheats(emu, codes, it.addressOf(0).reinterpret<BooleanVar>(), entries.size.convert(), rejected)
        }
        List(entries.size) { index -> rejected[index].takeIf { it >= 0 }?.let(CheatRejection::InvalidLine) }
    }

    override fun stateSize(): Int = ds_state_size(emu).toInt()

    override fun serializeState(): ByteArray? = collect { sink, context -> ds_serialize_state(emu, sink, context) }

    override fun deserializeState(state: ByteArray): Boolean {
        if (state.isEmpty()) return false
        return state.usePinned { ds_deserialize_state(emu, it.addressOf(0).reinterpret(), state.size.convert()) }
    }

    override fun readBattery(): ByteArray? = collect { sink, context -> ds_read_battery(emu, sink, context) }

    override fun consumeBatteryDirty(): Boolean = ds_consume_battery_dirty(emu)

    override fun captureFrame(): IntArray? {
        val pixels = IntArray(DS_FRAME_PIXELS.toInt())
        val ok = pixels.usePinned { ds_copy_frame_argb(emu, it.addressOf(0).reinterpret<UIntVar>(), pixels.size.convert()) }
        return if (ok) pixels else null
    }

    override fun captureFrameInto(buffer: FrameBuffer): Boolean =
        buffer.bytes.usePinned { ds_copy_frame_rgba(emu, it.addressOf(0).reinterpret<UByteVar>(), buffer.size.convert()) }

    /**
     * Las funciones de ds_core que entregan bytes (estado, batería) los pasan
     * a un «sumidero» mientras viven: aquí se copian a un ByteArray de
     * Kotlin. El ByteArray viaja por el contexto como StableRef.
     */
    private fun collect(call: (sink: kotlinx.cinterop.CPointer<kotlinx.cinterop.CFunction<(CPointer<UByteVar>?, size_t, COpaquePointer?) -> Unit>>, context: COpaquePointer) -> Boolean): ByteArray? {
        val holder = Holder()
        val ref = StableRef.create(holder)
        return try {
            if (call(byteSink, ref.asCPointer())) holder.bytes else null
        } finally {
            ref.dispose()
        }
    }

    private class Holder {
        var bytes: ByteArray? = null
    }

    @ThreadLocal
    private companion object {
        val byteSink = staticCFunction { data: CPointer<UByteVar>?, length: size_t, context: COpaquePointer? ->
            val holder = context!!.asStableRef<Holder>().get()
            holder.bytes = data?.readBytes(length.toInt()) ?: ByteArray(0)
        }
    }
}
