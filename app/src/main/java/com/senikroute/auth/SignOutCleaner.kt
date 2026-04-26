package com.senikroute.auth

import android.content.Context
import androidx.work.WorkManager
import com.senikroute.data.db.SenikDatabase
import com.senikroute.data.storage.PhotoStorage
import com.senikroute.recording.BufferController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wipes everything that would otherwise leak across an account switch on the same device:
 *  - local Room DB (drives, track points, waypoints, photos, location buffer)
 *  - photo files on disk
 *  - any pending sync WorkManager jobs
 *  - in-flight buffer service
 *
 * Called from AuthRepository.signOut() before Firebase sign-out flips the auth state.
 */
@Singleton
class SignOutCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: SenikDatabase,
    private val photos: PhotoStorage,
    private val bufferController: BufferController,
) {
    suspend fun wipeAll() = withContext(Dispatchers.IO) {
        bufferController.stop()
        WorkManager.getInstance(context).cancelAllWork()

        // Drops all rows in every Room table — covers drives, track_points, waypoints,
        // waypoint_photos, location_buffer in one call.
        db.clearAllTables()

        // Wipe photo files on disk so account-switch can't reveal them via FileProvider.
        runCatching {
            photos.photosDir.listFiles()?.forEach { it.delete() }
        }
    }
}
