package de.hastnetwork.jarvis.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hastnetwork.jarvis.data.local.AppSettings
import de.hastnetwork.jarvis.data.local.DarkModeOverride
import de.hastnetwork.jarvis.data.local.SettingsDataStore
import de.hastnetwork.jarvis.data.model.CalendarEvent
import de.hastnetwork.jarvis.data.model.StatusResponse
import de.hastnetwork.jarvis.data.repository.StatusRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val STATUS_POLL_INTERVAL_MS = 30_000L
private const val EVENTS_POLL_INTERVAL_MS = 60_000L
private const val EVENTS_SHOW_DURATION_MS = 10_000L

class HomeViewModel(
    private val statusRepository: StatusRepository,
    private val settingsDataStore: SettingsDataStore,
) : ViewModel() {

    private val _status = MutableStateFlow<StatusResponse?>(null)
    val status: StateFlow<StatusResponse?> = _status.asStateFlow()

    private val _tickerEvents = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val tickerEvents: StateFlow<List<CalendarEvent>> = _tickerEvents.asStateFlow()

    private val _tickerVisible = MutableStateFlow(false)
    val tickerVisible: StateFlow<Boolean> = _tickerVisible.asStateFlow()

    val settings: StateFlow<AppSettings> = run {
        val flow = MutableStateFlow(AppSettings())
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { flow.value = it }
        }
        flow
    }

    init {
        viewModelScope.launch {
            while (true) {
                refreshStatus()
                delay(STATUS_POLL_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            while (true) {
                val events = statusRepository.getUpcomingEvents().getOrDefault(emptyList())
                if (events.isNotEmpty()) {
                    _tickerEvents.value = events
                    _tickerVisible.value = true
                    delay(EVENTS_SHOW_DURATION_MS)
                    _tickerVisible.value = false
                } else {
                    _tickerEvents.value = emptyList()
                    _tickerVisible.value = false
                }
                delay(EVENTS_POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshStatus() {
        statusRepository.getStatus()
            .onSuccess { _status.value = it }
            .onFailure { _status.value = null }
    }

    /** Called by the top-right toggle; flips the explicit override away from whatever is currently shown. */
    fun toggleDarkMode(currentlyDark: Boolean) {
        viewModelScope.launch {
            settingsDataStore.updateDarkModeOverride(
                if (currentlyDark) DarkModeOverride.LIGHT else DarkModeOverride.DARK
            )
        }
    }
}
