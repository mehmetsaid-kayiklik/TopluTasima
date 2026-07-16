package com.example.toplutasima.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class TransitActionWorkerTest {
    @Test
    fun `succeeds after recording a boarding timestamp`() = runBlocking {
        val updater = FakeTransitActualUpdater(result = true)
        val worker = buildWorker(
            updater = updater,
            inputData = workDataOf(
                TransitActionWorker.KEY_TRIP_ID to "trip-success",
                TransitActionWorker.KEY_IS_BOARDING to true,
                TransitActionWorker.KEY_TIMESTAMP to "08:45"
            )
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(
            listOf(ActualUpdate("trip-success", "08:45", null)),
            updater.updates
        )
    }

    @Test
    fun `retries when the remote record cannot be updated before the limit`() = runBlocking {
        val updater = FakeTransitActualUpdater(result = false)
        val worker = buildWorker(
            updater = updater,
            inputData = workDataOf(
                TransitActionWorker.KEY_TRIP_ID to "trip-retry",
                TransitActionWorker.KEY_TIMESTAMP to "09:10"
            ),
            runAttemptCount = 1
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(1, updater.updates.size)
    }

    @Test
    fun `fails immediately when the trip id is missing`() = runBlocking {
        val updater = FakeTransitActualUpdater(result = true)
        val worker = buildWorker(updater = updater, inputData = workDataOf())

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertTrue(
            (result as ListenableWorker.Result.Failure)
                .outputData
                .getString(TransitActionWorker.OUTPUT_FAILURE_REASON)
                .orEmpty()
                .contains(TransitActionWorker.KEY_TRIP_ID)
        )
        assertTrue(updater.updates.isEmpty())
    }

    @Test
    fun `fails permanently after reaching the retry limit`() = runBlocking {
        val updater = FakeTransitActualUpdater(result = true)
        val worker = buildWorker(
            updater = updater,
            inputData = workDataOf(TransitActionWorker.KEY_TRIP_ID to "missing-trip"),
            runAttemptCount = TransitActionWorker.MAX_RETRY_ATTEMPTS
        )

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
        assertTrue(updater.updates.isEmpty())
    }

    private fun buildWorker(
        updater: TransitActualUpdater,
        inputData: androidx.work.Data,
        runAttemptCount: Int = 0
    ): TransitActionWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<TransitActionWorker>(
            context = context,
            inputData = inputData
        )
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(TransitActionWorkerFactory(updater))
            .build()
    }

    private class TransitActionWorkerFactory(
        private val updater: TransitActualUpdater
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? = if (workerClassName == TransitActionWorker::class.java.name) {
            TransitActionWorker(
                appContext = appContext,
                workerParams = workerParameters,
                actualUpdater = updater,
                dependencyDescription = updater
            )
        } else {
            null
        }
    }

    private data class ActualUpdate(
        val tripId: String,
        val actualDeparture: String?,
        val actualArrival: String?
    )

    private class FakeTransitActualUpdater(
        private val result: Boolean
    ) : TransitActualUpdater {
        val updates = mutableListOf<ActualUpdate>()

        override suspend fun updateActual(
            tripId: String,
            actualDeparture: String?,
            actualArrival: String?
        ): Boolean {
            updates += ActualUpdate(tripId, actualDeparture, actualArrival)
            return result
        }
    }
}
