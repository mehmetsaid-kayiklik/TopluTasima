package com.example.toplutasima.transit.provenance

import com.example.toplutasima.model.Departure
import com.example.toplutasima.usecase.TransitRecordCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRecordProvenanceResolverTest {
    private val now = 5_000_000L
    private val provenanceUseCase = TransitFieldProvenanceUseCase(nowEpochMillis = { now })
    private val resolver = TransitRecordProvenanceResolver(provenanceUseCase)

    @Test
    fun `old record without evidence remains unknown`() {
        val result = resolver.resolveFields(
            userId = "uid-a",
            localRecordId = "old-1",
            record = mapOf("hat" to "S5"),
            fieldIds = setOf("hat")
        )

        assertEquals(TransitFieldSource.UNKNOWN, result.getValue("hat").source)
        assertEquals(TransitFieldFreshness.UNKNOWN, result.getValue("hat").freshness)
    }

    @Test
    fun `exact session metadata wins over historical inference`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        val row = mapOf(
            "mesafe" to "5.00 km",
            TransitRecordCalculations.FIELD_ORS_DISTANCE_KM to 5.0
        )
        store.markManualFields("uid-a", "local-1", row, setOf("mesafe"), now)

        val result = resolver.resolveFields(
            "uid-a",
            "local-1",
            row,
            store,
            fieldIds = setOf("mesafe")
        )

        assertEquals(TransitFieldSource.MANUAL, result.getValue("mesafe").source)
    }

    @Test
    fun `explicit ORS and RMV fields are evidence but legacy distance text is not`() {
        val ors = resolver.resolveFields(
            "uid-a",
            "ors-1",
            mapOf(
                "mesafe" to "5.00 km",
                TransitRecordCalculations.FIELD_ORS_DISTANCE_KM to 5.0
            ),
            fieldIds = setOf("mesafe")
        )
        val rmv = resolver.resolveFields(
            "uid-a",
            "rmv-1",
            mapOf(
                TransitRecordCalculations.FIELD_RMV_DISTANCE_KM to 5.0
            ),
            fieldIds = setOf(TransitRecordCalculations.FIELD_RMV_DISTANCE_KM)
        )
        val legacy = resolver.resolveFields(
            "uid-a",
            "legacy-1",
            mapOf("mesafe" to "5.00 km"),
            fieldIds = setOf("mesafe")
        )

        assertEquals(TransitFieldSource.ORS_DISTANCE, ors.getValue("mesafe").source)
        assertEquals(
            TransitFieldSource.RMV_DISTANCE,
            rmv.getValue(TransitRecordCalculations.FIELD_RMV_DISTANCE_KM).source
        )
        assertEquals(TransitFieldSource.UNKNOWN, legacy.getValue("mesafe").source)
    }

    @Test
    fun `cache keeps backing source and planned fallback distinction`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        val cachedFallback = provenanceUseCase.departureTime(
            fieldId = "planlananBinis",
            departure = Departure(
                line = "S5",
                direction = "Friedrichsdorf",
                time = "10:00",
                track = "1",
                typeTr = "S-Bahn",
                realtime = ""
            ),
            observedAtEpochMillis = now,
            isFromCache = true
        )
        val row = mapOf("planlananBinis" to "10:00")
        store.putKnownFields(
            "uid-a",
            "local-1",
            row,
            mapOf("planlananBinis" to cachedFallback)
        )

        val result = resolver.resolveFields(
            "uid-a",
            "local-1",
            row,
            store,
            fieldIds = setOf("planlananBinis")
        ).getValue("planlananBinis")

        assertEquals(TransitFieldSource.CACHE, result.source)
        assertEquals(TransitFieldSource.PLANNED_RMV, result.backingSource)
        assertEquals(TransitFieldSource.LIVE_RMV, result.fallbackFor)
        assertTrue(result.isFallback)
    }

    @Test
    fun `future device clock skew does not create negative freshness`() {
        val store = TransitRecordProvenanceStore(nowEpochMillis = { now })
        val row = mapOf("hat" to "S5")
        store.markManualFields("uid-a", "local-1", row, setOf("hat"), now + 60_000L)

        val result = resolver.resolveFields(
            "uid-a",
            "local-1",
            row,
            store,
            fieldIds = setOf("hat")
        ).getValue("hat")

        assertEquals(TransitFieldFreshness.FRESH, result.freshness)
    }

    @Test
    fun `disabled provenance returns no metadata and leaves record flow untouched`() {
        val result = resolver.resolveFields(
            "uid-a",
            "local-1",
            mapOf("hat" to "S5"),
            fieldIds = setOf("hat"),
            enabled = false
        )

        assertTrue(result.isEmpty())
        assertFalse(result.containsKey("hat"))
    }
}
