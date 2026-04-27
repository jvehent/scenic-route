package com.senikroute.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.senikroute.data.db.entities.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {

    @Query("SELECT * FROM track_points WHERE driveId = :driveId ORDER BY seq ASC")
    fun observe(driveId: String): Flow<List<TrackPointEntity>>

    @Query("SELECT * FROM track_points WHERE driveId = :driveId ORDER BY seq ASC")
    suspend fun get(driveId: String): List<TrackPointEntity>

    @Query("SELECT COALESCE(MAX(seq), -1) FROM track_points WHERE driveId = :driveId")
    suspend fun maxSeq(driveId: String): Int

    @Query("SELECT COUNT(*) FROM track_points WHERE driveId = :driveId")
    suspend fun count(driveId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<TrackPointEntity>)

    @Query("DELETE FROM track_points WHERE driveId = :driveId")
    suspend fun deleteForDrive(driveId: String)

    @Query("DELETE FROM track_points WHERE driveId = :driveId AND (seq < :minSeq OR seq > :maxSeq)")
    suspend fun deleteOutsideSeqRange(driveId: String, minSeq: Int, maxSeq: Int)
}
