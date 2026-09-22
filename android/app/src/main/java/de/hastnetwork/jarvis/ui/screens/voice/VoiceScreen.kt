package de.hastnetwork.jarvis.ui.screens.voice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import de.hastnetwork.jarvis.JarvisApp
import de.hastnetwork.jarvis.R
import de.hastnetwork.jarvis.audio.AudioPlayer
import de.hastnetwork.jarvis.audio.AudioRecorder
import de.hastnetwork.jarvis.ui.components.AgentAvatar
import de.hastnetwork.jarvis.ui.components.AgentSwitcherSheet
import de.hastnetwork.jarvis.ui.components.ToolCallIndicator
import de.hastnetwork.jarvis.ui.components.VoiceOrb
import de.hastnetwork.jarvis.ui.components.VoiceOrbState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(
    initialAgentId: String?,
    initialConversationId: String?,
    onClose: () -> Unit,
    onSwitchToChat: (agentId: String?, conversationId: String?) -> Unit,
) {
    val app = LocalContext.current.applicationContext as JarvisApp
    val container = app.container

    val viewModel: VoiceViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                VoiceViewModel(
                    agentRepository = container.agentRepository,
                    voiceRepository = container.createVoiceRepository(),
                    audioRecorder = AudioRecorder(),
                    audioPlayer = AudioPlayer(),
                    initialAgentId = initialAgentId,
                    initialConversationId = initialConversationId,
                )
            }
        }
    )

    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onMicPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.onMicPermissionResult(true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    var showAgentSheet by remember { mutableStateOf(false) }
    val selectedAgent = state.agents.firstOrNull { it.id == state.selectedAgentId }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.voice_close),
                        )
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
                            text = selectedAgent?.name ?: stringResource(R.string.voice_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleSubtitles() }) {
                        Icon(
                            imageVector = if (state.subtitlesEnabled) Icons.Filled.ClosedCaption else Icons.Filled.ClosedCaptionOff,
                            contentDescription = stringResource(R.string.voice_subtitles_toggle),
                        )
                    }
                },
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                OutlinedButton(onClick = { onSwitchToChat(state.selectedAgentId, state.conversationId) }) {
                    Icon(imageVector = Icons.Filled.Chat, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.voice_switch_to_chat))
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!state.micPermissionGranted) {
                PermissionRationale(
                    onGrantClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
            }

            Spacer(Modifier.height(24.dp))

            VoiceOrb(state = state.orbState, modifier = Modifier.padding(vertical = 16.dp))

            val statusText = when {
                state.errorMessage != null -> state.errorMessage!!
                state.orbState == VoiceOrbState.SPEAKING -> stringResource(R.string.voice_speaking)
                state.connectionState == VoiceConnectionState.READY -> stringResource(R.string.voice_listening)
                else -> stringResource(R.string.voice_connecting)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ToolCallIndicator(label = state.activeToolLabel)

            Spacer(Modifier.height(16.dp))

            if (state.subtitlesEnabled) {
                CaptionsList(
                    captions = state.captions,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                )
            }
        }
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
private fun PermissionRationale(onGrantClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.voice_mic_permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onGrantClick) {
            Text(stringResource(R.string.voice_grant_permission))
        }
    }
}

@Composable
private fun CaptionsList(captions: List<CaptionEntry>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(captions.size) {
        if (captions.isNotEmpty()) listState.animateScrollToItem(captions.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(captions, key = { it.id }) { caption ->
            val isUser = caption.role == "user"
            Text(
                text = caption.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isUser) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
