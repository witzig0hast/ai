package de.hastnetwork.jarvis.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hastnetwork.jarvis.data.local.AppSettings
import de.hastnetwork.jarvis.data.local.DarkModeOverride
import de.hastnetwork.jarvis.data.local.SettingsDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsDataStore: SettingsDataStore) : ViewModel() {

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { _settings.value = it }
        }
    }

    fun updateBaseUrl(value: String) {
        viewModelScope.launch { settingsDataStore.updateBaseUrl(value) }
    }

    fun updateToken(value: String) {
        viewModelScope.launch { settingsDataStore.updateToken(value) }
    }

    fun updateDarkModeOverride(mode: DarkModeOverride) {
        viewModelScope.launch { settingsDataStore.updateDarkModeOverride(mode) }
    }
}
