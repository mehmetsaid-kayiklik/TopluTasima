package com.example.toplutasima

import com.example.toplutasima.network.ApiErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorsTest {
    @Test
    fun `429 keeps retry after and request id`() {
        val error = ApiErrors.fromHttpStatus(
            provider = "RMV",
            endpoint = "himsearch",
            requestId = "req-1",
            statusCode = 429,
            body = "quota",
            retryAfterHeader = "12"
        )

        assertTrue(error.isRateLimited)
        assertEquals(12L, error.retryAfterSeconds)
        assertTrue(error.message!!.contains("requestId=req-1"))
        assertTrue(error.message!!.contains("12"))
    }

    @Test
    fun `body preview is compact and capped`() {
        val body = "a\n".repeat(400)
        val error = ApiErrors.fromHttpStatus(
            provider = "RMV",
            endpoint = "trip",
            requestId = "req-2",
            statusCode = 500,
            body = body
        )

        assertTrue(error.bodyPreview.length <= 240)
        assertTrue(error.message!!.contains("HTTP 500"))
    }

    @Test
    fun `403 explains access rejection`() {
        val error = ApiErrors.fromHttpStatus(
            provider = "RMV",
            endpoint = "trip",
            requestId = "req-3",
            statusCode = 403,
            body = "forbidden"
        )

        assertEquals(403, error.statusCode)
        assertTrue(error.isAccessDenied)
        assertTrue(error.message!!.contains("HTTP 403"))
        assertTrue(error.message!!.contains("erisimi reddetti"))
    }
}
