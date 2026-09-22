package de.hastnetwork.jarvis

/*
 * NOTE on "fully replacing Google Assistant": this app implements a Home
 * screen + Voice screen you navigate into manually (tap/mic). It does NOT
 * register as the phone's system assistant. Doing that requires:
 *   1. A `VoiceInteractionSessionService` + `VoiceInteractionService` pair
 *      declared in the manifest (`android.voice_interaction.session` /
 *      `.MAIN` intent filters, plus the `android.voice_interaction` XML
 *      meta-data describing the session service).
 *   2. Requesting `RoleManager.ROLE_ASSISTANT` at runtime
 *      (`RoleManager.createRequestRoleIntent(ROLE_ASSISTANT)`) so the user
 *      can pick this app as their default assistant in system settings.
 *   3. Handling the assistant-invocation entry points (long-press home,
 *      "Hey X" if a hotword is added, the assistant swipe gesture) inside
 *      the VoiceInteractionSessionService's UI instead of a normal Activity.
 * This is intentionally out of scope for this pass (see architecture.md
 * §14 "Bekannte Einschränkungen") and is the next build-out step - see
 * android/README.md for more detail.
 *
 * NOTE on the proactive push channel (architecture.md §11): on launch, this
 * Activity requests the `POST_NOTIFICATIONS` runtime permission (API 33+)
 * and then starts `JarvisEventsService`, a small foreground service that
 * keeps `/ws/events` open and turns `reminder_due` pushes into system
 * notifications - by design, not behind a Settings opt-in toggle. See
 * android/README.md for the reasoning and what to change if a toggle is
 * preferred instead.
 */

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import de.hastnetwork.jarvis.ui.navigation.JarvisNavHost
import de.hastnetwork.jarvis.ui.theme.JarvisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as JarvisApp).container

        setContent {
            val settings by container.currentSettings.collectAsState()
            JarvisTheme(darkModeOverride = settings.darkModeOverride) {
                StartEventsServiceEffect()
                JarvisNavHost()
            }
        }
    }
}

/**
 * Side-effect-only composable (no UI) that requests `POST_NOTIFICATIONS`
 * once on API 33+ (a no-op permission on older versions) and then starts
 * [JarvisEventsService] either way - the service runs fine without the
 * permission, it just can't post the reminder notification until granted
 * (see [JarvisEventsService.postReminderNotification]).
 */
@Composable
private fun StartEventsServiceEffect() {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> startEventsService(context) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (granted) {
                startEventsService(context)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startEventsService(context)
        }
    }
}

private fun startEventsService(context: android.content.Context) {
    ContextCompat.startForegroundService(context, Intent(context, JarvisEventsService::class.java))
}
