package com.example.toplutasima.transit.insights

import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.summary.TransitSummaryDataQuality
import kotlin.math.abs
import kotlin.math.roundToInt

/** One explainable confidence policy shared by every insight type. */
class TransitInsightConfidenceEvaluator {

    fun evaluate(
        records: List<Map<String, Any>>,
        dataQuality: TransitSummaryDataQuality,
        provenanceByRecordId: Map<String, Map<String, com.example.toplutasima.transit.provenance.TransitFieldProvenance>>,
        comparisonRecordCount: Int? = null,
        minimumSampleSize: Int = 2
    ): TransitInsightConfidenceAssessment {
        if (records.size < minimumSampleSize) {
            return TransitInsightConfidenceAssessment(
                level = TransitInsightConfidence.INSUFFICIENT_DATA,
                score = 0,
                factors = listOf(
                    TransitInsightConfidenceFactor(
                        type = TransitInsightConfidenceFactorType.SAMPLE_SIZE,
                        penaltyPoints = 100,
                        affectedRatio = if (minimumSampleSize == 0) 0.0 else {
                            (1.0 - records.size.toDouble() / minimumSampleSize).coerceIn(0.0, 1.0)
                        },
                        affectedRecordCount = (minimumSampleSize - records.size).coerceAtLeast(0),
                        detail = "${records.size}/$minimumSampleSize records"
                    )
                )
            )
        }

        val factors = buildList {
            sampleSizeFactor(records.size)?.let(::add)
            healthFactor(records.size, dataQuality)?.let(::add)
            missingActualTimeFactor(records)?.let(::add)
            unknownProvenanceFactor(records, provenanceByRecordId)?.let(::add)
            comparisonRecordCount?.let { periodImbalanceFactor(records.size, it) }?.let(::add)
            outlierFactor(records)?.let(::add)
        }
        val score = (100 - factors.sumOf { it.penaltyPoints }).coerceIn(0, 100)
        val level = when {
            score >= 75 -> TransitInsightConfidence.HIGH
            score >= 50 -> TransitInsightConfidence.MEDIUM
            else -> TransitInsightConfidence.LOW
        }
        return TransitInsightConfidenceAssessment(level = level, score = score, factors = factors)
    }

    private fun sampleSizeFactor(size: Int): TransitInsightConfidenceFactor? {
        val penalty = when (size) {
            in 0..1 -> 100
            in 2..3 -> 35
            in 4..7 -> 15
            else -> 0
        }
        if (penalty == 0) return null
        return TransitInsightConfidenceFactor(
            type = TransitInsightConfidenceFactorType.SAMPLE_SIZE,
            penaltyPoints = penalty,
            affectedRatio = (1.0 - size.toDouble() / 8.0).coerceIn(0.0, 1.0),
            affectedRecordCount = size,
            detail = "$size records"
        )
    }

    private fun healthFactor(
        recordCount: Int,
        quality: TransitSummaryDataQuality
    ): TransitInsightConfidenceFactor? {
        if (!quality.assessmentApplied || quality.issueCount == 0 || recordCount == 0) return null
        val ratio = (quality.issueCount.toDouble() / recordCount).coerceIn(0.0, 1.0)
        val penalty = (ratio * 30.0).roundToInt().coerceAtLeast(1)
        return TransitInsightConfidenceFactor(
            type = TransitInsightConfidenceFactorType.DATA_HEALTH,
            penaltyPoints = penalty,
            affectedRatio = ratio,
            affectedRecordCount = quality.issueCount.coerceAtMost(recordCount),
            detail = "${quality.issueCount} health findings"
        )
    }

    private fun missingActualTimeFactor(records: List<Map<String, Any>>): TransitInsightConfidenceFactor? {
        if (records.isEmpty()) return null
        val missing = records.count { row ->
            row.string(FIELD_ACTUAL_DEPARTURE).isBlank() || row.string(FIELD_ACTUAL_ARRIVAL).isBlank()
        }
        if (missing == 0) return null
        val ratio = missing.toDouble() / records.size
        return TransitInsightConfidenceFactor(
            type = TransitInsightConfidenceFactorType.MISSING_ACTUAL_TIMES,
            penaltyPoints = (ratio * 20.0).roundToInt().coerceAtLeast(1),
            affectedRatio = ratio,
            affectedRecordCount = missing,
            detail = "$missing records miss actual times"
        )
    }

    private fun unknownProvenanceFactor(
        records: List<Map<String, Any>>,
        provenanceByRecordId: Map<String, Map<String, com.example.toplutasima.transit.provenance.TransitFieldProvenance>>
    ): TransitInsightConfidenceFactor? {
        if (records.isEmpty()) return null
        val unknownRecords = records.count { row ->
            val provenance = provenanceByRecordId[row.recordId()]
            provenance.isNullOrEmpty() || provenance.values.any { it.source == TransitFieldSource.UNKNOWN }
        }
        if (unknownRecords == 0) return null
        val ratio = unknownRecords.toDouble() / records.size
        return TransitInsightConfidenceFactor(
            type = TransitInsightConfidenceFactorType.UNKNOWN_PROVENANCE,
            penaltyPoints = (ratio * 20.0).roundToInt().coerceAtLeast(1),
            affectedRatio = ratio,
            affectedRecordCount = unknownRecords,
            detail = "$unknownRecords records have unknown provenance"
        )
    }

    private fun periodImbalanceFactor(current: Int, previous: Int): TransitInsightConfidenceFactor? {
        val larger = maxOf(current, previous)
        if (larger == 0) return null
        val ratio = abs(current - previous).toDouble() / larger
        if (ratio < 0.25) return null
        return TransitInsightConfidenceFactor(
            type = TransitInsightConfidenceFactorType.PERIOD_IMBALANCE,
            penaltyPoints = (ratio * 15.0).roundToInt().coerceAtLeast(1),
            affectedRatio = ratio,
            affectedRecordCount = abs(current - previous),
            detail = "$current vs $previous records"
        )
    }

    private fun outlierFactor(records: List<Map<String, Any>>): TransitInsightConfidenceFactor? {
        val durations = records.mapNotNull { it.double(FIELD_ACTUAL_DURATION) }.filter { it > 0.0 }.sorted()
        if (durations.size < 4) return null
        val q1 = percentile(durations, 0.25)
        val q3 = percentile(durations, 0.75)
        val iqr = q3 - q1
        val outliers = if (iqr <= 0.0) {
            durations.count { it != q1 }
        } else {
            val lower = q1 - 1.5 * iqr
            val upper = q3 + 1.5 * iqr
            durations.count { it < lower || it > upper }
        }
        if (outliers == 0) return null
        val ratio = outliers.toDouble() / durations.size
        return TransitInsightConfidenceFactor(
            type = TransitInsightConfidenceFactorType.OUTLIER_INFLUENCE,
            penaltyPoints = (ratio * 20.0).roundToInt().coerceAtLeast(1),
            affectedRatio = ratio,
            affectedRecordCount = outliers,
            detail = "$outliers duration outliers"
        )
    }

    private fun percentile(sorted: List<Double>, percentile: Double): Double {
        if (sorted.size == 1) return sorted.single()
        val index = percentile * (sorted.lastIndex)
        val lower = index.toInt()
        val upper = kotlin.math.ceil(index).toInt()
        if (lower == upper) return sorted[lower]
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
    }

    private fun Map<String, Any>.string(key: String): String = this[key]?.toString()?.trim().orEmpty()
    private fun Map<String, Any>.double(key: String): Double? = this[key]?.toString()?.toDoubleOrNull()
    private fun Map<String, Any>.recordId(): String =
        string(FIELD_ID).ifBlank { string(FIELD_FIRESTORE_ID) }

    private companion object {
        const val FIELD_ID = "id"
        const val FIELD_FIRESTORE_ID = "firestoreDocId"
        const val FIELD_ACTUAL_DEPARTURE = "gercekBinis"
        const val FIELD_ACTUAL_ARRIVAL = "gercekInis"
        const val FIELD_ACTUAL_DURATION = "gercekYolSuresi"
    }
}
