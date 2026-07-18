package com.example.toplutasima.domain.transit.health

import com.example.toplutasima.domain.transit.validation.TransitValidationField

enum class TransitHealthSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class TransitHealthIssueCode {
    POSSIBLE_DUPLICATE,
    OVERLAPPING_SEGMENT,
    SAME_STOP,
    INVALID_PLANNED_TIME,
    INVALID_ACTUAL_TIME,
    MISSING_ACTUAL_TIME,
    PLANNED_TIME_ORDER,
    ACTUAL_TIME_ORDER,
    NEGATIVE_DURATION,
    UNUSUAL_DURATION,
    STORED_DURATION_MISMATCH,
    INVALID_DISTANCE,
    EXTREME_DISTANCE,
    ROUTE_DISTANCE_MISMATCH,
    UNKNOWN_PROVENANCE
}

data class TransitHealthFieldTarget(
    val field: TransitValidationField,
    val fieldId: String? = null
)

data class TransitHealthIssue(
    val code: TransitHealthIssueCode,
    val severity: TransitHealthSeverity,
    val localRecordId: String,
    val target: TransitHealthFieldTarget,
    val detail: String = "",
    val relatedRecordIds: Set<String> = emptySet()
) {
    val id: String = buildString {
        append(localRecordId)
        append(':')
        append(code.name)
        append(':')
        append(target.field.name)
        append(':')
        append(target.fieldId.orEmpty())
        if (relatedRecordIds.isNotEmpty()) {
            append(':')
            append(relatedRecordIds.sorted().joinToString(","))
        }
    }
}

/**
 * A correction is a proposal only. The use-case layer never writes it automatically.
 * Callers must obtain explicit user confirmation before applying [fields].
 */
data class TransitHealthCorrection(
    val id: String,
    val localRecordId: String,
    val fields: Map<String, Any>,
    val issueIds: Set<String>,
    val description: String,
    val deterministic: Boolean = true
)

data class TransitHealthScanResult(
    val issues: List<TransitHealthIssue>,
    val corrections: List<TransitHealthCorrection>,
    val scannedRecordCount: Int
) {
    val issuesByRecordId: Map<String, List<TransitHealthIssue>> =
        issues.groupBy { it.localRecordId }

    val highestSeverityByRecordId: Map<String, TransitHealthSeverity> =
        issuesByRecordId.mapValues { (_, recordIssues) ->
            recordIssues.maxBy { it.severity.ordinal }.severity
        }
}
