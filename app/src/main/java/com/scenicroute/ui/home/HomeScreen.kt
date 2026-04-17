package com.scenicroute.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.scenicroute.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onRecordFromEarlier: () -> Unit,
    onExplore: () -> Unit,
    onSettings: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(modifier = Modifier.fillMaxWidth(), onClick = onRecord) {
                Text(stringResource(R.string.home_record))
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRecordFromEarlier) {
                Text(stringResource(R.string.home_record_from_earlier))
            }
            OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onExplore) {
                Text(stringResource(R.string.home_explore_nearby))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.home_recent_drives),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "Your recorded drives will appear here.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
