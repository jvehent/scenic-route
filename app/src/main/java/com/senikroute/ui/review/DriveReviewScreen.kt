package com.senikroute.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senikroute.data.model.Visibility
import com.senikroute.ui.layout.FormMaxWidth
import com.senikroute.ui.map.CameraBehavior
import com.senikroute.ui.map.SenikMap
import com.senikroute.ui.recording.WaypointSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveReviewScreen(
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
    vm: DriveReviewViewModel = hiltViewModel(),
) {
    val drive by vm.drive.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val waypoints by vm.waypoints.collectAsStateWithLifecycle()
    val photos by vm.photos.collectAsStateWithLifecycle()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var visibility by remember { mutableStateOf(Visibility.PRIVATE) }
    var tagsCsv by remember { mutableStateOf("") }
    var commentsEnabled by remember { mutableStateOf(false) }
    var initialized by remember { mutableStateOf(false) }

    // Tap-to-add mode + the lat/lng captured by the most recent map tap.
    var addMode by remember { mutableStateOf(false) }
    var pendingLat by remember { mutableStateOf<Double?>(null) }
    var pendingLng by remember { mutableStateOf<Double?>(null) }
    val sheetOpen by remember { derivedStateOf { pendingLat != null && pendingLng != null } }

    // Trim state: panel collapsed/expanded, current range (in track-index units), and
    // a confirm dialog before the destructive write actually fires.
    var trimExpanded by remember { mutableStateOf(false) }
    var trimRange by remember(track.size) {
        mutableStateOf(0f..(track.size - 1).coerceAtLeast(0).toFloat())
    }
    var trimConfirmOpen by remember { mutableStateOf(false) }
    val previewTrack by remember {
        derivedStateOf {
            if (!trimExpanded || track.isEmpty()) {
                track
            } else {
                val from = trimRange.start.toInt().coerceIn(0, track.lastIndex)
                val to = trimRange.endInclusive.toInt().coerceIn(from, track.lastIndex)
                track.subList(from, to + 1)
            }
        }
    }

    LaunchedEffect(drive) {
        val d = drive
        if (d != null && !initialized) {
            title = d.title
            description = d.description
            visibility = Visibility.fromStored(d.visibility)
            tagsCsv = d.tags.joinToString(", ")
            commentsEnabled = d.commentsEnabled
            initialized = true
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Review drive") }) },
    ) { padding ->
        val d = drive
        if (d == null) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) { Text("Loading…") }
            return@Scaffold
        }

        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = FormMaxWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "%.1f km · %d min".format(
                        (d.distanceM ?: 0) / 1000.0,
                        (d.durationS ?: 0) / 60,
                    ),
                    style = MaterialTheme.typography.titleLarge,
                )

                MapWithWaypoints(
                    track = previewTrack,
                    waypoints = waypoints,
                    addMode = addMode,
                    onTap = { lat, lng ->
                        if (addMode) {
                            pendingLat = lat
                            pendingLng = lng
                            addMode = false
                        }
                    },
                )

                WaypointEditorRow(
                    waypointCount = waypoints.size,
                    photoCount = photos.size,
                    addMode = addMode,
                    canAdd = track.isNotEmpty(),
                    onToggleAddMode = { addMode = !addMode },
                )

                TrimSection(
                    track = track,
                    expanded = trimExpanded,
                    range = trimRange,
                    onToggle = {
                        trimExpanded = !trimExpanded
                        if (trimExpanded && track.isNotEmpty()) {
                            trimRange = 0f..(track.size - 1).toFloat()
                        }
                    },
                    onRangeChange = { trimRange = it },
                    onApply = { trimConfirmOpen = true },
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                )

                OutlinedTextField(
                    value = tagsCsv,
                    onValueChange = { tagsCsv = it },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                Text("Visibility", style = MaterialTheme.typography.labelLarge)
                VisibilityRow(Visibility.PRIVATE, visibility, "Private — only you", "No one else can see this drive.") { visibility = it }
                VisibilityRow(Visibility.UNLISTED, visibility, "Unlisted — share-by-link", "Anyone with the link can view, but it's not in discovery.") { visibility = it }
                VisibilityRow(Visibility.PUBLIC, visibility, "Public — appears in discovery", "Visible in Explore; signed-in users can comment if you allow it below.") { visibility = it }

                CommentsToggleRow(
                    enabled = commentsEnabled,
                    visibility = visibility,
                    onChange = { commentsEnabled = it },
                )

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { vm.discard(onDiscarded) },
                    ) { Text("Discard") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = { vm.save(title, description, visibility, tagsCsv, commentsEnabled, onSaved) },
                    ) { Text("Save") }
                }
            }
        }
    }

    if (sheetOpen) {
        val lat = pendingLat
        val lng = pendingLng
        if (lat != null && lng != null) {
            WaypointSheet(
                onDismiss = {
                    pendingLat = null
                    pendingLng = null
                },
                onSave = { draft ->
                    vm.addWaypointAt(
                        lat = lat,
                        lng = lng,
                        note = draft.note,
                        vehicleReqs = draft.vehicleReqs,
                        photoPaths = draft.photoPaths,
                    )
                    pendingLat = null
                    pendingLng = null
                },
            )
        }
    }

    if (trimConfirmOpen) {
        val from = trimRange.start.toInt().coerceIn(0, track.lastIndex.coerceAtLeast(0))
        val to = trimRange.endInclusive.toInt().coerceIn(from, track.lastIndex.coerceAtLeast(0))
        val droppedStart = from
        val droppedEnd = (track.size - 1) - to
        AlertDialog(
            onDismissRequest = { trimConfirmOpen = false },
            title = { Text("Trim track?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This will permanently delete $droppedStart point${if (droppedStart == 1) "" else "s"} from the start " +
                            "and $droppedEnd point${if (droppedEnd == 1) "" else "s"} from the end of the track.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Any waypoints and photos in those segments will also be deleted.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "This cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.trimDrive(from, to) {
                            trimConfirmOpen = false
                            trimExpanded = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Trim and discard data") }
            },
            dismissButton = {
                TextButton(onClick = { trimConfirmOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun MapWithWaypoints(
    track: List<com.senikroute.data.db.entities.TrackPointEntity>,
    waypoints: List<com.senikroute.data.db.entities.WaypointEntity>,
    addMode: Boolean,
    onTap: (Double, Double) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        SenikMap(
            modifier = Modifier.fillMaxSize(),
            track = track,
            waypoints = waypoints,
            cameraBehavior = CameraBehavior.FitBounds,
            onMapTap = onTap,
        )
        if (addMode) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    "Tap the map to drop a waypoint",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun WaypointEditorRow(
    waypointCount: Int,
    photoCount: Int,
    addMode: Boolean,
    canAdd: Boolean,
    onToggleAddMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Waypoints", style = MaterialTheme.typography.labelLarge)
            Text(
                "$waypointCount waypoint${if (waypointCount == 1) "" else "s"} · " +
                    "$photoCount photo${if (photoCount == 1) "" else "s"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (addMode) {
            AssistChip(
                onClick = onToggleAddMode,
                label = { Text("Cancel") },
            )
        } else {
            Button(
                onClick = onToggleAddMode,
                enabled = canAdd,
            ) {
                Icon(Icons.Filled.AddLocation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add waypoint")
            }
        }
    }
}

@Composable
private fun TrimSection(
    track: List<com.senikroute.data.db.entities.TrackPointEntity>,
    expanded: Boolean,
    range: ClosedFloatingPointRange<Float>,
    onToggle: () -> Unit,
    onRangeChange: (ClosedFloatingPointRange<Float>) -> Unit,
    onApply: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Trim track", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Cut points off the start or end of the recorded route.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (expanded) {
                AssistChip(onClick = onToggle, label = { Text("Cancel") })
            } else {
                OutlinedButton(onClick = onToggle, enabled = track.size >= 2) {
                    Icon(Icons.Filled.ContentCut, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Trim…")
                }
            }
        }
        if (expanded && track.size >= 2) {
            val from = range.start.toInt().coerceIn(0, track.lastIndex)
            val to = range.endInclusive.toInt().coerceIn(from, track.lastIndex)
            val keptKm = computeDistanceKm(track.subList(from, to + 1))
            val keptDurationMin = ((track[to].recordedAt - track[from].recordedAt) / 60_000L).toInt()
            Text(
                "Keeping ${to - from + 1} of ${track.size} points · " +
                    "%.1f km · %d min".format(keptKm, keptDurationMin),
                style = MaterialTheme.typography.bodySmall,
            )
            RangeSlider(
                value = range,
                onValueChange = onRangeChange,
                valueRange = 0f..(track.size - 1).toFloat(),
                steps = (track.size - 2).coerceAtLeast(0),
            )
            Text(
                "⚠ Saving will permanently discard the trimmed parts and any waypoints/photos in them. " +
                    "This cannot be undone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onApply,
                enabled = (from > 0 || to < track.lastIndex),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Apply trim") }
        }
    }
}

private fun computeDistanceKm(points: List<com.senikroute.data.db.entities.TrackPointEntity>): Double {
    if (points.size < 2) return 0.0
    var meters = 0.0
    for (i in 1 until points.size) {
        meters += com.senikroute.data.repo.haversineMeters(
            points[i - 1].lat, points[i - 1].lng,
            points[i].lat, points[i].lng,
        )
    }
    return meters / 1000.0
}

@Composable
private fun CommentsToggleRow(
    enabled: Boolean,
    visibility: Visibility,
    onChange: (Boolean) -> Unit,
) {
    val publicEnough = visibility != Visibility.PRIVATE
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Allow comments", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (publicEnough) {
                    "Off by default. When on, signed-in viewers can post comments and questions."
                } else {
                    "Only takes effect when the drive is unlisted or public."
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
        )
    }
}

@Composable
private fun VisibilityRow(
    value: Visibility,
    selected: Visibility,
    label: String,
    sub: String,
    onSelect: (Visibility) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = value == selected, onClick = { onSelect(value) })
        Spacer(Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
