package com.scenicroute

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.scenicroute.data.profile.ProfileRepository
import com.scenicroute.recording.BufferController
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ScenicRouteApp : Application(), Configuration.Provider {

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
