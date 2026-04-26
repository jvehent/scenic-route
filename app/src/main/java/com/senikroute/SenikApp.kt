package com.senikroute

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.senikroute.data.profile.ProfileRepository
import com.senikroute.recording.BufferController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SenikApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var bufferController: BufferController
    @Inject lateinit var profileRepository: ProfileRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        bufferController.observeSettings()
        profileRepository.observeAuthAndBootstrap()
    }
}
