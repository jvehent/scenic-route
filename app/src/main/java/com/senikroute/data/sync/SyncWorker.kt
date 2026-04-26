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
            val n = firestoreSync.syncAll()
            Log.i(TAG, "doWork: success synced=$n")
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
