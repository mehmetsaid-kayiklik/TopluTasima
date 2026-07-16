package com.example.toplutasima.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class LocalTripRepositoryDeleteTest {

    @Test
    fun `remote failure leaves local trip untouched`() = runBlocking {
        val remoteFailure = IllegalStateException("Firestore unavailable")
        var localDeleteCalled = false

        try {
            deleteRemoteTripThenLocal(
                deleteRemote = { throw remoteFailure },
                deleteLocal = { localDeleteCalled = true }
            )
            fail("Expected the remote failure to reach the caller")
        } catch (actual: IllegalStateException) {
            assertSame(remoteFailure, actual)
        }

        assertFalse(localDeleteCalled)
    }

    @Test
    fun `successful remote delete is followed by local delete`() = runBlocking {
        val calls = mutableListOf<String>()

        deleteRemoteTripThenLocal(
            deleteRemote = { calls += "remote" },
            deleteLocal = { calls += "local" }
        )

        assertEquals(listOf("remote", "local"), calls)
    }
}
