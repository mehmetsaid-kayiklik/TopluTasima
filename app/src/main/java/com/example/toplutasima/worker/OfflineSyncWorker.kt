package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.transit.sync.TransitOfflineQueueStatusAdapter

internal interface OfflineQueueSyncOperations {
    suspend fun drain(context: Context): Int
    fun pendingCount(context: Context): Int
}

private object DefaultOfflineQueueSyncOperations : OfflineQueueSyncOperations {
    override suspend fun drain(context: Context): Int = OfflineQueueStore.drain(context)

    override fun pendingCount(context: Context): Int = OfflineQueueStore.pendingCount(context)
}

class OfflineSyncWorker internal constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val queueOperations: OfflineQueueSyncOperations
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters
    ) : this(appContext, workerParams, DefaultOfflineQueueSyncOperations)

    companion object {
        internal const val MAX_RETRY_ATTEMPTS = 5
        private const val TAG = "OfflineSyncWorker"
    }

    override suspend fun doWork(): Result {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            return permanentFailure("offline queue sync retry limit reached")
        }

        return try {
            val synced = queueOperations.drain(applicationContext)
            val remaining = queueOperations.pendingCount(applicationContext)
            Log.d(TAG, "synced=$synced remaining=$remaining")
            if (remaining > 0) {
                retryOrFailure("offline queue still has $remaining pending item(s)")
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "offline queue sync failed: ${e.message}", e)
            retryOrFailure("offline queue sync failed", e)
        }
    }

    private fun retryOrFailure(reason: String, throwable: Throwable? = null): Result =
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            permanentFailure(reason, throwable)
        } else {
            TransitOfflineQueueStatusAdapter.onWorkerRetry(applicationContext, reason)
            Result.retry()
        }

    private fun permanentFailure(reason: String, throwable: Throwable? = null): Result {
        TransitOfflineQueueStatusAdapter.onWorkerPermanentFailure(applicationContext, reason)
        Log.e(
            TAG,
            "$reason; permanently failing after $runAttemptCount retry attempt(s)",
            throwable
        )
        return Result.failure()
    }
}
