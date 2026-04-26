package com.senikroute.data.discovery

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.senikroute.data.repo.encodeGeohash
import com.senikroute.data.repo.haversineMeters
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

private const val INDEX_PRECISION = 5

@Singleton
class DiscoveryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    suspend fun fetchFeatured(limit: Int = 5): List<DiscoveryDrive> {
        // The deletedAt==null filter is required by the security rule for /drives:
        // anonymous users may only read public, non-trashed docs, and Firestore
        // rejects the whole query unless it filters on every field the rule reads.
        val snap = runCatching {
            firestore.collection("drives")
                .whereEqualTo("visibility", "public")
                .whereEqualTo("deletedAt", null)
                .orderBy("updatedAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get()
                .await()
        }.onFailure { android.util.Log.w("DiscoveryRepo", "fetchFeatured failed", it) }
            .getOrNull() ?: return emptyList()
        return snap.documents.mapNotNull { doc ->
            val centroidLat = doc.getDouble("centroidLat") ?: return@mapNotNull null
            val centroidLng = doc.getDouble("centroidLng") ?: return@mapNotNull null
            DiscoveryDrive(
                driveId = doc.id,
                title = doc.getString("title").orEmpty(),
                distanceM = (doc.getLong("distanceM") ?: 0L).toInt(),
                durationS = (doc.getLong("durationS") ?: 0L).toInt(),
                centroidLat = centroidLat,
                centroidLng = centroidLng,
                coverPhotoUrl = doc.getString("coverPhotoUrl"),
                tags = (doc.get("tags") as? List<*>)?.filterIsInstance<String>().orEmpty(),
                ownerAnonHandle = doc.getString("ownerAnonHandle"),
                trackUrl = doc.getString("trackUrl"),
                distanceFromUserKm = 0.0,
            )
        }
    }

    suspend fun findNearby(
        lat: Double,
        lng: Double,
        radiusKm: Int,
    ): List<DiscoveryDrive> {
        val prefixes = neighborPrefixes(lat, lng)
        val results = mutableListOf<DiscoveryDrive>()
        for (prefix in prefixes) {
            val snap = runCatching {
                firestore.collection("public_drives_index")
                    .document(prefix)
                    .collection("drives")
                    .get()
                    .await()
            }.getOrNull() ?: continue
            snap.documents.forEach { doc ->
                val drive = doc.toDiscoveryDrive(lat, lng) ?: return@forEach
                if (drive.distanceFromUserKm <= radiusKm) results += drive
            }
        }
        return results
            .distinctBy { it.driveId }
            .sortedBy { it.distanceFromUserKm }
    }

    private fun neighborPrefixes(lat: Double, lng: Double): List<String> {
        // 3x3 grid of geohash boxes around the user at INDEX_PRECISION. Each box is
        // ~4.9km x 4.9km at precision 5, so this covers roughly 15km x 15km — enough
        // for radii up to ~7km. For larger radii we'd step up to precision 4 (~40km
        // per box) — punt on that until we observe it mattering.
        val box = boxSizeDeg(INDEX_PRECISION)
        val dLat = box.first
        val dLng = box.second
        val out = linkedSetOf<String>()
        for (dy in -1..1) for (dx in -1..1) {
            val hlat = (lat + dy * dLat).coerceIn(-90.0, 90.0)
            val hlng = normalizeLng(lng + dx * dLng)
            out += encodeGeohash(hlat, hlng, INDEX_PRECISION)
        }
        return out.toList()
    }

    private fun boxSizeDeg(precision: Int): Pair<Double, Double> {
        // At precision 5, each geohash cell is approximately 0.044° lat x 0.044° lng near the equator.
        // Using a single representative value is fine for neighbor expansion.
        val step = when (precision) {
            5 -> 0.044
            4 -> 0.18
            else -> 0.044
        }
        return step to step
    }

    private fun normalizeLng(lng: Double): Double {
        var l = lng
        while (l > 180) l -= 360
        while (l < -180) l += 360
        return l
    }

    @Suppress("UNCHECKED_CAST")
    private fun com.google.firebase.firestore.DocumentSnapshot.toDiscoveryDrive(
        userLat: Double,
        userLng: Double,
    ): DiscoveryDrive? {
        val centroidLat = getDouble("centroidLat") ?: return null
        val centroidLng = getDouble("centroidLng") ?: return null
        val distKm = haversineMeters(userLat, userLng, centroidLat, centroidLng) / 1000.0
        return DiscoveryDrive(
            driveId = id,
            title = getString("title") ?: "",
            distanceM = (getLong("distanceM") ?: 0L).toInt(),
            durationS = (getLong("durationS") ?: 0L).toInt(),
            centroidLat = centroidLat,
            centroidLng = centroidLng,
            coverPhotoUrl = getString("coverPhotoUrl"),
            tags = (get("tags") as? List<String>).orEmpty(),
            ownerAnonHandle = getString("ownerAnonHandle"),
            trackUrl = getString("trackUrl"),
            distanceFromUserKm = distKm,
        )
    }
}
