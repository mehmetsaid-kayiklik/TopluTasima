package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.network.FirestoreService
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TransitActionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "TransitActionWorker"
        const val KEY_TRIP_ID = "tripId"
        const val KEY_IS_BOARDING = "isBoarding"
        const val KEY_TIMESTAMP = "timestamp" // We can capture the time when the user clicked the button
    }

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        val isBoarding = inputData.getBoolean(KEY_IS_BOARDING, true)
        val timestamp = inputData.getString(KEY_TIMESTAMP) ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        return try {
            if (isBoarding) {
                FirestoreService.updateActual(tripId, timestamp, null)
            } else {
                FirestoreService.updateActual(tripId, null, timestamp)
            }
            Log.d(TAG, "Successfully updated Firestore for trip $tripId (isBoarding=$isBoarding, time=$timestamp)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update Firestore, will retry: ${e.message}")
            Result.retry()
        }
    }
}
