package com.example.toplutasima.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class OfflineSyncWorkerTest {
    @Test
    fun `succeeds when the queue is fully drained`() = runBlocking {
        val operations = FakeOfflineQueueSyncOperations(drainResult = 3, pendingResult = 0)
        val worker = buildWorker(operations)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, operations.drainCalls)
        assertEquals(1, operations.pendingCountCalls)
    }

    @Test
    fun `retries when queue processing throws before the limit`() = runBlocking {
        val operations = FakeOfflineQueueSyncOperations(
            drainFailure = IllegalStateException("encrypted queue unavailable")
        )
        val worker = buildWorker(operations, runAttemptCount = 1)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(1, operations.drainCalls)
        assertEquals(0, operations.pendingCountCalls)
    }

    @Test
    fun `fails permanently after reaching the retry limit`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<OfflineSyncWorker>(context)
            .setRunAttemptCount(OfflineSyncWorker.MAX_RETRY_ATTEMPTS)
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(
        operations: OfflineQueueSyncOperations,
        runAttemptCount: Int = 0
    ): OfflineSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<OfflineSyncWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(OfflineSyncWorkerFactory(operations))
            .build()
    }

    private class OfflineSyncWorkerFactory(
        private val operations: OfflineQueueSyncOperations
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? = if (workerClassName == OfflineSyncWorker::class.java.name) {
            OfflineSyncWorker(appContext, workerParameters, operations)
        } else {
            null
        }
    }

    private class FakeOfflineQueueSyncOperations(
        private val drainResult: Int = 0,
        private val pendingResult: Int = 0,
        private val drainFailure: Exception? = null
    ) : OfflineQueueSyncOperations {
        var drainCalls = 0
        var pendingCountCalls = 0

        override suspend fun drain(context: Context): Int {
            drainCalls += 1
            drainFailure?.let { throw it }
            return drainResult
        }

        override fun pendingCount(context: Context): Int {
            pendingCountCalls += 1
            return pendingResult
        }
    }
}
