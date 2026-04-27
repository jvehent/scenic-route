package com.senikroute.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.senikroute.data.db.entities.WaypointEntity
import com.senikroute.data.db.entities.WaypointPhotoEntity
import android.content.Context
import android.net.Uri
import com.senikroute.data.io.GeoFormat
import com.senikroute.data.model.DriveStatus
import com.senikroute.data.model.Visibility
import com.senikroute.ui.layout.mapHeight
import com.senikroute.ui.map.CameraBehavior
import com.senikroute.ui.map.SenikMap
import com.senikroute.ui.theme.SenikBrandTitle
import java.io.File
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onAuthorClick: () -> Unit,
    vm: DriveDetailViewModel = hiltViewModel(),
) {
    val drive by vm.drive.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val waypoints by vm.waypoints.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()
    val photosByWaypoint = remember(photos) { photos.groupBy { it.waypointId } }
    var confirmingDelete by remember { mutableStateOf(false) }
    var exportMenuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SenikBrandTitle(subtitle = drive?.title?.ifBlank { "Untitled drive" } ?: "Drive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    val d = drive
                    val canShare = d != null &&
                        Visibility.fromStored(d.visibility) != Visibility.PRIVATE
                    IconButton(
                        enabled = canShare,
                        onClick = {
                            val id = drive?.id ?: return@IconButton
                            val title = drive?.title?.ifBlank { null } ?: "A scenic drive"
                            val url = com.senikroute.ui.nav.Destinations.shareUrlFor(id)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share drive"))
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                    Box {
                        IconButton(onClick = { exportMenuOpen = true }) {
                            Icon(Icons.Filled.IosShare, contentDescription = "Export")
                        }
                        DropdownMenu(
                            expanded = exportMenuOpen,
                            onDismissRequest = { exportMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Export as GPX") },
                                onClick = {
                                    exportMenuOpen = false
                                    vm.export(GeoFormat.GPX) { uri, err ->
                                        if (uri != null) shareFile(context, uri, GeoFormat.GPX.mime, "Export drive (GPX)")
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Export as KML") },
                                onClick = {
                                    exportMenuOpen = false
                                    vm.export(GeoFormat.KML) { uri, err ->
                                        if (uri != null) shareFile(context, uri, GeoFormat.KML.mime, "Export drive (KML)")
                                    }
                                },
                            )
                        }
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
                    IconButton(onClick = { confirmingDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                },
            )
        },
    ) { padding ->
        val d = drive
        if (d == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("Loading…")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(mapHeight())) {
                    SenikMap(
                        modifier = Modifier.fillMaxSize(),
                        track = track,
                        waypoints = waypoints,
                        cameraBehavior = CameraBehavior.FitBounds,
                    )
                }
            }
            item {
                MetadataCard(
                    driveId = d.id,
                    distanceM = d.distanceM ?: 0,
                    durationS = d.durationS ?: 0,
                    startedAt = d.startedAt,
                    visibility = Visibility.fromStored(d.visibility),
                    status = DriveStatus.fromStored(d.status),
                    description = d.description,
                    tags = d.tags,
                    onAuthorClick = onAuthorClick,
                )
            }
            if (waypoints.isNotEmpty()) {
                item {
                    Text(
                        "Waypoints",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(waypoints, key = { it.id }) { wp ->
                    WaypointCard(wp, photosByWaypoint[wp.id].orEmpty(), onPhotoClick)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Move to trash?") },
            text = { Text("The drive will be hidden and permanently removed after 30 days. You can restore or delete it sooner from your profile.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDelete = false
                    vm.delete(onDeleted)
                }) { Text("Move to trash") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MetadataCard(
    driveId: String,
    distanceM: Int,
    durationS: Int,
    startedAt: Long,
    visibility: Visibility,
    status: DriveStatus,
    description: String,
    tags: List<String>,
    onAuthorClick: () -> Unit = {},
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "%.1f km · %d min".format(distanceM / 1000.0, durationS / 60),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "By you",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { onAuthorClick() },
            )
            Text(
                "${status.name.lowercase()} · ${visibility.name.lowercase()} · ${DateFormat.getDateInstance().format(Date(startedAt))}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge)
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(tags.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
            com.senikroute.ui.theme.DriveIdRow(driveId)
        }
    }
}

@Composable
private fun WaypointCard(
    wp: WaypointEntity,
    photos: List<WaypointPhotoEntity>,
    onPhotoClick: (String) -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (photos.isNotEmpty()) {
                val scroll = rememberScrollState()
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    photos.forEach { p ->
                        // Prefer remoteUrl when present (works for rehydrated photos with no
                        // local copy); fall back to the on-disk file for not-yet-uploaded ones.
                        val viewerArg = p.remoteUrl ?: p.localPath
                        if (viewerArg != null) {
                            AsyncImage(
                                model = p.remoteUrl ?: File(p.localPath!!),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(120.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onPhotoClick(viewerArg) },
                            )
                        }
                    }
                }
            }
            Text(
                "%.5f, %.5f".format(wp.lat, wp.lng),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            wp.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            wp.vehicleReqs?.let { reqs ->
                val parts = buildList {
                    if (reqs.requires4wd == true) add("4WD")
                    if (reqs.rvFriendly == false) add("not RV-friendly")
                    reqs.maxWidthM?.let { add("max width %.1f m".format(it)) }
                    reqs.maxHeightM?.let { add("max height %.1f m".format(it)) }
                    addAll(reqs.tags.map { it.replace('_', ' ') })
                }
                if (parts.isNotEmpty()) {
                    Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelLarge)
                }
                reqs.notes?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun shareFile(context: Context, uri: Uri, mime: String, chooserTitle: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
