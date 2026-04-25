package com.scenicroute.data.repo

import com.google.firebase.firestore.FirebaseFirestore
import com.scenicroute.data.db.dao.DriveDao
import com.scenicroute.data.db.dao.TrackPointDao
import com.scenicroute.data.db.dao.WaypointDao
import com.scenicroute.data.db.dao.WaypointPhotoDao
import com.scenicroute.data.sync.SyncScheduler
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.db.entities.TrackPointEntity
import com.scenicroute.data.db.entities.WaypointEntity
import com.scenicroute.data.db.entities.WaypointPhotoEntity
import com.scenicroute.data.model.BoundingBox
import com.scenicroute.data.model.DriveStatus
import com.scenicroute.data.model.SyncState
import com.scenicroute.data.model.VehicleReqs
import com.scenicroute.data.model.VehicleSummary
import com.scenicroute.data.model.Visibility
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriveRepository @Inject constructor(
    private val driveDao: DriveDao,
    private val trackPointDao: TrackPointDao,
    private val waypointDao: WaypointDao,
    private val waypointPhotoDao: WaypointPhotoDao,
    private val syncScheduler: SyncScheduler,
    private val firestore: FirebaseFirestore,
) {

    fun observeDrives(ownerUid: String): Flow<List<DriveEntity>> =
        driveDao.observeAllForOwner(ownerUid)

    fun observeTrashed(ownerUid: String): Flow<List<DriveEntity>> =
        driveDao.observeTrashed(ownerUid)

    fun observeTrashedCount(ownerUid: String): Flow<Int> =
        driveDao.observeTrashedCount(ownerUid)

    fun observeDrive(id: String): Flow<DriveEntity?> = driveDao.observe(id)

    fun observeTrack(driveId: String): Flow<List<TrackPointEntity>> =
        trackPointDao.observe(driveId)

    fun observeWaypoints(driveId: String): Flow<List<WaypointEntity>> =
        waypointDao.observe(driveId)

    fun observePhotos(driveId: String): Flow<List<WaypointPhotoEntity>> =
        waypointPhotoDao.observeForDrive(driveId)

    suspend fun getActiveDrive(ownerUid: String): DriveEntity? =
        driveDao.getActiveForOwner(ownerUid)

    suspend fun startDrive(
        ownerUid: String,
        startLat: Double,
        startLng: Double,
        startedAt: Long = System.currentTimeMillis(),
    ): DriveEntity {
        val drive = DriveEntity(
            id = UUID.randomUUID().toString(),
            ownerUid = ownerUid,
            status = DriveStatus.RECORDING.name,
            visibility = Visibility.PRIVATE.name,
            startLat = startLat,
            startLng = startLng,
            startedAt = startedAt,
            syncState = SyncState.LOCAL.name,
        )
        driveDao.upsert(drive)
        return drive
    }

    suspend fun appendTrackPoints(driveId: String, points: List<PendingTrackPoint>) {
        if (points.isEmpty()) return
        val start = trackPointDao.maxSeq(driveId) + 1
        val entities = points.mapIndexed { i, p ->
            TrackPointEntity(
                driveId = driveId,
                seq = start + i,
                lat = p.lat,
                lng = p.lng,
                alt = p.alt,
                speed = p.speed,
                accuracy = p.accuracy,
                recordedAt = p.recordedAt,
            )
        }
        trackPointDao.insertAll(entities)
    }

    suspend fun addWaypoint(
        driveId: String,
        lat: Double,
        lng: Double,
        recordedAt: Long = System.currentTimeMillis(),
        note: String? = null,
        vehicleReqs: VehicleReqs? = null,
    ): WaypointEntity {
        val wp = WaypointEntity(
            id = UUID.randomUUID().toString(),
            driveId = driveId,
            lat = lat,
            lng = lng,
            recordedAt = recordedAt,
            note = note,
            vehicleReqs = vehicleReqs,
            syncState = SyncState.LOCAL.name,
        )
        waypointDao.upsert(wp)
        return wp
    }

    suspend fun addPhoto(
        waypointId: String,
        localPath: String,
        width: Int? = null,
        height: Int? = null,
        takenAt: Long = System.currentTimeMillis(),
    ): WaypointPhotoEntity {
        val photo = WaypointPhotoEntity(
            id = UUID.randomUUID().toString(),
            waypointId = waypointId,
            localPath = localPath,
            width = width,
            height = height,
            takenAt = takenAt,
            syncState = SyncState.LOCAL.name,
        )
        waypointPhotoDao.upsert(photo)
        return photo
    }

    suspend fun stopDrive(driveId: String, endedAt: Long = System.currentTimeMillis()): DriveEntity? {
        val drive = driveDao.getById(driveId) ?: return null
        val points = trackPointDao.get(driveId)
        val waypoints = waypointDao.get(driveId)

        val distanceM = computeDistanceMeters(points).toInt()
        val bbox = BoundingBox.fromPoints(points.map { it.lat to it.lng })
        val last = points.lastOrNull()
        val vehicleSummary = VehicleSummary.summarize(waypoints.mapNotNull { it.vehicleReqs })

        val updated = drive.copy(
            status = DriveStatus.DRAFT.name,
            endLat = last?.lat ?: drive.startLat,
            endLng = last?.lng ?: drive.startLng,
            endedAt = endedAt,
            durationS = ((endedAt - drive.startedAt) / 1000L).toInt(),
            distanceM = distanceM,
            boundingBox = bbox,
            vehicleSummary = vehicleSummary,
            geohash = bbox?.let { centroidGeohash(it) },
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.DIRTY.name,
        )
        driveDao.update(updated)
        syncScheduler.scheduleNow()
        return updated
    }

    suspend fun updateDriveMeta(
        driveId: String,
        title: String,
        description: String,
        visibility: Visibility,
        tags: List<String>,
        coverWaypointId: String?,
    ): DriveEntity? {
        val d = driveDao.getById(driveId) ?: return null
        val updated = d.copy(
            title = title,
            description = description,
            visibility = visibility.name,
            tags = tags,
            coverWaypointId = coverWaypointId,
            updatedAt = System.currentTimeMillis(),
            syncState = SyncState.DIRTY.name,
        )
        driveDao.update(updated)
        syncScheduler.scheduleNow()
        return updated
    }

    suspend fun markTrackUploaded(driveId: String, trackUrl: String, trackBytes: Long) {
        val d = driveDao.getById(driveId) ?: return
        driveDao.update(d.copy(trackUrl = trackUrl, trackBytes = trackBytes))
    }

    suspend fun markDriveSynced(driveId: String) {
        val d = driveDao.getById(driveId) ?: return
        driveDao.update(
            d.copy(
                syncState = SyncState.SYNCED.name,
                serverVersion = (d.serverVersion ?: 0) + 1,
            ),
        )
    }

    suspend fun markWaypointSynced(waypointId: String) {
        val wp = waypointDao.getById(waypointId) ?: return
        waypointDao.update(wp.copy(syncState = SyncState.SYNCED.name))
    }

    suspend fun markPhotoSynced(photoId: String, remoteUrl: String) {
        val photo = waypointPhotoDao.getById(photoId) ?: return
        waypointPhotoDao.upsert(photo.copy(remoteUrl = remoteUrl, syncState = SyncState.SYNCED.name))
    }

    suspend fun getDirtyDrivesFor(ownerUid: String) = driveDao.getPendingSync(ownerUid)
    suspend fun getPendingWaypoints(driveId: String) = waypointDao.getPendingSync(driveId)
    suspend fun getPendingPhotos(driveId: String) = waypointPhotoDao.getPendingSync(driveId)

    /** Soft delete: tombstones for 30 days, then a Cloud Function purges. */
    suspend fun softDeleteDrive(id: String) {
        val d = driveDao.getById(id) ?: return
        driveDao.update(
            d.copy(
                deletedAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.DIRTY.name,
            ),
        )
        syncScheduler.scheduleNow()
    }

    suspend fun restoreDrive(id: String) {
        val d = driveDao.getById(id) ?: return
        driveDao.update(
            d.copy(
                deletedAt = null,
                updatedAt = System.currentTimeMillis(),
                syncState = SyncState.DIRTY.name,
            ),
        )
        syncScheduler.scheduleNow()
    }

    /** Permanent delete: removes locally + asks Firestore to delete (cascade fires server-side). */
    suspend fun hardDeleteDrive(id: String) {
        runCatching {
            firestore.collection("drives").document(id).delete()
        }
        driveDao.delete(id)
    }

    private fun computeDistanceMeters(points: List<TrackPointEntity>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            total += haversineMeters(points[i - 1].lat, points[i - 1].lng, points[i].lat, points[i].lng)
        }
        return total
    }

    private fun centroidGeohash(b: BoundingBox): String {
        val lat = (b.north + b.south) / 2.0
        val lng = (b.east + b.west) / 2.0
        return encodeGeohash(lat, lng, precision = 9)
    }

    data class PendingTrackPoint(
        val lat: Double,
        val lng: Double,
        val alt: Double?,
        val speed: Float?,
        val accuracy: Float?,
        val recordedAt: Long,
    )
}

fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = kotlin.math.sin(dLat / 2).let { it * it } +
            kotlin.math.cos(Math.toRadians(lat1)) *
            kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLng / 2).let { it * it }
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
}

private const val GEOHASH_CHARS = "0123456789bcdefghjkmnpqrstuvwxyz"

fun encodeGeohash(lat: Double, lng: Double, precision: Int = 9): String {
    var latLo = -90.0; var latHi = 90.0
    var lngLo = -180.0; var lngHi = 180.0
    val sb = StringBuilder(precision)
    var bit = 0
    var ch = 0
    var even = true
    while (sb.length < precision) {
        if (even) {
            val mid = (lngLo + lngHi) / 2
            if (lng >= mid) { ch = ch or (1 shl (4 - bit)); lngLo = mid } else lngHi = mid
        } else {
            val mid = (latLo + latHi) / 2
            if (lat >= mid) { ch = ch or (1 shl (4 - bit)); latLo = mid } else latHi = mid
        }
        even = !even
        bit++
        if (bit == 5) {
            sb.append(GEOHASH_CHARS[ch])
            bit = 0; ch = 0
        }
    }
    return sb.toString()
}
