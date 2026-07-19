package com.example.toplutasima.transit.history

import com.example.toplutasima.domain.transit.validation.TransitRecordSegmentInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitHistoryUndoEligibilityUseCaseTest {
    private val useCase = TransitHistoryUndoEligibilityUseCase()

    @Test
    fun `last manual time change can be safely undone`() {
        val event = event(change("gercekBinis", "08:00", "08:05"))

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("gercekBinis" to "08:05"),
            tombstoneActive = false,
            validationInputAfterUndo = validInput(actualDeparture = "08:00")
        )

        assertEquals(TransitUndoDecision.ALLOWED, result.decision)
        assertEquals("08:00", result.patch["gercekBinis"])
        assertTrue(result.canProceed)
    }

    @Test
    fun `safe automatic duration correction can be undone`() {
        val event = event(
            change("gercekYolSuresi", "25", "30"),
            operation = TransitChangeOperation.AUTOMATIC_HEALTH_CORRECTION,
            source = TransitChangeSource.DATA_HEALTH
        )

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("gercekYolSuresi" to "30"),
            tombstoneActive = false,
            validationInputAfterUndo = validInput(
                actualArrival = "08:25",
                storedActualDuration = 25
            )
        )

        assertEquals(TransitUndoDecision.ALLOWED, result.decision)
        assertEquals("25", result.patch["gercekYolSuresi"])
    }

    @Test
    fun `later record change blocks undo`() {
        val target = event(change("mesafe", "5.0", "6.0"), occurredAt = 1_000L)
        val later = event(change("hat", "U1", "U2"), occurredAt = 2_000L)

        val result = useCase.evaluate(
            event = target,
            recordHistory = listOf(target, later),
            currentValues = mapOf("mesafe" to "6.0"),
            tombstoneActive = false,
            validationInputAfterUndo = validInput()
        )

        assertEquals(TransitUndoDecision.BLOCKED, result.decision)
        assertEquals(TransitUndoBlockReason.NOT_LATEST_CHANGE, result.blockReason)
    }

    @Test
    fun `changed current value blocks stale undo`() {
        val event = event(change("mesafe", "5.0", "6.0"))

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("mesafe" to "7.0"),
            tombstoneActive = false,
            validationInputAfterUndo = validInput()
        )

        assertEquals(TransitUndoBlockReason.CURRENT_VALUE_CHANGED, result.blockReason)
    }

    @Test
    fun `active tombstone blocks undo`() {
        val event = event(change("mesafe", "5.0", "6.0"))

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("mesafe" to "6.0"),
            tombstoneActive = true,
            validationInputAfterUndo = validInput()
        )

        assertFalse(result.canProceed)
        assertEquals(TransitUndoBlockReason.TOMBSTONE_ACTIVE, result.blockReason)
    }

    @Test
    fun `unknown old value is never fabricated for undo`() {
        val event = event(
            TransitFieldChange(
                fieldId = "gercekBinis",
                oldValue = TransitHistoryValue.unknown(),
                newValue = TransitHistoryValue.known("08:05")
            )
        )

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("gercekBinis" to "08:05"),
            tombstoneActive = false,
            validationInputAfterUndo = validInput()
        )

        assertEquals(TransitUndoBlockReason.UNKNOWN_PREVIOUS_VALUE, result.blockReason)
    }

    @Test
    fun `unsafe field cannot be undone`() {
        val event = event(change("hat", "U1", "U2"))

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("hat" to "U2"),
            tombstoneActive = false,
            validationInputAfterUndo = validInput()
        )

        assertEquals(TransitUndoBlockReason.UNSAFE_FIELD, result.blockReason)
    }

    @Test
    fun `critical validation result blocks undo`() {
        val event = event(change("gercekBinis", "08:00", "08:05"))

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("gercekBinis" to "08:05"),
            tombstoneActive = false,
            validationInputAfterUndo = validInput(sameStop = true)
        )

        assertEquals(TransitUndoDecision.BLOCKED, result.decision)
        assertEquals(TransitUndoBlockReason.CRITICAL_VALIDATION, result.blockReason)
        assertTrue(result.validationResult?.criticalIssues?.isNotEmpty() == true)
    }

    @Test
    fun `validation input is mandatory before undo`() {
        val event = event(change("mesafe", "5.0", "6.0"))

        val result = useCase.evaluate(
            event = event,
            recordHistory = listOf(event),
            currentValues = mapOf("mesafe" to "6.0"),
            tombstoneActive = false,
            validationInputAfterUndo = null
        )

        assertEquals(TransitUndoBlockReason.VALIDATION_REQUIRED, result.blockReason)
    }

    private fun event(
        change: TransitFieldChange,
        operation: TransitChangeOperation = TransitChangeOperation.MANUAL_EDIT,
        source: TransitChangeSource = TransitChangeSource.USER,
        occurredAt: Long = 1_000L
    ) = TransitChangeEvent(
        eventId = "event-$occurredAt-${change.fieldId}",
        userId = "user-A",
        recordId = "record-1",
        operation = operation,
        occurredAtEpochMillis = occurredAt,
        recordedAtEpochMillis = occurredAt,
        source = source,
        changes = listOf(change)
    )

    private fun change(fieldId: String, old: Any?, new: Any?) = TransitFieldChange(
        fieldId = fieldId,
        oldValue = TransitHistoryValue.fromKnownField(old),
        newValue = TransitHistoryValue.fromKnownField(new)
    )

    private fun validInput(
        actualDeparture: String = "08:00",
        actualArrival: String = "08:30",
        storedActualDuration: Int? = null,
        sameStop: Boolean = false
    ) = TransitRecordValidationInput(
        segments = listOf(
            TransitRecordSegmentInput(
                boardingStop = "A",
                alightingStop = if (sameStop) "A" else "B",
                plannedDeparture = "08:00",
                plannedArrival = "08:30",
                actualDeparture = actualDeparture,
                actualArrival = actualArrival,
                distanceKm = "5.0",
                storedActualDurationMinutes = storedActualDuration
            )
        ),
        actualTimesRequired = true
    )
}
