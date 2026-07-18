package com.example.toplutasima.transit.summary

import com.example.toplutasima.model.SummaryData

/**
 * Inputs for one transit summary calculation.
 *
 * [selectedRecords] is already scoped by the caller (for example, to one
 * `yearMonth`). [boundedTrendRecords] contains only the bounded history needed
 * by the monthly trend. Keeping the two inputs separate prevents a selected
 * month refresh from requiring all historical records.
 */
data class TransitSummaryRequest(
    val selectedRecords: List<Map<String, Any>>,
    val boundedTrendRecords: List<Map<String, Any>> = selectedRecords,
    val availableSheetNames: List<String> = emptyList()
)

/**
 * Result supplied by an optional data-health adapter.
 *
 * The assessor owns the decision about which records may contribute to
 * statistics. The summary engine deliberately does not redefine validation or
 * health rules.
 */
data class TransitSummaryHealthAssessment(
    val recordsForStatistics: List<Map<String, Any>>,
    val informationalIssueCount: Int = 0,
    val warningIssueCount: Int = 0,
    val criticalIssueCount: Int = 0
) {
    init {
        require(informationalIssueCount >= 0) { "informationalIssueCount must not be negative" }
        require(warningIssueCount >= 0) { "warningIssueCount must not be negative" }
        require(criticalIssueCount >= 0) { "criticalIssueCount must not be negative" }
    }
}

/**
 * Adapter boundary for the post-save health scanner.
 *
 * Implementations may reuse DataHealthChecker and the transit validation
 * use-case without coupling the statistics engine to their UI models.
 */
fun interface TransitSummaryHealthAssessor {
    suspend fun assess(records: List<Map<String, Any>>): TransitSummaryHealthAssessment
}

data class TransitSummaryDataQuality(
    val assessmentApplied: Boolean,
    val scannedRecordCount: Int,
    val statisticsRecordCount: Int,
    val excludedRecordCount: Int,
    val informationalIssueCount: Int,
    val warningIssueCount: Int,
    val criticalIssueCount: Int
) {
    val issueCount: Int
        get() = informationalIssueCount + warningIssueCount + criticalIssueCount

    val hasIssues: Boolean
        get() = issueCount > 0

    val statisticsWereFiltered: Boolean
        get() = excludedRecordCount > 0
}

data class TransitSummaryResult(
    val summary: SummaryData,
    val availableSheetNames: List<String>,
    val selectedDataQuality: TransitSummaryDataQuality,
    val trendDataQuality: TransitSummaryDataQuality
)
