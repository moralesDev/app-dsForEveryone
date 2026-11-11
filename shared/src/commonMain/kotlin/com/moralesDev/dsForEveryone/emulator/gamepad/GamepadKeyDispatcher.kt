package com.moralesDev.dsForEveryone.emulator.gamepad

import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.concurrent.Volatile

/**
 * Lo común a Android e iOS de una pulsación de mando: una vez que cada
 * plataforma ha dicho de qué mando viene ([GamepadDevice.descriptor]), qué
 * tecla es (en [GamepadKeyCodes], los mismos números en las dos) y si se
 * pulsa o se suelta, aquí se decide adónde va — al capturador de la pantalla
 * de mapeo, o traducida por el mapeo de ese mando a [events] para el juego.
 *
 * Vive como singleton de la aplicación: el capturador de la pantalla de mapeo
 * y el traductor de la partida en curso comparten el mismo origen, nunca a la
 * vez. Sin colas: un evento entra y se traduce o se captura en el momento,
 * igual que el estado compartido de `InputManager` para el táctil.
 *
 * [isActive] evita que un mando fuera de la pantalla de juego o la de mapeo
 * se cuele en el juego o se trague la navegación normal de la app.
 */
class GamepadKeyDispatcher(
    settingsRepository: SettingsRepository,
    applicationScope: CoroutineScope,
) {

    @Volatile
    var isActive: Boolean = false

    @Volatile
    private var mappingsByDescriptor: Map<String, GamepadMapping> = emptyMap()

    /** `null` si nadie espera el próximo botón; si no, el próximo que se pulse es suyo y no del juego. */
    @Volatile
    private var captureCallback: ((keyCode: Int) -> Unit)? = null

    private val _events = MutableSharedFlow<GamepadInput>(extraBufferCapacity = 16)
    val events: SharedFlow<GamepadInput> = _events.asSharedFlow()

    init {
        settingsRepository.settings
            .map { it.gamepadMappings }
            .onEach { mappingsByDescriptor = it }
            .launchIn(applicationScope)
    }

    /** El próximo botón que llegue de un mando se le entrega a [onCaptured] en vez de inyectarse al juego. */
    fun beginCapture(onCaptured: (keyCode: Int) -> Unit) {
        captureCallback = onCaptured
    }

    fun cancelCapture() {
        captureCallback = null
    }

    /**
     * Una tecla de mando que se pulsa o se suelta. Devuelve `true` si queda
     * consumida aquí —con [isActive], todas las de un mando, mapeadas o no—.
     * [isKeyboard] elige el mapeo por defecto si ese aparato no tiene uno
     * guardado.
     */
    fun dispatch(descriptor: String, keyCode: Int, isPressed: Boolean, isKeyboard: Boolean = false): Boolean {
        if (!isActive) return false
        if (isPressed) {
            captureCallback?.let { callback ->
                captureCallback = null
                callback(keyCode)
                return true
            }
        }
        val mapping = mappingsByDescriptor[descriptor] ?: GamepadMapping.defaultFor(isKeyboard)
        val target = mapping.targetFor(keyCode) ?: return true
        _events.tryEmit(
            when (target) {
                is GamepadTarget.Button -> GamepadInput.Button(target.button, isPressed)
                GamepadTarget.Blow -> GamepadInput.Blow(isPressed)
            },
        )
        return true
    }
}
