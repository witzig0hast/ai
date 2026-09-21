package de.hastnetwork.jarvis.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "jarvis_settings")

enum class DarkModeOverride {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val baseUrl: String = "",
    val token: String = "",
    val darkModeOverride: DarkModeOverride = DarkModeOverride.SYSTEM,
) {
    /** Trimmed base URL with any trailing slash removed, ready to be joined with a path. */
    val normalizedBaseUrl: String
        get() = baseUrl.trim().trimEnd('/')

    val isConfigured: Boolean
        get() = normalizedBaseUrl.isNotEmpty() && token.isNotBlank()
}

/**
 * Persists the backend base URL, device bearer token and dark-mode override
 * via Jetpack DataStore (Preferences). This is the single source of truth
 * for connection settings; [AppContainer] caches the latest value in a
 * [kotlinx.coroutines.flow.StateFlow] so synchronous call sites (OkHttp
 * interceptors) can read it without suspending.
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val BASE_URL = stringPreferencesKey("base_url")
        val TOKEN = stringPreferencesKey("token")
        val DARK_MODE_OVERRIDE = stringPreferencesKey("dark_mode_override")
    }

    val settingsFlow: Flow<AppSettings> =
        context.dataStore.data.map { prefs ->
            AppSettings(
                baseUrl = prefs[Keys.BASE_URL] ?: "",
                token = prefs[Keys.TOKEN] ?: "",
                darkModeOverride = prefs[Keys.DARK_MODE_OVERRIDE]?.let { stored ->
                    runCatching { DarkModeOverride.valueOf(stored) }.getOrDefault(DarkModeOverride.SYSTEM)
                } ?: DarkModeOverride.SYSTEM,
            )
        }

    suspend fun updateBaseUrl(baseUrl: String) {
        context.dataStore.edit { it[Keys.BASE_URL] = baseUrl }
    }

    suspend fun updateToken(token: String) {
        context.dataStore.edit { it[Keys.TOKEN] = token }
    }

    suspend fun updateDarkModeOverride(mode: DarkModeOverride) {
        context.dataStore.edit { it[Keys.DARK_MODE_OVERRIDE] = mode.name }
    }
}
