package com.example.toplutasima.network.rmv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RmvDistanceServiceTest {
    private val service = RmvDistanceService(
        railDistanceCalculator = { 8.5 }
    )

    @Test
    fun `rail distance returns API and HAFAS polyline for Hauptwache to Willy Brandt Platz`() = runBlocking {
        val hauptwache = Pair(50.113963, 8.679292)
        val willyBrandtPlatz = Pair(50.108992, 8.673898)
        val polyline = listOf(
            hauptwache,
            Pair(50.112950, 8.679300),
            Pair(50.111300, 8.677500),
            Pair(50.109800, 8.675100),
            willyBrandtPlatz
        )

        val result = service.calculateDistanceRail(
            coords = listOf(hauptwache, willyBrandtPlatz),
            polylineCoords = polyline
        )

        assertEquals(8.5, result.apiDistanceKm ?: 0.0, 0.01)
        assertEquals(0.70, result.polyDistanceKm ?: 0.0, 0.02)
    }

    @Test
    fun `rail distance slices full polyline to multi stop segment`() = runBlocking {
        val before = Pair(50.000000, 8.000000)
        val from = Pair(50.010000, 8.000000)
        val middle = Pair(50.020000, 8.000000)
        val to = Pair(50.030000, 8.000000)
        val after = Pair(50.040000, 8.000000)
        val fullPolyline = listOf(
            before,
            from,
            Pair(50.015000, 8.001000),
            middle,
            Pair(50.025000, 8.001000),
            to,
            after
        )
        val fullRouteDistance = service.calculateDistanceRail(
            coords = listOf(before, after),
            polylineCoords = fullPolyline
        )

        val segmentDistance = service.calculateDistanceRail(
            coords = listOf(from, middle, to),
            allStopCoords = listOf(before, from, middle, to, after),
            fromIdx = 1,
            toIdx = 3,
            polylineCoords = fullPolyline
        )

        assertEquals(2.26, segmentDistance.polyDistanceKm ?: 0.0, 0.05)
        assertTrue(
            "segment should not include pre/post segment polyline",
            (segmentDistance.polyDistanceKm ?: 0.0) < (fullRouteDistance.polyDistanceKm ?: 0.0)
        )
    }

    @Test
    fun `rail distance returns null poly result when polyline is missing`() = runBlocking {
        val from = Pair(50.113963, 8.679292)
        val to = Pair(50.108992, 8.673898)

        val result = service.calculateDistanceRail(
            coords = listOf(from, to),
            polylineCoords = emptyList()
        )

        assertEquals(8.5, result.apiDistanceKm ?: 0.0, 0.01)
        assertNull(result.polyDistanceKm)
    }

    @Test
    fun `rail distance rejects suspicious polyline above guard threshold`() = runBlocking {
        val from = Pair(50.113963, 8.679292)
        val to = Pair(50.108992, 8.673898)
        val suspiciousPolyline = listOf(
            from,
            Pair(50.200000, 8.800000),
            Pair(50.150000, 8.750000),
            to
        )

        val result = service.calculateDistanceRail(
            coords = listOf(from, to),
            polylineCoords = suspiciousPolyline
        )

        assertEquals(8.5, result.apiDistanceKm ?: 0.0, 0.01)
        assertNull(result.polyDistanceKm)
    }
}
