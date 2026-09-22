package de.hastnetwork.jarvis.ui.screens.reminders

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.hastnetwork.jarvis.JarvisApp
import de.hastnetwork.jarvis.R
import de.hastnetwork.jarvis.data.model.Reminder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

private val dueAtFormatter = DateTimeFormatter.ofPattern("EEE, d. MMM  HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as JarvisApp
    val viewModel: RemindersViewModel = viewModel(
        factory = viewModelFactory {
            initializer { RemindersViewModel(app.container.reminderRepository) }
        }
    )

    val state by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.reminders_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.reminders_add))
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading && state.reminders.isEmpty() ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

                state.reminders.isEmpty() -> Text(
                    text = stringResource(R.string.reminders_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                    items(state.reminders, key = { it.id }) { reminder ->
                        ReminderRow(reminder = reminder, onDelete = { viewModel.deleteReminder(reminder.id) })
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { text, dueAt -> viewModel.createReminder(text, dueAt) },
        )
    }
}

@Composable
private fun ReminderRow(reminder: Reminder, onDelete: () -> Unit) {
    Surface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = reminder.text, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = formatDueAt(reminder.dueAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.reminders_delete))
            }
        }
    }
}

private fun formatDueAt(iso: String): String =
    try {
        Instant.parse(iso).atZone(ZoneId.systemDefault()).format(dueAtFormatter)
    } catch (_: Exception) {
        iso
    }

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun AddReminderDialog(onDismiss: () -> Unit, onCreate: (text: String, dueAt: Instant) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var customDateTime by remember { mutableStateOf<LocalDateTime?>(null) }

    fun openPickers() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        customDateTime = LocalDateTime.of(year, month + 1, dayOfMonth, hour, minute)
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun createWithQuickPick(minutesFromNow: Long) {
        if (text.isBlank()) return
        onCreate(text, Instant.now().plusSeconds(minutesFromNow * 60))
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reminders_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.reminders_text_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.reminders_quick_pick_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { createWithQuickPick(10) }, label = { Text(stringResource(R.string.reminders_quick_10m)) })
                    AssistChip(onClick = { createWithQuickPick(30) }, label = { Text(stringResource(R.string.reminders_quick_30m)) })
                    AssistChip(onClick = { createWithQuickPick(60) }, label = { Text(stringResource(R.string.reminders_quick_1h)) })
                    AssistChip(onClick = { createWithQuickPick(180) }, label = { Text(stringResource(R.string.reminders_quick_3h)) })
                }
                OutlinedButton(onClick = { openPickers() }, modifier = Modifier.padding(top = 12.dp)) {
                    Text(
                        customDateTime?.format(DateTimeFormatter.ofPattern("d. MMM, HH:mm"))
                            ?: stringResource(R.string.reminders_pick_custom)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val due = customDateTime?.atZone(ZoneId.systemDefault())?.toInstant()
                    if (text.isNotBlank() && due != null) {
                        onCreate(text, due)
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank() && customDateTime != null,
            ) {
                Text(stringResource(R.string.reminders_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.reminders_cancel)) }
        },
    )
}
