package com.scenicroute.ui.mydrives

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.db.entities.WaypointEntity
import com.scenicroute.data.model.DriveStatus
import com.scenicroute.ui.layout.CardGridMinWidth
import com.scenicroute.ui.layout.mapHeight
import com.scenicroute.ui.map.CameraBehavior
import com.scenicroute.ui.map.ScenicMap
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDrivesScreen(
    onBack: () -> Unit,
    onDriveClick: (String) -> Unit,
    onOpenTrash: () -> Unit,
    vm: MyDrivesViewModel = hiltViewModel(),
) {
    val drives by vm.drives.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val trashedCount by vm.trashedCount.collectAsStateWithLifecycle()
    var sortMenuOpen by remember { mutableStateOf(false) }

    val pins = remember(drives) {
        drives.mapNotNull { d ->
            val bbox = d.boundingBox
            val lat = bbox?.let { (it.north + it.south) / 2.0 } ?: d.startLat
            val lng = bbox?.let { (it.east + it.west) / 2.0 } ?: d.startLng
            WaypointEntity(
                id = d.id,
                driveId = d.id,
                lat = lat,
                lng = lng,
                recordedAt = d.startedAt,
                syncState = "LOCAL",
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your drives (${drives.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenTrash) {
                        BadgedBox(
                            badge = {
                                if (trashedCount > 0) Badge { Text("$trashedCount") }
                            },
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Trash")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (pins.size >= 1) {
                Box(modifier = Modifier.fillMaxWidth().height(mapHeight(compact = 220.dp, medium = 320.dp, expanded = 400.dp))) {
                    ScenicMap(
                        modifier = Modifier.fillMaxSize(),
                        waypoints = pins,
                        cameraBehavior = CameraBehavior.Manual,
                    )
                }
            }
            Box {
                OutlinedButton(
                    onClick = { sortMenuOpen = true },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(sort.label)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    DriveSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.label) },
                            onClick = {
                                vm.setSort(s)
                                sortMenuOpen = false
                            },
                        )
                    }
                }
            }
            if (drives.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nothing recorded yet.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = CardGridMinWidth),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(drives, key = { it.id }) { d ->
                        DriveRow(d) { onDriveClick(d.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveRow(d: DriveEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(d.title.ifBlank { "Untitled drive" }, style = MaterialTheme.typography.titleLarge)
            Text(
                "%.1f km · %d min".format((d.distanceM ?: 0) / 1000.0, (d.durationS ?: 0) / 60),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${DriveStatus.fromStored(d.status).name.lowercase()} · ${d.visibility.lowercase()} · ${DateFormat.getDateInstance().format(Date(d.startedAt))}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
