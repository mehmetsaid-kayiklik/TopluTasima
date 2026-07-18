package com.example.toplutasima.transit.summary

import com.example.toplutasima.usecase.SummaryCalculator
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * Pure transit-summary orchestration around the existing SummaryCalculator.
 *
 * Room/Firestore access stays outside this class. The caller supplies scoped
 * records and the engine performs CPU work on [calculationDispatcher].
 */
class TransitSummaryEngine(
    private val healthAssessor: TransitSummaryHealthAssessor? = null,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    suspend fun calculate(request: TransitSummaryRequest): TransitSummaryResult =
        withContext(calculationDispatcher) {
            currentCoroutineContext().ensureActive()

            val selectedAssessment = assess(request.selectedRecords)
            currentCoroutineContext().ensureActive()

            val trendAssessment = if (request.boundedTrendRecords === request.selectedRecords) {
                selectedAssessment
            } else {
                assess(request.boundedTrendRecords)
            }
            currentCoroutineContext().ensureActive()

            // selectedRecords is already scoped, so the calculator's unfiltered
            // path preserves all existing metric formulas without loading history.
            val (baseSummary, derivedSheetNames) = SummaryCalculator.computeSummary(
                selectedAssessment.assessment.recordsForStatistics
            )
            currentCoroutineContext().ensureActive()

            val boundedTrend = SummaryCalculator.computeMonthlyTrend(
                trendAssessment.assessment.recordsForStatistics
            )
            currentCoroutineContext().ensureActive()

            TransitSummaryResult(
                summary = baseSummary.copy(monthlyTrend = boundedTrend),
                availableSheetNames = request.availableSheetNames
                    .takeUnless { it.isEmpty() }
                    ?.distinct()
                    ?: derivedSheetNames,
                selectedDataQuality = selectedAssessment.toDataQuality(),
                trendDataQuality = trendAssessment.toDataQuality()
            )
        }

    private suspend fun assess(records: List<Map<String, Any>>): AssessedRecords {
        val assessment = healthAssessor?.assess(records)
            ?: TransitSummaryHealthAssessment(recordsForStatistics = records)
        currentCoroutineContext().ensureActive()

        return AssessedRecords(
            sourceRecordCount = records.size,
            assessmentApplied = healthAssessor != null,
            assessment = assessment
        )
    }

    private data class AssessedRecords(
        val sourceRecordCount: Int,
        val assessmentApplied: Boolean,
        val assessment: TransitSummaryHealthAssessment
    ) {
        fun toDataQuality(): TransitSummaryDataQuality {
            val statisticsRecordCount = assessment.recordsForStatistics.size
            return TransitSummaryDataQuality(
                assessmentApplied = assessmentApplied,
                scannedRecordCount = sourceRecordCount,
                statisticsRecordCount = statisticsRecordCount,
                excludedRecordCount = (sourceRecordCount - statisticsRecordCount).coerceAtLeast(0),
                informationalIssueCount = assessment.informationalIssueCount,
                warningIssueCount = assessment.warningIssueCount,
                criticalIssueCount = assessment.criticalIssueCount
            )
        }
    }
}
