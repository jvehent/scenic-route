package com.senikroute.ui.explore

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senikroute.R
import com.senikroute.data.db.entities.TrackPointEntity
import com.senikroute.data.db.entities.WaypointEntity
import com.senikroute.data.discovery.DiscoveryDrive
import com.senikroute.ui.layout.mapHeight
import com.senikroute.ui.map.CameraBehavior
import com.senikroute.ui.map.SenikMap
import com.senikroute.ui.theme.SenikBrandTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    isSignedIn: Boolean,
    onSignInClick: () -> Unit,
    onDriveClick: (String) -> Unit,
    vm: ExploreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val featured by vm.featured.collectAsStateWithLifecycle()
    val radiusKm by vm.radiusKm.collectAsStateWithLifecycle()

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) vm.onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        vm.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { SenikBrandTitle(subtitle = "${stringResource(R.string.nav_explore)} · ${radiusKm} km") },
                actions = {
                    IconButton(onClick = {
                        vm.refresh()
                        vm.reloadFeatured()
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    if (!isSignedIn) {
                        TextButton(onClick = onSignInClick) { Text("Sign in") }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (featured.isNotEmpty()) {
                item { FeaturedHero(featured = featured, onDriveClick = onDriveClick) }
            }
            item {
                Text(
                    "Near you · $radiusKm km",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item { NearbyMap(state) }
            when {
                state.needsLocationPermission -> item {
                    PermissionGate(
                        onGrant = { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                    )
                }
                state.loading && state.drives.isEmpty() -> item {
                    Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null && state.drives.isEmpty() -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                state.drives.isEmpty() -> item {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.explore_empty))
                    }
                }
                else -> items(state.drives, key = { it.driveId }) { drive ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        DiscoveryCard(
                            drive = drive,
                            onOpen = { onDriveClick(drive.driveId) },
                            onNavigate = { openNavigation(context, drive.centroidLat, drive.centroidLng) },
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FeaturedHero(featured: List<DiscoveryDrive>, onDriveClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Featured drives around the world",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(modifier = Modifier.fillMaxWidth().height(mapHeight(compact = 260.dp, medium = 380.dp, expanded = 480.dp))) {
            val pins = featured.map { f ->
                WaypointEntity(
                    id = f.driveId,
                    driveId = f.driveId,
                    lat = f.centroidLat,
                    lng = f.centroidLng,
                    recordedAt = 0L,
                    syncState = "LOCAL",
                )
            }
            SenikMap(
                modifier = Modifier.fillMaxSize(),
                waypoints = pins,
                cameraBehavior = CameraBehavior.FitBounds,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            featured.forEach { d ->
                FeaturedCard(d) { onDriveClick(d.driveId) }
            }
        }
    }
}

@Composable
private fun FeaturedCard(drive: DiscoveryDrive, onClick: () -> Unit) {
    Card(modifier = Modifier.width(240.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                drive.title.ifBlank { "Untitled drive" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
            )
            Text(
                "%.1f km · %d min".format(drive.distanceM / 1000.0, drive.durationS / 60),
                style = MaterialTheme.typography.bodyLarge,
            )
            drive.ownerAnonHandle?.let {
                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NearbyMap(state: ExploreUiState) {
    Box(modifier = Modifier.fillMaxWidth().height(mapHeight(compact = 220.dp, medium = 320.dp, expanded = 400.dp)).padding(horizontal = 16.dp)) {
        val waypoints = state.drives.map { d ->
            WaypointEntity(
                id = d.driveId,
                driveId = d.driveId,
                lat = d.centroidLat,
                lng = d.centroidLng,
                recordedAt = 0L,
                syncState = "LOCAL",
            )
        }
        val userPin = state.userLat?.let { ulat ->
            state.userLng?.let { ulng ->
                listOf(
                    TrackPointEntity(
                        driveId = "user", seq = 0,
                        lat = ulat, lng = ulng,
                        recordedAt = 0L,
                    ),
                )
            }
        } ?: emptyList()
        SenikMap(
            modifier = Modifier.fillMaxSize(),
            track = userPin,
            waypoints = waypoints,
            cameraBehavior = CameraBehavior.FollowLatest,
        )
    }
}

@Composable
private fun DiscoveryCard(
    drive: DiscoveryDrive,
    onOpen: () -> Unit,
    onNavigate: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                drive.title.ifBlank { "Untitled drive" },
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "%.1f km · %d min · %.1f km away".format(
                    drive.distanceM / 1000.0,
                    drive.durationS / 60,
                    drive.distanceFromUserKm,
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            drive.ownerAnonHandle?.let {
                Text(it, style = MaterialTheme.typography.labelLarge)
            }
            if (drive.tags.isNotEmpty()) {
                Text(
                    drive.tags.joinToString(" · ") { "#$it" },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = onNavigate) { Text("Navigate to start") }
        }
    }
}

@Composable
private fun PermissionGate(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "Grant location permission to see scenic drives near you.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGrant) { Text("Grant") }
    }
}

private fun openNavigation(context: android.content.Context, lat: Double, lng: Double) {
    val gmm = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng"))
        .apply { setPackage("com.google.android.apps.maps") }
    runCatching { context.startActivity(gmm) }
        .onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng")))
        }
}
