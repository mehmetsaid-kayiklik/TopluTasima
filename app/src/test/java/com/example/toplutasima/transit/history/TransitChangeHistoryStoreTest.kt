package com.example.toplutasima.transit.history

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitChangeHistoryStoreTest {
    @Test
    fun `create history is stored without copying a full record`() {
        val store = store()

        val result = store.append(draft(operation = TransitChangeOperation.CREATE))

        assertEquals(TransitHistoryAppendOutcome.ADDED, result.outcome)
        assertEquals(TransitChangeOperation.CREATE, result.event?.operation)
        assertTrue(result.event?.changes.orEmpty().isEmpty())
    }

    @Test
    fun `manual update stores only changed fields`() {
        val changes = TransitRecordDiffUseCase().diff(
            before = mapOf("hat" to "U1", "not" to "aynı", "gun" to "Pazartesi"),
            after = mapOf("hat" to "U2", "not" to "aynı", "gun" to "Salı")
        )
        val result = store().append(
            draft(operation = TransitChangeOperation.MANUAL_EDIT, changes = changes)
        )

        assertEquals(listOf("hat"), result.event?.changes?.map { it.fieldId })
        assertEquals("U1", result.event?.changes?.single()?.oldValue?.value)
        assertEquals("U2", result.event?.changes?.single()?.newValue?.value)
    }

    @Test
    fun `health correction history keeps the deterministic field diff`() {
        val result = store().append(
            draft(
                operation = TransitChangeOperation.AUTOMATIC_HEALTH_CORRECTION,
                source = TransitChangeSource.DATA_HEALTH,
                changes = listOf(change("gercekYolSuresi", "-5", "25"))
            )
        )

        assertEquals(TransitChangeSource.DATA_HEALTH, result.event?.source)
        assertEquals("gercekYolSuresi", result.event?.changes?.single()?.fieldId)
    }

    @Test
    fun `local delete and delete sync are chronologically ordered`() {
        val store = store()
        store.append(
            draft(
                operation = TransitChangeOperation.DELETE_SYNC,
                source = TransitChangeSource.SYNC_WORKER,
                occurredAt = 2_000L,
                dedupe = "queue-delete-1"
            )
        )
        store.append(
            draft(
                operation = TransitChangeOperation.LOCAL_DELETE,
                occurredAt = 1_000L,
                dedupe = "local-delete-1"
            )
        )

        assertEquals(
            listOf(TransitChangeOperation.LOCAL_DELETE, TransitChangeOperation.DELETE_SYNC),
            store.eventsForRecord(USER_A, RECORD).map { it.operation }
        )
    }

    @Test
    fun `duplicate queue retry produces one history row`() {
        val store = store()
        val first = store.append(
            draft(
                operation = TransitChangeOperation.DELETE_SYNC,
                source = TransitChangeSource.SYNC_WORKER,
                occurredAt = 1_000L,
                dedupe = "queue-1"
            )
        )
        val retry = store.append(
            draft(
                operation = TransitChangeOperation.DELETE_SYNC,
                source = TransitChangeSource.SYSTEM,
                occurredAt = 9_000L,
                dedupe = "queue-1"
            )
        )

        assertEquals(TransitHistoryAppendOutcome.ADDED, first.outcome)
        assertEquals(TransitHistoryAppendOutcome.DUPLICATE, retry.outcome)
        assertEquals(first.event?.eventId, retry.event?.eventId)
        assertEquals(1, store.eventsForRecord(USER_A, RECORD).size)
    }

    @Test
    fun `worker status retry updates one event instead of appending rows`() {
        val store = store()
        val event = requireNotNull(
            store.append(
                draft(
                    operation = TransitChangeOperation.DELETE_SYNC,
                    source = TransitChangeSource.SYNC_WORKER,
                    dedupe = "queue-1",
                    syncStatus = TransitHistorySyncStatus.PENDING
                )
            ).event
        )

        assertTrue(
            store.updateSyncStatus(
                USER_A,
                RECORD,
                event.eventId,
                TransitHistorySyncStatus.TEMPORARY_ERROR
            )
        )
        assertTrue(
            store.updateSyncStatus(
                USER_A,
                RECORD,
                event.eventId,
                TransitHistorySyncStatus.SYNCED
            )
        )

        val restored = store.eventsForRecord(USER_A, RECORD).single()
        assertEquals(TransitHistorySyncStatus.SYNCED, restored.syncStatus)
    }

    @Test
    fun `same record id remains isolated by UID`() = runBlocking {
        val store = store()
        store.append(draft(userId = USER_A, operation = TransitChangeOperation.CREATE))
        store.append(draft(userId = USER_B, operation = TransitChangeOperation.LOCAL_DELETE))

        assertEquals(
            listOf(TransitChangeOperation.CREATE),
            store.observeRecord(USER_A, RECORD).first().map { it.operation }
        )
        assertEquals(
            listOf(TransitChangeOperation.LOCAL_DELETE),
            store.observeRecord(USER_B, RECORD).first().map { it.operation }
        )
    }

    @Test
    fun `history survives application restart`() {
        var persisted: String? = null
        val first = store(read = { persisted }, write = { persisted = it; true })
        first.append(
            draft(
                operation = TransitChangeOperation.MANUAL_EDIT,
                changes = listOf(change("hat", "U1", "U2"))
            )
        )

        val restarted = store(read = { persisted }, write = { persisted = it; true })

        assertEquals(1, restarted.eventsForRecord(USER_A, RECORD).size)
        assertEquals("U2", restarted.eventsForRecord(USER_A, RECORD).single().changes.single().newValue.value)
    }

    @Test
    fun `corrupt storage is cleared and never blocks main flow`() {
        var cleared = false
        val store = store(
            read = { "{not-json" },
            write = { true },
            clear = { cleared = true }
        )

        val result = store.append(draft(operation = TransitChangeOperation.CREATE))

        assertTrue(cleared)
        assertEquals(TransitHistoryAppendOutcome.ADDED, result.outcome)
        assertEquals(1, store.eventsForRecord(USER_A, RECORD).size)
    }

    @Test
    fun `write failure keeps non-fatal in-memory history`() {
        val store = store(write = { false })

        val result = store.append(draft(operation = TransitChangeOperation.CREATE))

        assertEquals(TransitHistoryAppendOutcome.ADDED_MEMORY_ONLY, result.outcome)
        assertEquals(1, store.eventsForRecord(USER_A, RECORD).size)
    }

    @Test
    fun `unknown old value remains explicitly unknown`() {
        val changes = TransitRecordDiffUseCase().diff(
            before = null,
            after = mapOf("hat" to "U1"),
            fieldIds = setOf("hat")
        )

        assertEquals(TransitHistoryValueState.UNKNOWN, changes.single().oldValue.state)
        assertEquals("Bilinmiyor", changes.single().oldValue.displayValue())
    }

    @Test
    fun `session provenance is never persisted as durable evidence`() {
        val sessionEvidence = TransitHistoryProvenanceEvidence(
            source = TransitHistoryProvenanceSource.LIVE_RMV,
            durability = TransitHistoryEvidenceDurability.SESSION_ONLY
        )
        val result = store().append(
            draft(
                operation = TransitChangeOperation.MANUAL_EDIT,
                changes = listOf(
                    change("hat", "U1", "U2").copy(newProvenance = sessionEvidence)
                )
            )
        )

        assertNull(result.event?.changes?.single()?.newProvenance)
    }

    @Test
    fun `history is bounded per user and record`() {
        val store = store(maximumEventsPerUser = 2, maximumEventsPerRecord = 2)
        repeat(4) { index ->
            store.append(
                draft(
                    operation = TransitChangeOperation.LOCAL_DELETE,
                    occurredAt = 1_000L + index,
                    dedupe = "delete-$index"
                )
            )
        }

        assertEquals(2, store.eventsForRecord(USER_A, RECORD).size)
        assertEquals(listOf(1_002L, 1_003L), store.eventsForRecord(USER_A, RECORD).map { it.occurredAtEpochMillis })
    }

    @Test
    fun `disabled feature performs no read or write`() {
        var reads = 0
        var writes = 0
        val store = TransitChangeHistoryStore(
            readRaw = { reads++; null },
            writeRaw = { writes++; true },
            enabled = false
        )

        val result = store.append(draft(operation = TransitChangeOperation.CREATE))

        assertEquals(TransitHistoryAppendOutcome.DISABLED, result.outcome)
        assertEquals(0, reads)
        assertEquals(0, writes)
        assertTrue(store.snapshotForUser(USER_A).isEmpty())
    }

    @Test
    fun `invalid event cannot enter storage`() {
        val store = store()

        val result = store.append(
            draft(operation = TransitChangeOperation.MANUAL_EDIT, changes = emptyList())
        )

        assertEquals(TransitHistoryAppendOutcome.INVALID, result.outcome)
        assertTrue(store.snapshotForUser(USER_A).isEmpty())
    }

    private fun store(
        read: () -> String? = { null },
        write: (String) -> Boolean = { true },
        clear: () -> Unit = {},
        maximumEventsPerUser: Int = 500,
        maximumEventsPerRecord: Int = 80
    ): TransitChangeHistoryStore {
        var clock = 10_000L
        return TransitChangeHistoryStore(
            readRaw = read,
            writeRaw = write,
            clearRaw = clear,
            enabled = true,
            nowEpochMillis = { clock++ },
            maximumEventsPerUser = maximumEventsPerUser,
            maximumEventsPerRecord = maximumEventsPerRecord
        )
    }

    private fun draft(
        userId: String = USER_A,
        operation: TransitChangeOperation,
        source: TransitChangeSource = TransitChangeSource.USER,
        occurredAt: Long = 1_000L,
        changes: List<TransitFieldChange> = emptyList(),
        dedupe: String? = null,
        syncStatus: TransitHistorySyncStatus = TransitHistorySyncStatus.NOT_APPLICABLE
    ) = TransitChangeEventDraft(
        userId = userId,
        recordId = RECORD,
        operation = operation,
        occurredAtEpochMillis = occurredAt,
        source = source,
        changes = changes,
        deduplicationKey = dedupe,
        syncStatus = syncStatus
    )

    private fun change(fieldId: String, old: Any?, new: Any?) = TransitFieldChange(
        fieldId = fieldId,
        oldValue = TransitHistoryValue.fromKnownField(old),
        newValue = TransitHistoryValue.fromKnownField(new)
    )

    private companion object {
        const val USER_A = "user-A"
        const val USER_B = "user-B"
        const val RECORD = "record-1"
    }
}
