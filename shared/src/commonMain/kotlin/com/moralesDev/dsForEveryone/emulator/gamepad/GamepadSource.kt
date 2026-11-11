package com.moralesDev.dsForEveryone.emulator.gamepad

import com.moralesDev.dsForEveryone.emulator.DsButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Un mando conectado, con lo mínimo que le importa al resto del proyecto: su
 * identidad estable ([descriptor], sobrevive a reconexiones) y su nombre para
 * mostrar. Cada plataforma lo saca de lo suyo (`InputDevice` en Android,
 * `GCController` en iOS).
 */
data class GamepadDevice(val descriptor: String, val name: String)

/** Un objetivo mapeado, ya traducido a lo que le importa a `InputManager`. */
sealed interface GamepadInput {
    data class Button(val button: DsButton, val isPressed: Boolean) : GamepadInput
    data class Blow(val active: Boolean) : GamepadInput
}

/**
 * Los mandos físicos tal como los ve la pantalla de juego: cuáles hay, cuándo
 * llega uno nuevo (para el aviso «Mando detectado») y sus pulsaciones ya
 * traducidas por el mapeo. Común a Android e iOS; cada plataforma lo
 * implementa con su API de mandos (docs/ios.md, iOS-4).
 *
 * [isActive] lo pone la pantalla de juego mientras está viva: fuera de ella
 * un mando no debe colarse en el juego ni tragarse la navegación normal.
 */
interface GamepadSource {
    val connectedDevices: StateFlow<List<GamepadDevice>>

    /** Sólo las conexiones *nuevas*, no el estado en curso. */
    val connections: Flow<GamepadDevice>

    val events: Flow<GamepadInput>

    var isActive: Boolean

    /** El próximo botón que se pulse va a [onCaptured] en vez de al juego: la pantalla de mapeo. */
    fun beginCapture(onCaptured: (keyCode: Int) -> Unit)

    fun cancelCapture()
}

/** Sin mandos: para pruebas y para una plataforma que no los lea. */
class NoGamepads : GamepadSource {
    override val connectedDevices: StateFlow<List<GamepadDevice>> =
        MutableStateFlow<List<GamepadDevice>>(emptyList()).asStateFlow()
    override val connections: Flow<GamepadDevice> = emptyFlow()
    override val events: Flow<GamepadInput> = emptyFlow()
    override var isActive: Boolean = false
    override fun beginCapture(onCaptured: (keyCode: Int) -> Unit) = Unit
    override fun cancelCapture() = Unit
}
