package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.data.repository.ProfileSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface PeriodicSyncOperations {
    fun currentUserIdOrNull(): String?
    suspend fun syncTrips(context: Context, fullSync: Boolean)
    suspend fun syncProfiles(context: Context)
}

private object DefaultPeriodicSyncOperations : PeriodicSyncOperations {
    override fun currentUserIdOrNull(): String? = CurrentUserProvider.currentUserIdOrNull()

    override suspend fun syncTrips(context: Context, fullSync: Boolean) {
        val database = AppDatabase.getDatabase(context)
        LocalTripRepository(context, database.tripDao()).syncFromFirestore(fullSync = fullSync)
    }

    override suspend fun syncProfiles(context: Context) {
        ProfileSyncRepository(context).refreshSharedProfiles()
    }
}

class PeriodicSyncWorker internal constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val syncOperations: PeriodicSyncOperations
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters
    ) : this(appContext, workerParams, DefaultPeriodicSyncOperations)

    companion object {
        internal const val MAX_RETRY_ATTEMPTS = 5
        private const val TAG = "PeriodicSyncWorker"
        private const val SYNC_PREFS = "sync_prefs"
        private const val KEY_LAST_FULL_SYNC_TIMESTAMP = "last_full_sync_timestamp"
        private const val FULL_SYNC_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            return@withContext permanentFailure("periodic sync retry limit reached")
        }

        return@withContext try {
            val userId = syncOperations.currentUserIdOrNull() ?: run {
                Log.i(TAG, "Periodic sync skipped: no authenticated user")
                return@withContext Result.success()
            }
            Log.d(TAG, "Starting periodic sync from Firestore")
            val prefs = applicationContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val scopedFullSyncKey = "$KEY_LAST_FULL_SYNC_TIMESTAMP:$userId"
            val lastFullSync = prefs.getLong(scopedFullSyncKey, 0L)
            val shouldRunFullSync = lastFullSync <= 0L || now - lastFullSync >= FULL_SYNC_INTERVAL_MS

            syncOperations.syncTrips(applicationContext, fullSync = shouldRunFullSync)
            syncOperations.syncProfiles(applicationContext)
            if (shouldRunFullSync) {
                if (syncOperations.currentUserIdOrNull() == userId) {
                    prefs.edit().putLong(scopedFullSyncKey, now).apply()
                }
            }
            Log.d(TAG, "Periodic sync from Firestore completed; fullSync=$shouldRunFullSync")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Periodic sync failed", e)
            retryOrFailure("periodic sync failed", e)
        }
    }

    private fun retryOrFailure(reason: String, throwable: Throwable? = null): Result =
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            permanentFailure(reason, throwable)
        } else {
            Result.retry()
        }

    private fun permanentFailure(reason: String, throwable: Throwable? = null): Result {
        Log.e(
            TAG,
            "$reason; permanently failing after $runAttemptCount retry attempt(s)",
            throwable
        )
        return Result.failure()
    }
}
