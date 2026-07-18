package com.example.toplutasima.usecase.transit

import com.example.toplutasima.domain.transit.validation.TransitRecordSegmentInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationInput
import com.example.toplutasima.domain.transit.validation.ValidationIssueCode
import com.example.toplutasima.domain.transit.validation.ValidationSeverity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitRecordValidationUseCaseTest {
    private val useCase = TransitRecordValidationUseCase()

    @Test
    fun `normal transit record can be saved`() {
        val result = validate(healthySegment(), actualTimesRequired = true)

        assertTrue(result.issues.isEmpty())
        assertTrue(result.canSave)
    }

    @Test
    fun `same boarding and alighting stop is critical`() {
        val result = validate(
            healthySegment().copy(
                boardingStop = "Frankfurt Hauptbahnhof",
                alightingStop = "  frankfurt   hauptbahnhof "
            )
        )

        assertTrue(result.issues.any {
            it.code == ValidationIssueCode.SAME_STOP &&
                it.severity == ValidationSeverity.CRITICAL
        })
        assertFalse(result.canSave)
    }

    @Test
    fun `negative same-day time order is critical`() {
        val result = validate(
            healthySegment().copy(
                actualDeparture = "10:00",
                actualArrival = "09:45"
            ),
            actualTimesRequired = true
        )

        assertTrue(result.issues.any {
            it.code == ValidationIssueCode.ACTUAL_TIME_ORDER &&
                it.severity == ValidationSeverity.CRITICAL
        })
        assertFalse(result.canSave)
    }

    @Test
    fun `negative stored duration is critical`() {
        val result = validate(
            healthySegment().copy(storedActualDurationMinutes = -5),
            actualTimesRequired = true
        )

        assertTrue(result.issues.any { it.code == ValidationIssueCode.NEGATIVE_DURATION })
        assertFalse(result.canSave)
    }

    @Test
    fun `valid journey crossing midnight has no false positive`() {
        val result = validate(
            healthySegment().copy(
                plannedDeparture = "23:45",
                plannedArrival = "00:15",
                actualDeparture = "23:50",
                actualArrival = "00:20",
                storedPlannedDurationMinutes = 30,
                storedActualDurationMinutes = 30
            ),
            actualTimesRequired = true
        )

        assertTrue(result.issues.isEmpty())
        assertTrue(result.canSave)
    }

    @Test
    fun `missing required actual times asks for conscious confirmation`() {
        val result = validate(
            healthySegment().copy(actualDeparture = "", actualArrival = ""),
            actualTimesRequired = true
        )

        assertTrue(result.issues.any { it.code == ValidationIssueCode.MISSING_ACTUAL_TIMES })
        assertTrue(result.requiresUserConfirmation)
        assertFalse(result.canSave)
    }

    @Test
    fun `explicit user override acknowledges warning`() {
        val initial = validate(
            healthySegment().copy(actualDeparture = "", actualArrival = ""),
            actualTimesRequired = true
        )
        val warningIds = initial.pendingWarnings.map { it.id }.toSet()

        val overridden = useCase.applyUserOverride(initial, warningIds)

        assertTrue(overridden.pendingWarnings.isEmpty())
        assertTrue(overridden.canSave)
    }

    @Test
    fun `critical issue remains blocking when warnings are overridden`() {
        val initial = validate(
            healthySegment().copy(
                alightingStop = "Frankfurt Hauptbahnhof",
                actualDeparture = "",
                actualArrival = ""
            ),
            actualTimesRequired = true
        )
        val allIssueIds = initial.issues.map { it.id }.toSet()

        val overridden = useCase.applyUserOverride(initial, allIssueIds)

        assertTrue(overridden.criticalIssues.any { it.code == ValidationIssueCode.SAME_STOP })
        assertFalse(overridden.canSave)
    }

    @Test
    fun `overlapping planned segments are critical`() {
        val first = healthySegment().copy(
            plannedDeparture = "10:00",
            plannedArrival = "10:30",
            actualDeparture = "",
            actualArrival = ""
        )
        val second = healthySegment().copy(
            boardingStop = "Konstablerwache",
            alightingStop = "Suedbahnhof",
            plannedDeparture = "10:20",
            plannedArrival = "10:50",
            actualDeparture = "",
            actualArrival = ""
        )

        val result = useCase.validate(
            TransitRecordValidationInput(segments = listOf(first, second))
        )

        assertTrue(result.issues.any { it.code == ValidationIssueCode.PLANNED_SEGMENT_OVERLAP })
        assertFalse(result.canSave)
    }

    @Test
    fun `invalid distance is critical and large RMV ORS mismatch is a warning`() {
        val invalid = validate(healthySegment().copy(distanceKm = -1.0))
        val mismatch = validate(
            healthySegment().copy(rmvDistanceKm = 2.0, orsDistanceKm = 10.0)
        )

        assertTrue(invalid.issues.any { it.code == ValidationIssueCode.INVALID_DISTANCE })
        assertTrue(mismatch.issues.any {
            it.code == ValidationIssueCode.INCONSISTENT_DISTANCE &&
                it.severity == ValidationSeverity.WARNING
        })
    }

    @Test
    fun `existing health duration rule reports stored duration mismatch`() {
        val result = validate(
            healthySegment().copy(storedActualDurationMinutes = 5),
            actualTimesRequired = true
        )

        assertTrue(result.issues.any { it.code == ValidationIssueCode.STORED_DURATION_MISMATCH })
    }

    private fun validate(
        segment: TransitRecordSegmentInput,
        actualTimesRequired: Boolean = false
    ) = useCase.validate(
        TransitRecordValidationInput(
            segments = listOf(segment),
            actualTimesRequired = actualTimesRequired
        )
    )

    private fun healthySegment() = TransitRecordSegmentInput(
        boardingStop = "Frankfurt Hauptbahnhof",
        alightingStop = "Bad Homburg",
        boardingStopId = "A=1@O=Frankfurt Hauptbahnhof",
        alightingStopId = "A=1@O=Bad Homburg",
        plannedDeparture = "18:22",
        plannedArrival = "18:44",
        actualDeparture = "18:30",
        actualArrival = "19:00",
        distanceKm = 17.5,
        rmvDistanceKm = 17.2,
        orsDistanceKm = 17.5,
        storedPlannedDurationMinutes = 22,
        storedActualDurationMinutes = 30
    )
}
