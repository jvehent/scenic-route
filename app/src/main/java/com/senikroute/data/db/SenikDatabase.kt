package com.senikroute.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.senikroute.data.db.dao.DriveDao
import com.senikroute.data.db.dao.LocationBufferDao
import com.senikroute.data.db.dao.TrackPointDao
import com.senikroute.data.db.dao.WaypointDao
import com.senikroute.data.db.dao.WaypointPhotoDao
import com.senikroute.data.db.entities.DriveEntity
import com.senikroute.data.db.entities.LocationBufferEntity
import com.senikroute.data.db.entities.TrackPointEntity
import com.senikroute.data.db.entities.WaypointEntity
import com.senikroute.data.db.entities.WaypointPhotoEntity

@Database(
    entities = [
        DriveEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
        WaypointPhotoEntity::class,
        LocationBufferEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class SenikDatabase : RoomDatabase() {
    abstract fun driveDao(): DriveDao
    abstract fun trackPointDao(): TrackPointDao
    abstract fun waypointDao(): WaypointDao
    abstract fun waypointPhotoDao(): WaypointPhotoDao
    abstract fun locationBufferDao(): LocationBufferDao

    companion object {
        const val NAME = "senik.db"
    }
}
