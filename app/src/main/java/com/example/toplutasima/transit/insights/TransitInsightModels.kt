package com.example.toplutasima.transit.insights

import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.summary.TransitSummaryResult

/** The semantic identity is also used to prevent duplicate cards. */
enum class TransitInsightType {
    MOST_USED_LINE,
    MOST_USED_ORIGIN,
    MOST_USED_DESTINATION,
    MOST_USED_TRANSFER,
    BUSIEST_TRAVEL_DAY,
    BUSIEST_TIME_SLOT,
    AVERAGE_TRIP_DURATION,
    AVERAGE_WAITING_TIME,
    AVERAGE_DELAY,
    MOST_DELAYED_LINE,
    MOST_RELIABLE_LINE,
    ROUTE_OFTEN_LONGER_THAN_PLANNED,
    USAGE_CHANGE,
    DELAY_CHANGE,
    DISTANCE_CHANGE,
    WEEKDAY_WEEKEND_DIFFERENCE,
    MORNING_EVENING_DIFFERENCE,
    ROUTE_LINE_COMPARISON,
    LINE_OR_ROUTE_TREND,
    DATA_QUALITY_NOTICE,
    INSUFFICIENT_DATA
}

enum class TransitInsightConfidence {
    HIGH,
    MEDIUM,
    LOW,
    INSUFFICIENT_DATA
}

enum class TransitInsightConfidenceFactorType {
    SAMPLE_SIZE,
    DATA_HEALTH,
    MISSING_ACTUAL_TIMES,
    UNKNOWN_PROVENANCE,
    PERIOD_IMBALANCE,
    OUTLIER_INFLUENCE
}

data class TransitInsightConfidenceFactor(
    val type: TransitInsightConfidenceFactorType,
    /** A non-negative deduction from the otherwise ideal score. */
    val penaltyPoints: Int,
    /** Ratio in the inclusive 0..1 range which produced this factor. */
    val affectedRatio: Double,
    val affectedRecordCount: Int,
    val detail: String
) {
    init {
        require(penaltyPoints >= 0) { "penaltyPoints must not be negative" }
        require(affectedRatio in 0.0..1.0) { "affectedRatio must be between 0 and 1" }
        require(affectedRecordCount >= 0) { "affectedRecordCount must not be negative" }
    }
}

data class TransitInsightConfidenceAssessment(
    val level: TransitInsightConfidence,
    val score: Int,
    val factors: List<TransitInsightConfidenceFactor>
) {
    init {
        require(score in 0..100) { "score must be between 0 and 100" }
    }
}

data class TransitInsightExplanation(
    val recordCount: Int,
    val periodLabel: String,
    /** A compact, deterministic equation; never an opaque generated explanation. */
    val calculation: String,
    val comparisonPeriodLabel: String? = null,
    val evidence: List<String> = emptyList()
) {
    init {
        require(recordCount >= 0) { "recordCount must not be negative" }
    }
}

data class TransitInsight(
    val type: TransitInsightType,
    /** Stable key also includes the subject for repeatable list rendering. */
    val semanticKey: String,
    val subject: String? = null,
    val value: String,
    val periodLabel: String,
    val confidence: TransitInsightConfidenceAssessment,
    val explanation: TransitInsightExplanation,
    val priority: Int
)

/**
 * Bounded input for one selected month and, when needed, exactly one comparison month.
 *
 * The caller normally passes the selected summary it already calculated. When it is missing or
 * does not match the filtered record count, [TransitInsightsEngine] reuses TransitSummaryEngine.
 */
data class TransitInsightsRequest(
    val selectedPeriodLabel: String,
    val selectedRecords: List<Map<String, Any>>,
    val previousPeriodLabel: String? = null,
    val previousRecords: List<Map<String, Any>> = emptyList(),
    val selectedSummary: TransitSummaryResult? = null,
    val previousSummary: TransitSummaryResult? = null,
    val tombstonedRecordIds: Set<String> = emptySet(),
    val provenanceByRecordId: Map<String, Map<String, TransitFieldProvenance>> = emptyMap(),
    val maxInsights: Int = 12
) {
    init {
        require(selectedPeriodLabel.isNotBlank()) { "selectedPeriodLabel must not be blank" }
        require(maxInsights > 0) { "maxInsights must be positive" }
    }
}

data class TransitInsightsResult(
    val insights: List<TransitInsight>,
    /** Reusable result when the engine had to calculate or validate the selected summary. */
    val selectedSummary: TransitSummaryResult,
    val previousSummary: TransitSummaryResult?,
    val consideredRecordCount: Int,
    val excludedDeletedRecordCount: Int
)
