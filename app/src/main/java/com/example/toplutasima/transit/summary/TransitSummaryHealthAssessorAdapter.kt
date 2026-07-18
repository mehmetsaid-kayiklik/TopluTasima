package com.example.toplutasima.transit.summary

import com.example.toplutasima.data.repository.toEntity
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
import com.example.toplutasima.usecase.transit.TransitPostSaveHealthUseCase

/** Bridges the shared post-save health policy into summary calculation without duplicating rules. */
class TransitSummaryHealthAssessorAdapter(
    private val healthUseCase: TransitPostSaveHealthUseCase,
    private val provenanceStore: TransitRecordProvenanceStore,
    private val provenanceEnabled: Boolean
) : TransitSummaryHealthAssessor {
    override suspend fun assess(records: List<Map<String, Any>>): TransitSummaryHealthAssessment {
        val entities = records.map { record ->
            record.toEntity(record["userId"]?.toString().orEmpty())
        }
        val scan = healthUseCase.scan(
            records = entities,
            provenanceStore = provenanceStore,
            provenanceEnabled = provenanceEnabled
        )
        val criticalRecordIds = scan.issues.asSequence()
            .filter { it.severity == TransitHealthSeverity.CRITICAL }
            .map { it.localRecordId }
            .toSet()

        return TransitSummaryHealthAssessment(
            recordsForStatistics = records.filter { row ->
                row["id"]?.toString().orEmpty() !in criticalRecordIds
            },
            informationalIssueCount = scan.issues.count { it.severity == TransitHealthSeverity.INFO },
            warningIssueCount = scan.issues.count { it.severity == TransitHealthSeverity.WARNING },
            criticalIssueCount = scan.issues.count { it.severity == TransitHealthSeverity.CRITICAL }
        )
    }
}
