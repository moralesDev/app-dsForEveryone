package com.moralesDev.dsForEveryone.data.settings

import android.content.Context
import androidx.core.os.ConfigurationCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "emulator_settings",
)

/**
 * Preferencias del emulador sobre DataStore.
 *
 * Aquí sólo viven escalares. Las ROMs, las partidas y los archivos de sistema
 * nunca pasan por este almacén: son binarios que van a disco.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<EmulatorSettings> = context.settingsDataStore.data.map { preferences ->
        EmulatorSettings(
            // Sin elección explícita se sigue el idioma del teléfono.
            language = AppLanguage.fromTag(preferences[Keys.LANGUAGE]) ?: systemLanguage(),
            skipWelcome = preferences[Keys.SKIP_WELCOME] ?: DEFAULT.skipWelcome,
        )
    }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.LANGUAGE] = language.tag }

    suspend fun setSkipWelcome(skip: Boolean) = edit { it[Keys.SKIP_WELCOME] = skip }

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
    }

    private companion object {
        val DEFAULT = EmulatorSettings()
    }
}
