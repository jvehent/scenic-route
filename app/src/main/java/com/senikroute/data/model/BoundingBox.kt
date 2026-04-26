package com.senikroute.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BoundingBox(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
) {
    fun contains(lat: Double, lng: Double): Boolean =
        lat in south..north && lng in west..east

    companion object {
        fun fromPoints(points: List<Pair<Double, Double>>): BoundingBox? {
            if (points.isEmpty()) return null
            var n = points[0].first; var s = n
            var e = points[0].second; var w = e
            for ((lat, lng) in points) {
                if (lat > n) n = lat
                if (lat < s) s = lat
                if (lng > e) e = lng
                if (lng < w) w = lng
            }
            return BoundingBox(n, s, e, w)
        }
    }
}
