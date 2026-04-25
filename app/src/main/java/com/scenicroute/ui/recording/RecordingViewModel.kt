package com.scenicroute.ui.recording

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.scenicroute.data.db.entities.TrackPointEntity
import com.scenicroute.data.db.entities.WaypointEntity
import com.scenicroute.data.model.VehicleReqs
import com.scenicroute.data.repo.DriveRepository
import com.scenicroute.recording.RecordingController
import com.scenicroute.recording.RecordingUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val controller: RecordingController,
    private val driveRepo: DriveRepository,
) : ViewModel() {

    val state: StateFlow<RecordingUiState> = controller.state

    val track: StateFlow<List<TrackPointEntity>> = controller.state
        .flatMapLatest { s ->
            s.activeDriveId?.let { driveRepo.observeTrack(it) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val waypoints: StateFlow<List<WaypointEntity>> = controller.state
        .flatMapLatest { s ->
            s.activeDriveId?.let { driveRepo.observeWaypoints(it) } ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @SuppressLint("MissingPermission")
    fun startRecording(onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                val client = LocationServices.getFusedLocationProviderClient(appContext)
                val current = client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                    ?: error("Could not get current location")
                controller.start(current.latitude, current.longitude, current.time).getOrThrow()
            }.onFailure { onError(it.message ?: "Failed to start") }
        }
    }

    fun stopRecording(onStopped: (String?) -> Unit) {
        viewModelScope.launch {
            val driveId = controller.stop().getOrNull()
            onStopped(driveId)
        }
    }

    fun addWaypoint(
        note: String?,
        vehicleReqs: VehicleReqs?,
        photoPaths: List<String>,
        onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            val s = state.value
            val lat = s.lastLat; val lng = s.lastLng
            if (lat == null || lng == null) {
                onError("No GPS fix yet")
                return@launch
            }
            controller.addWaypoint(lat, lng, note, vehicleReqs, photoPaths)
                .onFailure { onError(it.message ?: "Failed to add waypoint") }
        }
    }
}
