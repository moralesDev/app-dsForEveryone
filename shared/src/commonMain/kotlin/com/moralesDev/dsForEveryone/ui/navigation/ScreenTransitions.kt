package com.moralesDev.dsForEveryone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import com.moralesDev.dsForEveryone.ui.components.LOADING_VEIL_FADE_MS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.TimeSource

/**
 * Quién decide cuándo se ve la pantalla de carga y cuánto dura.
 *
 * El problema que resuelve es de tiempos, no de estética. Cambiar de pantalla
 * no es gratis: salir del juego destruirá la textura GL del core y obligará a
 * la biblioteca a componer y medir su primera pantalla. Mientras eso ocurre,
 * lo que se ve es la pantalla anterior deshaciéndose, y parece una avería.
 *
 * La solución no es esperar un número fijo de milisegundos y confiar. Es un
 * orden concreto:
 *
 * 1. Se muestra el velo y se **espera a que sea opaco**. No basta con que esté
 *    dibujado: aparece con un fundido de [LOADING_VEIL_FADE_MS], y mientras
 *    dura se ve lo que hay debajo. Navegar antes no taparía nada.
 * 2. Con la pantalla ya cubierta del todo, se navega.
 * 3. Se espera a que el destino haya compuesto, medido y pintado.
 * 4. Si todo eso fue más rápido que el mínimo, se completa la espera.
 *
 * El mínimo no está para simular lentitud: está para que un cambio rápido no
 * produzca un parpadeo, que molesta más que la espera.
 */
@Stable
class ScreenTransitions internal constructor(private val scope: CoroutineScope) {

    var isRunning: Boolean by mutableStateOf(false)
        private set

    /**
     * Cubre la pantalla, ejecuta [navigate] con la vista ya tapada y se retira
     * cuando el destino está pintado.
     *
     * @param minimumMillis lo mínimo que se deja el velo, contando desde que
     *   está en pantalla. Ver [LEAVING_GAME_MS] y [DEFAULT_MS].
     */
    fun navigateWithVeil(minimumMillis: Long = DEFAULT_MS, navigate: () -> Unit) {
        // Un segundo toque mientras hay un cambio en marcha no encola otro:
        // el velo tapa la pantalla, así que el usuario está pulsando a ciegas.
        if (isRunning) return
        isRunning = true

        scope.launch {
            try {
                awaitDrawnFrames()
                val startedAt = TimeSource.Monotonic.markNow()

                // El velo ya está en pantalla, pero todavía translúcido.
                delay(LOADING_VEIL_FADE_MS.toLong())
                navigate()

                awaitDrawnFrames()
                val elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds
                delay((minimumMillis - elapsedMillis).coerceAtLeast(0))
            } finally {
                // En `finally` para que una cancelación no deje la aplicación
                // tapada por un velo que ya nadie va a quitar.
                isRunning = false
            }
        }
    }

    /**
     * Espera dos fotogramas. Hace falta que sean dos: el primero libera la
     * corrutina al principio del fotograma, antes de que Compose aplique la
     * recomposición pendiente, así que sólo al terminar el segundo se puede dar
     * por hecho que lo nuevo está realmente en pantalla.
     */
    private suspend fun awaitDrawnFrames() {
        withFrameNanos { }
        withFrameNanos { }
    }

    companion object {
        /**
         * Cambios entre pantallas normales. Son baratos —una lista, unos
         * ajustes— y aquí el velo sólo evita el salto seco.
         */
        const val DEFAULT_MS = 420L

        /**
         * Salir del juego (fase 2). Es el caso caro: hay que desmontar la
         * textura GL y liberar la ROM antes de que la biblioteca tenga la CPU
         * para ella sola.
         */
        const val LEAVING_GAME_MS = 900L
    }
}

@Composable
fun rememberScreenTransitions(): ScreenTransitions {
    val scope = rememberCoroutineScope()
    return remember(scope) { ScreenTransitions(scope) }
}
