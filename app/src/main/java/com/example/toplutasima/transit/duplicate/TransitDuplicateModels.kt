package com.example.toplutasima.transit.duplicate

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.domain.transit.validation.ValidationIssue
import com.example.toplutasima.transit.provenance.TransitFieldProvenance

enum class TransitDuplicateReason {
    SAME_DATE,
    NEAR_PLANNED_DEPARTURE,
    SAME_BOARDING_STOP,
    SAME_ALIGHTING_STOP,
    SAME_LINE,
    SIMILAR_PLANNED_TIMES,
    SIMILAR_ACTUAL_TIMES,
    SIMILAR_DISTANCE,
    SAME_SEGMENT_FINGERPRINT,
    SAME_RMV_JOURNEY,
    MANUAL_AND_AUTOMATIC_PAIR,
    COMPLEMENTARY_COMPLETENESS
}

data class TransitDuplicateCandidate(
    val firstRecordId: String,
    val secondRecordId: String,
    /** Similarity evidence, not a probability or correctness claim. */
    val similarityScore: Double,
    val reasons: Set<TransitDuplicateReason>,
    val decisionFingerprint: String,
    val userId: String = ""
) {
    val stablePairId: String = listOf(firstRecordId, secondRecordId).sorted().joinToString(":")
}

enum class TransitDuplicateDecision {
    KEEP_SEPARATE,
    KEEP_FIRST,
    KEEP_SECOND,
    MERGE_FIELDS,
    REVIEW_LATER
}

enum class TransitMergeValueSource { FIRST, SECOND }

data class TransitDuplicateMergeSelection(
    val valueSourceByField: Map<String, TransitMergeValueSource>
)

data class TransitDuplicateMergePreview(
    val targetRecordId: String,
    val sourceRecordIdToDelete: String,
    val mergedRecord: TripEntity,
    val selectedProvenanceByField: Map<String, TransitFieldProvenance>,
    val validationIssues: List<ValidationIssue>,
    val acknowledgedWarningIds: Set<String> = emptySet()
) {
    val criticalIssues: List<ValidationIssue>
        get() = validationIssues.filter { !it.canOverride }

    val pendingWarnings: List<ValidationIssue>
        get() = validationIssues.filter {
            it.canOverride && it.id !in acknowledgedWarningIds
        }

    val canApply: Boolean
        get() = criticalIssues.isEmpty() && pendingWarnings.isEmpty()
}

fun interface TransitDuplicateDecisionLookup {
    fun isKeptSeparate(userId: String, decisionFingerprint: String): Boolean
}
