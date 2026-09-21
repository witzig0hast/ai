package de.hastnetwork.jarvis.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hastnetwork.jarvis.JarvisApp
import de.hastnetwork.jarvis.R
import de.hastnetwork.jarvis.data.model.ConversationSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenConversation: (ConversationSummary) -> Unit, onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as JarvisApp
    val repository = app.container.conversationRepository
    val scope = rememberCoroutineScope()

    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    fun refresh() {
        scope.launch {
            isLoading = true
            repository.getConversations().onSuccess { conversations = it }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                conversations.isEmpty() -> Text(
                    text = stringResource(R.string.history_empty),
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(conversations, key = { it.id }) { conversation ->
                        Surface(onClick = { onOpenConversation(conversation) }) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(text = conversation.title, style = MaterialTheme.typography.bodyLarge)
                                    if (conversation.preview.isNotBlank()) {
                                        Text(
                                            text = conversation.preview,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                        )
                                    }
                                }
                                IconButton(onClick = {
                                    scope.launch {
                                        repository.deleteConversation(conversation.id)
                                        refresh()
                                    }
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.history_delete))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
