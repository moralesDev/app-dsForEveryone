package com.moralesDev.dsForEveryone.ui.game

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moralesDev.dsForEveryone.R
import com.moralesDev.dsForEveryone.data.rom.RomIdentity
import com.moralesDev.dsForEveryone.data.rom.RomInfo
import com.moralesDev.dsForEveryone.data.rom.RomRepository
import com.moralesDev.dsForEveryone.data.rom.RomSource
import com.moralesDev.dsForEveryone.data.save.SaveManager
import com.moralesDev.dsForEveryone.data.save.SaveStateManager
import com.moralesDev.dsForEveryone.data.save.SaveStateSlot
import com.moralesDev.dsForEveryone.data.save.StateIdentity
import com.moralesDev.dsForEveryone.data.save.StateReadResult
import com.moralesDev.dsForEveryone.data.settings.EmulatorSettings
import com.moralesDev.dsForEveryone.data.settings.ImageFilter
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.data.settings.TouchMode
import com.moralesDev.dsForEveryone.di.ApplicationScope
import com.moralesDev.dsForEveryone.emulator.DsButton
import com.moralesDev.dsForEveryone.emulator.EmulatorController
import com.moralesDev.dsForEveryone.emulator.EmulatorStatus
import com.moralesDev.dsForEveryone.emulator.core.CoreLoadResult
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode
import com.moralesDev.dsForEveryone.ui.UiMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Qué se está eligiendo en la hoja de ranuras. */
enum class SlotAction { SAVE, LOAD }

/**
 * Acción sobre una ranura que destruye algo y necesita un sí explícito. Las dos
 * son irreversibles: no hay papelera de save states.
 */
sealed interface SlotConfirmation {
    val slot: Int

    data class Overwrite(override val slot: Int) : SlotConfirmation

    data class Delete(override val slot: Int) : SlotConfirmation
}

/**
 * Sesión de juego, contra `StubEmulatorCore` hasta que llegue el core nativo
 * (fase 4).
 *
 * Si la navegación trae un `romId` —desde la biblioteca (fase 3.3)— se carga
 * esa ROM de verdad, leída a través de [RomRepository] (descomprime el `.zip`
 * si hace falta), junto con su partida de batería si ya tenía una. Sin
 * `romId` se abre un cartucho de mentira que sólo necesita suficientes bytes
 * para pasar la comprobación de tamaño mínimo del stub: es lo que deja seguir
 * probando pantallas, táctil y mandos sin pasar por la biblioteca, como en
 * las fases 2.x. En ese caso no hay identidad de sesión y guardar o cargar un
 * estado no hace nada.
 */
@HiltViewModel
class GameViewModel @Inject constructor(
    private val controller: EmulatorController,
    private val settingsRepository: SettingsRepository,
    private val romRepository: RomRepository,
    private val saveManager: SaveManager,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val romId: String? = savedStateHandle["romId"]

    val settings: StateFlow<EmulatorSettings> = settingsRepository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = EmulatorSettings(),
    )

    val status: StateFlow<EmulatorStatus> get() = controller.status

    /** Máscara real de doce bits: lo que se pinta pulsado sale de aquí, no de estado local del composable. */
    val pressedMask: StateFlow<Int> get() = controller.input.pressed

    val micNoiseActive: StateFlow<Boolean> get() = controller.input.micNoiseActive

    /** Lo lee el hilo de GL directamente; no pasa por Compose ni por corrutinas. */
    val currentFrame: IntArray? get() = controller.currentFrame

    private val _slotAction = MutableStateFlow<SlotAction?>(null)
    val slotAction: StateFlow<SlotAction?> = _slotAction.asStateFlow()

    private val _stateSlots = MutableStateFlow<List<SaveStateSlot>>(emptyList())
    val stateSlots: StateFlow<List<SaveStateSlot>> = _stateSlots.asStateFlow()

    private val _autoSlot = MutableStateFlow<SaveStateSlot?>(null)
    val autoSlot: StateFlow<SaveStateSlot?> = _autoSlot.asStateFlow()

    private val _pendingConfirmation = MutableStateFlow<SlotConfirmation?>(null)
    val pendingConfirmation: StateFlow<SlotConfirmation?> = _pendingConfirmation.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    /** `null` mientras no se identifique la ROM, o siempre, si se entró sin `romId`. */
    private val _rom = MutableStateFlow<RomInfo?>(null)
    val rom: StateFlow<RomInfo?> = _rom.asStateFlow()

    /**
     * Con qué debe cuadrar un save state para poder restaurarse en esta
     * sesión. `null` hasta que la ROM cargue de verdad —y para siempre, si se
     * entró sin `romId`—, y entonces guardar y cargar no hacen nada.
     */
    private var identity: StateIdentity? = null

    init {
        viewModelScope.launch {
            val rom = romId?.let { romRepository.findById(it) }
            _rom.value = rom
            val battery = romId?.let { saveManager.loadBattery(it) }

            // openRomForCore evita cargar la ROM entera en un ByteArray de
            // Kotlin cuando se puede: una ROM suelta grande (128 MB no es
            // raro en un cartucho real de DS) agotaba el heap antes de
            // llegar siquiera al core (ver docs/estabilizacion-core.md).
            val source = rom?.let { romRepository.openRomForCore(it) } ?: RomSource.Bytes(FAKE_CARTRIDGE)
            val result = when (source) {
                is RomSource.Bytes -> controller.open(source.data, battery)
                is RomSource.Descriptor -> controller.openFromDescriptor(source.fd, source.length, battery)
            }

            if (result is CoreLoadResult.Success && romId != null) {
                identity = StateIdentity(
                    romSha256 = romId,
                    // Se calcula sobre los bytes que acaban de entrar en el
                    // core, no sobre los que se leyeron al dar de alta la ROM:
                    // es lo que se está emulando de verdad. Con Descriptor el
                    // core ya se quedó con ese descriptor, así que hace falta
                    // un flujo propio para el CRC32.
                    romCrc32 = when (source) {
                        is RomSource.Bytes -> RomIdentity.crc32(source.data)
                        is RomSource.Descriptor ->
                            rom?.let { romRepository.openRawStream(it)?.use(RomIdentity::crc32) } ?: 0L
                    },
                    coreName = controller.coreName,
                    coreVersion = controller.coreVersion,
                    stateSize = controller.stateSize(),
                )
                romRepository.markPlayed(romId)
                refreshSlots()
            }
            controller.resume()
        }

        // El core avisa cuando el juego escribe su partida; aquí se persiste,
        // siempre, sin ajuste que lo desactive: si el juego ofrece guardar,
        // guardar tiene que funcionar.
        viewModelScope.launch {
            controller.batteryUpdates.collect { data ->
                romId?.let { saveManager.persistBattery(it, data) }
            }
        }
    }

    fun onButton(button: DsButton, pressed: Boolean) = controller.input.setPressed(button, pressed)

    fun onDirections(directions: Set<DsButton>) = controller.input.setDirections(directions)

    fun onTouch(x: Int, y: Int, down: Boolean) = controller.input.setTouch(x, y, down)

    fun onMicNoise(active: Boolean) = controller.input.setMicNoise(active)

    fun pause() = controller.pause()

    fun resume() = controller.resume()

    fun setLayoutMode(mode: LayoutMode, portrait: Boolean) {
        viewModelScope.launch {
            if (portrait) {
                settingsRepository.setPortraitLayoutMode(mode)
            } else {
                settingsRepository.setLandscapeLayoutMode(mode)
            }
        }
    }

    fun setTouchMode(mode: TouchMode) {
        viewModelScope.launch { settingsRepository.setTouchMode(mode) }
    }

    fun setImageFilter(filter: ImageFilter) {
        viewModelScope.launch { settingsRepository.setImageFilter(filter) }
    }

    // --------------------------------------------------- save states

    fun chooseSlot(action: SlotAction) {
        _slotAction.value = action
        refreshSlots()
    }

    /** Cierra la hoja de ranuras sin elegir ninguna. La emulación sigue en pausa: el menú la reanuda al cerrarse. */
    fun dismissSlotPicker() {
        _slotAction.value = null
        _pendingConfirmation.value = null
    }

    /** Toque sobre una ranura. Guardar encima de algo no se hace de un solo toque: un save state sobrescrito no se recupera. */
    fun onSlotChosen(slot: Int) {
        val occupied = _stateSlots.value.firstOrNull { it.index == slot }?.isEmpty == false
        when (_slotAction.value) {
            SlotAction.SAVE -> if (occupied) confirm(SlotConfirmation.Overwrite(slot)) else saveStateTo(slot)
            SlotAction.LOAD -> loadStateFrom(slot)
            null -> Unit
        }
    }

    fun requestSlotDeletion(slot: Int) = confirm(SlotConfirmation.Delete(slot))

    /** Recupera el respaldo automático. Es la única acción del usuario sobre esa ranura además de borrarla. */
    fun restoreAutoBackup() = loadStateFrom(SaveStateManager.AUTO_SLOT)

    fun cancelConfirmation() {
        _pendingConfirmation.value = null
    }

    fun acceptConfirmation() {
        val pending = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        when (pending) {
            is SlotConfirmation.Overwrite -> saveStateTo(pending.slot)
            is SlotConfirmation.Delete -> deleteState(pending.slot)
        }
    }

    private fun confirm(confirmation: SlotConfirmation) {
        _pendingConfirmation.value = confirmation
    }

    private fun saveStateTo(slot: Int) {
        val sessionIdentity = identity
        val rid = romId
        if (sessionIdentity == null || rid == null) {
            _message.value = UiMessage(R.string.msg_state_no_session)
            return
        }
        viewModelScope.launch {
            val payload = controller.saveState()
            if (payload == null) {
                _message.value = UiMessage(R.string.msg_state_failed)
                return@launch
            }
            val frame = controller.captureFrame()
            val saved = saveManager.writeState(rid, slot, payload, frame, sessionIdentity)
            _message.value = if (saved) {
                UiMessage(R.string.msg_state_saved, slot)
            } else {
                UiMessage(R.string.msg_state_failed)
            }
            refreshSlots()
            _slotAction.value = null
        }
    }

    /**
     * El core sólo llega a ver el estado si ha superado antes todas las
     * comprobaciones del contenedor. Si algo no cuadra, la partida en curso se
     * queda exactamente como estaba y el menú sigue abierto.
     */
    private fun loadStateFrom(slot: Int) {
        val sessionIdentity = identity
        val rid = romId
        if (sessionIdentity == null || rid == null) {
            _message.value = UiMessage(R.string.msg_state_no_session)
            return
        }
        viewModelScope.launch {
            when (val result = saveManager.readState(rid, slot, sessionIdentity)) {
                StateReadResult.Empty -> _message.value = UiMessage(R.string.msg_state_slot_empty, slot)
                StateReadResult.Unreadable -> _message.value = UiMessage(R.string.msg_state_unreadable)
                is StateReadResult.Rejected -> _message.value = result.reason.toMessage()
                is StateReadResult.Loaded -> {
                    val ok = controller.loadState(result.payload)
                    _message.value = if (ok) {
                        UiMessage(R.string.msg_state_loaded, slot)
                    } else {
                        UiMessage(R.string.msg_state_rejected_by_core)
                    }
                }
            }
            _slotAction.value = null
            refreshSlots()
        }
    }

    private fun deleteState(slot: Int) {
        val rid = romId ?: return
        viewModelScope.launch {
            saveManager.deleteState(rid, slot)
            _message.value = UiMessage(R.string.msg_state_deleted, slot)
            refreshSlots()
        }
    }

    private fun refreshSlots() {
        val rid = romId ?: return
        viewModelScope.launch {
            _stateSlots.value = saveManager.stateSlots(rid, identity)
            _autoSlot.value = saveManager.autoSlot(rid, identity)
        }
    }

    fun consumeMessage() {
        _message.value = null
    }

    /**
     * Al salir del juego se vuelca la partida antes de soltar el core.
     *
     * No puede usar `viewModelScope`, que se cancela justo antes de llegar
     * aquí: por eso el volcado y la parada corren en [applicationScope], que
     * vive lo que vive la aplicación, y en ese orden exacto — la parada
     * detiene el hilo de emulación, así que un volcado que llegara después no
     * tendría ya con quién hablar.
     */
    override fun onCleared() {
        val rid = romId
        applicationScope.launch {
            if (rid != null) {
                controller.flushBattery()?.let { saveManager.persistBattery(rid, it) }
            }
            controller.stop()
        }
    }

    private companion object {
        /**
         * Suficiente para superar la comprobación de tamaño mínimo de
         * `StubEmulatorCore`; el contenido no importa, el stub no lee nada
         * más que el título.
         */
        val FAKE_CARTRIDGE = ByteArray(512)
    }
}
