package com.example.toplutasima.transit.provenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRecordProvenanceStoreTest {
    private val now = 1_000_000L
    private val provenanceUseCase = TransitFieldProvenanceUseCase(nowEpochMillis = { now })

    @Test
    fun `only edited field becomes manual and unchanged fields are preserved`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        val original = mapOf(
            "binisDuragi" to "Frankfurt Hbf",
            "planlananBinis" to "10:00"
        )
        store.putKnownFields(
            userId = "uid-a",
            localRecordId = "local-1",
            currentValues = original,
            provenanceByField = mapOf(
                "binisDuragi" to provenanceUseCase.plannedRmv("binisDuragi", now),
                "planlananBinis" to provenanceUseCase.plannedRmv("planlananBinis", now)
            )
        )

        val edited = original + ("binisDuragi" to "Konstablerwache")
        store.markManualFields(
            userId = "uid-a",
            localRecordId = "local-1",
            currentValues = edited,
            changedFieldIds = setOf("binisDuragi"),
            editedAtEpochMillis = now + 1
        )

        val snapshot = store.snapshotForRecord("uid-a", "local-1")!!
        assertEquals(TransitFieldSource.MANUAL, snapshot.fields.getValue("binisDuragi").provenance.source)
        assertEquals(
            TransitFieldSource.PLANNED_RMV,
            snapshot.fields.getValue("planlananBinis").provenance.source
        )
    }

    @Test
    fun `same local record id is isolated by UID`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        store.markManualFields("uid-a", "same-id", mapOf("hat" to "S5"), setOf("hat"))
        store.markManualFields("uid-b", "same-id", mapOf("hat" to "U4"), setOf("hat"))

        assertEquals(
            TransitFieldSource.MANUAL,
            store.matchingProvenance("uid-a", "same-id", mapOf("hat" to "S5"))["hat"]?.source
        )
        assertTrue(
            store.matchingProvenance("uid-a", "same-id", mapOf("hat" to "U4")).isEmpty()
        )
        assertEquals(
            TransitFieldSource.MANUAL,
            store.matchingProvenance("uid-b", "same-id", mapOf("hat" to "U4"))["hat"]?.source
        )
    }

    @Test
    fun `value fingerprint rejects metadata after an untracked value change`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        store.putKnownFields(
            "uid-a",
            "local-1",
            mapOf("hat" to "S5"),
            mapOf("hat" to provenanceUseCase.plannedRmv("hat", now))
        )

        assertTrue(
            store.matchingProvenance("uid-a", "local-1", mapOf("hat" to "S6")).isEmpty()
        )
    }

    @Test
    fun `time fingerprint ignores automatic seconds stripping`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        store.putKnownFields(
            "uid-a",
            "local-1",
            mapOf("planlananBinis" to "10:00:00"),
            mapOf("planlananBinis" to provenanceUseCase.plannedRmv("planlananBinis", now))
        )

        assertEquals(
            TransitFieldSource.PLANNED_RMV,
            store.matchingProvenance(
                "uid-a",
                "local-1",
                mapOf("planlananBinis" to "10:00")
            )["planlananBinis"]?.source
        )
    }

    @Test
    fun `invalid keys do not create hidden state`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        store.markManualFields("", "local-1", mapOf("hat" to "S5"), setOf("hat"))
        store.markManualFields("uid-a", "", mapOf("hat" to "S5"), setOf("hat"))

        assertTrue(store.snapshots.value.isEmpty())
        assertNull(store.snapshotForRecord("uid-a", "local-1"))
    }
}
