package com.senikroute.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val firestoreSync: FirestoreSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "doWork: starting attempt=$runAttemptCount")
        return try {
            // Push first so any local edits land server-side before we look at the server's
            // view of the world. Then pull any owned drives missing from the local DB
            // (this is what makes "reinstall + sign back in" actually recover prior drives).
            val pushed = firestoreSync.syncAll()
            val pulled = firestoreSync.pullOwnedDrives()
            Log.i(TAG, "doWork: success pushed=$pushed pulled=$pulled")
            Result.success()
        } catch (t: Throwable) {
            Log.e(TAG, "doWork: failed", t)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "senik-sync"
        private const val TAG = "SyncWorker"
    }
}
