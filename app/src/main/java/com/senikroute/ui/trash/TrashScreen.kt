package com.senikroute.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senikroute.data.db.entities.DriveEntity
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    vm: TrashViewModel = hiltViewModel(),
) {
    val drives by vm.drives.collectAsStateWithLifecycle()
    var confirming by remember { mutableStateOf<DriveEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recently deleted (${drives.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        if (drives.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("Nothing in the trash.", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Drives in the trash are permanently removed after $PURGE_AFTER_DAYS days.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(drives, key = { it.id }) { d ->
                TrashRow(
                    drive = d,
                    onRestore = { vm.restore(d.id) },
                    onDeleteNow = { confirming = d },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    confirming?.let { drive ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Delete permanently?") },
            text = {
                Text(
                    "This removes \"${drive.title.ifBlank { "this drive" }}\" along with its trace, waypoints, photos, and any comments. This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val id = drive.id
                    confirming = null
                    vm.deleteNow(id)
                }) { Text("Delete now") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TrashRow(
    drive: DriveEntity,
    onRestore: () -> Unit,
    onDeleteNow: () -> Unit,
) {
    val deletedAt = drive.deletedAt ?: return
    val ageDays = max(0L, (System.currentTimeMillis() - deletedAt) / (24L * 60 * 60 * 1000)).toInt()
    val daysLeft = (PURGE_AFTER_DAYS - ageDays).coerceAtLeast(0)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(drive.title.ifBlank { "Untitled drive" }, style = MaterialTheme.typography.titleLarge)
            Text(
                "%.1f km · %d min".format(
                    (drive.distanceM ?: 0) / 1000.0,
                    (drive.durationS ?: 0) / 60,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Deleted ${relativeDays(ageDays)} · purges in $daysLeft day${if (daysLeft == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onRestore) {
                    Text("Restore")
                }
                OutlinedButton(modifier = Modifier.weight(1f), onClick = onDeleteNow) {
                    Text("Delete now")
                }
            }
        }
    }
}

private fun relativeDays(d: Int): String = when (d) {
    0 -> "today"
    1 -> "yesterday"
    else -> "$d days ago"
}
