package com.scenicroute.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.scenicroute.data.db.entities.DriveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveDao {

    @Query("SELECT * FROM drives WHERE ownerUid = :uid AND deletedAt IS NULL ORDER BY startedAt DESC")
    fun observeAllForOwner(uid: String): Flow<List<DriveEntity>>

    @Query("SELECT * FROM drives WHERE ownerUid = :uid AND deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(uid: String): Flow<List<DriveEntity>>

    @Query("SELECT COUNT(*) FROM drives WHERE ownerUid = :uid AND deletedAt IS NOT NULL")
    fun observeTrashedCount(uid: String): Flow<Int>

    @Query("SELECT * FROM drives WHERE id = :id")
    fun observe(id: String): Flow<DriveEntity?>

    @Query("SELECT * FROM drives WHERE id = :id")
    suspend fun getById(id: String): DriveEntity?

    @Query("SELECT * FROM drives WHERE status = 'RECORDING' AND ownerUid = :uid AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveForOwner(uid: String): DriveEntity?

    @Query("SELECT * FROM drives WHERE ownerUid = :uid AND status != 'RECORDING' AND syncState IN ('LOCAL', 'DIRTY')")
    suspend fun getPendingSync(uid: String): List<DriveEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(drive: DriveEntity)

    @Update
    suspend fun update(drive: DriveEntity)

    @Query("DELETE FROM drives WHERE id = :id")
    suspend fun delete(id: String)
}
