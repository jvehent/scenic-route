package com.senikroute.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.senikroute.ui.layout.FormMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senikroute.R
import com.senikroute.auth.AuthViewModel
import com.senikroute.data.prefs.UserSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
    authVm: AuthViewModel = hiltViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = FormMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenProfile,
            ) { Text("Your profile") }
            HorizontalDivider()

            LookbackBufferSection(settings, vm)
            HorizontalDivider()
            DiscoverySection(settings, vm)
            HorizontalDivider()
            UploadSection(settings, vm)
            HorizontalDivider()

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { authVm.signOut(); onBack() },
            ) {
                Text(stringResource(R.string.settings_sign_out))
            }
        }
        }
    }
}

@Composable
private fun LookbackBufferSection(settings: UserSettings, vm: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_lookback_buffer), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.settings_lookback_explainer),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Switch(
                checked = settings.bufferEnabled,
                onCheckedChange = { vm.setBufferEnabled(it) },
            )
        }
        if (settings.bufferEnabled) {
            Text(
                stringResource(R.string.settings_buffer_window) + ": ${settings.bufferMinutes} min",
                style = MaterialTheme.typography.labelLarge,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                UserSettings.VALID_BUFFER_MINUTES.drop(1).forEach { m ->
                    FilterChip(
                        selected = settings.bufferMinutes == m,
                        onClick = { vm.setBufferMinutes(m) },
                        label = { Text("$m") },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverySection(settings: UserSettings, vm: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.settings_discovery_radius), style = MaterialTheme.typography.titleLarge)
        Text("${settings.discoveryRadiusKm} km", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = settings.discoveryRadiusKm.toFloat(),
            onValueChange = { vm.setDiscoveryRadius(it.toInt().coerceIn(1, 100)) },
            valueRange = 1f..100f,
            steps = 99,
        )
    }
}

@Composable
private fun UploadSection(settings: UserSettings, vm: SettingsViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.settings_wifi_only),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        Switch(
            checked = settings.wifiOnlyUploads,
            onCheckedChange = { vm.setWifiOnlyUploads(it) },
        )
    }
}
