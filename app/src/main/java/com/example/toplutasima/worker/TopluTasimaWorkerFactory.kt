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
        return try {
            when (workerClassName) {
                TransitActionWorker::class.java.name -> {
                    Log.d(TAG, "Creating TransitActionWorker, repository=$transitRecordRepository")
                    TransitActionWorker(
                        appContext = appContext,
                        workerParams = workerParameters,
                        repository = transitRecordRepository
                    )
                }
                else -> {
                    Log.w(TAG, "Unknown worker class: $workerClassName")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "createWorker failed: $e", e)
            TransitTrackerLogger.log(
                appContext, TAG,
                "createWorker FAILED: class=$workerClassName error=${e.message}"
            )
            null
        }
    }
}
