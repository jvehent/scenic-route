package com.senikroute.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_buffer",
    indices = [Index(value = ["recordedAt"])],
)
data class LocationBufferEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val lat: Double,
    val lng: Double,
    val alt: Double? = null,
    val speed: Float? = null,
    val accuracy: Float? = null,
    val recordedAt: Long,
)
