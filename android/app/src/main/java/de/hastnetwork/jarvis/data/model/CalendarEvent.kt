package de.hastnetwork.jarvis.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /api/calendar/upcoming` list item (architecture.md §5). The backend
 * is a stub for now and may return an empty array — the Home screen ticker
 * must never show anything (not even briefly) when the list is empty.
 */
@Serializable
data class CalendarEvent(
    val title: String,
    @SerialName("start_time") val startTime: String,
    val location: String? = null,
)
