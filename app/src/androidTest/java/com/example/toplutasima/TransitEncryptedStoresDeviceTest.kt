package com.example.toplutasima

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidate
import com.example.toplutasima.transit.duplicate.TransitDuplicateDecision
import com.example.toplutasima.transit.duplicate.TransitDuplicateDecisionStore
import com.example.toplutasima.transit.duplicate.TransitDuplicateReason
import com.example.toplutasima.transit.history.TransitChangeEventDraft
import com.example.toplutasima.transit.history.TransitChangeHistoryStore
import com.example.toplutasima.transit.history.TransitChangeOperation
import com.example.toplutasima.transit.history.TransitChangeSource
import com.example.toplutasima.transit.history.TransitHistoryAppendOutcome
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitEncryptedStoresDeviceTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun clearStoresBeforeTest() = clearStores()

    @After
    fun clearStoresAfterTest() = clearStores()

    @Test
    fun changeHistorySurvivesStoreRecreationAndKeepsUidIsolation() {
        val firstStore = TransitChangeHistoryStore.create(context, enabled = true)
        val result = firstStore.append(
            TransitChangeEventDraft(
                userId = "device-user-a",
                recordId = "device-record",
                operation = TransitChangeOperation.CREATE,
                occurredAtEpochMillis = 1_752_931_200_000L,
                source = TransitChangeSource.USER,
                deduplicationKey = "device-history-create"
            )
        )
        assertTrue(
            result.outcome == TransitHistoryAppendOutcome.ADDED ||
                result.outcome == TransitHistoryAppendOutcome.ADDED_MEMORY_ONLY
        )

        val recreated = TransitChangeHistoryStore.create(context, enabled = true)
        val restored = recreated.eventsForRecord("device-user-a", "device-record")
        assertEquals(1, restored.size)
        assertEquals(TransitChangeOperation.CREATE, restored.single().operation)
        assertTrue(recreated.eventsForRecord("device-user-b", "device-record").isEmpty())
    }

    @Test
    fun duplicateDecisionSurvivesStoreRecreationAndKeepsUidIsolation() {
        val candidate = TransitDuplicateCandidate(
            firstRecordId = "first",
            secondRecordId = "second",
            similarityScore = 0.9,
            reasons = setOf(TransitDuplicateReason.SAME_SEGMENT_FINGERPRINT),
            decisionFingerprint = "device-fingerprint",
            userId = "device-user-a"
        )
        val firstStore = TransitDuplicateDecisionStore.create(context, enabled = true)
        assertTrue(
            firstStore.record(
                userId = "device-user-a",
                candidate = candidate,
                decision = TransitDuplicateDecision.KEEP_SEPARATE
            )
        )

        val recreated = TransitDuplicateDecisionStore.create(context, enabled = true)
        assertEquals(
            TransitDuplicateDecision.KEEP_SEPARATE,
            recreated.decision("device-user-a", candidate.decisionFingerprint)?.decision
        )
        assertNull(recreated.decision("device-user-b", candidate.decisionFingerprint))
    }

    private fun clearStores() {
        context.deleteSharedPreferences(HISTORY_PREFERENCES)
        context.deleteSharedPreferences(DUPLICATE_PREFERENCES)
    }

    private companion object {
        const val HISTORY_PREFERENCES = "transit_change_history"
        const val DUPLICATE_PREFERENCES = "transit_duplicate_decisions_encrypted"
    }
}
