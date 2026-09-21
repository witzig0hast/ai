package de.hastnetwork.jarvis.data.repository

import de.hastnetwork.jarvis.data.model.CalendarEvent
import de.hastnetwork.jarvis.data.model.StatusResponse
import de.hastnetwork.jarvis.data.remote.JarvisApi

/**
 * Backs the Home screen's status row (`GET /api/status`) and events ticker
 * (`GET /api/calendar/upcoming`). Not in the original suggested file list
 * but split out to keep `AgentRepository`/`ConversationRepository` focused.
 */
class StatusRepository(private val api: JarvisApi) {

    suspend fun getStatus(): Result<StatusResponse> = runCatching { api.getStatus() }

    suspend fun getUpcomingEvents(withinMinutes: Int = 180): Result<List<CalendarEvent>> = runCatching {
        api.getUpcomingEvents(withinMinutes)
    }
}
