package com.example.toplutasima.transit.duplicate

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.transit.provenance.TransitFieldFreshness
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitDuplicateMergeUseCaseTest {
    private val useCase = TransitDuplicateMergeUseCase()

    @Test
    fun `field selection merges values while preserving target identity`() {
        val preview = useCase.preview(
            first = trip("first").copy(not = "first note", gercekInis = "08:45"),
            second = trip("second").copy(not = "better note", gercekInis = "08:42"),
            selection = TransitDuplicateMergeSelection(
                mapOf("not" to TransitMergeValueSource.SECOND, "gercekInis" to TransitMergeValueSource.SECOND)
            )
        )
        assertNotNull(preview)
        assertEquals("first", preview!!.mergedRecord.id)
        assertEquals("better note", preview.mergedRecord.not)
        assertEquals("08:42", preview.mergedRecord.gercekInis)
        assertEquals("second", preview.sourceRecordIdToDelete)
    }

    @Test
    fun `actual time is never silently filled from planned time`() {
        val preview = useCase.preview(
            trip("first").copy(gercekBinis = null),
            trip("second").copy(planlananBinis = "09:30", gercekBinis = null),
            TransitDuplicateMergeSelection(mapOf("planlananBinis" to TransitMergeValueSource.SECOND))
        )!!
        assertEquals("09:30", preview.mergedRecord.planlananBinis)
        assertTrue(preview.mergedRecord.gercekBinis.isNullOrBlank())
    }

    @Test
    fun `critical validation blocks merge`() {
        val preview = useCase.preview(
            trip("first"),
            trip("second").copy(inisDuragi = "Start"),
            TransitDuplicateMergeSelection(mapOf("inisDuragi" to TransitMergeValueSource.SECOND))
        )!!
        assertTrue(preview.criticalIssues.isNotEmpty())
        assertFalse(preview.canApply)
    }

    @Test
    fun `warnings require explicit acknowledgement`() {
        val preview = useCase.preview(
            trip("first"),
            trip("second").copy(gercekBinis = null, gercekInis = null),
            TransitDuplicateMergeSelection(
                mapOf(
                    "gercekBinis" to TransitMergeValueSource.SECOND,
                    "gercekInis" to TransitMergeValueSource.SECOND
                )
            )
        )!!
        assertTrue(preview.pendingWarnings.isNotEmpty())
        val accepted = useCase.acknowledgeWarnings(preview, preview.pendingWarnings.mapTo(mutableSetOf()) { it.id })
        assertTrue(accepted.canApply)
    }

    @Test
    fun `selected field carries its matching provenance`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { 1_000L })
        val first = trip("first").copy(not = "manual")
        val second = trip("second").copy(not = "rmv")
        store.putKnownFields(
            "uid", "second", mapOf("not" to "rmv"),
            mapOf("not" to TransitFieldProvenance("not", TransitFieldSource.PLANNED_RMV, 900L, TransitFieldFreshness.FRESH))
        )
        val preview = useCase.preview(
            first,
            second,
            TransitDuplicateMergeSelection(mapOf("not" to TransitMergeValueSource.SECOND)),
            store
        )!!
        assertEquals(TransitFieldSource.PLANNED_RMV, preview.selectedProvenanceByField.getValue("not").source)
    }

    @Test
    fun `disabled merge feature produces no preview`() {
        assertTrue(
            TransitDuplicateMergeUseCase(enabled = false).preview(
                trip("first"), trip("second"), TransitDuplicateMergeSelection(emptyMap())
            ) == null
        )
    }

    private fun trip(id: String) = TripEntity(
        id = id,
        userId = "uid",
        tarih = "18.07.2026",
        tur = "S-Bahn",
        hat = "S8",
        binisDuragi = "Start",
        inisDuragi = "End",
        planlananBinis = "08:00",
        planlananInis = "08:40",
        gercekBinis = "08:02",
        gercekInis = "08:43",
        mesafe = "35.0 km"
    )
}
