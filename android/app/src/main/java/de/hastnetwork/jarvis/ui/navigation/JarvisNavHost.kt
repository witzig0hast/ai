package de.hastnetwork.jarvis.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import de.hastnetwork.jarvis.ui.screens.chat.ChatScreen
import de.hastnetwork.jarvis.ui.screens.history.HistoryScreen
import de.hastnetwork.jarvis.ui.screens.home.HomeScreen
import de.hastnetwork.jarvis.ui.screens.reminders.RemindersScreen
import de.hastnetwork.jarvis.ui.screens.settings.SettingsScreen
import de.hastnetwork.jarvis.ui.screens.voice.VoiceScreen
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Route definitions. `agentId`/`conversationId` are threaded through Voice
 * <-> Chat as optional query args so switching modes continues the same
 * conversation with the same agent (spec requirement).
 */
object JarvisDestinations {
    const val HOME = "home"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val REMINDERS = "reminders"

    private const val VOICE_BASE = "voice"
    private const val CHAT_BASE = "chat"

    const val VOICE_ROUTE = "$VOICE_BASE?agentId={agentId}&conversationId={conversationId}"
    const val CHAT_ROUTE = "$CHAT_BASE?agentId={agentId}&conversationId={conversationId}"

    fun voice(agentId: String? = null, conversationId: String? = null): String =
        "$VOICE_BASE?agentId=${agentId.encode()}&conversationId=${conversationId.encode()}"

    fun chat(agentId: String? = null, conversationId: String? = null): String =
        "$CHAT_BASE?agentId=${agentId.encode()}&conversationId=${conversationId.encode()}"

    private fun String?.encode(): String =
        if (this.isNullOrEmpty()) "" else URLEncoder.encode(this, "UTF-8")

    fun decodeArg(value: String?): String? =
        if (value.isNullOrEmpty()) null else URLDecoder.decode(value, "UTF-8")
}

@Composable
fun JarvisNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = JarvisDestinations.HOME) {
        composable(JarvisDestinations.HOME) {
            HomeScreen(
                onOpenVoice = { navController.navigate(JarvisDestinations.voice()) },
                onOpenSettings = { navController.navigate(JarvisDestinations.SETTINGS) },
                onOpenReminders = { navController.navigate(JarvisDestinations.REMINDERS) },
            )
        }

        composable(
            route = JarvisDestinations.VOICE_ROUTE,
            arguments = listOf(
                navArgument("agentId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val agentId = JarvisDestinations.decodeArg(backStackEntry.arguments?.getString("agentId"))
            val conversationId = JarvisDestinations.decodeArg(backStackEntry.arguments?.getString("conversationId"))
            VoiceScreen(
                initialAgentId = agentId,
                initialConversationId = conversationId,
                onClose = { navController.popBackStack() },
                onSwitchToChat = { newAgentId, newConversationId ->
                    navController.navigate(JarvisDestinations.chat(newAgentId, newConversationId)) {
                        popUpTo(JarvisDestinations.HOME)
                    }
                },
            )
        }

        composable(
            route = JarvisDestinations.CHAT_ROUTE,
            arguments = listOf(
                navArgument("agentId") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("conversationId") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { backStackEntry ->
            val agentId = JarvisDestinations.decodeArg(backStackEntry.arguments?.getString("agentId"))
            val conversationId = JarvisDestinations.decodeArg(backStackEntry.arguments?.getString("conversationId"))
            ChatScreen(
                initialAgentId = agentId,
                initialConversationId = conversationId,
                onOpenVoice = { newAgentId, newConversationId ->
                    navController.navigate(JarvisDestinations.voice(newAgentId, newConversationId))
                },
                onOpenHistory = { navController.navigate(JarvisDestinations.HISTORY) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(JarvisDestinations.HISTORY) {
            HistoryScreen(
                onOpenConversation = { conversation ->
                    navController.navigate(JarvisDestinations.chat(conversation.agentId, conversation.id)) {
                        popUpTo(JarvisDestinations.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable(JarvisDestinations.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }

        composable(JarvisDestinations.REMINDERS) {
            RemindersScreen(onBack = { navController.popBackStack() })
        }
    }
}
