package com.senikroute.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.senikroute.data.db.entities.WaypointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {

    @Query("SELECT * FROM waypoints WHERE driveId = :driveId ORDER BY recordedAt ASC")
    fun observe(driveId: String): Flow<List<WaypointEntity>>

    @Query("SELECT * FROM waypoints WHERE driveId = :driveId ORDER BY recordedAt ASC")
    suspend fun get(driveId: String): List<WaypointEntity>

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getById(id: String): WaypointEntity?

    @Query("SELECT * FROM waypoints WHERE driveId = :driveId AND syncState IN ('LOCAL', 'DIRTY')")
    suspend fun getPendingSync(driveId: String): List<WaypointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(waypoint: WaypointEntity)

    @Update
    suspend fun update(waypoint: WaypointEntity)

    @Query("DELETE FROM waypoints WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM waypoints WHERE driveId = :driveId AND (recordedAt < :startTimeMs OR recordedAt > :endTimeMs)")
    suspend fun getOutsideTimeRange(driveId: String, startTimeMs: Long, endTimeMs: Long): List<WaypointEntity>

    @Query("DELETE FROM waypoints WHERE driveId = :driveId AND (recordedAt < :startTimeMs OR recordedAt > :endTimeMs)")
    suspend fun deleteOutsideTimeRange(driveId: String, startTimeMs: Long, endTimeMs: Long)
}
