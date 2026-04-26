package com.senikroute.ui.recording

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senikroute.R
import com.senikroute.recording.RecordingUiState
import com.senikroute.ui.map.CameraBehavior
import com.senikroute.ui.map.SenikMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onStopped: (driveId: String?) -> Unit,
    onBack: () -> Unit,
    vm: RecordingViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val track by vm.track.collectAsStateWithLifecycle()
    val waypoints by vm.waypoints.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    val needsNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val requiredPerms = remember {
        buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (needsNotification) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }

    var permsGranted by remember {
        mutableStateOf(requiredPerms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val permLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        permsGranted = results.all { it.value }
        if (permsGranted && state.status == RecordingUiState.Status.IDLE) {
            vm.startRecording { error = it }
        }
    }

    LaunchedEffect(Unit) {
        if (state.status == RecordingUiState.Status.IDLE) {
            if (permsGranted) vm.startRecording { error = it }
            else permLauncher.launch(requiredPerms)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Recording") })
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (permsGranted) {
                    SenikMap(
                        modifier = Modifier.fillMaxSize(),
                        track = track,
                        waypoints = waypoints,
                        cameraBehavior = CameraBehavior.FollowLatest,
                    )
                } else {
                    PermissionPrompt(onGrant = { permLauncher.launch(requiredPerms) })
                }
            }

            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RecordingStats(state)
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.status == RecordingUiState.Status.RECORDING,
                    onClick = { sheetOpen = true },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recording_add_waypoint))
                }
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        vm.stopRecording { driveId ->
                            onStopped(driveId)
                            if (driveId == null) onBack()
                        }
                    },
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.recording_stop))
                }
            }
        }
    }

    if (sheetOpen) {
        WaypointSheet(
            onDismiss = { sheetOpen = false },
            onSave = { draft ->
                vm.addWaypoint(
                    note = draft.note.takeIf { it.isNotBlank() },
                    vehicleReqs = draft.vehicleReqs,
                    photoPaths = draft.photoPaths,
                ) { error = it }
                sheetOpen = false
            },
        )
    }
}

@Composable
private fun RecordingStats(state: RecordingUiState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.recording_distance, state.distanceM / 1000.0),
            style = MaterialTheme.typography.titleLarge,
        )
        val mm = (state.elapsedMs / 60_000).toInt()
        val ss = ((state.elapsedMs / 1000) % 60).toInt()
        Text(
            text = stringResource(R.string.recording_duration, mm, ss),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text("Waypoints: ${state.waypointCount}", style = MaterialTheme.typography.bodyLarge)
        state.lastAccuracyM?.let {
            Text("GPS accuracy: %.0f m".format(it), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.recording_need_location), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onGrant) { Text(stringResource(R.string.recording_grant)) }
    }
}
