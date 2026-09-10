package com.moralesDev.dsForEveryone.data.settings

import android.content.Context
import androidx.core.os.ConfigurationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moralesDev.dsForEveryone.emulator.gamepad.GamepadMapping
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroup
import com.moralesDev.dsForEveryone.emulator.layout.ControlGroupFraction
import com.moralesDev.dsForEveryone.emulator.layout.ControlLayout
import com.moralesDev.dsForEveryone.emulator.layout.LayoutMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "emulator_settings",
)

/**
 * Preferencias del emulador sobre DataStore.
 *
 * Aquí sólo viven escalares. Las ROMs, las partidas y los archivos de sistema
 * nunca pasan por este almacén: son binarios que van a disco, y nada de esto
 * pasa por `SharedPreferences`.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<EmulatorSettings> = context.settingsDataStore.data.map { preferences ->
        EmulatorSettings(
            // Sin elección explícita se sigue el idioma del teléfono.
            language = AppLanguage.fromTag(preferences[Keys.LANGUAGE]) ?: systemLanguage(),
            skipWelcome = preferences[Keys.SKIP_WELCOME] ?: DEFAULT.skipWelcome,
            portraitLayoutMode = layoutModeFromName(preferences[Keys.PORTRAIT_LAYOUT])
                ?: DEFAULT.portraitLayoutMode,
            landscapeLayoutMode = layoutModeFromName(preferences[Keys.LANDSCAPE_LAYOUT])
                ?: DEFAULT.landscapeLayoutMode,
            screenGap = preferences[Keys.SCREEN_GAP] ?: DEFAULT.screenGap,
            secondaryScale = preferences[Keys.SECONDARY_SCALE] ?: DEFAULT.secondaryScale,
            screensSwapped = preferences[Keys.SCREENS_SWAPPED] ?: DEFAULT.screensSwapped,
            imageFilter = ImageFilter.fromName(preferences[Keys.IMAGE_FILTER])
                ?: DEFAULT.imageFilter,
            speed = EmulationSpeed.fromName(preferences[Keys.SPEED]) ?: DEFAULT.speed,
            touchMode = TouchMode.fromName(preferences[Keys.TOUCH_MODE]) ?: DEFAULT.touchMode,
            touchOffsetDp = preferences[Keys.TOUCH_OFFSET_DP] ?: DEFAULT.touchOffsetDp,
            portraitControlLayout = preferences[Keys.PORTRAIT_CONTROLS]?.let(ControlLayout::decode),
            landscapeControlLayout = preferences[Keys.LANDSCAPE_CONTROLS]?.let(ControlLayout::decode),
            gamepadMappings = preferences[Keys.GAMEPAD_MAPPINGS]?.let(GamepadMapping::decodeAll)
                ?: DEFAULT.gamepadMappings,
        )
    }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.tag }

    suspend fun setSkipWelcome(skip: Boolean) = edit { it[Keys.SKIP_WELCOME] = skip }

    suspend fun setPortraitLayoutMode(mode: LayoutMode) = edit {
        it[Keys.PORTRAIT_LAYOUT] = mode.name
    }

    suspend fun setLandscapeLayoutMode(mode: LayoutMode) = edit {
        it[Keys.LANDSCAPE_LAYOUT] = mode.name
    }

    suspend fun setScreenGap(gap: Float) = edit { it[Keys.SCREEN_GAP] = gap.coerceIn(0f, 1f) }

    suspend fun setSecondaryScale(scale: Float) = edit {
        it[Keys.SECONDARY_SCALE] = scale.coerceIn(MIN_SECONDARY_SCALE, MAX_SECONDARY_SCALE)
    }

    suspend fun setScreensSwapped(swapped: Boolean) = edit { it[Keys.SCREENS_SWAPPED] = swapped }

    suspend fun setImageFilter(filter: ImageFilter) = edit { it[Keys.IMAGE_FILTER] = filter.name }

    suspend fun setSpeed(speed: EmulationSpeed) = edit { it[Keys.SPEED] = speed.name }

    suspend fun setTouchMode(mode: TouchMode) = edit { it[Keys.TOUCH_MODE] = mode.name }

    suspend fun setTouchOffsetDp(offset: Float) = edit {
        it[Keys.TOUCH_OFFSET_DP] = offset.coerceAtLeast(0f)
    }

    /** `null` borra el perfil guardado: la pantalla de juego y el editor caen entonces al perfil por defecto. */
    suspend fun setPortraitControlLayout(fractions: Map<ControlGroup, ControlGroupFraction>?) = edit {
        if (fractions == null) it.remove(Keys.PORTRAIT_CONTROLS) else it[Keys.PORTRAIT_CONTROLS] = ControlLayout.encode(fractions)
    }

    suspend fun setLandscapeControlLayout(fractions: Map<ControlGroup, ControlGroupFraction>?) = edit {
        if (fractions == null) it.remove(Keys.LANDSCAPE_CONTROLS) else it[Keys.LANDSCAPE_CONTROLS] = ControlLayout.encode(fractions)
    }

    /** Reasigna el mapeo completo de un mando. Se guardan siempre los trece objetivos: ver `GamepadMapping.decode`. */
    suspend fun setGamepadMapping(descriptor: String, mapping: GamepadMapping) = edit { preferences ->
        val current = preferences[Keys.GAMEPAD_MAPPINGS]?.let(GamepadMapping::decodeAll) ?: emptyMap()
        preferences[Keys.GAMEPAD_MAPPINGS] = GamepadMapping.encodeAll(current + (descriptor to mapping))
    }

    /** Borra el mapeo personalizado de un mando: vuelve a usar [GamepadMapping.DEFAULT]. */
    suspend fun resetGamepadMapping(descriptor: String) = edit { preferences ->
        val current = preferences[Keys.GAMEPAD_MAPPINGS]?.let(GamepadMapping::decodeAll) ?: emptyMap()
        preferences[Keys.GAMEPAD_MAPPINGS] = GamepadMapping.encodeAll(current - descriptor)
    }

    private fun layoutModeFromName(name: String?): LayoutMode? =
        LayoutMode.entries.firstOrNull { it.name == name }

    private fun systemLanguage(): AppLanguage {
        val locales = ConfigurationCompat.getLocales(context.resources.configuration)
        val primary = locales[0]?.language
        return if (primary == AppLanguage.SPANISH.tag) AppLanguage.SPANISH else AppLanguage.ENGLISH
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val SKIP_WELCOME = booleanPreferencesKey("skip_welcome")
        val PORTRAIT_LAYOUT = stringPreferencesKey("portrait_layout")
        val LANDSCAPE_LAYOUT = stringPreferencesKey("landscape_layout")
        val SCREEN_GAP = floatPreferencesKey("screen_gap")
        val SECONDARY_SCALE = floatPreferencesKey("secondary_scale")
        val SCREENS_SWAPPED = booleanPreferencesKey("screens_swapped")
        val IMAGE_FILTER = stringPreferencesKey("image_filter")
        val SPEED = stringPreferencesKey("speed")
        val TOUCH_MODE = stringPreferencesKey("touch_mode")
        val TOUCH_OFFSET_DP = floatPreferencesKey("touch_offset_dp")
        val PORTRAIT_CONTROLS = stringPreferencesKey("portrait_controls")
        val LANDSCAPE_CONTROLS = stringPreferencesKey("landscape_controls")
        val GAMEPAD_MAPPINGS = stringPreferencesKey("gamepad_mappings")
    }

    private companion object {
        val DEFAULT = EmulatorSettings()
        const val MIN_SECONDARY_SCALE = 0.1f
        const val MAX_SECONDARY_SCALE = 0.9f
    }
}
