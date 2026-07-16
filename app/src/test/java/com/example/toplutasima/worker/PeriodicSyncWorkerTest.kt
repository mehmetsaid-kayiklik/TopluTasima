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
class PeriodicSyncWorkerTest {
    @Test
    fun `succeeds without syncing when there is no authenticated user`() = runBlocking {
        val operations = FakePeriodicSyncOperations(currentUserId = null)
        val worker = buildWorker(operations)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertTrue(operations.fullSyncArguments.isEmpty())
        assertEquals(0, operations.profileSyncCalls)
    }

    @Test
    fun `succeeds after trip and profile sync complete`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val operations = FakePeriodicSyncOperations(currentUserId = "periodic-success-user")
        val worker = buildWorker(operations, context = context)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(listOf(true), operations.fullSyncArguments)
        assertEquals(1, operations.profileSyncCalls)
    }

    @Test
    fun `retries when a sync operation fails before the limit`() = runBlocking {
        val operations = FakePeriodicSyncOperations(
            currentUserId = "periodic-retry-user",
            tripSyncFailure = IllegalStateException("Firestore unavailable")
        )
        val worker = buildWorker(operations, runAttemptCount = 1)

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Retry)
        assertEquals(listOf(true), operations.fullSyncArguments)
        assertEquals(0, operations.profileSyncCalls)
    }

    @Test
    fun `fails permanently after reaching the retry limit`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val worker = TestListenableWorkerBuilder<PeriodicSyncWorker>(context)
            .setRunAttemptCount(PeriodicSyncWorker.MAX_RETRY_ATTEMPTS)
            .build()

        val result = worker.doWork()

        assertTrue(result is ListenableWorker.Result.Failure)
    }

    private fun buildWorker(
        operations: PeriodicSyncOperations,
        runAttemptCount: Int = 0,
        context: Context = ApplicationProvider.getApplicationContext()
    ): PeriodicSyncWorker =
        TestListenableWorkerBuilder<PeriodicSyncWorker>(context)
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(PeriodicSyncWorkerFactory(operations))
            .build()

    private class PeriodicSyncWorkerFactory(
        private val operations: PeriodicSyncOperations
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters
        ): ListenableWorker? = if (workerClassName == PeriodicSyncWorker::class.java.name) {
            PeriodicSyncWorker(appContext, workerParameters, operations)
        } else {
            null
        }
    }

    private class FakePeriodicSyncOperations(
        var currentUserId: String?,
        private val tripSyncFailure: Exception? = null
    ) : PeriodicSyncOperations {
        val fullSyncArguments = mutableListOf<Boolean>()
        var profileSyncCalls = 0

        override fun currentUserIdOrNull(): String? = currentUserId

        override suspend fun syncTrips(context: Context, fullSync: Boolean) {
            fullSyncArguments += fullSync
            tripSyncFailure?.let { throw it }
        }

        override suspend fun syncProfiles(context: Context) {
            profileSyncCalls += 1
        }
    }
}
