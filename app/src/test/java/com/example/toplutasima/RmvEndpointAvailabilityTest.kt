package com.example.toplutasima

import com.example.toplutasima.network.ApiErrors
import com.example.toplutasima.network.EndpointSupportState
import com.example.toplutasima.network.RmvEndpointAvailability
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RmvEndpointAvailabilityTest {
    @After
    fun tearDown() {
        RmvEndpointAvailability.clear()
    }

    @Test
    fun `starts unknown and can be marked supported`() {
        assertEquals(EndpointSupportState.UNKNOWN, RmvEndpointAvailability.state("himsearch"))

        RmvEndpointAvailability.markSupported("himsearch")

        assertEquals(EndpointSupportState.SUPPORTED, RmvEndpointAvailability.state("himsearch"))
        assertFalse(RmvEndpointAvailability.isUnavailable("himsearch"))
    }

    @Test
    fun `unsupported http marks endpoint unavailable`() {
        val error = ApiErrors.fromHttpStatus(
            provider = "RMV",
            endpoint = "journeyTrackMatch",
            requestId = "req",
            statusCode = 404,
            body = ""
        )

        RmvEndpointAvailability.markFromException("journeyTrackMatch", error)

        assertTrue(RmvEndpointAvailability.isUnavailable("journeyTrackMatch"))
    }
}
