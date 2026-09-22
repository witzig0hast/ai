package de.hastnetwork.jarvis.ui.screens.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hastnetwork.jarvis.data.model.Reminder
import de.hastnetwork.jarvis.data.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class RemindersUiState(
    val reminders: List<Reminder> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/** `/api/reminders` list/create/delete for [RemindersScreen] (architecture.md §10). */
class RemindersViewModel(private val repository: ReminderRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getReminders(includeFired = false)
                .onSuccess { _uiState.value = _uiState.value.copy(reminders = it, isLoading = false, errorMessage = null) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    fun createReminder(text: String, dueAt: Instant) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.createReminder(text.trim(), dueAt.toString())
                .onSuccess { refresh() }
                .onFailure { e -> _uiState.value = _uiState.value.copy(errorMessage = e.message) }
        }
    }

    fun deleteReminder(id: String) {
        // Optimistic removal so the list feels instant; refresh() on
        // failure restores the true server state.
        val previous = _uiState.value.reminders
        _uiState.value = _uiState.value.copy(reminders = previous.filterNot { it.id == id })
        viewModelScope.launch {
            repository.deleteReminder(id).onFailure {
                _uiState.value = _uiState.value.copy(reminders = previous, errorMessage = it.message)
            }
        }
    }
}
