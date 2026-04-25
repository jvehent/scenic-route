package com.scenicroute.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.scenicroute.data.db.entities.LocationBufferEntity

@Dao
interface LocationBufferDao {

    @Query("SELECT * FROM location_buffer WHERE recordedAt >= :sinceMs ORDER BY recordedAt ASC")
    suspend fun since(sinceMs: Long): List<LocationBufferEntity>

    @Query("SELECT COUNT(*) FROM location_buffer")
    suspend fun count(): Int

    @Query("SELECT MIN(recordedAt) FROM location_buffer")
    suspend fun oldestRecordedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: LocationBufferEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<LocationBufferEntity>)

    @Query("DELETE FROM location_buffer WHERE recordedAt < :cutoffMs")
    suspend fun prune(cutoffMs: Long)

    @Query("DELETE FROM location_buffer")
    suspend fun clear()
}
