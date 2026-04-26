package com.senikroute.recording

import android.location.Location
import com.senikroute.data.repo.DriveRepository.PendingTrackPoint
import com.senikroute.data.repo.haversineMeters
import kotlin.math.abs

class LocationSampler(
    private val minIntervalMs: Long = 2_000,
    private val minDisplacementM: Double = 10.0,
    private val headingChangeDeg: Float = 20f,
    private val maxAccuracyM: Float = 30f,
) {
    private var lastAccepted: Location? = null

    fun accept(fix: Location): PendingTrackPoint? {
        if (fix.hasAccuracy() && fix.accuracy > maxAccuracyM && lastAccepted?.accuracy?.let { it <= maxAccuracyM } == true) {
            return null
        }

        val prev = lastAccepted
        if (prev != null) {
            val dt = fix.time - prev.time
            if (dt < minIntervalMs) return null

            val dist = haversineMeters(prev.latitude, prev.longitude, fix.latitude, fix.longitude)
            val headingChanged = if (fix.hasBearing() && prev.hasBearing()) {
                abs(deltaDegrees(prev.bearing, fix.bearing)) >= headingChangeDeg
            } else false

            if (dist < minDisplacementM && !headingChanged) return null
        }

        lastAccepted = fix
        return PendingTrackPoint(
            lat = fix.latitude,
            lng = fix.longitude,
            alt = if (fix.hasAltitude()) fix.altitude else null,
            speed = if (fix.hasSpeed()) fix.speed else null,
            accuracy = if (fix.hasAccuracy()) fix.accuracy else null,
            recordedAt = fix.time,
        )
    }

    fun reset() { lastAccepted = null }

    private fun deltaDegrees(a: Float, b: Float): Float {
        var d = (b - a + 540f) % 360f - 180f
        if (d < -180f) d += 360f
        return d
    }
}
