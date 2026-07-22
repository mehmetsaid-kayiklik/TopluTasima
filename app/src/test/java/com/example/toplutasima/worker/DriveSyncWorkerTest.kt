package com.example.toplutasima.worker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.toplutasima.drive.repository.DriveSyncRunResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class DriveSyncWorkerTest {
    @Test
    fun `disabled gate skips repository`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations()

        val result = buildWorker(operations, enabled = false).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, operations.syncCalls)
    }

    @Test
    fun `signed out worker succeeds without touching queue`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations(ownerUid = null)

        val result = buildWorker(operations).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, operations.syncCalls)
    }

    @Test
    fun `work without an owner input cannot synchronize`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations()

        val result = buildWorker(operations, requestedOwnerUid = null).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, operations.syncCalls)
    }

    @Test
    fun `work from previous account cannot synchronize current account queue`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations(ownerUid = "user-b")

        val result = buildWorker(operations, requestedOwnerUid = "user-a").doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(0, operations.syncCalls)
    }

    @Test
    fun `fully processed queue succeeds`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations(
            result = DriveSyncRunResult(2, retryRequired = false, permanentFailureCount = 0)
        )

        val result = buildWorker(operations).doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, operations.syncCalls)
    }

    @Test
    fun `retryable queue result asks WorkManager to retry`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations(
            result = DriveSyncRunResult(0, retryRequired = true, permanentFailureCount = 0)
        )

        val result = buildWorker(operations).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `fatal queue result fails without pretending success`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations(
            result = DriveSyncRunResult(0, retryRequired = false, permanentFailureCount = 1)
        )

        val result = buildWorker(operations).doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    @Test
    fun `mixed retryable and fatal result keeps retryable work alive`() = runBlocking {
        val operations = FakeDriveSyncWorkerOperations(
            result = DriveSyncRunResult(0, retryRequired = true, permanentFailureCount = 1)
        )

        val result = buildWorker(operations).doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
    }

    @Test
    fun `cancellation is rethrown`() {
        val operations = FakeDriveSyncWorkerOperations(failure = CancellationException("cancel"))
        var cancelled = false

        try {
            runBlocking { buildWorker(operations).doWork() }
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertTrue(cancelled)
    }

    private fun buildWorker(
        operations: DriveSyncWorkerOperations,
        enabled: Boolean = true,
        requestedOwnerUid: String? = "user-a"
    ): DriveSyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val builder = TestListenableWorkerBuilder<DriveSyncWorker>(context)
            .setWorkerFactory(DriveWorkerFactory(operations, enabled))
        if (requestedOwnerUid != null) {
            builder.setInputData(workDataOf(DriveSyncWorker.INPUT_OWNER_UID to requestedOwnerUid))
        }
        return builder.build()
    }

    private class DriveWorkerFactory(
        private val operations: DriveSyncWorkerOperations,
        private val enabled: Boolean
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? = if (workerClassName == DriveSyncWorker::class.java.name) {
            DriveSyncWorker(appContext, workerParameters, operations, enabled)
        } else {
            null
        }
    }

    private class FakeDriveSyncWorkerOperations(
        private var ownerUid: String? = "user-a",
        private val result: DriveSyncRunResult = DriveSyncRunResult(
            processedCount = 0,
            retryRequired = false,
            permanentFailureCount = 0
        ),
        private val failure: Exception? = null
    ) : DriveSyncWorkerOperations {
        var syncCalls = 0

        override fun currentUserIdOrNull(): String? = ownerUid

        override suspend fun synchronize(ownerUid: String): DriveSyncRunResult {
            syncCalls++
            failure?.let { throw it }
            return result
        }
    }
}
