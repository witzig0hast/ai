package de.hastnetwork.jarvis.ui.screens.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.hastnetwork.jarvis.data.model.MemoryFact
import de.hastnetwork.jarvis.data.repository.MemoryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MemoryUiState(
    val facts: List<MemoryFact> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/**
 * `/api/memory` list/delete for [MemoryScreen] (architecture.md §14). Purely
 * a transparency/control view - the agent writes facts itself via the
 * `remember_fact` tool, so there's no create UI here on purpose.
 */
class MemoryViewModel(private val repository: MemoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(MemoryUiState())
    val uiState: StateFlow<MemoryUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            repository.getFacts()
                .onSuccess { _uiState.value = _uiState.value.copy(facts = it, isLoading = false, errorMessage = null) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = e.message) }
        }
    }

    fun deleteFact(id: String) {
        val previous = _uiState.value.facts
        _uiState.value = _uiState.value.copy(facts = previous.filterNot { it.id == id })
        viewModelScope.launch {
            repository.deleteFact(id).onFailure {
                _uiState.value = _uiState.value.copy(facts = previous, errorMessage = it.message)
            }
        }
    }
}
