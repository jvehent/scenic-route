package com.senikroute.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.senikroute.data.sync.TrackGeoJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton

private val json = Json { ignoreUnknownKeys = true }

@Singleton
class PublicDriveRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun fetchDrive(driveId: String): RemoteDrive? {
        val doc = firestore.collection("drives").document(driveId).get().await()
        if (!doc.exists()) return null
        return RemoteDrive(
            id = doc.id,
            ownerUid = doc.getString("ownerUid"),
            ownerAnonHandle = doc.getString("ownerAnonHandle"),
            title = doc.getString("title").orEmpty(),
            description = doc.getString("description").orEmpty(),
            visibility = doc.getString("visibility").orEmpty(),
            distanceM = (doc.getLong("distanceM") ?: 0L).toInt(),
            durationS = (doc.getLong("durationS") ?: 0L).toInt(),
            startedAt = doc.getLong("startedAt") ?: 0L,
            endedAt = doc.getLong("endedAt"),
            tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            coverPhotoUrl = doc.getString("coverPhotoUrl"),
            startLat = doc.getDouble("startLat"),
            startLng = doc.getDouble("startLng"),
            endLat = doc.getDouble("endLat"),
            endLng = doc.getDouble("endLng"),
            centroidLat = doc.getDouble("centroidLat"),
            centroidLng = doc.getDouble("centroidLng"),
            trackUrl = doc.getString("trackUrl"),
            commentsEnabled = doc.getBoolean("commentsEnabled") ?: false,
        )
    }

    suspend fun fetchWaypoints(driveId: String): List<RemoteWaypoint> {
        val snap = firestore.collection("drives")
            .document(driveId)
            .collection("waypoints")
            .get()
            .await()
        return snap.documents
            .filter { it.getBoolean("hidden") != true }
            .mapNotNull { doc ->
                val lat = doc.getDouble("lat") ?: return@mapNotNull null
                val lng = doc.getDouble("lng") ?: return@mapNotNull null
                val photoUrls = (doc.get("photoUrls") as? List<*>)
                    ?.filterIsInstance<String>()
                    .orEmpty()
                RemoteWaypoint(
                    id = doc.id,
                    lat = lat,
                    lng = lng,
                    recordedAt = doc.getLong("recordedAt") ?: 0L,
                    note = doc.getString("note"),
                    photoUrls = photoUrls,
                    vehicleReqsJson = doc.getString("vehicleReqs"),
                )
            }
            .sortedBy { it.recordedAt }
    }

    suspend fun fetchTrack(trackUrl: String): List<RemoteTrackPoint> {
        val raw = runCatching { downloadBytes(trackUrl) }
            .onFailure { android.util.Log.w("PublicDriveRepo", "fetchTrack: download failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrThrow()
        android.util.Log.w("PublicDriveRepo", "fetchTrack: downloaded ${raw.size} bytes")
        val text = runCatching { gunzip(raw) }
            .onFailure { android.util.Log.w("PublicDriveRepo", "fetchTrack: gunzip failed: ${it.message}; first16=${raw.take(16).joinToString(",") { b -> b.toUByte().toString() }}") }
            .getOrThrow()
        android.util.Log.w("PublicDriveRepo", "fetchTrack: gunzipped to ${text.length} chars; head=${text.take(120)}")
        val parsed = runCatching { json.decodeFromString(TrackGeoJson.serializer(), text) }
            .onFailure { android.util.Log.w("PublicDriveRepo", "fetchTrack: parse failed: ${it.message}") }
            .getOrThrow()
        val feature = parsed.features.firstOrNull()
        if (feature == null) {
            android.util.Log.w("PublicDriveRepo", "fetchTrack: no features in collection")
            return emptyList()
        }
        val coords = feature.geometry.coordinates
        val recordedAt = feature.properties.recordedAt
        android.util.Log.w("PublicDriveRepo", "fetchTrack: parsed ${coords.size} coords, ${recordedAt.size} timestamps")
        return coords.mapIndexed { i, c ->
            RemoteTrackPoint(
                seq = i,
                lng = c.getOrNull(0) ?: 0.0,
                lat = c.getOrNull(1) ?: 0.0,
                alt = c.getOrNull(2),
                recordedAt = recordedAt.getOrNull(i),
            )
        }
    }

    private suspend fun downloadBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        // Log the URL prefix (host + path) without the auth token query string so PII /
        // tokens stay out of production logs but we can see if the URL looks right.
        val urlForLog = url.substringBefore('?').take(120)
        android.util.Log.w("PublicDriveRepo", "downloadBytes: GET $urlForLog")
        val parsed = URL(url)
        // Only follow https. Block file://, ftp://, http:// — defense vs SSRF + plaintext.
        require(parsed.protocol.equals("https", ignoreCase = true)) {
            "refusing to download non-https url"
        }
        val conn = parsed.openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 30_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            conn.connect()
            android.util.Log.w("PublicDriveRepo", "downloadBytes: HTTP ${conn.responseCode}, content-type=${conn.contentType}, content-encoding=${conn.contentEncoding}, content-length=${conn.contentLengthLong}")
            check(conn.responseCode in 200..299) { "HTTP ${conn.responseCode} fetching track" }
            // Reject obviously oversized payloads up front via Content-Length.
            val cl = conn.contentLengthLong
            check(cl < 0 || cl <= MAX_TRACK_DOWNLOAD_BYTES) {
                "track too large: $cl bytes"
            }
            // Cap the actual bytes read in case Content-Length lies.
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            conn.inputStream.use { input ->
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    total += n
                    check(total <= MAX_TRACK_DOWNLOAD_BYTES) { "track exceeded cap mid-stream" }
                    out.write(buf, 0, n)
                }
            }
            out.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val MAX_TRACK_DOWNLOAD_BYTES = 10L * 1024 * 1024
    }

    /**
     * Decompress [bytes] if they begin with the gzip magic header (0x1f 0x8b); otherwise
     * treat them as already-decompressed text. Android's HttpURLConnection (OkHttp under
     * the hood) auto-decompresses responses with Content-Encoding: gzip — Firebase Storage
     * sometimes sends that header, sometimes doesn't, depending on bucket / file metadata.
     * Sniffing the magic byte is robust to either behavior.
     */
    private fun gunzip(bytes: ByteArray): String {
        val isGzipped = bytes.size >= 2 &&
            bytes[0] == 0x1F.toByte() &&
            bytes[1] == 0x8B.toByte()
        return if (isGzipped) {
            ByteArrayInputStream(bytes).use { input ->
                GZIPInputStream(input).bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } else {
            android.util.Log.w("PublicDriveRepo", "gunzip: response not gzipped (likely auto-decompressed by HttpURLConnection)")
            String(bytes, Charsets.UTF_8)
        }
    }
}
