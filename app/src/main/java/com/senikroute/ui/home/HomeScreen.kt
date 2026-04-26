package com.senikroute.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senikroute.R
import com.senikroute.data.db.entities.DriveEntity
import com.senikroute.data.model.DriveStatus
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onRecord: () -> Unit,
    onRecordFromEarlier: () -> Unit,
    onExplore: () -> Unit,
    onSettings: () -> Unit,
    onDriveClick: (String) -> Unit,
    onAllDrives: () -> Unit,
    onImported: (List<String>) -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val drives by vm.drives.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pickFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            vm.import(
                uri = uri,
                onSuccess = { ids ->
                    val n = ids.size
                    Toast.makeText(
                        context,
                        if (n == 1) "Drive imported" else "Imported $n drives",
                        Toast.LENGTH_SHORT,
                    ).show()
                    onImported(ids)
                },
                onError = { msg ->
                    Toast.makeText(context, "Import failed: $msg", Toast.LENGTH_LONG).show()
                },
            )
        }
    }

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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(padding.calculateTopPadding())) }
            item {
                Button(modifier = Modifier.fillMaxWidth(), onClick = onRecord) {
                    Text(stringResource(R.string.home_record))
                }
            }
            item {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onRecordFromEarlier) {
                    Text(stringResource(R.string.home_record_from_earlier))
                }
            }
            item {
                OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onExplore) {
                    Text(stringResource(R.string.home_explore_nearby))
                }
            }
            item {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { pickFile.launch(arrayOf("*/*")) },
                ) {
                    Text("Import GPX/KML")
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
            item {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.home_recent_drives),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    androidx.compose.material3.TextButton(onClick = onAllDrives) {
                        Text("See all")
                    }
                }
            }
            if (drives.isEmpty()) {
                item {
                    Text("Your recorded drives will appear here.")
                }
            } else {
                items(drives, key = { it.id }) { drive ->
                    DriveRow(drive, onClick = { onDriveClick(drive.id) })
                }
            }
        }
    }
}

@Composable
private fun DriveRow(drive: DriveEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = drive.title.ifBlank { "Untitled drive" },
                style = MaterialTheme.typography.titleLarge,
            )
            val distanceKm = (drive.distanceM ?: 0) / 1000.0
            val durationMin = (drive.durationS ?: 0) / 60
            Text(
                text = "%.1f km · %d min".format(distanceKm, durationMin),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "${DriveStatus.fromStored(drive.status).name.lowercase()} · ${DateFormat.getDateInstance().format(Date(drive.startedAt))}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
