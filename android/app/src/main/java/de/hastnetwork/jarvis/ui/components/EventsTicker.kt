package de.hastnetwork.jarvis.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.hastnetwork.jarvis.data.model.CalendarEvent
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatStartTime(iso: String): String =
    try {
        OffsetDateTime.parse(iso).format(timeFormatter)
    } catch (_: DateTimeParseException) {
        iso
    }

/**
 * "Departure board"-style ticker line for the next upcoming appointment(s).
 * The caller (HomeViewModel) owns the ~60s-periodic show-for-~10s cadence
 * via [visible]; this composable only renders when [visible] AND [events]
 * is non-empty, so an empty list never causes an empty-state flicker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventsTicker(events: List<CalendarEvent>, visible: Boolean, modifier: Modifier = Modifier) {
    var showDetail by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible && events.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        val first = events.firstOrNull()
        if (first != null) {
            val summary = buildString {
                append(formatStartTime(first.startTime))
                append("  ·  ")
                append(first.title)
                if (events.size > 1) {
                    append("  (+${events.size - 1})")
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clickable { showDetail = true }
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    if (showDetail && events.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showDetail = false }, sheetState = sheetState) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                events.forEachIndexed { index, event ->
                    Column(Modifier.padding(vertical = 12.dp)) {
                        Text(text = event.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = formatStartTime(event.startTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!event.location.isNullOrBlank()) {
                            Text(
                                text = event.location,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (index != events.lastIndex) HorizontalDivider()
                }
            }
        }
    }
}
