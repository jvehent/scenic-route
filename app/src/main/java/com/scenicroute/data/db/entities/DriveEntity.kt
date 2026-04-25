package com.scenicroute.data.db.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scenicroute.data.model.BoundingBox
import com.scenicroute.data.model.VehicleSummary

@Entity(
    tableName = "drives",
    indices = [
        Index(value = ["ownerUid"]),
        Index(value = ["status"]),
        Index(value = ["startedAt"]),
    ],
)
data class DriveEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val title: String = "",
    val description: String = "",
    val status: String,
    val visibility: String,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double? = null,
    val endLng: Double? = null,
    val boundingBox: BoundingBox? = null,
    val distanceM: Int? = null,
    val durationS: Int? = null,
    val startedAt: Long,
    val endedAt: Long? = null,
    val coverWaypointId: String? = null,
    val tags: List<String> = emptyList(),
    val vehicleSummary: VehicleSummary? = null,
    val geohash: String? = null,
    val commentCount: Int = 0,
    val trackUrl: String? = null,
    val trackBytes: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncState: String,
    val serverVersion: Int? = null,
)
