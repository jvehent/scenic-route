package com.scenicroute.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.db.entities.TrackPointEntity
import android.net.Uri
import com.scenicroute.data.db.entities.WaypointEntity
import com.scenicroute.data.db.entities.WaypointPhotoEntity
import com.scenicroute.data.io.DriveExporter
import com.scenicroute.data.io.GeoFormat
import com.scenicroute.data.repo.DriveRepository
import com.scenicroute.ui.nav.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriveDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DriveRepository,
    private val exporter: DriveExporter,
) : ViewModel() {

    private val driveId: String = checkNotNull(savedStateHandle[Destinations.ARG_DRIVE_ID])

    val drive: StateFlow<DriveEntity?> = repo.observeDrive(driveId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val track: StateFlow<List<TrackPointEntity>> = repo.observeTrack(driveId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val waypoints: StateFlow<List<WaypointEntity>> = repo.observeWaypoints(driveId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos: StateFlow<List<WaypointPhotoEntity>> = repo.observePhotos(driveId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.softDeleteDrive(driveId)
            onDone()
        }
    }

    fun export(format: GeoFormat, onResult: (Uri?, String?) -> Unit) {
        viewModelScope.launch {
            runCatching { exporter.export(driveId, format) }
                .onSuccess { onResult(it, null) }
                .onFailure { onResult(null, it.message ?: "Export failed") }
        }
    }
}
