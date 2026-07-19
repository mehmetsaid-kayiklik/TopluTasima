package com.example.toplutasima.transit.duplicate

import com.example.toplutasima.data.local.entity.TripEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitDuplicateCandidateUseCaseTest {
    private val useCase = TransitDuplicateCandidateUseCase()

    @Test
    fun `exact duplicate produces one explainable candidate`() {
        val candidates = useCase.findCandidates(listOf(trip("a"), trip("b")))
        assertEquals(1, candidates.size)
        assertTrue(TransitDuplicateReason.SAME_LINE in candidates.single().reasons)
        assertTrue(TransitDuplicateReason.SAME_SEGMENT_FINGERPRINT in candidates.single().reasons)
    }

    @Test
    fun `similar route with different known lines and journeys stays separate`() {
        val first = trip("a").copy(hat = "S8", journeyRef = "j1")
        val second = trip("b").copy(hat = "RE5", journeyRef = "j2")
        assertTrue(useCase.findCandidates(listOf(first, second)).isEmpty())
    }

    @Test
    fun `same date with a different route stays separate`() {
        val second = trip("b").copy(
            binisDuragi = "Darmstadt Hbf",
            inisDuragi = "Wiesbaden Hbf",
            fromStopId = "different-from",
            toStopId = "different-to"
        )

        assertTrue(useCase.findCandidates(listOf(trip("a"), second)).isEmpty())
    }

    @Test
    fun `same route on a different day stays separate`() {
        val second = trip("b").copy(tarih = "19.07.2026")

        assertTrue(useCase.findCandidates(listOf(trip("a"), second)).isEmpty())
    }

    @Test
    fun `cross-midnight nearby times are compared safely`() {
        val first = trip("a").copy(planlananBinis = "23:58", planlananInis = "00:30")
        val second = trip("b").copy(planlananBinis = "00:02", planlananInis = "00:32")
        assertEquals(1, useCase.findCandidates(listOf(first, second)).size)
    }

    @Test
    fun `manual and automatic records expose that reason`() {
        val first = trip("a").copy(journeyRef = null)
        val second = trip("b").copy(journeyRef = "rmv-journey")
        val candidate = useCase.findCandidates(listOf(first, second)).single()
        assertTrue(TransitDuplicateReason.MANUAL_AND_AUTOMATIC_PAIR in candidate.reasons)
    }

    @Test
    fun `incomplete and complete records expose complementary completeness`() {
        val incomplete = trip("a").copy(
            gercekBinis = null,
            gercekInis = null,
            mesafe = null
        )
        val candidate = useCase.findCandidates(listOf(incomplete, trip("b"))).single()

        assertTrue(TransitDuplicateReason.COMPLEMENTARY_COMPLETENESS in candidate.reasons)
    }

    @Test
    fun `excluded tombstone record never becomes a candidate`() {
        assertTrue(
            useCase.findCandidates(
                listOf(trip("a"), trip("b")),
                excludedRecordIds = setOf("b")
            ).isEmpty()
        )
    }

    @Test
    fun `keep-separate decision suppresses only unchanged fingerprint`() {
        val original = useCase.findCandidates(listOf(trip("a"), trip("b"))).single()
        val lookup = TransitDuplicateDecisionLookup { _, fingerprint ->
            fingerprint == original.decisionFingerprint
        }
        assertTrue(useCase.findCandidates(listOf(trip("a"), trip("b")), decisionLookup = lookup).isEmpty())

        val changed = useCase.findCandidates(
            listOf(trip("a"), trip("b").copy(not = "meaningful change")),
            decisionLookup = lookup
        ).single()
        assertNotEquals(original.decisionFingerprint, changed.decisionFingerprint)
    }

    @Test
    fun `stale candidate no longer matches changed source records`() {
        val first = trip("a")
        val second = trip("b")
        val candidate = useCase.findCandidates(listOf(first, second)).single()

        assertTrue(useCase.matchesCurrentRecords(candidate, first, second))
        assertFalse(useCase.matchesCurrentRecords(candidate, first, second.copy(not = "changed")))
    }

    @Test
    fun `same record ID under different UIDs never collides`() {
        assertTrue(useCase.findCandidates(listOf(trip("same", "uid-a"), trip("same", "uid-b"))).isEmpty())
    }

    @Test
    fun `disabled feature returns no resolution candidate`() {
        assertTrue(TransitDuplicateCandidateUseCase(enabled = false).findCandidates(listOf(trip("a"), trip("b"))).isEmpty())
    }

    private fun trip(id: String, userId: String = "uid") = TripEntity(
        id = id,
        userId = userId,
        tarih = "18.07.2026",
        hat = "S8",
        binisDuragi = "Frankfurt Hbf",
        inisDuragi = "Mainz Hbf",
        planlananBinis = "08:00",
        planlananInis = "08:40",
        gercekBinis = "08:02",
        gercekInis = "08:43",
        mesafe = "35.0 km",
        fromStopId = "from",
        toStopId = "to"
    )
}
