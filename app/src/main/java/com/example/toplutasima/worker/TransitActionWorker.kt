package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.data.AppEventBus
import com.example.toplutasima.repository.TransitRecordRepository
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
        const val KEY_TIMESTAMP = "timestamp"
    }

    override suspend fun doWork(): Result {
        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        val isBoarding = inputData.getBoolean(KEY_IS_BOARDING, true)
        val timestamp = inputData.getString(KEY_TIMESTAMP)
            ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        return try {
            val repository = TransitRecordRepository(applicationContext)
            val updated = if (isBoarding) {
                repository.updateActual(tripId, timestamp, null)
            } else {
                repository.updateActual(tripId, null, timestamp)
            }
            if (!updated) {
                Log.w(TAG, "updateActual basarisiz: trip=$tripId isBoarding=$isBoarding time=$timestamp — Result.failure()")
                return Result.failure()
            }
            Log.d(TAG, "Yolculuk zamani islendi: trip=$tripId isBoarding=$isBoarding time=$timestamp")

            // UI senkronizasyonu için AppEventBus'a emit et
            AppEventBus.emit(
                AppEventBus.Event.TripSynced(
                    tripId = tripId,
                    isBoarding = isBoarding,
                    timestamp = timestamp
                )
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Firestore yazılamadı, yeniden deneniyor: ${e.message}")
            Result.retry()
        }
    }
}
