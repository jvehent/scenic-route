package com.scenicroute.di

import android.content.Context
import androidx.room.Room
import com.scenicroute.data.db.ScenicDatabase
import com.scenicroute.data.db.dao.DriveDao
import com.scenicroute.data.db.dao.LocationBufferDao
import com.scenicroute.data.db.dao.TrackPointDao
import com.scenicroute.data.db.dao.WaypointDao
import com.scenicroute.data.db.dao.WaypointPhotoDao
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
    fun provideDatabase(@ApplicationContext ctx: Context): ScenicDatabase =
        Room.databaseBuilder(ctx, ScenicDatabase::class.java, ScenicDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideDriveDao(db: ScenicDatabase): DriveDao = db.driveDao()
    @Provides fun provideTrackPointDao(db: ScenicDatabase): TrackPointDao = db.trackPointDao()
    @Provides fun provideWaypointDao(db: ScenicDatabase): WaypointDao = db.waypointDao()
    @Provides fun provideWaypointPhotoDao(db: ScenicDatabase): WaypointPhotoDao = db.waypointPhotoDao()
    @Provides fun provideLocationBufferDao(db: ScenicDatabase): LocationBufferDao = db.locationBufferDao()
}
