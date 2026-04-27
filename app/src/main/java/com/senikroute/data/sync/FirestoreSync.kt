package com.senikroute.data.sync

import android.net.Uri
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.senikroute.auth.AuthRepository
import com.senikroute.auth.AuthState
import com.senikroute.data.db.entities.DriveEntity
import com.senikroute.data.db.entities.TrackPointEntity
import com.senikroute.data.db.entities.WaypointEntity
import com.senikroute.data.db.entities.WaypointPhotoEntity
import com.senikroute.data.model.Visibility
import com.senikroute.data.repo.DriveRepository
import com.senikroute.data.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FirestoreSync"

private val LENIENT_JSON = Json { ignoreUnknownKeys = true }

@Singleton
class FirestoreSync @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val authRepo: AuthRepository,
    private val driveRepo: DriveRepository,
) {
    /**
     * Fetches all drives owned by the signed-in user from Firestore and writes any that
     * are missing from the local DB. Used to repopulate drives after a fresh install +
     * sign-in (e.g. user switched devices, reinstalled, restored from backup). Idempotent:
     * drives already present locally are skipped, so re-running is a no-op.
     *
     * Photos are NOT downloaded into local files — only the remote URL is stored in
     * [WaypointPhotoEntity]. The UI loads them on demand via Coil. Width/height/takenAt
     * are not preserved across rehydration since they were only ever in the local DB.
     *
     * Returns the number of drives newly added to the local DB.
     */
    suspend fun pullOwnedDrives(): Int {
        val state = authRepo.authState.first()
        val signedIn = state as? AuthState.SignedIn ?: return 0
        if (!signedIn.isEmailVerified) return 0
        val uid = signedIn.uid
        val have = driveRepo.localDriveIdsFor(uid)
        val snap = firestore.collection("drives")
            .whereEqualTo("ownerUid", uid)
            .get()
            .await()
        var added = 0
        for (doc in snap.documents) {
            if (doc.id in have) continue
            // Skip soft-deleted drives — those are owner-tombstoned, no need to re-surface them
            // on a new install. They'll be purged by the cleanup function within 30 days anyway.
            if (doc.getLong("deletedAt") != null) continue
            runCatching { rehydrateOne(doc) }
                .onSuccess { added++ }
                .onFailure { Log.w(TAG, "pullOwnedDrives: failed to rehydrate one drive") }
        }
        Log.d(TAG, "pullOwnedDrives: rehydrated $added of ${snap.size()} server drives")
        return added
    }

    private suspend fun rehydrateOne(doc: com.google.firebase.firestore.DocumentSnapshot) {
        val driveId = doc.id
        val ownerUid = doc.getString("ownerUid") ?: return
        val startedAt = doc.getLong("startedAt") ?: 0L
        // Reconstruct DriveEntity from the Firestore fields we wrote in toFirestoreMap().
        val visibilityStr = (doc.getString("visibility") ?: "private").uppercase()
        val statusStr = (doc.getString("status") ?: "synced").uppercase()
        val drive = DriveEntity(
            id = driveId,
            ownerUid = ownerUid,
            title = doc.getString("title").orEmpty(),
            description = doc.getString("description").orEmpty(),
            status = statusStr,
            visibility = visibilityStr,
            startLat = doc.getDouble("startLat") ?: 0.0,
            startLng = doc.getDouble("startLng") ?: 0.0,
            endLat = doc.getDouble("endLat"),
            endLng = doc.getDouble("endLng"),
            distanceM = (doc.getLong("distanceM") ?: 0L).toInt(),
            durationS = (doc.getLong("durationS") ?: 0L).toInt(),
            startedAt = startedAt,
            endedAt = doc.getLong("endedAt"),
            tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            commentsEnabled = doc.getBoolean("commentsEnabled") ?: false,
            geohash = doc.getString("geohash"),
            trackUrl = doc.getString("trackUrl"),
            trackBytes = doc.getLong("trackBytes"),
            updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis(),
            deletedAt = null,
            syncState = SyncState.SYNCED.name,
            serverVersion = (doc.getLong("version") ?: 1L).toInt(),
        )

        // Track GeoJSON: download, gunzip, parse into TrackPointEntity rows.
        val track = drive.trackUrl?.let { downloadTrack(driveId, it) }.orEmpty()

        // Waypoints + their attached photo URLs.
        val wpDocs = firestore.collection("drives").document(driveId)
            .collection("waypoints").get().await()
        val waypoints = mutableListOf<WaypointEntity>()
        val photos = mutableListOf<WaypointPhotoEntity>()
        for (wp in wpDocs.documents) {
            if (wp.getBoolean("hidden") == true) continue
            val lat = wp.getDouble("lat") ?: continue
            val lng = wp.getDouble("lng") ?: continue
            waypoints += WaypointEntity(
                id = wp.id,
                driveId = driveId,
                lat = lat,
                lng = lng,
                recordedAt = wp.getLong("recordedAt") ?: startedAt,
                note = wp.getString("note"),
                vehicleReqs = null,
                syncState = SyncState.SYNCED.name,
            )
            val urls = (wp.get("photoUrls") as? List<*>)?.filterIsInstance<String>().orEmpty()
            for ((idx, url) in urls.withIndex()) {
                photos += WaypointPhotoEntity(
                    id = "${wp.id}-rehydrated-$idx",
                    waypointId = wp.id,
                    localPath = null, // not on disk; UI loads from remoteUrl via Coil
                    remoteUrl = url,
                    width = null, height = null,
                    takenAt = wp.getLong("recordedAt") ?: startedAt,
                    syncState = SyncState.SYNCED.name,
                )
            }
        }

        driveRepo.rehydrateDrive(drive, track, waypoints, photos)
    }

    /**
     * Downloads the gzipped GeoJSON track at [trackUrl] and parses it into
     * [TrackPointEntity] rows. Skips the track if the download fails — the drive will
     * still be visible locally, just without a polyline; a later sync run can retry.
     *
     * Note this hits the same defensive download path as PublicDriveRepository (HTTPS-only,
     * size cap), but inlined here to avoid coupling the sync layer to the public-drive repo.
     */
    private suspend fun downloadTrack(driveId: String, trackUrl: String): List<TrackPointEntity> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val parsed = URL(trackUrl)
                require(parsed.protocol.equals("https", ignoreCase = true)) { "non-https" }
                val conn = parsed.openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "GET"
                    conn.connectTimeout = 30_000
                    conn.readTimeout = 30_000
                    check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    conn.inputStream.use { input ->
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            total += n
                            check(total <= MAX_TRACK_BYTES) { "track > $MAX_TRACK_BYTES" }
                            out.write(buf, 0, n)
                        }
                    }
                    val text = ByteArrayInputStream(out.toByteArray()).use { gz ->
                        GZIPInputStream(gz).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                    val parsedTrack = LENIENT_JSON.decodeFromString(TrackGeoJson.serializer(), text)
                    val feature = parsedTrack.features.firstOrNull()
                        ?: return@withContext emptyList<TrackPointEntity>()
                    val coords = feature.geometry.coordinates
                    val recordedAt = feature.properties.recordedAt
                    coords.mapIndexed { i, c ->
                        TrackPointEntity(
                            driveId = driveId,
                            seq = i,
                            lat = c.getOrNull(1) ?: 0.0,
                            lng = c.getOrNull(0) ?: 0.0,
                            alt = c.getOrNull(2),
                            speed = null,
                            accuracy = null,
                            recordedAt = recordedAt.getOrNull(i) ?: 0L,
                        )
                    }
                } finally {
                    conn.disconnect()
                }
            }
        }.onFailure { Log.w(TAG, "downloadTrack: failed for one drive") }
            .getOrDefault(emptyList())
    }

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
        // Pin Content-Type to application/gzip so storage rules can rely on the type
        // check rather than letting the SDK infer the value. The storage rule
        // (firebase/storage.rules:33) restricts uploads to gzip/x-gzip/octet-stream;
        // matching that explicitly closes the door on a future SDK changing its
        // mime-detection heuristics in a way that bypasses the rule.
        val metadata = StorageMetadata.Builder()
            .setContentType("application/gzip")
            .build()
        ref.putBytes(bytes, metadata).await()
        val url = ref.downloadUrl.await()
        return url.toString() to bytes.size.toLong()
    }

    private companion object {
        const val MAX_TRACK_BYTES = 5L * 1024 * 1024
    }

    private suspend fun uploadPhoto(uid: String, driveId: String, photo: WaypointPhotoEntity): String? {
        // Rehydrated photos have no local file — they already live in the cloud.
        val localPath = photo.localPath ?: return null
        val file = File(localPath).takeIf { it.exists() } ?: return null
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
    put("vehicleSummary", vehicleSummary?.let { Json.encodeToString(com.senikroute.data.model.VehicleSummary.serializer(), it) })
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
    put("commentsEnabled", commentsEnabled)
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
            Json.encodeToString(com.senikroute.data.model.VehicleReqs.serializer(), it),
        )
    }
}

