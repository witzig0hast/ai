package de.hastnetwork.jarvis.data.repository

import de.hastnetwork.jarvis.data.model.MemoryFact
import de.hastnetwork.jarvis.data.model.MemoryFactCreateRequest
import de.hastnetwork.jarvis.data.remote.JarvisApi

/**
 * `/api/memory` data access (architecture.md §14). The agent writes facts
 * itself via the `remember_fact` tool during chat/voice - this repository
 * mainly backs [de.hastnetwork.jarvis.ui.screens.memory.MemoryScreen]'s
 * transparency/control view (list + delete), but also exposes create for
 * completeness against the REST surface.
 */
class MemoryRepository(private val api: JarvisApi) {

    suspend fun getFacts(): Result<List<MemoryFact>> = runCatching { api.getMemoryFacts() }

    suspend fun createFact(text: String): Result<MemoryFact> = runCatching {
        api.createMemoryFact(MemoryFactCreateRequest(text))
    }

    suspend fun deleteFact(id: String): Result<Unit> = runCatching {
        api.deleteMemoryFact(id)
        Unit
    }
}
