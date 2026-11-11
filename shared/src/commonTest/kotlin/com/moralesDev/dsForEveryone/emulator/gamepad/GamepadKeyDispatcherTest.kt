package com.moralesDev.dsForEveryone.emulator.gamepad

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.moralesDev.dsForEveryone.data.settings.SettingsRepository
import com.moralesDev.dsForEveryone.emulator.DsButton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lo común de una pulsación de mando, igual en Android que en iOS. */
@OptIn(ExperimentalCoroutinesApi::class)
class GamepadKeyDispatcherTest {

    private class MemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private fun setUp(scope: kotlinx.coroutines.CoroutineScope): Pair<GamepadKeyDispatcher, SettingsRepository> {
        val settings = SettingsRepository(MemoryDataStore(), systemLanguageTag = { null })
        return GamepadKeyDispatcher(settings, scope) to settings
    }

    @Test
    fun `inactivo no consume nada ni llega al juego`() = runTest(UnconfinedTestDispatcher()) {
        val (dispatcher, _) = setUp(backgroundScope)
        val events = mutableListOf<GamepadInput>()
        backgroundScope.launch { dispatcher.events.toList(events) }

        assertFalse(dispatcher.dispatch("mando", GamepadKeyCodes.BUTTON_A, isPressed = true))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `activo traduce con el mapeo por defecto pulsar y soltar`() = runTest(UnconfinedTestDispatcher()) {
        val (dispatcher, _) = setUp(backgroundScope)
        val events = mutableListOf<GamepadInput>()
        backgroundScope.launch { dispatcher.events.toList(events) }
        dispatcher.isActive = true

        assertTrue(dispatcher.dispatch("mando", GamepadKeyCodes.BUTTON_A, isPressed = true))
        assertTrue(dispatcher.dispatch("mando", GamepadKeyCodes.BUTTON_A, isPressed = false))
        assertTrue(dispatcher.dispatch("mando", GamepadKeyCodes.BUTTON_THUMBL, isPressed = true))

        assertEquals(
            listOf(
                GamepadInput.Button(DsButton.A, true),
                GamepadInput.Button(DsButton.A, false),
                GamepadInput.Blow(true),
            ),
            events,
        )
    }

    @Test
    fun `una tecla sin asignar se consume sin llegar al juego`() = runTest(UnconfinedTestDispatcher()) {
        val (dispatcher, _) = setUp(backgroundScope)
        val events = mutableListOf<GamepadInput>()
        backgroundScope.launch { dispatcher.events.toList(events) }
        dispatcher.isActive = true

        assertTrue(dispatcher.dispatch("mando", GamepadKeyCodes.BUTTON_MODE, isPressed = true))
        assertTrue(events.isEmpty())
    }

    @Test
    fun `capturando el siguiente boton va al capturador una sola vez`() = runTest(UnconfinedTestDispatcher()) {
        val (dispatcher, _) = setUp(backgroundScope)
        val events = mutableListOf<GamepadInput>()
        backgroundScope.launch { dispatcher.events.toList(events) }
        dispatcher.isActive = true
        val captured = mutableListOf<Int>()

        dispatcher.beginCapture { captured += it }
        dispatcher.dispatch("mando", GamepadKeyCodes.BUTTON_R2, isPressed = true)
        dispatcher.dispatch("mando", GamepadKeyCodes.BUTTON_B, isPressed = true)

        assertEquals(listOf(GamepadKeyCodes.BUTTON_R2), captured)
        assertEquals(listOf<GamepadInput>(GamepadInput.Button(DsButton.B, true)), events)
    }

    @Test
    fun `un teclado sin mapeo guardado usa el suyo por defecto`() = runTest(UnconfinedTestDispatcher()) {
        val (dispatcher, _) = setUp(backgroundScope)
        val events = mutableListOf<GamepadInput>()
        backgroundScope.launch { dispatcher.events.toList(events) }
        dispatcher.isActive = true

        dispatcher.dispatch("teclado", GamepadKeyCodes.KEY_X, isPressed = true, isKeyboard = true)
        dispatcher.dispatch("teclado", GamepadKeyCodes.SPACE, isPressed = true, isKeyboard = true)
        dispatcher.dispatch("teclado", GamepadKeyCodes.BUTTON_A, isPressed = true, isKeyboard = true)

        assertEquals(listOf(GamepadInput.Button(DsButton.A, true), GamepadInput.Blow(true)), events)
    }

    @Test
    fun `cada mando usa su propio mapeo`() = runTest(UnconfinedTestDispatcher()) {
        val (dispatcher, settings) = setUp(backgroundScope)
        val events = mutableListOf<GamepadInput>()
        backgroundScope.launch { dispatcher.events.toList(events) }
        dispatcher.isActive = true
        settings.setGamepadMapping(
            "ios:Xbox",
            GamepadMapping.DEFAULT.with(GamepadTarget.Button(DsButton.R), GamepadKeyCodes.BUTTON_R2),
        )
        runCurrent()

        dispatcher.dispatch("ios:Xbox", GamepadKeyCodes.BUTTON_R2, isPressed = true)
        dispatcher.dispatch("otro", GamepadKeyCodes.BUTTON_R2, isPressed = true)
        dispatcher.dispatch("otro", GamepadKeyCodes.BUTTON_R1, isPressed = true)

        assertEquals(
            listOf<GamepadInput>(GamepadInput.Button(DsButton.R, true), GamepadInput.Button(DsButton.R, true)),
            events,
        )
    }
}
