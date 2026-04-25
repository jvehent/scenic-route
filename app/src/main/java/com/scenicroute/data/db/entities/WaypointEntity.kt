package com.scenicroute.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.scenicroute.data.model.VehicleReqs

@Entity(
    tableName = "waypoints",
    indices = [Index(value = ["driveId"])],
    foreignKeys = [
        ForeignKey(
            entity = DriveEntity::class,
            parentColumns = ["id"],
            childColumns = ["driveId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WaypointEntity(
    @PrimaryKey val id: String,
    val driveId: String,
    val lat: Double,
    val lng: Double,
    val recordedAt: Long,
    val note: String? = null,
    val vehicleReqs: VehicleReqs? = null,
    val syncState: String,
)
