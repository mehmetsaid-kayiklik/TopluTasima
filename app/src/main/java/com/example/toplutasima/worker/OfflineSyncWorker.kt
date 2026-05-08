package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.data.OfflineQueueStore

class OfflineSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val synced = OfflineQueueStore.drain(applicationContext)
            val remaining = OfflineQueueStore.pendingCount(applicationContext)
            Log.d("OfflineSyncWorker", "synced=$synced remaining=$remaining")
            if (remaining > 0) Result.retry() else Result.success()
        } catch (e: Exception) {
            Log.e("OfflineSyncWorker", "offline queue sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
