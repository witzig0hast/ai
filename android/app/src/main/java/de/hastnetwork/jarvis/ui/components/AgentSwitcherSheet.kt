package de.hastnetwork.jarvis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.hastnetwork.jarvis.R
import de.hastnetwork.jarvis.data.model.Agent

/** Small colored circular avatar with the agent's first letter, used in top bars and this sheet. */
@Composable
fun AgentAvatar(agent: Agent?, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val color = agent?.avatarColor?.toColorOrNull() ?: MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(size)
            .background(color = color, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = agent?.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "J",
            color = Color.White,
            fontSize = (size.value / 2).sp,
        )
    }
}

private fun String.toColorOrNull(): Color? = try {
    Color(android.graphics.Color.parseColor(this))
} catch (_: IllegalArgumentException) {
    null
}

/**
 * Bottom sheet listing agents from `GET /api/agents`, shared between the
 * Voice screen's top bar and the Chat screen's top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentSwitcherSheet(
    agents: List<Agent>,
    selectedAgentId: String?,
    onSelect: (Agent) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(R.string.agent_switcher_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column {
            agents.forEach { agent ->
                val isSelected = agent.id == selectedAgentId
                Surface(
                    onClick = { onSelect(agent) },
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        AgentAvatar(agent = agent)
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = agent.name, style = MaterialTheme.typography.bodyLarge)
                                if (agent.isDefault) {
                                    Text(
                                        text = stringResource(R.string.agent_switcher_default_badge),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (agent.description.isNotBlank()) {
                                Text(
                                    text = agent.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}
