package de.hastnetwork.jarvis.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import de.hastnetwork.jarvis.data.local.DarkModeOverride

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as JarvisApp
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { SettingsViewModel(app.container.settingsDataStore) }
        }
    )

    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.nav_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_backend_section),
                style = MaterialTheme.typography.titleLarge,
            )
            OutlinedTextField(
                value = settings.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text(stringResource(R.string.settings_base_url)) },
                placeholder = { Text(stringResource(R.string.settings_base_url_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = settings.token,
                onValueChange = viewModel::updateToken,
                label = { Text(stringResource(R.string.settings_token)) },
                placeholder = { Text(stringResource(R.string.settings_token_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.settings_appearance_section),
                style = MaterialTheme.typography.titleLarge,
            )
            DarkModeOverride.entries.forEach { mode ->
                val label = when (mode) {
                    DarkModeOverride.SYSTEM -> stringResource(R.string.settings_dark_mode_system)
                    DarkModeOverride.LIGHT -> stringResource(R.string.settings_dark_mode_light)
                    DarkModeOverride.DARK -> stringResource(R.string.settings_dark_mode_dark)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = settings.darkModeOverride == mode,
                            onClick = { viewModel.updateDarkModeOverride(mode) },
                        ),
                ) {
                    RadioButton(selected = settings.darkModeOverride == mode, onClick = { viewModel.updateDarkModeOverride(mode) })
                    Text(text = label, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
