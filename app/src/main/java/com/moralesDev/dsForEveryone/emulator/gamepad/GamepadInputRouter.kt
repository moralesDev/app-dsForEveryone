package com.moralesDev.dsForEveryone.emulator.gamepad

import android.view.KeyEvent
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.emulator.DsButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/** Un objetivo mapeado, ya traducido a lo que le importa a `InputManager`. */
sealed interface GamepadInput {
    data class Button(val button: DsButton, val isPressed: Boolean) : GamepadInput
    data class Blow(val active: Boolean) : GamepadInput
}

/**
 * Único punto donde entra un `KeyEvent` de mando, venga de Bluetooth o de
 * USB. Vive como singleton de la aplicación porque el capturador de la
 * pantalla de mapeo y el traductor de la partida en curso comparten el mismo
 * flujo de eventos —nunca a la vez, pero el mismo origen—.
 *
 * Se llama desde `Activity.dispatchKeyEvent`, el punto más alto posible:
 * intercepta antes de que el evento le llegue al foco de Compose. Mismo
 * espíritu que el estado compartido de `InputManager` para el táctil — nada
 * de colas, un evento entra y se traduce o se captura en el momento.
 *
 * [isActive] evita que un mando sin pantalla de juego ni de mapeo abiertas
 * interfiera con la navegación normal de la app (biblioteca, ajustes): fuera
 * de esas dos pantallas, [dispatch] no consume nada.
 */
class GamepadInputRouter(
    settingsRepository: SettingsRepository,
    applicationScope: CoroutineScope,
) {

    @Volatile
    var isActive: Boolean = false

    @Volatile
    private var mappingsByDescriptor: Map<String, GamepadMapping> = emptyMap()

    /** `null` si nadie está esperando el próximo botón; si no, quien llame a [dispatch] se lo entrega y no al juego. */
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
     * Llamar desde `Activity.dispatchKeyEvent`. Devuelve `true` si el evento
     * era de un mando y quedó consumido aquí —no debe seguir su camino normal
     * hacia el foco de Compose—.
     */
    fun dispatch(event: KeyEvent): Boolean {
        if (!isActive) return false
        val device = event.device ?: return false
        if (!device.isGamepad()) return false
        // Auto-repetición de tener el botón mantenido: ya se maneja con el propio ACTION_DOWN/UP, se ignora sin dejarla pasar.
        if (event.repeatCount > 0) return true

        if (event.action == KeyEvent.ACTION_DOWN) {
            captureCallback?.let { callback ->
                captureCallback = null
                callback(event.keyCode)
                return true
            }
        }
        if (event.action != KeyEvent.ACTION_DOWN && event.action != KeyEvent.ACTION_UP) return true

        val mapping = mappingsByDescriptor[device.descriptor] ?: GamepadMapping.DEFAULT
        val target = mapping.targetFor(event.keyCode) ?: return true
        val isPressed = event.action == KeyEvent.ACTION_DOWN
        _events.tryEmit(
            when (target) {
                is GamepadTarget.Button -> GamepadInput.Button(target.button, isPressed)
                GamepadTarget.Blow -> GamepadInput.Blow(isPressed)
            },
        )
        return true
    }
}
