package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.example.toplutasima.data.AppEventBus
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import com.example.toplutasima.repository.TransitRecordRepository
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal fun interface TransitActualUpdater {
    suspend fun updateActual(tripId: String, actualDeparture: String?, actualArrival: String?): Boolean
}

class TransitActionWorker internal constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val actualUpdater: TransitActualUpdater,
    dependencyDescription: Any
) : CoroutineWorker(appContext, workerParams) {

    constructor(
        appContext: Context,
        workerParams: WorkerParameters,
        repository: TransitRecordRepository
    ) : this(
        appContext = appContext,
        workerParams = workerParams,
        actualUpdater = TransitActualUpdater { tripId, actualDeparture, actualArrival ->
            repository.updateActual(tripId, actualDeparture, actualArrival)
        },
        dependencyDescription = repository
    )

    init {
        TransitTrackerLogger.log(
            appContext,
            TAG,
            "CONSTRUCTOR called - instance created"
        )

        Log.d(TAG, "TransitActionWorker constructed - repository=$dependencyDescription")
        TransitTrackerLogger.log(appContext, TAG, "TransitActionWorker constructed OK")
    }

    companion object {
        const val TAG = "TransitActionWorker"
        const val KEY_TRIP_ID = "tripId"
        const val KEY_IS_BOARDING = "isBoarding"
        const val KEY_TIMESTAMP = "timestamp"
        const val OUTPUT_FAILURE_REASON = "failureReason"
        const val OUTPUT_EXCEPTION_CLASS = "exceptionClass"
        const val OUTPUT_EXCEPTION_MESSAGE = "exceptionMessage"
        const val OUTPUT_STACK_TRACE = "stackTrace"
        internal const val MAX_RETRY_ATTEMPTS = 5
        private const val MAX_OUTPUT_STRING_LENGTH = 1_000
        private const val MAX_STACK_TRACE_OUTPUT_LENGTH = 7_000
    }

    override suspend fun doWork(): Result {
        return try {
            doWorkWithRetryHandling()
        } catch (e: Exception) {
            val msgError = "doWork() crashed: ${e::class.java.name}: ${e.message}"
            logException(msgError, e)
            Result.failure(failureOutputData(msgError, e))
        }
    }

    private suspend fun doWorkWithRetryHandling(): Result {
        Log.d(TAG, "doWork() entered")
        TransitTrackerLogger.log(applicationContext, TAG, "doWork() entered")

        val tripId = inputData.getString(KEY_TRIP_ID) ?: return missingInputFailure()
        val isBoarding = inputData.getBoolean(KEY_IS_BOARDING, true)
        val msgStart = "doWork started: tripId=$tripId isBoarding=$isBoarding"
        Log.d(TAG, msgStart)
        TransitTrackerLogger.log(applicationContext, TAG, msgStart)

        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            return permanentRetryFailure(
                "Transit action retry limit reached: trip=$tripId isBoarding=$isBoarding"
            )
        }

        val timestamp = inputData.getString(KEY_TIMESTAMP)
            ?: LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))

        return try {
            val updated = if (isBoarding) {
                actualUpdater.updateActual(tripId, timestamp, null)
            } else {
                actualUpdater.updateActual(tripId, null, timestamp)
            }
            val msgResult = "updateActual result: $updated for tripId=$tripId"
            Log.d(TAG, msgResult)
            TransitTrackerLogger.log(applicationContext, TAG, msgResult)

            if (!updated) {
                val msgNotFound = "updateActual returned false - document not found for id=$tripId"
                Log.w(TAG, msgNotFound)
                TransitTrackerLogger.log(applicationContext, TAG, msgNotFound)

                return retryOrFailure(
                    "updateActual basarisiz: trip=$tripId isBoarding=$isBoarding time=$timestamp"
                )
            }
            val msgSuccess = "Yolculuk zamani islendi: trip=$tripId isBoarding=$isBoarding time=$timestamp"
            Log.d(TAG, msgSuccess)
            TransitTrackerLogger.log(applicationContext, TAG, msgSuccess)

            AppEventBus.emit(
                AppEventBus.Event.TripSynced(
                    tripId = tripId,
                    isBoarding = isBoarding,
                    timestamp = timestamp
                )
            )

            Result.success()
        } catch (e: Exception) {
            retryOrFailure("Firestore yazilamadi: ${e.message}", e)
        }
    }

    private fun retryOrFailure(reason: String, throwable: Throwable? = null): Result {
        if (runAttemptCount >= MAX_RETRY_ATTEMPTS) {
            return permanentRetryFailure(reason, throwable)
        }

        val retryMessage =
            "$reason - Result.retry() attempt=${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS"
        if (throwable != null) {
            logException(retryMessage, throwable)
        } else {
            Log.w(TAG, retryMessage)
            TransitTrackerLogger.log(applicationContext, TAG, retryMessage)
        }
        return Result.retry()
    }

    private fun permanentRetryFailure(reason: String, throwable: Throwable? = null): Result {
        val permanentMessage =
            "$reason - permanently failing after $runAttemptCount retry attempt(s)"
        if (throwable != null) {
            logException(permanentMessage, throwable)
        } else {
            Log.e(TAG, permanentMessage)
            TransitTrackerLogger.log(applicationContext, TAG, permanentMessage)
        }
        return Result.failure(failureOutputData(permanentMessage, throwable))
    }

    private fun missingInputFailure(): Result {
        val msgMissing = "doWork() failed: missing required inputData '$KEY_TRIP_ID'"
        Log.e(TAG, msgMissing)
        TransitTrackerLogger.log(applicationContext, TAG, msgMissing)
        return Result.failure(failureOutputData(msgMissing))
    }

    private fun logException(message: String, throwable: Throwable) {
        Log.e(TAG, message, throwable)
        TransitTrackerLogger.log(
            applicationContext,
            TAG,
            "$message\n${Log.getStackTraceString(throwable)}"
        )
    }

    private fun failureOutputData(reason: String, throwable: Throwable? = null): Data {
        val builder = Data.Builder()
            .putString(OUTPUT_FAILURE_REASON, reason.take(MAX_OUTPUT_STRING_LENGTH))

        if (throwable != null) {
            builder
                .putString(OUTPUT_EXCEPTION_CLASS, throwable::class.java.name.take(MAX_OUTPUT_STRING_LENGTH))
                .putString(OUTPUT_EXCEPTION_MESSAGE, (throwable.message ?: "").take(MAX_OUTPUT_STRING_LENGTH))
                .putString(
                    OUTPUT_STACK_TRACE,
                    Log.getStackTraceString(throwable).take(MAX_STACK_TRACE_OUTPUT_LENGTH)
                )
        }

        return builder.build()
    }
}
