package com.example.toplutasima.transit.duplicate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitDuplicateDecisionStoreTest {
    @Test
    fun `decision is UID scoped idempotent and restored`() {
        var raw: String? = null
        val candidate = candidate("fp")
        val first = store({ raw }, { raw = it })
        assertTrue(first.record("uid-a", candidate, TransitDuplicateDecision.KEEP_SEPARATE))
        assertTrue(first.record("uid-a", candidate, TransitDuplicateDecision.KEEP_SEPARATE))
        val restored = store({ raw }, { raw = it })
        assertEquals(1, restored.decisionsForUser("uid-a").size)
        assertTrue(restored.isKeptSeparate("uid-a", "fp"))
        assertFalse(restored.isKeptSeparate("uid-b", "fp"))
    }

    @Test
    fun `changed fingerprint is not suppressed`() {
        var raw: String? = null
        val store = store({ raw }, { raw = it })
        store.record("uid", candidate("old"), TransitDuplicateDecision.KEEP_SEPARATE)
        assertFalse(store.isKeptSeparate("uid", "new"))
    }

    @Test
    fun `corrupt storage safely behaves as empty`() {
        val store = store({ "not-json" }, { })
        assertTrue(store.decisionsForUser("uid").isEmpty())
        assertNull(store.decision("uid", "fp"))
    }

    @Test
    fun `disabled feature does not write`() {
        var writes = 0
        val store = TransitDuplicateDecisionStore({ null }, { writes++ }, enabled = false)
        assertFalse(store.record("uid", candidate("fp"), TransitDuplicateDecision.KEEP_SEPARATE))
        assertEquals(0, writes)
    }

    private fun candidate(fingerprint: String) = TransitDuplicateCandidate(
        "a", "b", 0.9, setOf(TransitDuplicateReason.SAME_DATE), fingerprint
    )

    private fun store(read: () -> String?, write: (String) -> Unit) =
        TransitDuplicateDecisionStore(read, write, enabled = true)
}
