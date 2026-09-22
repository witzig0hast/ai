package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors the `MemoryFact` entity from architecture.md §14. */
@Serializable
data class MemoryFact(
    val id: String,
    val text: String,
    @SerialName("created_at") val createdAt: String,
)

/** Body of `POST /api/memory` (architecture.md §14). */
@Serializable
data class MemoryFactCreateRequest(val text: String)
