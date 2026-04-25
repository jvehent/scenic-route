package com.scenicroute.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_points",
    primaryKeys = ["driveId", "seq"],
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
data class TrackPointEntity(
    val driveId: String,
    val seq: Int,
    val lat: Double,
    val lng: Double,
    val alt: Double? = null,
    val speed: Float? = null,
    val accuracy: Float? = null,
    val recordedAt: Long,
)
