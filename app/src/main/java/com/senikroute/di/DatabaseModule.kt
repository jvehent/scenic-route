package com.senikroute.di

import android.content.Context
import androidx.room.Room
import com.senikroute.data.db.SenikDatabase
import com.senikroute.data.db.dao.DriveDao
import com.senikroute.data.db.dao.LocationBufferDao
import com.senikroute.data.db.dao.TrackPointDao
import com.senikroute.data.db.dao.WaypointDao
import com.senikroute.data.db.dao.WaypointPhotoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): SenikDatabase =
        Room.databaseBuilder(ctx, SenikDatabase::class.java, SenikDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideDriveDao(db: SenikDatabase): DriveDao = db.driveDao()
    @Provides fun provideTrackPointDao(db: SenikDatabase): TrackPointDao = db.trackPointDao()
    @Provides fun provideWaypointDao(db: SenikDatabase): WaypointDao = db.waypointDao()
    @Provides fun provideWaypointPhotoDao(db: SenikDatabase): WaypointPhotoDao = db.waypointPhotoDao()
    @Provides fun provideLocationBufferDao(db: SenikDatabase): LocationBufferDao = db.locationBufferDao()
}
