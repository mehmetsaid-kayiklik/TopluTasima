package com.example.toplutasima.data

import android.app.Application
import android.util.Log
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class OfflineQueueStoreTest {
    @Test
    fun `encrypted preference failure recreates encrypted storage without plaintext fallback`() {
        var openAttempts = 0
        var resetCalls = 0
        val logMessages = mutableListOf<String>()

        val storage = openEncryptedStorageWithRecovery(
            openEncrypted = {
                openAttempts++
                if (openAttempts == 1) throw GeneralSecurityException("corrupted key entry")
                "encrypted-storage"
            },
            resetEncryptedStorage = { resetCalls++ },
            logError = { message, _ -> logMessages += message }
        )

        assertEquals("encrypted-storage", storage)
        assertEquals(2, openAttempts)
        assertEquals(1, resetCalls)
        assertTrue(logMessages.single().contains("Plaintext fallback is disabled"))
    }

    @Test
    fun `encrypted preference recovery failure is surfaced`() {
        val recoveryFailure = IOException("keystore remains unavailable")
        var openAttempts = 0

        try {
            openEncryptedStorageWithRecovery(
                openEncrypted = {
                    openAttempts++
                    if (openAttempts == 1) throw GeneralSecurityException("initial failure")
                    throw recoveryFailure
                },
                resetEncryptedStorage = {},
                logError = { _, _ -> }
            )
            fail("Expected encrypted storage recovery to fail")
        } catch (actual: IllegalStateException) {
            assertSame(recoveryFailure, actual.cause)
        }

        assertEquals(2, openAttempts)
    }

    @Test
    fun `concurrent drains process each queued item exactly once`() = runBlocking {
        val queuedActions = (1..3).map { index ->
            OfflineQueueStore.QueuedAction(
                id = "action-$index",
                userId = "user-A",
                type = "test",
                payload = "{}"
            )
        }
        val actionAddedDuringDrain = OfflineQueueStore.QueuedAction(
            id = "action-added-during-drain",
            userId = "user-A",
            type = "test",
            payload = "{}"
        )
        val storedActions = AtomicReference(queuedActions)
        val processCounts = ConcurrentHashMap<String, AtomicInteger>()
        val processingStarted = CompletableDeferred<Unit>()
        val releaseProcessing = CompletableDeferred<Unit>()

        suspend fun drainQueue() = OfflineQueueStore.drainQueue(
            currentUserId = "user-A",
            loadActions = storedActions::get,
            saveActions = storedActions::set,
            processAction = { action ->
                processingStarted.complete(Unit)
                releaseProcessing.await()
                processCounts.computeIfAbsent(action.id) { AtomicInteger() }.incrementAndGet()
            }
        )

        val firstDrain = async(Dispatchers.Default) { drainQueue() }
        processingStarted.await()
        val secondDrain = async(Dispatchers.Default) { drainQueue() }
        storedActions.updateAndGet { actions -> actions + actionAddedDuringDrain }
        yield()
        releaseProcessing.complete(Unit)

        val syncedCounts = awaitAll(firstDrain, secondDrain)

        assertEquals(listOf(1, queuedActions.size), syncedCounts.sorted())
        assertTrue(storedActions.get().isEmpty())
        assertEquals(
            (queuedActions + actionAddedDuringDrain).map { it.id }.toSet(),
            processCounts.keys
        )
        assertTrue(processCounts.values.all { count -> count.get() == 1 })
    }

    @Test
    fun `newer update supersedes an older pending update for the same record`() = runBlocking {
        val olderUpdate = OfflineQueueStore.QueuedAction(
            id = "older-update",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-X",
            payload = "{\"value\":\"old\"}",
            createdAt = 100L
        )
        val newerUpdate = OfflineQueueStore.QueuedAction(
            id = "newer-update",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-X",
            payload = "{\"value\":\"new\"}",
            createdAt = 200L
        )
        val storedActions = AtomicReference(listOf(olderUpdate, newerUpdate))
        val attemptedActionIds = mutableListOf<String>()
        ShadowLog.clear()

        val synced = OfflineQueueStore.drainQueue(
            currentUserId = "user-A",
            loadActions = storedActions::get,
            saveActions = storedActions::set,
            processAction = { action -> attemptedActionIds += action.id }
        )

        assertEquals(1, synced)
        assertEquals(listOf(newerUpdate.id), attemptedActionIds)
        assertTrue(storedActions.get().isEmpty())
        assertTrue(
            ShadowLog.getLogsForTag("OfflineQueueStore").any { logItem ->
                logItem.type == Log.WARN &&
                    logItem.msg.contains("superseded") &&
                    logItem.msg.contains(olderUpdate.id) &&
                    logItem.msg.contains(newerUpdate.id)
            }
        )
    }

    @Test
    fun `failed newest update stays queued while superseded update is removed`() = runBlocking {
        val olderUpdate = OfflineQueueStore.QueuedAction(
            id = "older-X",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-X",
            payload = "{\"value\":\"old\"}",
            createdAt = 100L
        )
        val independentUpdate = OfflineQueueStore.QueuedAction(
            id = "only-Y",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-Y",
            payload = "{\"value\":\"independent\"}",
            createdAt = 150L
        )
        val newerUpdate = OfflineQueueStore.QueuedAction(
            id = "newer-X",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-X",
            payload = "{\"value\":\"new\"}",
            createdAt = 200L
        )
        val storedActions = AtomicReference(
            listOf(olderUpdate, independentUpdate, newerUpdate)
        )
        val attemptedActionIds = mutableListOf<String>()

        val synced = OfflineQueueStore.drainQueue(
            currentUserId = "user-A",
            loadActions = storedActions::get,
            saveActions = storedActions::set,
            processAction = { action ->
                attemptedActionIds += action.id
                if (action.id == newerUpdate.id) throw IOException("still offline")
            }
        )

        assertEquals(1, synced)
        assertEquals(listOf(independentUpdate.id, newerUpdate.id), attemptedActionIds)
        assertEquals(listOf(newerUpdate), storedActions.get())
    }

    @Test
    fun `user A action is not processed while user B is signed in`() = runBlocking {
        val userAAction = OfflineQueueStore.QueuedAction(
            id = "user-a-action",
            userId = "user-A",
            type = "updateRecord",
            recordId = "trip-A",
            payload = "{\"value\":\"A\"}"
        )
        val storedActions = AtomicReference(listOf(userAAction))
        val attemptedWrites = mutableListOf<String>()
        ShadowLog.clear()

        val synced = OfflineQueueStore.drainQueue(
            currentUserId = "user-B",
            loadActions = storedActions::get,
            saveActions = storedActions::set,
            processAction = { action -> attemptedWrites += action.userId }
        )

        assertEquals(0, synced)
        assertTrue(attemptedWrites.isEmpty())
        assertEquals(listOf(userAAction), storedActions.get())
        assertTrue(
            ShadowLog.getLogsForTag("OfflineQueueStore").any { logItem ->
                logItem.type == Log.WARN &&
                    logItem.msg.contains("Deferring") &&
                    logItem.msg.contains("ownerUid=user-A") &&
                    logItem.msg.contains("currentUid=user-B")
            }
        )
    }

    @Test
    fun `queue lifecycle callbacks identify retry and success by record`() = runBlocking {
        val succeeds = OfflineQueueStore.QueuedAction(
            id = "queue-success",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-success",
            payload = "{}"
        )
        val retries = OfflineQueueStore.QueuedAction(
            id = "queue-retry",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-retry",
            payload = "{}"
        )
        val storedActions = AtomicReference(listOf(succeeds, retries))
        val processing = mutableListOf<String>()
        val processed = mutableListOf<String>()
        val failed = mutableListOf<String>()

        val synced = OfflineQueueStore.drainQueue(
            currentUserId = "user-A",
            loadActions = storedActions::get,
            saveActions = storedActions::set,
            processAction = { action ->
                if (action.id == retries.id) throw IOException("offline")
            },
            onProcessing = { processing += it.recordId },
            onProcessed = { processed += it.recordId },
            onProcessFailed = { action, _ -> failed += action.recordId }
        )

        assertEquals(1, synced)
        assertEquals(listOf("record-success", "record-retry"), processing)
        assertEquals(listOf("record-success"), processed)
        assertEquals(listOf("record-retry"), failed)
        assertEquals(listOf(retries), storedActions.get())
    }

    @Test
    fun `delete dominates create and a later update for the same record`() = runBlocking {
        val create = OfflineQueueStore.QueuedAction(
            id = "create",
            userId = "user-A",
            type = "saveTrip",
            recordId = "record-1",
            payload = "{}",
            createdAt = 100L
        )
        val delete = OfflineQueueStore.QueuedAction(
            id = "delete",
            userId = "user-A",
            type = "deleteTrip",
            recordId = "record-1",
            payload = "{\"firestoreDocId\":\"document-1\"}",
            createdAt = 200L
        )
        val lateUpdate = OfflineQueueStore.QueuedAction(
            id = "late-update",
            userId = "user-A",
            type = "updateRecord",
            recordId = "record-1",
            payload = "{}",
            createdAt = 300L
        )
        val storedActions = AtomicReference(listOf(create, delete, lateUpdate))
        val processed = mutableListOf<String>()

        val synced = OfflineQueueStore.drainQueue(
            currentUserId = "user-A",
            loadActions = storedActions::get,
            saveActions = storedActions::set,
            processAction = { processed += it.id }
        )

        assertEquals(1, synced)
        assertEquals(listOf("delete"), processed)
        assertTrue(storedActions.get().isEmpty())
    }

    @Test
    fun `duplicate deletes are coalesced idempotently`() = runBlocking {
        val olderDelete = OfflineQueueStore.QueuedAction(
            id = "delete-old",
            userId = "user-A",
            type = "deleteTrip",
            recordId = "record-1",
            payload = "{\"firestoreDocId\":\"document-1\"}",
            createdAt = 100L
        )
        val newerDelete = olderDelete.copy(id = "delete-new", createdAt = 200L)
        val storedActions = AtomicReference(listOf(olderDelete, newerDelete))
        val processed = mutableListOf<String>()

        OfflineQueueStore.drainQueue(
            currentUserId = "user-A",
            loadActions = storedActions::get,
            saveActions = storedActions::set,
            processAction = { processed += it.id }
        )

        assertEquals(listOf("delete-new"), processed)
        assertTrue(storedActions.get().isEmpty())
    }

    @Test
    fun `cancellation is rethrown and pending delete remains queued`() = runBlocking {
        val delete = OfflineQueueStore.QueuedAction(
            id = "delete-1",
            userId = "user-A",
            type = "deleteTrip",
            recordId = "record-1",
            payload = "{\"firestoreDocId\":\"document-1\"}"
        )
        val storedActions = AtomicReference(listOf(delete))

        try {
            OfflineQueueStore.drainQueue(
                currentUserId = "user-A",
                loadActions = storedActions::get,
                saveActions = storedActions::set,
                processAction = { throw CancellationException("cancelled") }
            )
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            // Expected: cancellation is not converted into a retry/domain failure.
        }

        assertEquals(listOf(delete), storedActions.get())
    }
}
