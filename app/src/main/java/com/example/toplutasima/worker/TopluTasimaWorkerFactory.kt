package com.example.toplutasima.worker

import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.example.toplutasima.diagnostics.TransitTrackerLogger
import com.example.toplutasima.repository.TransitRecordRepository

class TopluTasimaWorkerFactory(
    private val transitRecordRepository: TransitRecordRepository
) : WorkerFactory() {

    companion object {
        private const val TAG = "WorkerFactory"
    }

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        val msg = "createWorker() called: class=$workerClassName"
        Log.d(TAG, msg)
        TransitTrackerLogger.log(appContext, TAG, msg)

        return when (workerClassName) {
            TransitActionWorker::class.java.name -> {
                try {
                    val w = TransitActionWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        repository = transitRecordRepository
                    )
                    TransitTrackerLogger.log(appContext, TAG, "TransitActionWorker created OK")
                    w
                } catch (e: Exception) {
                    val errMsg =
                        "createWorker() FAILED for $workerClassName: ${e::class.simpleName}: ${e.message}"
                    Log.e(TAG, errMsg, e)
                    TransitTrackerLogger.log(appContext, TAG, errMsg)
                    throw e
                }
            }
            else -> {
                // Return null to let WorkManager use delegate/default factory for other workers
                val warnMsg = "createWorker() — delegating to default factory: $workerClassName"
                Log.w(TAG, warnMsg)
                TransitTrackerLogger.log(appContext, TAG, warnMsg)
                null
            }
        }
    }
}
