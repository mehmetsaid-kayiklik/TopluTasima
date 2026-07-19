package com.example.toplutasima.usecase.transit

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.domain.transit.health.TransitHealthIssueCode
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidateUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitPostSaveHealthUseCaseTest {
    private val useCase = TransitPostSaveHealthUseCase()

    @Test
    fun `possible duplicate candidates are reported for both local records`() {
        val result = useCase.scan(
            listOf(healthyTrip("one"), healthyTrip("two")),
            provenanceEnabled = false
        )

        val duplicates = result.issues.filter { it.code == TransitHealthIssueCode.POSSIBLE_DUPLICATE }
        assertEquals(setOf("one", "two"), duplicates.map { it.localRecordId }.toSet())
        assertTrue(duplicates.all { it.severity == TransitHealthSeverity.WARNING })
    }

    @Test
    fun `disabled post-save health duplicate detector emits no duplicate warning`() {
        val disabledDuplicateUseCase = TransitPostSaveHealthUseCase(
            duplicateCandidateUseCase = TransitDuplicateCandidateUseCase(enabled = false)
        )

        val result = disabledDuplicateUseCase.scan(
            listOf(healthyTrip("one"), healthyTrip("two")),
            provenanceEnabled = false
        )

        assertFalse(result.issues.any { it.code == TransitHealthIssueCode.POSSIBLE_DUPLICATE })
    }

    @Test
    fun `overlapping records of the same segment are reported`() {
        val first = healthyTrip("one").copy(
            planlananBinis = "10:00",
            planlananInis = "10:30",
            gercekBinis = "10:00",
            gercekInis = "10:30",
            planlananYolSuresi = "30",
            gercekYolSuresi = "30"
        )
        val second = healthyTrip("two").copy(
            planlananBinis = "10:15",
            planlananInis = "10:45",
            gercekBinis = "10:15",
            gercekInis = "10:45",
            planlananYolSuresi = "30",
            gercekYolSuresi = "30"
        )

        val overlaps = useCase.scan(listOf(first, second), provenanceEnabled = false)
            .issues.filter { it.code == TransitHealthIssueCode.OVERLAPPING_SEGMENT }

        assertEquals(setOf("one", "two"), overlaps.map { it.localRecordId }.toSet())
        assertTrue(overlaps.all { it.relatedRecordIds.isNotEmpty() })
    }

    @Test
    fun `valid journey crossing midnight is not treated as reversed`() {
        val trip = healthyTrip("midnight").copy(
            planlananBinis = "23:50",
            planlananInis = "00:20",
            gercekBinis = "23:50",
            gercekInis = "00:20",
            planlananYolSuresi = "30",
            gercekYolSuresi = "30"
        )

        val codes = useCase.scan(listOf(trip), provenanceEnabled = false).issues.map { it.code }

        assertFalse(TransitHealthIssueCode.PLANNED_TIME_ORDER in codes)
        assertFalse(TransitHealthIssueCode.ACTUAL_TIME_ORDER in codes)
        assertFalse(TransitHealthIssueCode.UNUSUAL_DURATION in codes)
    }

    @Test
    fun `genuinely reversed actual time is critical`() {
        val trip = healthyTrip("reversed").copy(
            gercekBinis = "18:00",
            gercekInis = "17:00",
            gercekYolSuresi = null
        )

        val issue = useCase.scan(listOf(trip), provenanceEnabled = false).issues.first {
            it.code == TransitHealthIssueCode.ACTUAL_TIME_ORDER
        }

        assertEquals(TransitHealthSeverity.CRITICAL, issue.severity)
    }

    @Test
    fun `negative stored duration is critical and receives a safe proposal`() {
        val trip = healthyTrip("negative").copy(gercekYolSuresi = "-5")

        val result = useCase.scan(listOf(trip), provenanceEnabled = false)

        assertTrue(result.issues.any {
            it.code == TransitHealthIssueCode.NEGATIVE_DURATION &&
                it.severity == TransitHealthSeverity.CRITICAL
        })
        assertEquals("30", result.corrections.single().fields["gercekYolSuresi"])
    }

    @Test
    fun `unusually long but parseable duration remains a warning`() {
        val trip = healthyTrip("long").copy(
            planlananBinis = "10:00",
            planlananInis = "17:00",
            gercekBinis = "10:00",
            gercekInis = "17:00",
            planlananYolSuresi = "420",
            gercekYolSuresi = "420"
        )

        val issues = useCase.scan(listOf(trip), provenanceEnabled = false).issues.filter {
            it.code == TransitHealthIssueCode.UNUSUAL_DURATION
        }

        assertTrue(issues.isNotEmpty())
        assertTrue(issues.all { it.severity == TransitHealthSeverity.WARNING })
    }

    @Test
    fun `unusually low and high distances are reported`() {
        val low = healthyTrip("low").copy(mesafe = "0.01 km", orsMesafeKm = 0.01)
        val high = healthyTrip("high").copy(
            hat = "S6",
            planlananBinis = "11:00",
            planlananInis = "11:30",
            gercekBinis = "11:00",
            gercekInis = "11:30",
            mesafe = "750 km",
            orsMesafeKm = 750.0
        )

        val issues = useCase.scan(listOf(low, high), provenanceEnabled = false).issues
            .filter { it.code == TransitHealthIssueCode.EXTREME_DISTANCE }

        assertEquals(setOf("low", "high"), issues.map { it.localRecordId }.toSet())
    }

    @Test
    fun `missing actual times remain a warning that needs user review`() {
        val trip = healthyTrip("missing").copy(
            gercekBinis = "",
            gercekInis = "",
            gercekYolSuresi = null
        )

        val issue = useCase.scan(listOf(trip), provenanceEnabled = false).issues.first {
            it.code == TransitHealthIssueCode.MISSING_ACTUAL_TIME
        }

        assertEquals(TransitHealthSeverity.WARNING, issue.severity)
    }

    @Test
    fun `same stop and RMV ORS mismatch reuse Sprint 1 validation`() {
        val trip = healthyTrip("invalid").copy(
            inisDuragi = "Frankfurt Hbf",
            toStopId = "stop-a",
            orsMesafeKm = 10.0,
            rmvMesafeKm = 2.0
        )

        val codes = useCase.scan(listOf(trip), provenanceEnabled = false).issues.map { it.code }

        assertTrue(TransitHealthIssueCode.SAME_STOP in codes)
        assertTrue(TransitHealthIssueCode.ROUTE_DISTANCE_MISMATCH in codes)
    }

    @Test
    fun `unknown historical provenance is informational and does not block validation`() {
        val result = useCase.scan(listOf(healthyTrip("old")), provenanceEnabled = true)
        val issue = result.issues.first { it.code == TransitHealthIssueCode.UNKNOWN_PROVENANCE }

        assertEquals(TransitHealthSeverity.INFO, issue.severity)
        assertTrue(result.corrections.isEmpty())
    }

    @Test(timeout = 10_000L)
    fun `ten thousand record fixture completes without quadratic overlap work`() {
        val records = List(10_000) { index ->
            healthyTrip("large-$index").copy(
                hat = "line-$index",
                planlananYolSuresi = null,
                gercekYolSuresi = null
            )
        }

        val result = useCase.scan(records, provenanceEnabled = false)

        assertEquals(10_000, result.scannedRecordCount)
        assertFalse(result.issues.any { it.code == TransitHealthIssueCode.POSSIBLE_DUPLICATE })
        assertFalse(result.issues.any { it.code == TransitHealthIssueCode.OVERLAPPING_SEGMENT })
    }

    private fun healthyTrip(id: String): TripEntity = TripEntity(
        id = id,
        firestoreDocId = "doc-$id",
        tarih = "22.05.2026",
        gun = "Cuma",
        tur = "S-Bahn",
        hat = "S5",
        yon = "Friedrichsdorf",
        binisDuragi = "Frankfurt Hbf",
        planlananBinis = "10:00",
        gercekBinis = "10:00",
        inisDuragi = "Bad Homburg",
        planlananInis = "10:30",
        gercekInis = "10:30",
        planlananYolSuresi = "30",
        gercekYolSuresi = "30",
        mesafe = "12.00 km",
        orsMesafeKm = 12.0,
        rmvMesafeKm = 11.8,
        fromStopId = "stop-a",
        toStopId = "stop-b",
        yearMonth = "2026-05",
        sortDate = "2026-05-22",
        userId = "uid-a"
    )
}
