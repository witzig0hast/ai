package de.hastnetwork.jarvis.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import de.hastnetwork.jarvis.ui.components.EventsTicker
import de.hastnetwork.jarvis.ui.components.StatusRow
import de.hastnetwork.jarvis.ui.theme.rememberIsDarkTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val clockFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

@Composable
fun HomeScreen(onOpenVoice: () -> Unit, onOpenSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as JarvisApp
    val container = app.container
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.statusRepository, container.settingsDataStore) }
        }
    )

    val status by viewModel.status.collectAsState()
    val tickerEvents by viewModel.tickerEvents.collectAsState()
    val tickerVisible by viewModel.tickerVisible.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isDark = rememberIsDarkTheme(settings.darkModeOverride)

    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(1000)
        }
    }

    Scaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                // Whole screen is tappable -> straight into Voice mode, no
                // intermediate chooser. Ripple-free so it stays "ambient".
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenVoice,
                ),
        ) {
            // Top row: settings + dark-mode toggle, top-right corner.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .safeDrawingPadding()
                    .padding(top = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(imageVector = Icons.Filled.Settings, contentDescription = stringResource(R.string.nav_settings))
                }
                IconButton(onClick = { viewModel.toggleDarkMode(isDark) }) {
                    Icon(
                        imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = stringResource(R.string.home_dark_mode_toggle),
                    )
                }
            }

            // Center: clock + date + (above it) the events ticker.
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                EventsTicker(events = tickerEvents, visible = tickerVisible)
                Text(
                    text = now.format(clockFormatter),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = now.format(dateFormatter).replaceFirstChar { it.titlecase(Locale.GERMAN) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Bottom: subtle service-status row.
            StatusRow(
                status = status,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(bottom = 8.dp),
            )
        }
    }
}
