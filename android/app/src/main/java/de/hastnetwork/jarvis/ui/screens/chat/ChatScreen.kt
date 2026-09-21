package de.hastnetwork.jarvis.ui.screens.chat

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import de.hastnetwork.jarvis.data.model.Message
import de.hastnetwork.jarvis.ui.components.AgentAvatar
import de.hastnetwork.jarvis.ui.components.AgentSwitcherSheet
import de.hastnetwork.jarvis.ui.components.ChatBubble
import de.hastnetwork.jarvis.ui.components.SuggestionChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    initialAgentId: String?,
    initialConversationId: String?,
    onOpenVoice: (agentId: String?, conversationId: String?) -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as JarvisApp
    val container = app.container
    val context = LocalContext.current

    val viewModel: ChatViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ChatViewModel(
                    agentRepository = container.agentRepository,
                    conversationRepository = container.conversationRepository,
                    appContext = context.applicationContext,
                    initialAgentId = initialAgentId,
                    initialConversationId = initialConversationId,
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsState()
    var showAgentSheet by remember { mutableStateOf(false) }
    val selectedAgent = state.agents.firstOrNull { it.id == state.selectedAgentId }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, mime) = queryFileMeta(context, uri)
            viewModel.uploadAttachment(uri, name, mime)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAgentSheet = true },
                    ) {
                        AgentAvatar(agent = selectedAgent, size = 28.dp)
                        Text(
                            text = selectedAgent?.name ?: stringResource(R.string.chat_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.chat_history))
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = state.inputText,
                onInputTextChange = viewModel::onInputTextChange,
                pendingAttachments = state.pendingAttachments,
                isUploading = state.isUploading,
                onRemoveAttachment = viewModel::removePendingAttachment,
                onAttachClick = { filePickerLauncher.launch("*/*") },
                onSendClick = { viewModel.sendMessage() },
                onMicClick = { onOpenVoice(state.selectedAgentId, state.conversationId) },
            )
        },
    ) { innerPadding ->
        MessagesList(
            messages = state.messages,
            isStreaming = state.isStreaming,
            onSuggestionClick = { viewModel.sendSuggestion(it) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }

    if (showAgentSheet) {
        val sheetState = rememberModalBottomSheetState()
        AgentSwitcherSheet(
            agents = state.agents,
            selectedAgentId = state.selectedAgentId,
            onSelect = { agent ->
                viewModel.selectAgent(agent)
                showAgentSheet = false
            },
            onDismiss = { showAgentSheet = false },
            sheetState = sheetState,
        )
    }
}

@Composable
private fun MessagesList(
    messages: List<Message>,
    isStreaming: Boolean,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    val lastAssistantIndex = messages.indexOfLast { !it.isUser }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { message ->
            Column {
                ChatBubble(message = message)
                // Suggestion chips render directly under the latest assistant
                // answer only (not a fixed top bar) - per spec.
                val isLatestAssistant = messages.indexOf(message) == lastAssistantIndex
                if (isLatestAssistant && !isStreaming && message.suggestions.isNotEmpty()) {
                    SuggestionChips(
                        suggestions = message.suggestions,
                        onSuggestionClick = onSuggestionClick,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    inputText: String,
    onInputTextChange: (String) -> Unit,
    pendingAttachments: List<PendingAttachment>,
    isUploading: Boolean,
    onRemoveAttachment: (String) -> Unit,
    onAttachClick: () -> Unit,
    onSendClick: () -> Unit,
    onMicClick: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (pendingAttachments.isNotEmpty() || isUploading) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                pendingAttachments.forEach { attachment ->
                    AssistChip(
                        onClick = {},
                        label = { Text(attachment.filename, maxLines = 1) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.clickable { onRemoveAttachment(attachment.fileId) },
                            )
                        },
                    )
                }
                if (isUploading) {
                    AssistChip(onClick = {}, label = { Text(stringResource(R.string.chat_uploading)) })
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onAttachClick) {
                Icon(Icons.Filled.AttachFile, contentDescription = stringResource(R.string.chat_attach))
            }
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
                maxLines = 4,
            )
            IconButton(onClick = onMicClick) {
                Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.chat_mic))
            }
            IconButton(onClick = onSendClick, enabled = inputText.isNotBlank()) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
            }
        }
    }
}

private fun queryFileMeta(context: android.content.Context, uri: Uri): Pair<String, String?> {
    var name = "Datei"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && it.moveToFirst()) {
            name = it.getString(nameIndex) ?: name
        }
    }
    val mime = context.contentResolver.getType(uri)
    return name to mime
}
