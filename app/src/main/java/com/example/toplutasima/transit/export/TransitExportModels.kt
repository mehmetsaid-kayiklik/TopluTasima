package com.example.toplutasima.transit.export

import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.transit.provenance.TransitFieldFreshness
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.sync.TransitSyncPhase
import kotlinx.serialization.Serializable

/** Portable transit export formats. PDF remains owned by the legacy UI export path. */
enum class TransitExportFormat(
    val extension: String,
    val mimeType: String
) {
    CSV(extension = "csv", mimeType = "text/csv"),
    JSON(extension = "json", mimeType = "application/json")
}

enum class TransitExportScopeType {
    SELECTED_MONTH,
    DATE_RANGE,
    ALL_TRANSIT,
    FILTERED
}

/**
 * Scope values use ISO-8601 machine-readable dates. The domain use-case validates them before
 * reading any record into the output.
 */
data class TransitExportScope(
    val type: TransitExportScopeType,
    val selectedMonthIso: String? = null,
    val startDateIso: String? = null,
    val endDateIso: String? = null,
    val filterDescription: String? = null,
    val sortDescription: String? = null
) {
    companion object {
        fun selectedMonth(yearMonthIso: String) = TransitExportScope(
            type = TransitExportScopeType.SELECTED_MONTH,
            selectedMonthIso = yearMonthIso
        )

        fun dateRange(startDateIso: String, endDateIso: String) = TransitExportScope(
            type = TransitExportScopeType.DATE_RANGE,
            startDateIso = startDateIso,
            endDateIso = endDateIso
        )

        fun allTransit() = TransitExportScope(type = TransitExportScopeType.ALL_TRANSIT)

        fun filtered(
            filterDescription: String,
            sortDescription: String? = null,
            startDateIso: String? = null,
            endDateIso: String? = null
        ) = TransitExportScope(
            type = TransitExportScopeType.FILTERED,
            startDateIso = startDateIso,
            endDateIso = endDateIso,
            filterDescription = filterDescription,
            sortDescription = sortDescription
        )
    }
}

enum class TransitExportSection {
    RECORDS,
    SUMMARY,
    INSIGHTS,
    DATA_HEALTH
}

/** Only transit origins are representable at this boundary. */
enum class TransitExportRecordOrigin {
    RMV,
    MANUAL_TRANSIT,
    IMPORTED_TRANSIT,
    UNKNOWN_TRANSIT
}

/**
 * Provenance evidence must be explicit. Session-only knowledge is deliberately downgraded to
 * UNKNOWN in the exported document so it cannot be mistaken for durable evidence.
 */
enum class TransitExportProvenanceEvidence {
    RECORD_DERIVED,
    SESSION_ONLY
}

data class TransitExportProvenanceInput(
    val fieldId: String,
    val source: TransitFieldSource,
    val freshness: TransitFieldFreshness = TransitFieldFreshness.UNKNOWN,
    val lastUpdatedAtEpochMillis: Long? = null,
    val isFallback: Boolean = false,
    val backingSource: TransitFieldSource? = null,
    val fallbackFor: TransitFieldSource? = null,
    val evidence: TransitExportProvenanceEvidence = TransitExportProvenanceEvidence.RECORD_DERIVED
)

/**
 * Transit-only input boundary. It intentionally has no UID, Firestore document ID, queue payload,
 * GPS trace, activity-recognition data, deletion history, or non-transit field.
 */
data class TransitExportRecord(
    val localRecordId: String,
    val dateIso: String,
    val line: String? = null,
    val boardingStop: String? = null,
    val alightingStop: String? = null,
    val plannedDeparture: String? = null,
    val actualDeparture: String? = null,
    val plannedArrival: String? = null,
    val actualArrival: String? = null,
    val plannedDurationMinutes: Int? = null,
    val actualDurationMinutes: Int? = null,
    val delayMinutes: Int? = null,
    val distanceKm: Double? = null,
    val recordType: String? = null,
    val origin: TransitExportRecordOrigin = TransitExportRecordOrigin.UNKNOWN_TRANSIT,
    val syncPhase: TransitSyncPhase? = null,
    val healthSeverity: TransitHealthSeverity? = null,
    val provenance: List<TransitExportProvenanceInput> = emptyList(),
    val note: String? = null,
    /** Set by the caller's current filter; used only when [TransitExportScopeType.FILTERED]. */
    val matchesFilter: Boolean = true,
    /** Defense in depth in addition to [TransitExportRequest.tombstonedRecordIds]. */
    val isTombstoned: Boolean = false
)

data class TransitExportMetric(
    val id: String,
    val label: String,
    val value: String,
    val unit: String? = null
)

data class TransitExportInsight(
    val id: String,
    val title: String,
    val result: String,
    val period: String,
    val confidence: String,
    val explanation: String,
    val recordCount: Int
)

data class TransitExportHealthSummary(
    val scannedRecordCount: Int,
    val healthyRecordCount: Int,
    val informationalIssueCount: Int,
    val warningIssueCount: Int,
    val criticalIssueCount: Int
)

data class TransitExportRequest(
    val format: TransitExportFormat,
    val scope: TransitExportScope,
    val records: List<TransitExportRecord>,
    /** Must contain only tombstones belonging to the active, already-authenticated user. */
    val tombstonedRecordIds: Set<String> = emptySet(),
    val sections: Set<TransitExportSection> = setOf(TransitExportSection.RECORDS),
    val summary: List<TransitExportMetric> = emptyList(),
    val insights: List<TransitExportInsight> = emptyList(),
    val healthSummary: TransitExportHealthSummary? = null
)

sealed interface TransitExportPreparationResult {
    data class Ready(val document: PreparedTransitExport) : TransitExportPreparationResult
    data object Disabled : TransitExportPreparationResult
}

data class PreparedTransitExport(
    val suggestedFileName: String,
    val mimeType: String,
    val bytes: ByteArray,
    val exportedRecordCount: Int,
    val sha256: String
)

@Serializable
internal data class TransitExportEnvelopeDto(
    val metadata: TransitExportMetadataDto,
    val records: List<TransitExportRecordDto> = emptyList(),
    val summary: List<TransitExportMetricDto> = emptyList(),
    val insights: List<TransitExportInsightDto> = emptyList(),
    val dataHealth: TransitExportHealthSummaryDto? = null
)

@Serializable
internal data class TransitExportMetadataDto(
    val schema: String = "transit-export",
    val formatVersion: Int,
    val exportedAt: String,
    val scope: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val recordCount: Int,
    val filter: String? = null,
    val sort: String? = null,
    val includedSections: List<String>
)

@Serializable
internal data class TransitExportRecordDto(
    val localRecordId: String,
    val date: String,
    val line: String? = null,
    val boardingStop: String? = null,
    val alightingStop: String? = null,
    val plannedDeparture: String? = null,
    val actualDeparture: String? = null,
    val plannedArrival: String? = null,
    val actualArrival: String? = null,
    val plannedDurationMinutes: Int? = null,
    val actualDurationMinutes: Int? = null,
    val delayMinutes: Int? = null,
    val distanceKm: Double? = null,
    val recordType: String? = null,
    val recordOrigin: String,
    val syncStatus: String,
    val healthStatus: String,
    val provenance: List<TransitExportProvenanceDto> = emptyList(),
    val note: String? = null
)

@Serializable
internal data class TransitExportProvenanceDto(
    val fieldId: String,
    val source: String,
    val freshness: String,
    val lastUpdatedAt: String? = null,
    val fallback: Boolean,
    val backingSource: String? = null,
    val fallbackFor: String? = null
)

@Serializable
internal data class TransitExportMetricDto(
    val id: String,
    val label: String,
    val value: String,
    val unit: String? = null
)

@Serializable
internal data class TransitExportInsightDto(
    val id: String,
    val title: String,
    val result: String,
    val period: String,
    val confidence: String,
    val explanation: String,
    val recordCount: Int
)

@Serializable
internal data class TransitExportHealthSummaryDto(
    val scannedRecordCount: Int,
    val healthyRecordCount: Int,
    val informationalIssueCount: Int,
    val warningIssueCount: Int,
    val criticalIssueCount: Int
)
