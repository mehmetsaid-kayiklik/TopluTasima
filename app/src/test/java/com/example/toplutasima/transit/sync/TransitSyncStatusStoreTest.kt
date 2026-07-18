package com.example.toplutasima.transit.sync

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitSyncStatusStoreTest {
    @Test
    fun `offline pending state survives application restart`() = runBlocking {
        var persisted: String? = null
        val first = store(read = { persisted }, write = { persisted = it }, userId = "user-A")

        first.markLocalSaving("user-A", "record-1")
        first.markLocalSafe("user-A", "record-1")
        first.markPending("user-A", "record-1", "queue-1")

        val restarted = store(read = { persisted }, write = { persisted = it }, userId = "user-A")
        val restored = restarted.observeRecord("record-1").first()
        assertEquals(TransitSyncPhase.PENDING, restored?.phase)
        assertEquals("queue-1", restored?.queueActionId)
    }

    @Test
    fun `retry then successful sync advances one receipt`() = runBlocking {
        val store = store(userId = "user-A")
        store.markPending("user-A", "record-1", "queue-1")
        store.markSyncing("user-A", "record-1", "queue-1")
        store.markTemporaryError("user-A", "record-1", "offline")

        assertEquals(TransitSyncPhase.TEMPORARY_ERROR, store.observeRecord("record-1").first()?.phase)

        store.markSyncing("user-A", "record-1", "queue-1")
        store.markSynced("user-A", "record-1")
        val synced = store.observeRecord("record-1").first()
        assertEquals(TransitSyncPhase.SYNCED, synced?.phase)
        assertEquals(2, synced?.attemptCount)
    }

    @Test
    fun `permanent failure is distinct from retryable failure`() = runBlocking {
        val store = store(userId = "user-A")
        store.markPending("user-A", "record-1")
        store.markTemporaryError("user-A", "record-1", "timeout")
        store.markPendingForUserAsPermanentError("user-A", "retry limit")

        val result = store.observeRecord("record-1").first()
        assertEquals(TransitSyncPhase.PERMANENT_ERROR, result?.phase)
        assertEquals("retry limit", result?.detail)
    }

    @Test
    fun `changing UID never exposes another user's receipt`() = runBlocking {
        val store = store(userId = "user-A")
        store.markPending("user-A", "same-record")
        store.markSynced("user-B", "same-record")

        assertEquals(TransitSyncPhase.PENDING, store.observeRecord("same-record").first()?.phase)
        store.onUserChanged("user-B")
        assertEquals(TransitSyncPhase.SYNCED, store.observeRecord("same-record").first()?.phase)
        store.onUserChanged(null)
        assertNull(store.observeRecord("same-record").first())
    }

    @Test
    fun `duplicate queue events remain idempotent per record`() = runBlocking {
        val store = store(userId = "user-A")
        store.markPending("user-A", "record-1", "queue-old")
        store.markPending("user-A", "record-1", "queue-new")

        val receipt = store.observeRecord("record-1").first()
        assertEquals(1, store.snapshotForUser("user-A").size)
        assertEquals(TransitSyncPhase.PENDING, receipt?.phase)
        assertEquals("queue-new", receipt?.queueActionId)
    }

    @Test
    fun `pending delete and tombstone survive application restart`() = runBlocking {
        var persisted: String? = null
        val first = store(read = { persisted }, write = { persisted = it }, userId = "user-A")
        first.markLocalDeleted("user-A", "record-1", "document-1")
        first.markDeletePending("user-A", "record-1", "document-1", "delete-1")

        val restarted = store(read = { persisted }, write = { persisted = it }, userId = "user-A")
        val receipt = restarted.observeRecord("record-1").first()

        assertEquals(TransitSyncOperation.DELETE, receipt?.operation)
        assertEquals(TransitSyncPhase.DELETE_PENDING, receipt?.phase)
        assertEquals("document-1", receipt?.deleteMetadata?.firestoreDocId)
        assertTrue(restarted.isDeletionTombstoned("user-A", "record-1"))
        assertTrue(restarted.isDeletionTombstoned("user-A", firestoreDocId = "document-1"))
        assertFalse(restarted.isDeletionTombstoned("user-B", "record-1"))
    }

    @Test
    fun `duplicate delete event is idempotent`() = runBlocking {
        val store = store(userId = "user-A")
        store.markLocalDeleted("user-A", "record-1", "document-1")
        store.markDeletePending("user-A", "record-1", "document-1", "delete-1")
        val first = store.observeRecord("record-1").first()

        store.markDeletePending("user-A", "record-1", "document-1", "delete-1")
        val duplicate = store.observeRecord("record-1").first()

        assertEquals(first, duplicate)
        assertEquals(1, store.snapshotForUser("user-A").size)
    }

    @Test
    fun `stale create callback cannot overwrite a newer delete`() = runBlocking {
        val store = store(userId = "user-A")
        store.markPending("user-A", "record-1", "create-1")
        store.markLocalDeleted("user-A", "record-1", "document-1")
        store.markDeletePending("user-A", "record-1", "document-1", "delete-1")

        store.markSynced("user-A", "record-1", "create-1")

        val receipt = store.observeRecord("record-1").first()
        assertEquals(TransitSyncOperation.DELETE, receipt?.operation)
        assertEquals(TransitSyncPhase.DELETE_PENDING, receipt?.phase)
        assertEquals("delete-1", receipt?.queueActionId)
    }

    @Test
    fun `stale delete callback cannot finish a newer delete action`() = runBlocking {
        val store = store(userId = "user-A")
        store.markLocalDeleted("user-A", "record-1", "document-1")
        store.markDeletePending("user-A", "record-1", "document-1", "delete-old")
        store.markDeletePending("user-A", "record-1", "document-1", "delete-new")

        store.markDeleted("user-A", "record-1", "delete-old")

        val receipt = store.observeRecord("record-1").first()
        assertEquals(TransitSyncPhase.DELETE_PENDING, receipt?.phase)
        assertEquals("delete-new", receipt?.queueActionId)
    }

    @Test
    fun `delete retry and permanent failure use delete-specific phases`() = runBlocking {
        val store = store(userId = "user-A")
        store.markLocalDeleted("user-A", "record-1", "document-1")
        store.markDeletePending("user-A", "record-1", "document-1", "delete-1")
        store.markDeleting("user-A", "record-1", "delete-1")
        store.markPendingForUserAsTemporaryError("user-A", "offline")
        assertEquals(
            TransitSyncPhase.DELETE_TEMPORARY_ERROR,
            store.observeRecord("record-1").first()?.phase
        )

        store.markPendingForUserAsPermanentError("user-A", "retry limit")
        assertEquals(
            TransitSyncPhase.DELETE_PERMANENT_ERROR,
            store.observeRecord("record-1").first()?.phase
        )
    }

    @Test
    fun `local-only delete remains a tombstone`() = runBlocking {
        val store = store(userId = "user-A")
        store.markLocalDeleted("user-A", "record-1", "document-1")
        store.markDeleteLocalOnly("user-A", "record-1")

        val receipt = store.observeRecord("record-1").first()
        assertEquals(TransitDeleteDisposition.LOCAL_ONLY, receipt?.deleteMetadata?.disposition)
        assertTrue(store.isDeletionTombstoned("user-A", "record-1", "document-1"))
    }

    private fun store(
        read: () -> String? = { null },
        write: (String) -> Unit = {},
        userId: String?
    ): TransitSyncStatusStore {
        var clock = 1_000L
        return TransitSyncStatusStore(
            readRaw = read,
            writeRaw = write,
            initialUserId = userId,
            now = { clock++ }
        )
    }
}
