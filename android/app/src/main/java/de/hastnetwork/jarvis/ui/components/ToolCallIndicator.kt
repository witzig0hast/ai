package de.hastnetwork.jarvis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Friendly German label for a tool name from architecture.md §9's tool
 * list, for the small unobtrusive "🔧 ..." status line while a tool call is
 * in flight. Falls back to the raw tool name for anything unrecognized
 * (e.g. a tool added server-side later that the client doesn't know about
 * yet - these events are documented as informational-only and must be
 * ignorable/forward-compatible).
 */
fun toolCallFriendlyLabel(name: String): String = when (name) {
    "get_current_time" -> "prüfe Uhrzeit"
    "get_weather" -> "prüfe Wetter"
    "create_reminder" -> "lege Erinnerung an"
    "list_reminders" -> "sehe Erinnerungen nach"
    "cancel_reminder" -> "storniere Erinnerung"
    "home_assistant_call_service", "home_assistant_get_state" -> "spreche mit Home Assistant"
    "trigger_n8n_workflow" -> "starte n8n-Workflow"
    else -> name
}

/**
 * Small, muted "🔧 <label> …" status line shown while the agent is running
 * a tool. Shared between Voice and Chat screens. Disappears the moment the
 * caller clears [label] (on `tool_result`, or the next streamed text token -
 * whichever comes first, per spec) - deliberately unobtrusive, no spinner
 * or dialog.
 */
@Composable
fun ToolCallIndicator(label: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = label != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        if (label != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "🔧 $label …",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
