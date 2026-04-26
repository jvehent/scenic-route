package com.senikroute.ui.public_

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.senikroute.auth.AuthState
import com.senikroute.data.db.entities.TrackPointEntity
import com.senikroute.data.db.entities.WaypointEntity
import com.senikroute.data.remote.RemoteWaypoint
import com.senikroute.ui.layout.mapHeight
import com.senikroute.ui.map.CameraBehavior
import com.senikroute.ui.map.SenikMap
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicDriveScreen(
    onBack: () -> Unit,
    onPhotoClick: (String) -> Unit,
    onOwnerClick: (String) -> Unit,
    onSignInClick: () -> Unit,
    vm: PublicDriveViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val comments by vm.comments.collectAsStateWithLifecycle()
    val authState by vm.authState.collectAsStateWithLifecycle()
    val isOwner by vm.isOwner.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val drive = state.drive
    val signedIn = authState as? AuthState.SignedIn
    var commentError by remember { mutableStateOf<String?>(null) }

    val mapTrack = remember(state.track, drive) {
        state.track.map {
            TrackPointEntity(
                driveId = drive?.id ?: "remote",
                seq = it.seq,
                lat = it.lat,
                lng = it.lng,
                alt = it.alt,
                speed = null,
                accuracy = null,
                recordedAt = it.recordedAt ?: 0L,
            )
        }
    }
    val mapWaypoints = remember(state.waypoints, drive, mapTrack) {
        val real = state.waypoints.map {
            WaypointEntity(
                id = it.id,
                driveId = drive?.id ?: "remote",
                lat = it.lat,
                lng = it.lng,
                recordedAt = it.recordedAt,
                syncState = "SYNCED",
            )
        }
        // Featured/imported drives don't have a recorded track or waypoints — fall back
        // to the drive's known anchors (start, end, centroid) so FitBounds has at least
        // one point to zoom into instead of leaving the world map at z=0.
        if (real.isEmpty() && mapTrack.isEmpty() && drive != null) {
            val anchors = mutableListOf<WaypointEntity>()
            fun add(tag: String, lat: Double?, lng: Double?) {
                if (lat == null || lng == null) return
                anchors += WaypointEntity(
                    id = "${drive.id}:$tag",
                    driveId = drive.id,
                    lat = lat, lng = lng,
                    recordedAt = 0L,
                    syncState = "SYNCED",
                )
            }
            add("start", drive.startLat, drive.startLng)
            add("end", drive.endLat, drive.endLng)
            // Only add centroid if it's not a duplicate of start/end (covers the loop case
            // where start == end and we want a single distinct point).
            if (anchors.isEmpty()) add("centroid", drive.centroidLat, drive.centroidLng)
            return@remember anchors
        }
        real
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(drive?.title?.ifBlank { "Untitled drive" } ?: "Drive") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { vm.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    if (drive != null) {
                        IconButton(onClick = {
                            val title = drive.title.ifBlank { "A scenic drive" }
                            val url = com.senikroute.ui.nav.Destinations.shareUrlFor(drive.id)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, "$title\n$url")
                            }
                            context.startActivity(Intent.createChooser(intent, "Share drive"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && drive == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && drive == null -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                }
            }
            drive != null -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(mapHeight())) {
                            SenikMap(
                                modifier = Modifier.fillMaxSize(),
                                track = mapTrack,
                                waypoints = mapWaypoints,
                                cameraBehavior = CameraBehavior.FitBounds,
                            )
                        }
                    }
                    item {
                        MetadataCard(
                            drive,
                            state.waypoints.size,
                            mapTrack.size,
                            onOwnerClick = { drive.ownerUid?.let(onOwnerClick) },
                        )
                    }
                    drive.centroidLat?.let { lat ->
                        drive.centroidLng?.let { lng ->
                            item { NavigateButton(lat, lng) }
                        }
                    }
                    if (state.waypoints.isNotEmpty()) {
                        item {
                            Text(
                                "Waypoints",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                        items(state.waypoints, key = { it.id }) { wp ->
                            WaypointCard(wp, onPhotoClick)
                        }
                    }
                    if (drive.visibility == "public") {
                        item {
                            CommentsSection(
                                comments = comments,
                                isOwner = isOwner,
                                isSignedIn = signedIn != null,
                                isEmailVerified = signedIn?.isEmailVerified == true,
                                currentUid = signedIn?.uid,
                                onPost = { body, parentId ->
                                    vm.postComment(body, parentId) { commentError = it }
                                },
                                onMarkHelpful = { id, helpful, parentId ->
                                    vm.setHelpful(id, helpful, parentId) { commentError = it }
                                },
                                onDelete = { id -> vm.deleteComment(id) { commentError = it } },
                                onAuthorClick = onOwnerClick,
                                onSignInClick = onSignInClick,
                            )
                        }
                        commentError?.let {
                            item {
                                Text(
                                    it,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun MetadataCard(
    drive: com.senikroute.data.remote.RemoteDrive,
    waypointCount: Int,
    trackPointCount: Int,
    onOwnerClick: () -> Unit,
) {
    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "%.1f km · %d min".format(drive.distanceM / 1000.0, drive.durationS / 60),
                style = MaterialTheme.typography.titleLarge,
            )
            val handle = drive.ownerAnonHandle
            val date = drive.startedAt.takeIf { it > 0 }?.let { DateFormat.getDateInstance().format(Date(it)) }
            if (handle != null || date != null) {
                Row {
                    if (handle != null) {
                        Text(
                            handle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable(enabled = drive.ownerUid != null) { onOwnerClick() },
                        )
                    }
                    if (handle != null && date != null) {
                        Text(
                            " · ",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (date != null) {
                        Text(
                            date,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (drive.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(drive.description, style = MaterialTheme.typography.bodyLarge)
            }
            if (drive.tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(drive.tags.joinToString(" · ") { "#$it" }, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "$trackPointCount track points · $waypointCount waypoints",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NavigateButton(lat: Double, lng: Double) {
    val context = LocalContext.current
    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        TextButton(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            onClick = {
                val gmm = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("google.navigation:q=$lat,$lng"),
                ).apply { setPackage("com.google.android.apps.maps") }
                runCatching { context.startActivity(gmm) }.onFailure {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng")))
                }
            },
        ) {
            Icon(Icons.Filled.Navigation, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Navigate to start")
        }
    }
}

@Composable
private fun WaypointCard(wp: RemoteWaypoint, onPhotoClick: (String) -> Unit) {
    Card(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (wp.photoUrls.isNotEmpty()) {
                val scroll = rememberScrollState()
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(scroll),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    wp.photoUrls.forEach { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(120.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPhotoClick(url) },
                        )
                    }
                }
            }
            Text(
                "%.5f, %.5f".format(wp.lat, wp.lng),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            wp.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

