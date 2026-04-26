package com.senikroute.recording

import android.content.Context
import android.content.Intent
import android.os.Build
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.db.dao.LocationBufferDao
import com.senikroute.data.model.VehicleReqs
import com.senikroute.data.repo.DriveRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingController @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val driveRepo: DriveRepository,
    private val authRepo: AuthRepository,
    private val bufferDao: LocationBufferDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    suspend fun start(startLat: Double, startLng: Double, startedAt: Long): Result<String> = runCatching {
        mutex.withLock {
            val uid = currentUid() ?: error("not signed in")
            val existing = driveRepo.getActiveDrive(uid)
            val drive = existing ?: driveRepo.startDrive(uid, startLat, startLng, startedAt)
            _state.value = _state.value.copy(
                status = RecordingUiState.Status.RECORDING,
                activeDriveId = drive.id,
                startedAt = drive.startedAt,
                lastLat = startLat,
                lastLng = startLng,
                error = null,
            )
            startService()
            drive.id
        }
    }

    suspend fun startFromBuffer(sinceMs: Long): Result<String> = runCatching {
        mutex.withLock {
            val uid = currentUid() ?: error("not signed in")
            val points = bufferDao.since(sinceMs)
            if (points.size < 2) error("not enough buffered points")
            val first = points.first()
            val last = points.last()
            val drive = driveRepo.startDrive(uid, first.lat, first.lng, first.recordedAt)
            driveRepo.appendTrackPoints(
                drive.id,
                points.map {
                    DriveRepository.PendingTrackPoint(
                        lat = it.lat, lng = it.lng, alt = it.alt,
                        speed = it.speed, accuracy = it.accuracy, recordedAt = it.recordedAt,
                    )
                },
            )
            val finalized = driveRepo.stopDrive(drive.id, last.recordedAt)
            finalized?.id ?: drive.id
        }
    }

    suspend fun stop(): Result<String?> = runCatching {
        mutex.withLock {
            val driveId = _state.value.activeDriveId ?: return@withLock null
            _state.value = _state.value.copy(status = RecordingUiState.Status.STOPPING)
            driveRepo.stopDrive(driveId)
            stopService()
            _state.value = RecordingUiState()
            driveId
        }
    }

    fun onPointRecorded(point: DriveRepository.PendingTrackPoint, distanceDelta: Double) {
        scope.launch {
            val id = _state.value.activeDriveId ?: return@launch
            driveRepo.appendTrackPoints(id, listOf(point))
            val cur = _state.value
            _state.value = cur.copy(
                distanceM = cur.distanceM + distanceDelta.toInt(),
                elapsedMs = point.recordedAt - (cur.startedAt ?: point.recordedAt),
                lastLat = point.lat,
                lastLng = point.lng,
                lastAccuracyM = point.accuracy,
            )
        }
    }

    suspend fun addWaypoint(
        lat: Double,
        lng: Double,
        note: String?,
        vehicleReqs: VehicleReqs?,
        photoPaths: List<String> = emptyList(),
    ): Result<String> = runCatching {
        val driveId = _state.value.activeDriveId ?: error("no active drive")
        val wp = driveRepo.addWaypoint(driveId, lat, lng, note = note, vehicleReqs = vehicleReqs)
        photoPaths.forEach { driveRepo.addPhoto(wp.id, it) }
        _state.value = _state.value.copy(waypointCount = _state.value.waypointCount + 1)
        wp.id
    }

    fun reportError(message: String) {
        _state.value = _state.value.copy(error = message)
    }

    private fun startService() {
        val intent = Intent(appContext, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(intent)
        } else {
            appContext.startService(intent)
        }
    }

    private fun stopService() {
        val intent = Intent(appContext, RecordingService::class.java).apply {
            action = RecordingService.ACTION_STOP
        }
        appContext.startService(intent)
    }

    private suspend fun currentUid(): String? = when (val s = authRepo.authState.first()) {
        is AuthState.SignedIn -> s.uid
        else -> null
    }
}
