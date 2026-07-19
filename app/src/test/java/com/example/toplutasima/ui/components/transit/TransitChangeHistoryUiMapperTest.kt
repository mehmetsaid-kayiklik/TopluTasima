package com.example.toplutasima.ui.components.transit

import com.example.toplutasima.transit.history.TransitChangeEvent
import com.example.toplutasima.transit.history.TransitChangeOperation
import com.example.toplutasima.transit.history.TransitChangeSource
import com.example.toplutasima.transit.history.TransitFieldChange
import com.example.toplutasima.transit.history.TransitHistoryProvenanceEvidence
import com.example.toplutasima.transit.history.TransitHistoryProvenanceSource
import com.example.toplutasima.transit.history.TransitHistoryEvidenceDurability
import com.example.toplutasima.transit.history.TransitHistorySyncStatus
import com.example.toplutasima.transit.history.TransitHistoryValue
import com.example.toplutasima.ui.AppLanguage
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitChangeHistoryUiMapperTest {

    @Test
    fun `summary reads exact changed fields and values`() {
        val event = event(
            changes = listOf(
                change("hat", "S8", "S9"),
                change("gercekBinis", "08:10", "08:12")
            )
        )

        val summary = TransitChangeHistoryUiMapper.shortSummary(event, AppLanguage.TR)

        assertTrue(summary.contains("Hat: S8 değerinden S9 değerine"))
        assertTrue(summary.contains("Gerçek kalkış: 08:10 değerinden 08:12 değerine"))
    }

    @Test
    fun `unknown and empty remain distinguishable`() {
        val change = TransitFieldChange(
            fieldId = "not",
            oldValue = TransitHistoryValue.unknown(),
            newValue = TransitHistoryValue.empty()
        )

        val description = TransitChangeHistoryUiMapper.changeDescription(change, AppLanguage.TR)

        assertTrue(description.contains("Bilinmiyor"))
        assertTrue(description.contains("Boş"))
    }

    @Test
    fun `accessibility description includes title value period source and sync`() {
        val event = event(changes = listOf(change("mesafe", "12.5", "13")))

        val description = TransitChangeHistoryUiMapper.eventContentDescription(
            event = event,
            lang = AppLanguage.EN,
            zoneId = ZoneOffset.UTC
        )

        assertTrue(description.contains("Manual edit"))
        assertTrue(description.contains("01.01.1970 00:00"))
        assertTrue(description.contains("Source: User"))
        assertTrue(description.contains("Sync pending"))
        assertTrue(description.contains("Distance: from 12.5 to 13"))
    }

    @Test
    fun `provenance change is read explicitly`() {
        val change = TransitFieldChange(
            fieldId = "planlananBinis",
            oldValue = TransitHistoryValue.known("08:00"),
            newValue = TransitHistoryValue.known("08:00"),
            oldProvenance = TransitHistoryProvenanceEvidence(
                TransitHistoryProvenanceSource.CACHE,
                TransitHistoryEvidenceDurability.PERSISTED_FIELD
            ),
            newProvenance = TransitHistoryProvenanceEvidence(
                TransitHistoryProvenanceSource.PLANNED_RMV,
                TransitHistoryEvidenceDurability.PERSISTED_FIELD
            )
        )

        val description = TransitChangeHistoryUiMapper.changeDescription(change, AppLanguage.DE)

        assertTrue(description.contains("Cache"))
        assertTrue(description.contains("RMV Plandaten"))
    }

    @Test
    fun `short summary does not repeat all fields`() {
        val event = event(
            changes = listOf(
                change("hat", "S8", "S9"),
                change("mesafe", "10", "11"),
                change("not", "a", "b")
            )
        )

        val summary = TransitChangeHistoryUiMapper.shortSummary(event, AppLanguage.EN)

        assertTrue(summary.contains("1 more changes"))
        assertFalse(summary.contains("Note: from a to b"))
    }

    @Test
    fun `operation and source labels cover delete receipt access`() {
        assertEquals(
            "Deletion sync",
            TransitChangeHistoryUiMapper.operationLabel(TransitChangeOperation.DELETE_SYNC, AppLanguage.EN)
        )
        assertEquals(
            "Synchronization",
            TransitChangeHistoryUiMapper.sourceLabel(TransitChangeSource.SYNC_WORKER, AppLanguage.EN)
        )
    }

    private fun event(changes: List<TransitFieldChange>) = TransitChangeEvent(
        eventId = "event-1",
        userId = "uid-1",
        recordId = "record-1",
        operation = TransitChangeOperation.MANUAL_EDIT,
        occurredAtEpochMillis = 0L,
        recordedAtEpochMillis = 0L,
        source = TransitChangeSource.USER,
        changes = changes,
        syncStatus = TransitHistorySyncStatus.PENDING
    )

    private fun change(field: String, old: String, new: String) = TransitFieldChange(
        fieldId = field,
        oldValue = TransitHistoryValue.known(old),
        newValue = TransitHistoryValue.known(new)
    )
}
