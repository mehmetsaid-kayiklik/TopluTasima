package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.data.AppEventBus
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import com.example.toplutasima.repository.TransitRecordRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class TransitActionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val repository: TransitRecordRepository
) : CoroutineWorker(appContext, workerParams) {

    init {
        TransitTrackerLogger.log(
            appContext,
            "TransitActionWorker",
            "CONSTRUCTOR called — instance created"
        )

        // Defensive check: repository is non-null by Kotlin's type system,
        // but Koin DI might inject a Java-null via unchecked cast.
        @Suppress("SENSELESS_COMPARISON")
        if (repository == null) {
            val msg = "CRITICAL: repository is null at construction! DI injection failed."
            Log.e(TAG, msg)
            TransitTrackerLogger.log(appContext, TAG, msg)
            throw IllegalStateException(msg)
        }
        Log.d(TAG, "TransitActionWorker constructed — repository=$repository")
        TransitTrackerLogger.log(appContext, TAG, "TransitActionWorker constructed OK")
    }

    companion object {
        const val TAG = "TransitActionWorker"
        const val KEY_TRIP_ID = "tripId"
        const val KEY_IS_BOARDING = "isBoarding"
        const val KEY_TIMESTAMP = "timestamp"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "doWork() entered")
        TransitTrackerLogger.log(applicationContext, TAG, "doWork() entered")

        val tripId = inputData.getString(KEY_TRIP_ID) ?: return Result.failure()
        val isBoarding = inputData.getBoolean(KEY_IS_BOARDING, true)
        val msgStart = "doWork started: tripId=$tripId isBoarding=$isBoarding"
        Log.d(TAG, msgStart)
        TransitTrackerLogger.log(applicationContext, TAG, msgStart)

        val timestamp = inputData.getString(KEY_TIMESTAMP)
            ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        return try {
            val updated = if (isBoarding) {
                repository.updateActual(tripId, timestamp, null)
            } else {
                repository.updateActual(tripId, null, timestamp)
            }
            val msgResult = "updateActual result: $updated for tripId=$tripId"
            Log.d(TAG, msgResult)
            TransitTrackerLogger.log(applicationContext, TAG, msgResult)

            if (!updated) {
                val msgNotFound = "updateActual returned false — document not found for id=$tripId"
                Log.w(TAG, msgNotFound)
                TransitTrackerLogger.log(applicationContext, TAG, msgNotFound)

                val msgFailed = "updateActual basarisiz: trip=$tripId isBoarding=$isBoarding time=$timestamp — Result.retry()"
                Log.w(TAG, msgFailed)
                TransitTrackerLogger.log(applicationContext, TAG, msgFailed)

                return Result.retry()
            }
            val msgSuccess = "Yolculuk zamani islendi: trip=$tripId isBoarding=$isBoarding time=$timestamp"
            Log.d(TAG, msgSuccess)
            TransitTrackerLogger.log(applicationContext, TAG, msgSuccess)

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
            val msgError = "Firestore yazılamadı, yeniden deneniyor: ${e.message}"
            Log.e(TAG, msgError)
            TransitTrackerLogger.log(applicationContext, TAG, msgError)
            Result.retry()
        }
    }
}
