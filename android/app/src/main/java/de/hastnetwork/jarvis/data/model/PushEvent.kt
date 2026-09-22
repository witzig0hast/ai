package de.hastnetwork.jarvis.data.model

/** Server -> client push over `/ws/events` (architecture.md §11). */
sealed class JarvisPushEvent {
    data class ReminderDue(val id: String, val text: String) : JarvisPushEvent()
    data class BriefingReady(val summary: String) : JarvisPushEvent()
}
