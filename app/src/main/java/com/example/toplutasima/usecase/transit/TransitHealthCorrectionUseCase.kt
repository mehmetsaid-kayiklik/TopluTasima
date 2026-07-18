package com.example.toplutasima.usecase.transit

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.domain.transit.health.TransitHealthCorrection
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.domain.transit.health.TransitHealthIssueCode
import com.example.toplutasima.domain.transit.validation.TransitValidationField
import com.example.toplutasima.usecase.TransitTimeUtils

/** Builds deterministic correction proposals without changing any record. */
class TransitHealthCorrectionUseCase(
    private val maximumSafeDurationMinutes: Int = DEFAULT_MAX_SAFE_DURATION_MINUTES
) {
    init {
        require(maximumSafeDurationMinutes > 0) {
            "maximumSafeDurationMinutes must be positive"
        }
    }

    fun propose(
        records: List<TripEntity>,
        issues: List<TransitHealthIssue>
    ): List<TransitHealthCorrection> {
        val issuesByRecord = issues
            .filter { it.code in CORRECTABLE_ISSUE_CODES }
            .groupBy { it.localRecordId }

        return records.mapNotNull { record ->
            val recordIssues = issuesByRecord[record.id].orEmpty()
            if (recordIssues.isEmpty()) return@mapNotNull null

            val patch = linkedMapOf<String, Any>()
            val correctedIssueIds = linkedSetOf<String>()

            val plannedIssues = recordIssues.filter {
                it.target.field == TransitValidationField.PLANNED_ARRIVAL
            }
            safeDuration(record.planlananBinis, record.planlananInis)?.let { duration ->
                if (plannedIssues.isNotEmpty()) {
                    patch[FIELD_PLANNED_DURATION] = duration.toString()
                    correctedIssueIds += plannedIssues.map { it.id }
                }
            }

            val actualIssues = recordIssues.filter {
                it.target.field == TransitValidationField.ACTUAL_ARRIVAL
            }
            safeDuration(record.gercekBinis, record.gercekInis)?.let { duration ->
                if (actualIssues.isNotEmpty()) {
                    patch[FIELD_ACTUAL_DURATION] = duration.toString()
                    correctedIssueIds += actualIssues.map { it.id }
                }
            }

            if (patch.isEmpty()) return@mapNotNull null
            TransitHealthCorrection(
                id = "duration:${record.id}:${patch.keys.sorted().joinToString(",")}",
                localRecordId = record.id,
                fields = patch,
                issueIds = correctedIssueIds,
                description = "Geçerli saatlerden kayıtlı yolculuk süresini yeniden hesapla"
            )
        }
    }

    /** Pure preview helper; applying the returned fields remains the caller's responsibility. */
    fun preview(
        currentRecord: Map<String, Any?>,
        correction: TransitHealthCorrection
    ): Map<String, Any?> = currentRecord + correction.fields

    private fun safeDuration(departure: String?, arrival: String?): Int? {
        val departureMinutes = TransitTimeUtils.parseMinutesOrNull(departure) ?: return null
        val arrivalMinutes = TransitTimeUtils.parseMinutesOrNull(arrival) ?: return null
        val duration = TransitTimeUtils.computeDuration(departure.orEmpty(), arrival.orEmpty())
        if (duration <= 0 || duration > maximumSafeDurationMinutes) return null
        if (arrivalMinutes < departureMinutes && duration > MAX_CROSS_MIDNIGHT_MINUTES) return null
        return duration
    }

    companion object {
        const val FIELD_PLANNED_DURATION = "planlananYolSuresi"
        const val FIELD_ACTUAL_DURATION = "gercekYolSuresi"
        private const val DEFAULT_MAX_SAFE_DURATION_MINUTES = 6 * 60
        private const val MAX_CROSS_MIDNIGHT_MINUTES = 12 * 60
        private val CORRECTABLE_ISSUE_CODES = setOf(
            TransitHealthIssueCode.NEGATIVE_DURATION,
            TransitHealthIssueCode.STORED_DURATION_MISMATCH
        )
    }
}
