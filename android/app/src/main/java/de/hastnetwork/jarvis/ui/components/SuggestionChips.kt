package de.hastnetwork.jarvis.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Follow-up-suggestion chips, rendered directly under the assistant message
 * bubble they belong to (NOT pinned in a fixed top bar - see spec). Tapping
 * a chip sends it as the next user message.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionChips(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (suggestions.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            AssistChip(
                onClick = { onSuggestionClick(suggestion) },
                label = { Text(suggestion) },
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
    }
}
