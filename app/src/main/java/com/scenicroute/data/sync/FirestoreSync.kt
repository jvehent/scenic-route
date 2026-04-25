package com.scenicroute.data.sync

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.scenicroute.auth.AuthRepository
import com.scenicroute.auth.AuthState
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.db.entities.TrackPointEntity
import com.scenicroute.data.db.entities.WaypointEntity
import com.scenicroute.data.db.entities.WaypointPhotoEntity
import com.scenicroute.data.model.Visibility
import com.scenicroute.data.repo.DriveRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirestoreSync"

@Singleton
class FirestoreSync @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val authRepo: AuthRepository,
    private val driveRepo: DriveRepository,
) {
    /** Returns the number of drives successfully synced. Throws on network/auth errors so the worker retries. */
    suspend fun syncAll(): Int {
        Log.d(TAG, "syncAll: begin")
        val state = authRepo.authState.first()
        val signedIn = state as? AuthState.SignedIn
        if (signedIn == null) {
            Log.d(TAG, "syncAll: not signed in, skipping")
            return 0
        }
        // Don't log uid in production — Log.i/w/e are NOT stripped by R8.
        Log.d(TAG, "syncAll: emailVerified=${signedIn.isEmailVerified}")
        if (!signedIn.isEmailVerified) return 0

        val drives = driveRepo.getDirtyDrivesFor(signedIn.uid)
        Log.d(TAG, "syncAll: found ${drives.size} dirty drive(s)")
        var synced = 0
        for (drive in drives) {
            runCatching { syncDrive(drive, signedIn.uid) }
                .onSuccess { synced++ }
                .onFailure { Log.w(TAG, "syncAll: a drive failed to sync") } // no id in prod logs
        }
        Log.d(TAG, "syncAll: done, synced=$synced")
        return synced
    }

    private suspend fun syncDrive(drive: DriveEntity, uid: String) {
        val driveDoc = firestore.collection("drives").document(drive.id)
        // Look up the user's server-assigned anon handle so we don't echo a derived value
        // back to Firestore. Falls back to a placeholder if profile bootstrap hasn't completed.
        val handle = runCatching {
            firestore.collection("users").document(uid).get().await()
                .getString("anonHandle")
        }.getOrNull() ?: "traveler-pending"
        driveDoc.set(drive.toFirestoreMap(ownerUid = uid, ownerAnonHandle = handle), SetOptions.merge()).await()

        // 2. Upload track GeoJSON blob if not already uploaded.
        if (drive.trackUrl == null) {
            val points = driveRepo.observeTrack(drive.id).first()
            if (points.size >= 2) {
                val (url, bytes) = uploadTrack(drive.id, points)
                driveRepo.markTrackUploaded(drive.id, url, bytes)
                driveDoc.set(mapOf("trackUrl" to url, "trackBytes" to bytes), SetOptions.merge()).await()
            }
        }

        // 3. Waypoints.
        val pendingWaypoints = driveRepo.getPendingWaypoints(drive.id)
        for (wp in pendingWaypoints) {
            driveDoc.collection("waypoints")
                .document(wp.id)
                .set(wp.toFirestoreMap(), SetOptions.merge())
                .await()
            driveRepo.markWaypointSynced(wp.id)
        }

        // 4. Photos.
        val pendingPhotos = driveRepo.getPendingPhotos(drive.id)
        for (photo in pendingPhotos) {
            val remoteUrl = uploadPhoto(uid, drive.id, photo) ?: continue
            driveRepo.markPhotoSynced(photo.id, remoteUrl)
            // Append to waypoint.photoUrls (server-side merge).
            driveDoc.collection("waypoints")
                .document(photo.waypointId)
                .set(
                    mapOf("photoUrls" to com.google.firebase.firestore.FieldValue.arrayUnion(remoteUrl)),
                    SetOptions.merge(),
                )
                .await()
        }

        // 5. Mark drive synced.
        driveRepo.markDriveSynced(drive.id)
    }

    private suspend fun uploadTrack(
        driveId: String,
        points: List<TrackPointEntity>,
    ): Pair<String, Long> {
        val geojson = buildGeoJson(points)
        val bytes = gzip(geojson)
        check(bytes.size <= MAX_TRACK_BYTES) {
            "track exceeds upload cap (${bytes.size} > $MAX_TRACK_BYTES bytes)"
        }
        val ref = storage.reference.child("tracks/$driveId.geojson.gz")
        ref.putBytes(bytes).await()
        val url = ref.downloadUrl.await()
        return url.toString() to bytes.size.toLong()
    }

    private companion object {
        const val MAX_TRACK_BYTES = 5L * 1024 * 1024
    }

    private suspend fun uploadPhoto(uid: String, driveId: String, photo: WaypointPhotoEntity): String? {
        val file = File(photo.localPath).takeIf { it.exists() } ?: return null
        val ref = storage.reference
            .child("users/$uid/drives/$driveId/waypoints/${photo.waypointId}/${photo.id}.jpg")
        ref.putFile(Uri.fromFile(file)).await()
        return ref.downloadUrl.await().toString()
    }

    private fun buildGeoJson(points: List<TrackPointEntity>): String {
        val feature = TrackGeoJson(
            type = "FeatureCollection",
            features = listOf(
                TrackFeature(
                    type = "Feature",
                    geometry = LineStringGeometry(
                        type = "LineString",
                        coordinates = points.map { listOfNotNull(it.lng, it.lat, it.alt) },
                    ),
                    properties = TrackProps(
                        recordedAt = points.map { it.recordedAt },
                        speed = points.map { it.speed ?: 0f }.map { it.toDouble() },
                        accuracy = points.map { it.accuracy ?: 0f }.map { it.toDouble() },
                    ),
                ),
            ),
        )
        return Json.encodeToString(TrackGeoJson.serializer(), feature)
    }

    private fun gzip(s: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(s.toByteArray(Charsets.UTF_8)) }
        return out.toByteArray()
    }
}

private fun DriveEntity.toFirestoreMap(ownerUid: String, ownerAnonHandle: String): Map<String, Any?> = buildMap {
    put("ownerUid", ownerUid)
    put("ownerAnonHandle", ownerAnonHandle)
    put("title", title)
    put("description", description)
    put("visibility", visibility.lowercase())
    put("status", status.lowercase())
    put("startLat", startLat); put("startLng", startLng)
    put("endLat", endLat); put("endLng", endLng)
    put("distanceM", distanceM); put("durationS", durationS)
    put("startedAt", startedAt); put("endedAt", endedAt)
    put("tags", tags)
    put("vehicleSummary", vehicleSummary?.let { Json.encodeToString(com.scenicroute.data.model.VehicleSummary.serializer(), it) })
    put("boundingBox", boundingBox?.let {
        mapOf(
            "north" to it.north, "south" to it.south,
            "east" to it.east, "west" to it.west,
        )
    })
    put("geohash", geohash)
    put("centroidLat", boundingBox?.let { (it.north + it.south) / 2.0 })
    put("centroidLng", boundingBox?.let { (it.east + it.west) / 2.0 })
    put("coverPhotoUrl", null as String?)
    put("trackUrl", trackUrl)
    put("trackBytes", trackBytes)
    put("updatedAt", updatedAt)
    put("deletedAt", deletedAt)
    put("version", serverVersion ?: 1)
}

private fun WaypointEntity.toFirestoreMap(): Map<String, Any?> = buildMap {
    put("lat", lat); put("lng", lng)
    put("recordedAt", recordedAt)
    put("note", note)
    vehicleReqs?.let {
        put(
            "vehicleReqs",
            Json.encodeToString(com.scenicroute.data.model.VehicleReqs.serializer(), it),
        )
    }
}

