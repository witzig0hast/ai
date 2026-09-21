package de.hastnetwork.jarvis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hastnetwork.jarvis.R
import de.hastnetwork.jarvis.data.model.StatusResponse
import de.hastnetwork.jarvis.ui.theme.StatusDownLight
import de.hastnetwork.jarvis.ui.theme.StatusUpLight

private data class ServiceStatusItem(val label: String, val isUp: Boolean)

private fun buildItems(status: StatusResponse?, backendReachableLabel: String, ollama: String, stt: String, tts: String, ha: String, n8n: String): List<ServiceStatusItem> =
    listOf(
        ServiceStatusItem(backendReachableLabel, status != null),
        ServiceStatusItem(ollama, status?.ollama == true),
        ServiceStatusItem(stt, status?.stt == true),
        ServiceStatusItem(tts, status?.tts == true),
        ServiceStatusItem(ha, status?.homeAssistant == true),
        ServiceStatusItem(n8n, status?.n8n == true),
    )

/**
 * Muted, unobtrusive row of service-status dots pinned near the bottom of
 * the Home screen. Tapping it opens a bottom sheet with per-service detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusRow(status: StatusResponse?, modifier: Modifier = Modifier) {
    var showSheet by remember { mutableStateOf(false) }

    val backendLabel = stringResource(R.string.home_status_backend)
    val items = buildItems(
        status,
        backendLabel,
        stringResource(R.string.home_status_ollama),
        stringResource(R.string.home_status_stt),
        stringResource(R.string.home_status_tts),
        stringResource(R.string.home_status_home_assistant),
        stringResource(R.string.home_status_n8n),
    )

    Row(
        modifier = modifier
            .clickable { showSheet = true }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item -> StatusDot(isUp = item.isUp) }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Text(
                text = stringResource(R.string.home_status_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = item.label, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusDot(isUp = item.isUp)
                        Text(
                            text = if (item.isUp) stringResource(R.string.home_status_up) else stringResource(R.string.home_status_down),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun StatusDot(isUp: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(
                color = if (isUp) StatusUpLight else StatusDownLight,
                shape = CircleShape,
            ),
    )
}
