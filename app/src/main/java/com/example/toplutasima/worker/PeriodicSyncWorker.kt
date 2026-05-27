package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.data.repository.ProfileSyncRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PeriodicSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private companion object {
        const val SYNC_PREFS = "sync_prefs"
        const val KEY_LAST_FULL_SYNC_TIMESTAMP = "last_full_sync_timestamp"
        const val FULL_SYNC_INTERVAL_MS = 30L * 24L * 60L * 60L * 1000L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Log.d("PeriodicSyncWorker", "Starting periodic sync from Firestore")
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = LocalTripRepository(applicationContext, database.tripDao())
            val prefs = applicationContext.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val lastFullSync = prefs.getLong(KEY_LAST_FULL_SYNC_TIMESTAMP, 0L)
            val shouldRunFullSync = lastFullSync <= 0L || now - lastFullSync >= FULL_SYNC_INTERVAL_MS

            repository.syncFromFirestore(fullSync = shouldRunFullSync)
            ProfileSyncRepository(applicationContext).refreshSharedProfiles()
            if (shouldRunFullSync) {
                prefs.edit().putLong(KEY_LAST_FULL_SYNC_TIMESTAMP, now).apply()
            }
            Log.d("PeriodicSyncWorker", "Periodic sync from Firestore completed; fullSync=$shouldRunFullSync")
            Result.success()
        } catch (e: Exception) {
            Log.e("PeriodicSyncWorker", "Periodic sync failed", e)
            Result.retry()
        }
    }
}
