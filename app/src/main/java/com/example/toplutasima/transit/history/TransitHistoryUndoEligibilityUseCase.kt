package com.example.toplutasima.transit.history

import com.example.toplutasima.domain.transit.validation.TransitRecordValidationInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationResult
import com.example.toplutasima.usecase.transit.TransitRecordValidationUseCase

enum class TransitUndoDecision {
    ALLOWED,
    REQUIRES_WARNING_CONFIRMATION,
    BLOCKED
}

enum class TransitUndoBlockReason {
    EVENT_NOT_FOUND,
    UNSUPPORTED_OPERATION,
    NOT_LATEST_CHANGE,
    TOMBSTONE_ACTIVE,
    UNKNOWN_PREVIOUS_VALUE,
    UNSAFE_FIELD,
    CURRENT_VALUE_CHANGED,
    VALIDATION_REQUIRED,
    CRITICAL_VALIDATION
}

data class TransitUndoEligibility(
    val decision: TransitUndoDecision,
    val patch: Map<String, Any?> = emptyMap(),
    val blockReason: TransitUndoBlockReason? = null,
    val validationResult: TransitRecordValidationResult? = null
) {
    val canProceed: Boolean
        get() = decision != TransitUndoDecision.BLOCKED
}

/**
 * Pure safety check. The caller still applies [TransitUndoEligibility.patch] through the normal
 * repository update path and records a new UNDO event after a successful write.
 */
class TransitHistoryUndoEligibilityUseCase(
    private val validationUseCase: TransitRecordValidationUseCase = TransitRecordValidationUseCase()
) {
    fun evaluate(
        event: TransitChangeEvent,
        recordHistory: List<TransitChangeEvent>,
        currentValues: Map<String, Any?>,
        tombstoneActive: Boolean,
        validationInputAfterUndo: TransitRecordValidationInput?
    ): TransitUndoEligibility {
        if (recordHistory.none { it.eventId == event.eventId }) {
            return blocked(TransitUndoBlockReason.EVENT_NOT_FOUND)
        }
        if (event.operation !in UNDOABLE_OPERATIONS) {
            return blocked(TransitUndoBlockReason.UNSUPPORTED_OPERATION)
        }
        if (tombstoneActive) return blocked(TransitUndoBlockReason.TOMBSTONE_ACTIVE)
        if (recordHistory.any { it.eventId != event.eventId && it.isAfter(event) }) {
            return blocked(TransitUndoBlockReason.NOT_LATEST_CHANGE)
        }
        if (event.changes.isEmpty() || event.changes.any { it.fieldId !in SAFE_UNDO_FIELDS }) {
            return blocked(TransitUndoBlockReason.UNSAFE_FIELD)
        }
        if (event.changes.any { it.oldValue.state == TransitHistoryValueState.UNKNOWN }) {
            return blocked(TransitUndoBlockReason.UNKNOWN_PREVIOUS_VALUE)
        }
        if (event.changes.any { change ->
                !currentValues.containsKey(change.fieldId) ||
                    TransitHistoryValue.fromKnownField(currentValues[change.fieldId]) != change.newValue
            }
        ) {
            return blocked(TransitUndoBlockReason.CURRENT_VALUE_CHANGED)
        }

        val patch = event.changes.associate { change ->
            change.fieldId to when (change.oldValue.state) {
                TransitHistoryValueState.KNOWN -> change.oldValue.value
                TransitHistoryValueState.EMPTY -> null
                TransitHistoryValueState.UNKNOWN -> null // guarded above
            }
        }
        val validationInput = validationInputAfterUndo
            ?: return blocked(TransitUndoBlockReason.VALIDATION_REQUIRED)
        val validation = validationUseCase.validate(validationInput)
        if (validation.criticalIssues.isNotEmpty()) {
            return TransitUndoEligibility(
                decision = TransitUndoDecision.BLOCKED,
                blockReason = TransitUndoBlockReason.CRITICAL_VALIDATION,
                validationResult = validation
            )
        }
        return TransitUndoEligibility(
            decision = if (validation.pendingWarnings.isEmpty()) {
                TransitUndoDecision.ALLOWED
            } else {
                TransitUndoDecision.REQUIRES_WARNING_CONFIRMATION
            },
            patch = patch,
            validationResult = validation
        )
    }

    private fun TransitChangeEvent.isAfter(other: TransitChangeEvent): Boolean =
        occurredAtEpochMillis > other.occurredAtEpochMillis ||
            (occurredAtEpochMillis == other.occurredAtEpochMillis &&
                recordedAtEpochMillis > other.recordedAtEpochMillis) ||
            (occurredAtEpochMillis == other.occurredAtEpochMillis &&
                recordedAtEpochMillis == other.recordedAtEpochMillis && eventId != other.eventId)

    private fun blocked(reason: TransitUndoBlockReason) = TransitUndoEligibility(
        decision = TransitUndoDecision.BLOCKED,
        blockReason = reason
    )

    companion object {
        private val UNDOABLE_OPERATIONS = setOf(
            TransitChangeOperation.MANUAL_EDIT,
            TransitChangeOperation.AUTOMATIC_HEALTH_CORRECTION,
            TransitChangeOperation.USER_APPROVED_BULK_CORRECTION
        )
        private val SAFE_UNDO_FIELDS = setOf(
            "planlananBinis",
            "gercekBinis",
            "planlananInis",
            "gercekInis",
            "planlananYolSuresi",
            "gercekYolSuresi",
            "mesafe"
        )
    }
}
