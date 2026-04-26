package com.senikroute.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.senikroute.data.db.entities.WaypointPhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointPhotoDao {

    @Query("""
        SELECT * FROM waypoint_photos
        WHERE waypointId IN (SELECT id FROM waypoints WHERE driveId = :driveId)
        ORDER BY takenAt ASC
    """)
    fun observeForDrive(driveId: String): Flow<List<WaypointPhotoEntity>>

    @Query("SELECT * FROM waypoint_photos WHERE waypointId = :waypointId ORDER BY takenAt ASC")
    suspend fun get(waypointId: String): List<WaypointPhotoEntity>

    @Query("SELECT * FROM waypoint_photos WHERE id = :id")
    suspend fun getById(id: String): WaypointPhotoEntity?

    @Query("""
        SELECT * FROM waypoint_photos
        WHERE waypointId IN (SELECT id FROM waypoints WHERE driveId = :driveId)
          AND syncState IN ('LOCAL', 'DIRTY')
    """)
    suspend fun getPendingSync(driveId: String): List<WaypointPhotoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: WaypointPhotoEntity)

    @Query("DELETE FROM waypoint_photos WHERE id = :id")
    suspend fun delete(id: String)
}
