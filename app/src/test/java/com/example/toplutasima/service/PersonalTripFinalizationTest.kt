package com.example.toplutasima.service

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalTripFinalizationTest {
    @Test
    fun `trip is finalized only after the final distance batch is included`() = runBlocking {
        var distanceKm = 8.0
        val flushStarted = CompletableDeferred<Unit>()
        val releaseFinalBatch = CompletableDeferred<Unit>()
        val finalized = CompletableDeferred<PersonalTripFinalDistanceResult>()

        val finalizationJob = async(Dispatchers.Default) {
            completePersonalTripFinalization(
                timeoutMillis = 1_000L,
                flushFinalDistance = {
                    flushStarted.complete(Unit)
                    releaseFinalBatch.await()
                    distanceKm += 1.25
                    true
                },
                currentDistanceKm = { distanceKm },
                logFailure = { _, _ -> },
                onFinalized = finalized::complete
            )
        }

        flushStarted.await()
        assertFalse(finalized.isCompleted)

        releaseFinalBatch.complete(Unit)
        finalizationJob.await()

        assertTrue(finalized.await().flushSucceeded)
        assertEquals(9.25, finalized.await().distanceKm, 0.0)
    }

    @Test
    fun `finalization times out gracefully and still publishes the current distance`() = runBlocking {
        val logMessages = mutableListOf<String>()
        var finalized: PersonalTripFinalDistanceResult? = null

        completePersonalTripFinalization(
            timeoutMillis = 25L,
            flushFinalDistance = { awaitCancellation() },
            currentDistanceKm = { 6.75 },
            logFailure = { message, _ -> logMessages += message },
            onFinalized = { finalized = it }
        )

        val result = requireNotNull(finalized)
        assertFalse(result.flushSucceeded)
        assertEquals(6.75, result.distanceKm, 0.0)
        assertTrue(logMessages.single().contains("timed out"))
    }

    @Test
    fun `finalization logs a failed final batch before publishing completion`() = runBlocking {
        val failure = IllegalStateException("ORS unavailable")
        var loggedFailure: Throwable? = null
        var finalized: PersonalTripFinalDistanceResult? = null

        completePersonalTripFinalization(
            timeoutMillis = 1_000L,
            flushFinalDistance = { throw failure },
            currentDistanceKm = { 3.5 },
            logFailure = { _, error -> loggedFailure = error },
            onFinalized = { finalized = it }
        )

        assertTrue(loggedFailure is IllegalStateException)
        assertEquals(failure.message, loggedFailure?.message)
        val result = requireNotNull(finalized)
        assertFalse(result.flushSucceeded)
        assertEquals(3.5, result.distanceKm, 0.0)
    }
}
