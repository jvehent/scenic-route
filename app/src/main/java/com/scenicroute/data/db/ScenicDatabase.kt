package com.scenicroute.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.scenicroute.data.db.dao.DriveDao
import com.scenicroute.data.db.dao.LocationBufferDao
import com.scenicroute.data.db.dao.TrackPointDao
import com.scenicroute.data.db.dao.WaypointDao
import com.scenicroute.data.db.dao.WaypointPhotoDao
import com.scenicroute.data.db.entities.DriveEntity
import com.scenicroute.data.db.entities.LocationBufferEntity
import com.scenicroute.data.db.entities.TrackPointEntity
import com.scenicroute.data.db.entities.WaypointEntity
import com.scenicroute.data.db.entities.WaypointPhotoEntity

@Database(
    entities = [
        DriveEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
        WaypointPhotoEntity::class,
        LocationBufferEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ScenicDatabase : RoomDatabase() {
    abstract fun driveDao(): DriveDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun waypointDao(): WaypointDao
    abstract fun waypointPhotoDao(): WaypointPhotoDao
    abstract fun locationBufferDao(): LocationBufferDao

    companion object {
        const val NAME = "scenic.db"
    }
}
