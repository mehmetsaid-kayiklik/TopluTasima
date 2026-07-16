package com.example.toplutasima.network.rmv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RmvJourneyServiceTest {
    @Test
    fun `blank direction does not match the destination fallback`() {
        val matches = departureDirectionMatchesDestination(
            direction = "",
            destinationDisplayName = "Frankfurt Hauptbahnhof"
        )

        assertFalse(matches)
    }

    @Test
    fun `next day departure after midnight is not treated as past`() {
        val isPast = departureOccursBeforeRequest(
            requestDate = "2026-07-15",
            requestTime = "23:50",
            departureDate = "2026-07-16",
            departureTime = "00:05"
        )

        assertFalse(isPast)
        assertTrue(
            departureOccursBeforeRequest(
                requestDate = "2026-07-15",
                requestTime = "23:50",
                departureDate = "2026-07-15",
                departureTime = "00:05"
            )
        )
    }
}
