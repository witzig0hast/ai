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
 * §9 "Bekannte Einschränkungen") and is the next build-out step - see
 * android/README.md for more detail.
 */

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
                JarvisNavHost()
            }
        }
    }
}
