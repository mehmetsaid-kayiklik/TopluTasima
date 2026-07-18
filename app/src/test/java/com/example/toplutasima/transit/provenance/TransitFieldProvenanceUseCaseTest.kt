package com.example.toplutasima.transit.provenance

import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.Segment
import com.example.toplutasima.usecase.TransitRecordCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitFieldProvenanceUseCaseTest {
    private val now = 1_000_000L
    private val useCase = TransitFieldProvenanceUseCase(nowEpochMillis = { now })

    @Test
    fun `departure with realtime value is live RMV`() {
        val result = useCase.departureTime(
            fieldId = "departure",
            departure = departure(planned = "10:00", realtime = "10:04"),
            observedAtEpochMillis = now
        )

        assertEquals(TransitFieldSource.LIVE_RMV, result.source)
        assertEquals(TransitFieldFreshness.FRESH, result.freshness)
        assertFalse(result.isFallback)
        assertNull(result.fallbackFor)
    }

    @Test
    fun `departure without realtime value is planned fallback`() {
        val result = useCase.departureTime(
            fieldId = "departure",
            departure = departure(planned = "10:00", realtime = ""),
            observedAtEpochMillis = now
        )

        assertEquals(TransitFieldSource.PLANNED_RMV, result.source)
        assertTrue(result.isFallback)
        assertEquals(TransitFieldSource.LIVE_RMV, result.fallbackFor)
    }

    @Test
    fun `manual field is marked as manual and fresh`() {
        val result = useCase.manual(fieldId = "line", editedAtEpochMillis = now)

        assertEquals(TransitFieldSource.MANUAL, result.source)
        assertEquals(TransitFieldFreshness.FRESH, result.freshness)
        assertFalse(result.isFallback)
    }

    @Test
    fun `cached live value keeps backing source and becomes stale`() {
        val result = useCase.departureTime(
            fieldId = "departure",
            departure = departure(planned = "10:00", realtime = "10:04"),
            observedAtEpochMillis = now - 11 * 60_000L,
            isFromCache = true
        )

        assertEquals(TransitFieldSource.CACHE, result.source)
        assertEquals(TransitFieldSource.LIVE_RMV, result.backingSource)
        assertEquals(TransitFieldFreshness.STALE, result.freshness)
        assertTrue(result.isFallback)
    }

    @Test
    fun `missing value has unknown source and freshness`() {
        val result = useCase.departureTime(fieldId = "departure", departure = null)

        assertEquals(TransitFieldSource.UNKNOWN, result.source)
        assertEquals(TransitFieldFreshness.UNKNOWN, result.freshness)
        assertNull(result.lastUpdatedAtEpochMillis)
        assertFalse(result.isFallback)
    }

    @Test
    fun `transit location result is represented without GPS dependency`() {
        val result = useCase.transitLocation(
            fieldId = "nearbyStop",
            hasLocationResult = true,
            observedAtEpochMillis = now
        )

        assertEquals(TransitFieldSource.TRANSIT_LOCATION, result.source)
        assertEquals(TransitFieldFreshness.FRESH, result.freshness)
    }

    @Test
    fun `segment distance uses ORS by default`() {
        val result = useCase.segmentDistance(
            fieldId = "distance",
            segment = segment(orsDistanceKm = 4.2, rmvDistanceKm = 4.0),
            observedAtEpochMillis = now
        )

        assertEquals(TransitFieldSource.ORS_DISTANCE, result.source)
        assertFalse(result.isFallback)
    }

    @Test
    fun `segment distance falls back to RMV when ORS is unavailable`() {
        val result = useCase.segmentDistance(
            fieldId = "distance",
            segment = segment(orsDistanceKm = 0.0, rmvDistanceKm = 4.0),
            observedAtEpochMillis = now
        )

        assertEquals(TransitFieldSource.RMV_DISTANCE, result.source)
        assertTrue(result.isFallback)
        assertEquals(TransitFieldSource.ORS_DISTANCE, result.fallbackFor)
    }

    @Test
    fun `record distance reuses existing transit distance fields`() {
        val result = useCase.recordDistance(
            fieldId = "distance",
            record = mapOf(
                TransitRecordCalculations.FIELD_ORS_DISTANCE_KM to 0.0,
                TransitRecordCalculations.FIELD_RMV_DISTANCE_KM to 12.5,
                TransitRecordCalculations.FIELD_RMV_DISTANCE_UPDATED_AT to now
            )
        )

        assertEquals(TransitFieldSource.RMV_DISTANCE, result.source)
        assertEquals(now, result.lastUpdatedAtEpochMillis)
        assertTrue(result.isFallback)
    }

    @Test
    fun `future clock skew is clamped and remains fresh`() {
        val result = useCase.manual(fieldId = "line", editedAtEpochMillis = now + 60_000L)

        assertEquals(TransitFieldFreshness.FRESH, result.freshness)
    }

    private fun departure(planned: String, realtime: String): Departure = Departure(
        line = "S5",
        direction = "Friedrichsdorf",
        time = planned,
        track = "1",
        typeTr = "S-Bahn",
        realtime = realtime
    )

    private fun segment(orsDistanceKm: Double, rmvDistanceKm: Double?): Segment = Segment(
        typeTr = "S-Bahn",
        line = "S5",
        direction = "Friedrichsdorf",
        fromStop = "Frankfurt Hbf",
        toStop = "Bad Homburg",
        dep = "10:00",
        arr = "10:20",
        distanceKm = orsDistanceKm,
        polyDistanceKm = rmvDistanceKm
    )
}
