package com.moralesDev.dsForEveryone.emulator

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Posición del lápiz sobre la pantalla táctil, en coordenadas de la DS
 * (0..255, 0..191). [down] distingue "el dedo está en (0,0)" de "no hay
 * ningún dedo": la DS necesita saber cuándo el lápiz se ha levantado, no
 * sólo dónde estaba la última vez.
 */
data class TouchState(val x: Int, val y: Int, val down: Boolean) {
    companion object {
        val UP = TouchState(x = 0, y = 0, down = false)
    }
}

/**
 * Estado de los mandos y del lápiz, compartido entre el hilo de UI (que
 * escribe) y el hilo de emulación (que lee una vez por fotograma).
 *
 * La verdad de los botones vive en un [AtomicInteger] de doce bits, igual que
 * en `app-emulador` pero con dos bits más. La verdad del lápiz vive en una
 * referencia atómica a [TouchState]: es **estado compartido, no un comando
 * encolado** (ver docs/entrada.md). Mientras el dedo esté abajo, la UI
 * escribe la posición en cada fotograma —no sólo cuando cambia—, y el hilo
 * de emulación la lee al principio de cada uno.
 *
 * Los [StateFlow] son sólo un espejo para que la UI pueda resaltar botones y
 * dibujar el punto de contacto; si se retrasan un fotograma no pasa nada,
 * porque no son la fuente que alimenta al core.
 */
class InputManager {

    private val mask = AtomicInteger(0)
    private val touch = AtomicReference(TouchState.UP)

    private val _pressed = MutableStateFlow(0)
    private val _touch = MutableStateFlow(TouchState.UP)

    /** Máscara observable para pintar el estado de los botones. */
    val pressed: StateFlow<Int> = _pressed.asStateFlow()

    /** Posición del lápiz observable, para dibujar el punto de contacto. */
    val touchState: StateFlow<TouchState> = _touch.asStateFlow()

    /** Máscara que consume el core al inicio de cada fotograma. */
    val currentMask: Int get() = mask.get()

    /** Posición del lápiz que consume el core al inicio de cada fotograma. */
    val currentTouch: TouchState get() = touch.get()

    fun press(button: DsButton) {
        publishMask(mask.updateAndGet { it or button.mask })
    }

    fun release(button: DsButton) {
        publishMask(mask.updateAndGet { it and button.mask.inv() })
    }

    fun setPressed(button: DsButton, isPressed: Boolean) {
        if (isPressed) press(button) else release(button)
    }

    /**
     * Sustituye de golpe el estado de la cruceta. Se trata como una unidad
     * porque un mismo dedo puede pasar de `{UP}` a `{UP, RIGHT}` y a
     * `{RIGHT}` sin levantarse (ver [DirectionalPadZones]): emitir el
     * conjunto completo evita direcciones que se quedan pegadas al deslizar.
     */
    fun setDirections(directions: Set<DsButton>) {
        val added = directions.fold(0) { acc, button -> acc or button.mask }
        publishMask(mask.updateAndGet { (it and DIRECTION_MASK.inv()) or added })
    }

    /**
     * Escribe la posición del lápiz. Se llama en cada fotograma mientras el
     * dedo está abajo, no sólo cuando cambia: es estado continuo, igual que
     * la máscara de botones. Encolar un comando por evento táctil produciría
     * cientos de comandos por segundo y arrastres a saltos.
     *
     * @param x,y coordenada de la DS (0..255, 0..191). Se ignoran si [down]
     *   es `false`.
     */
    fun setTouch(x: Int, y: Int, down: Boolean) {
        val state = if (down) TouchState(x, y, down = true) else TouchState.UP
        touch.set(state)
        _touch.value = state
    }

    /** Suelta todo. Se usa al pausar, al abrir el menú y al perder el foco. */
    fun releaseAll() {
        publishMask(mask.updateAndGet { 0 })
        setTouch(0, 0, down = false)
    }

    private fun publishMask(value: Int) {
        _pressed.value = value
    }

    private companion object {
        val DIRECTION_MASK =
            DsButton.UP.mask or DsButton.DOWN.mask or
                DsButton.LEFT.mask or DsButton.RIGHT.mask
    }
}
