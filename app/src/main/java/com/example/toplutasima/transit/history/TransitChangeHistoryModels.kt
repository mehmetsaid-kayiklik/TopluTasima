package com.example.toplutasima.transit.history

import kotlinx.serialization.Serializable

@Serializable
enum class TransitChangeOperation {
    CREATE,
    MANUAL_EDIT,
    REMOTE_UPDATE,
    AUTOMATIC_HEALTH_CORRECTION,
    USER_APPROVED_BULK_CORRECTION,
    LOCAL_DELETE,
    DELETE_SYNC,
    DELETE_ROLLBACK,
    PROVENANCE_CHANGE,
    DUPLICATE_MERGE,
    UNDO
}

@Serializable
enum class TransitChangeSource {
    USER,
    RMV,
    DATA_HEALTH,
    SYNC_WORKER,
    REMOTE,
    DUPLICATE_RESOLUTION,
    SYSTEM,
    UNKNOWN
}

@Serializable
enum class TransitHistorySyncStatus {
    NOT_APPLICABLE,
    LOCAL_ONLY,
    PENDING,
    SYNCING,
    SYNCED,
    TEMPORARY_ERROR,
    PERMANENT_ERROR
}

@Serializable
enum class TransitHistoryValueState {
    KNOWN,
    EMPTY,
    UNKNOWN
}

/**
 * Keeps an unknown remote value distinct from a known empty value. This prevents a history row
 * from inventing a before-value when a remote update arrived without a trusted local snapshot.
 */
@Serializable
data class TransitHistoryValue(
    val state: TransitHistoryValueState,
    val value: String? = null
) {
    fun displayValue(unknownLabel: String = "Bilinmiyor", emptyLabel: String = "Boş"): String =
        when (state) {
            TransitHistoryValueState.KNOWN -> value.orEmpty()
            TransitHistoryValueState.EMPTY -> emptyLabel
            TransitHistoryValueState.UNKNOWN -> unknownLabel
        }

    companion object {
        fun known(value: Any): TransitHistoryValue {
            val text = canonicalValue(value)
            return if (text.isEmpty()) empty() else TransitHistoryValue(
                state = TransitHistoryValueState.KNOWN,
                value = text.take(MAX_VALUE_LENGTH)
            )
        }

        fun empty(): TransitHistoryValue = TransitHistoryValue(TransitHistoryValueState.EMPTY)

        fun unknown(): TransitHistoryValue = TransitHistoryValue(TransitHistoryValueState.UNKNOWN)

        fun fromKnownField(value: Any?): TransitHistoryValue =
            if (value == null) empty() else known(value)

        internal fun canonicalValue(value: Any?): String = when (value) {
            null -> ""
            is Number -> value.toString().toBigDecimalOrNull()
                ?.stripTrailingZeros()
                ?.toPlainString()
                ?: value.toString()
            is Boolean -> value.toString()
            else -> value.toString()
        }

        internal const val MAX_VALUE_LENGTH = 512
    }
}

@Serializable
enum class TransitHistoryProvenanceSource {
    LIVE_RMV,
    PLANNED_RMV,
    TRANSIT_LOCATION,
    RMV_DISTANCE,
    ORS_DISTANCE,
    CACHE,
    MANUAL,
    UNKNOWN
}

@Serializable
enum class TransitHistoryEvidenceDurability {
    PERSISTED_FIELD,
    EXPLICIT_USER_ACTION,
    SESSION_ONLY
}

/**
 * Provenance is persisted only when backed by a stored field or an explicit user action.
 * SESSION_ONLY metadata is deliberately stripped by [TransitChangeHistoryStore].
 */
@Serializable
data class TransitHistoryProvenanceEvidence(
    val source: TransitHistoryProvenanceSource,
    val durability: TransitHistoryEvidenceDurability
) {
    val isDurable: Boolean
        get() = durability != TransitHistoryEvidenceDurability.SESSION_ONLY
}

@Serializable
data class TransitFieldChange(
    val fieldId: String,
    val oldValue: TransitHistoryValue,
    val newValue: TransitHistoryValue,
    val oldProvenance: TransitHistoryProvenanceEvidence? = null,
    val newProvenance: TransitHistoryProvenanceEvidence? = null
)

/** Input without an event id; the store derives a deterministic id before persistence. */
data class TransitChangeEventDraft(
    val userId: String,
    val recordId: String,
    val operation: TransitChangeOperation,
    val occurredAtEpochMillis: Long,
    val source: TransitChangeSource,
    val changes: List<TransitFieldChange> = emptyList(),
    val syncStatus: TransitHistorySyncStatus = TransitHistorySyncStatus.NOT_APPLICABLE,
    /** Queue action id or another stable operation token; never a serialized queue payload. */
    val deduplicationKey: String? = null
)

@Serializable
data class TransitChangeEvent(
    val eventId: String,
    val userId: String,
    val recordId: String,
    val operation: TransitChangeOperation,
    val occurredAtEpochMillis: Long,
    val recordedAtEpochMillis: Long,
    val source: TransitChangeSource,
    val changes: List<TransitFieldChange> = emptyList(),
    val syncStatus: TransitHistorySyncStatus = TransitHistorySyncStatus.NOT_APPLICABLE
)

enum class TransitHistoryAppendOutcome {
    ADDED,
    ADDED_MEMORY_ONLY,
    DUPLICATE,
    DISABLED,
    INVALID
}

data class TransitHistoryAppendResult(
    val outcome: TransitHistoryAppendOutcome,
    val event: TransitChangeEvent? = null
)
