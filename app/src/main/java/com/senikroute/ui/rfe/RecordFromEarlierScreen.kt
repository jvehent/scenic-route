package com.senikroute.ui.rfe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.senikroute.R
import com.senikroute.data.db.entities.LocationBufferEntity
import com.senikroute.data.db.entities.TrackPointEntity
import com.senikroute.ui.map.CameraBehavior
import com.senikroute.ui.map.SenikMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordFromEarlierScreen(
    onSaved: (driveId: String) -> Unit,
    onBack: () -> Unit,
    vm: RecordFromEarlierViewModel = hiltViewModel(),
) {
    val buffer by vm.buffer.collectAsStateWithLifecycle()
    val minutes by vm.rangeMinutes.collectAsStateWithLifecycle()
    var error by remember { mutableStateOf<String?>(null) }

    val oldestAgeMinutes = remember(buffer) {
        buffer.minByOrNull { it.recordedAt }
            ?.let { ((System.currentTimeMillis() - it.recordedAt) / 60_000L).toInt().coerceAtLeast(1) }
            ?: 0
    }
    val selectedTrack: List<TrackPointEntity> = remember(buffer, minutes) { vm.selectedTrack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.rfe_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (selectedTrack.size >= 2) {
                    SenikMap(
                        modifier = Modifier.fillMaxSize(),
                        track = selectedTrack,
                        cameraBehavior = CameraBehavior.FitBounds,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            if (buffer.isEmpty()) stringResource(R.string.rfe_empty)
                            else "Not enough points in the selected range.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.rfe_range, minutes),
                    style = MaterialTheme.typography.titleLarge,
                )
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { vm.setRangeMinutes(it.toInt().coerceIn(1, maxOf(oldestAgeMinutes, 1))) },
                    valueRange = 1f..maxOf(oldestAgeMinutes, 1).toFloat(),
                    steps = (maxOf(oldestAgeMinutes - 2, 0)).coerceAtMost(60),
                    enabled = oldestAgeMinutes > 0,
                )
                Text(
                    "${selectedTrack.size} points · buffered up to $oldestAgeMinutes min ago",
                    style = MaterialTheme.typography.labelLarge,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedTrack.size >= 2,
                    onClick = {
                        vm.save(
                            onSaved = onSaved,
                            onError = { error = it },
                        )
                    },
                ) { Text(stringResource(R.string.rfe_save)) }
            }
        }
    }
}
