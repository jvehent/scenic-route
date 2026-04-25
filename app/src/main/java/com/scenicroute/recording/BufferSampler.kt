package com.scenicroute.recording

import android.location.Location
import com.scenicroute.data.db.entities.LocationBufferEntity
import com.scenicroute.data.repo.haversineMeters

class BufferSampler(
    private val minDisplacementM: Double = 30.0,
    private val maxAccuracyM: Float = 50f,
) {
    private var last: Location? = null

    fun accept(fix: Location): LocationBufferEntity? {
        if (fix.hasAccuracy() && fix.accuracy > maxAccuracyM) return null
        val prev = last
        if (prev != null) {
            val dist = haversineMeters(prev.latitude, prev.longitude, fix.latitude, fix.longitude)
            if (dist < minDisplacementM) return null
        }
        last = fix
        return LocationBufferEntity(
            lat = fix.latitude,
            lng = fix.longitude,
            alt = if (fix.hasAltitude()) fix.altitude else null,
            speed = if (fix.hasSpeed()) fix.speed else null,
            accuracy = if (fix.hasAccuracy()) fix.accuracy else null,
            recordedAt = fix.time,
        )
    }

    fun reset() { last = null }
}
