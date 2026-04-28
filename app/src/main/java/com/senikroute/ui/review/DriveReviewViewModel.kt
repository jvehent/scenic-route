package com.senikroute.ui.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.senikroute.data.db.entities.DriveEntity
import com.senikroute.data.db.entities.TrackPointEntity
import com.senikroute.data.db.entities.WaypointEntity
import com.senikroute.data.db.entities.WaypointPhotoEntity
import com.senikroute.data.model.VehicleReqs
import com.senikroute.data.model.Visibility
import com.senikroute.data.drive.DriveTakeoutManager
import com.senikroute.data.drive.GoogleDriveAuth
import com.senikroute.data.repo.DriveRepository
import com.senikroute.data.repo.haversineMeters
import com.senikroute.data.sync.SyncScheduler
import com.senikroute.ui.nav.Destinations
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriveReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: DriveRepository,
    private val syncScheduler: SyncScheduler,
    private val driveAuth: GoogleDriveAuth,
    private val takeoutManager: DriveTakeoutManager,
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

    fun save(
        title: String,
        description: String,
        visibility: Visibility,
        tagsCsv: String,
        commentsEnabled: Boolean,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            val tags = tagsCsv.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            repo.updateDriveMeta(
                driveId = driveId,
                title = title.trim(),
                description = description.trim(),
                visibility = visibility,
                tags = tags,
                coverWaypointId = null,
                commentsEnabled = commentsEnabled,
            )
            // Best-effort auto-save to Google Drive. Only fires if the user enabled the
            // toggle in Profile AND has previously granted Drive access (silent token).
            // No consent prompt is launched here — auto-save is meant to be background-y;
            // forcing a permission dialog every time would be hostile UX.
            runCatching {
                val token = driveAuth.trySilent()
                if (token != null) takeoutManager.autoSave(driveId, token)
            }
            onDone()
        }
    }

    fun discard(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.softDeleteDrive(driveId)
            onDone()
        }
    }

    /**
     * Drop a waypoint at [lat]/[lng], attaching any [photoPaths] (already finalized by
     * WaypointPhotosViewModel — EXIF stripped, size-capped). Snaps the lat/lng to the
     * closest track point if the tapped position is far from the route, so waypoints stay
     * meaningful even when the user taps slightly off the polyline.
     */
    fun addWaypointAt(
        lat: Double,
        lng: Double,
        note: String?,
        vehicleReqs: VehicleReqs?,
        photoPaths: List<String>,
    ) {
        viewModelScope.launch {
            val (snappedLat, snappedLng) = snapToTrack(lat, lng) ?: (lat to lng)
            val wp = repo.addWaypoint(
                driveId = driveId,
                lat = snappedLat,
                lng = snappedLng,
                note = note?.takeIf { it.isNotBlank() },
                vehicleReqs = vehicleReqs,
            )
            photoPaths.forEach { repo.addPhoto(wp.id, it) }
            // The waypoint/photo entities are created with syncState=LOCAL so the worker
            // picks them up next pass; nudge it now so the user sees them appear remotely.
            syncScheduler.scheduleNow()
        }
    }

    /**
     * Permanently trim the drive's track to the [keepFromIndex, keepToIndex] inclusive
     * range (indices into the currently observed track list). Drops out-of-range track
     * points, waypoints, and their photos; recomputes drive metrics. Caller must surface
     * a destructive-action confirmation first — there is no undo.
     */
    fun trimDrive(keepFromIndex: Int, keepToIndex: Int, onDone: () -> Unit = {}) {
        val pts = track.value
        if (pts.isEmpty()) return
        val safeFrom = keepFromIndex.coerceIn(0, pts.lastIndex)
        val safeTo = keepToIndex.coerceIn(safeFrom, pts.lastIndex)
        viewModelScope.launch {
            repo.trimDrive(driveId, pts[safeFrom].seq, pts[safeTo].seq)
            onDone()
        }
    }

    private fun snapToTrack(lat: Double, lng: Double): Pair<Double, Double>? {
        val pts = track.value
        if (pts.isEmpty()) return null
        val nearest = pts.minByOrNull { haversineMeters(lat, lng, it.lat, it.lng) } ?: return null
        // If the tap is more than 5 km from any track point, treat it as off-route and
        // honor the raw tap location instead of dragging the pin a long way.
        return if (haversineMeters(lat, lng, nearest.lat, nearest.lng) <= 5_000.0) {
            nearest.lat to nearest.lng
        } else {
            null
        }
    }
}
