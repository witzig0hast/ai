package de.hastnetwork.jarvis.data.repository

import de.hastnetwork.jarvis.data.model.Agent
import de.hastnetwork.jarvis.data.remote.JarvisApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Loads and caches the agent list ([GET /api/agents]) so the Home/Voice/Chat
 * top bars and the agent-switcher sheet can share one in-memory copy instead
 * of each re-fetching.
 */
class AgentRepository(private val api: JarvisApi) {

    private val _agents = MutableStateFlow<List<Agent>>(emptyList())
    val agents: StateFlow<List<Agent>> = _agents.asStateFlow()

    private val _selectedAgentId = MutableStateFlow<String?>(null)
    val selectedAgentId: StateFlow<String?> = _selectedAgentId.asStateFlow()

    val selectedAgent: Agent?
        get() = _agents.value.firstOrNull { it.id == _selectedAgentId.value }
            ?: _agents.value.firstOrNull { it.isDefault }
            ?: _agents.value.firstOrNull()

    suspend fun refresh(): Result<List<Agent>> = runCatching {
        val fetched = api.getAgents()
        _agents.value = fetched
        if (_selectedAgentId.value == null) {
            _selectedAgentId.value = fetched.firstOrNull { it.isDefault }?.id ?: fetched.firstOrNull()?.id
        }
        fetched
    }

    fun selectAgent(agentId: String) {
        _selectedAgentId.value = agentId
    }
}
