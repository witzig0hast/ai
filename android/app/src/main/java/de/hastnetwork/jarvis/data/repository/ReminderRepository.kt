package de.hastnetwork.jarvis.data.repository

import de.hastnetwork.jarvis.data.model.Reminder
import de.hastnetwork.jarvis.data.model.ReminderCreateRequest
import de.hastnetwork.jarvis.data.remote.JarvisApi

/** `/api/reminders` data access (architecture.md §10). */
class ReminderRepository(private val api: JarvisApi) {

    suspend fun getReminders(includeFired: Boolean = false): Result<List<Reminder>> = runCatching {
        api.getReminders(includeFired)
    }

    suspend fun createReminder(text: String, dueAt: String, conversationId: String? = null): Result<Reminder> =
        runCatching { api.createReminder(ReminderCreateRequest(text = text, dueAt = dueAt, conversationId = conversationId)) }

    suspend fun deleteReminder(id: String): Result<Unit> = runCatching {
        api.deleteReminder(id)
        Unit
    }
}
