package de.hastnetwork.jarvis.data.repository

import de.hastnetwork.jarvis.data.model.BriefingResponse
import de.hastnetwork.jarvis.data.remote.JarvisApi

/** `GET /api/briefing/today` data access (architecture.md §12/§13). */
class BriefingRepository(private val api: JarvisApi) {

    suspend fun getTodayBriefing(): Result<BriefingResponse> = runCatching { api.getTodayBriefing() }
}
