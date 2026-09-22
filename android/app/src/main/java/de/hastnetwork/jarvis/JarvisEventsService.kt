package de.hastnetwork.jarvis

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import de.hastnetwork.jarvis.data.model.JarvisPushEvent
import de.hastnetwork.jarvis.data.remote.EventsSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps [EventsSocket] (`/ws/events`,
 * architecture.md §11) open for as long as the app is meant to receive
 * proactive push notifications - fired reminders in particular - even when
 * no Chat/Voice screen is open.
 *
 * Started once from [MainActivity] on app launch (see the notification
 * permission flow there); this is a deliberate choice over a Settings
 * opt-in toggle, documented in `android/README.md`. Posting the actual
 * "Jarvis erinnert dich" notification additionally needs `POST_NOTIFICATIONS`
 * (API 33+) - if that's not granted, the service still runs (the
 * *foreground* notification just silently doesn't show, which is normal
 * platform behavior), but [postReminderNotification] skips posting rather
 * than crashing.
 */
class JarvisEventsService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var eventsSocket: EventsSocket? = null
    private var reminderNotificationSeq = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        ServiceCompat.startForeground(
            this,
            FOREGROUND_NOTIFICATION_ID,
            buildForegroundNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )

        val socket = (application as JarvisApp).container.createEventsSocket()
        eventsSocket = socket
        serviceScope.launch {
            socket.events.collect { event -> handleEvent(event) }
        }
        socket.start(serviceScope)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        eventsSocket?.stop()
        eventsSocket = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun handleEvent(event: JarvisPushEvent) {
        when (event) {
            is JarvisPushEvent.ReminderDue -> postReminderNotification(event.text)
            // Purely informational per architecture.md §11 - the backend
            // doesn't send this yet, and there's no dedicated UI action for
            // it beyond what a manual Home-screen refresh already shows.
            is JarvisPushEvent.BriefingReady -> Unit
        }
    }

    private fun postReminderNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = openAppIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.reminder_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .apply { if (contentIntent != null) setContentIntent(contentIntent) }
            .build()

        NotificationManagerCompat.from(this).notify(REMINDER_NOTIFICATION_ID_BASE + (reminderNotificationSeq++), notification)
    }

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.events_service_notification_title))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "jarvis_events"
        const val FOREGROUND_NOTIFICATION_ID = 1
        const val REMINDER_NOTIFICATION_ID_BASE = 1000
    }
}
