package com.senikroute.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "waypoint_photos",
    indices = [Index(value = ["waypointId"])],
    foreignKeys = [
        ForeignKey(
            entity = WaypointEntity::class,
            parentColumns = ["id"],
            childColumns = ["waypointId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class WaypointPhotoEntity(
    @PrimaryKey val id: String,
    val waypointId: String,
    val localPath: String,
    val remoteUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val takenAt: Long,
    val syncState: String,
)
