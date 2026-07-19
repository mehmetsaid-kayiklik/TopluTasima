package com.example.toplutasima.usecase.transit

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.domain.transit.health.TransitHealthFieldTarget
import com.example.toplutasima.domain.transit.health.TransitHealthIssue
import com.example.toplutasima.domain.transit.health.TransitHealthIssueCode
import com.example.toplutasima.domain.transit.health.TransitHealthScanResult
import com.example.toplutasima.domain.transit.health.TransitHealthSeverity
import com.example.toplutasima.domain.transit.validation.TransitRecordSegmentInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationInput
import com.example.toplutasima.domain.transit.validation.TransitValidationField
import com.example.toplutasima.domain.transit.validation.ValidationIssue
import com.example.toplutasima.domain.transit.validation.ValidationIssueCode
import com.example.toplutasima.domain.transit.validation.ValidationSeverity
import com.example.toplutasima.transit.provenance.TransitFieldFreshness
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceResolver
import com.example.toplutasima.transit.provenance.TransitRecordProvenanceStore
import com.example.toplutasima.transit.duplicate.TransitDuplicateCandidateUseCase
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * Pure post-save audit over Room transit entities. The caller owns dispatching and cancellation;
 * this synchronous implementation is intended to run on Dispatchers.Default.
 */
class TransitPostSaveHealthUseCase(
    private val validationUseCase: TransitRecordValidationUseCase = TransitRecordValidationUseCase(),
    private val correctionUseCase: TransitHealthCorrectionUseCase = TransitHealthCorrectionUseCase(),
    private val provenanceResolver: TransitRecordProvenanceResolver = TransitRecordProvenanceResolver(),
    private val duplicateCandidateUseCase: TransitDuplicateCandidateUseCase =
        TransitDuplicateCandidateUseCase(),
    private val minimumReasonableDistanceKm: Double = DEFAULT_MIN_DISTANCE_KM,
    private val maximumReasonableDistanceKm: Double = DEFAULT_MAX_DISTANCE_KM
) {
    init {
        require(minimumReasonableDistanceKm > 0.0)
        require(maximumReasonableDistanceKm > minimumReasonableDistanceKm)
    }

    fun scan(
        records: List<TripEntity>,
        provenanceStore: TransitRecordProvenanceStore? = null,
        provenanceEnabled: Boolean = true
    ): TransitHealthScanResult {
        if (records.isEmpty()) {
            return TransitHealthScanResult(emptyList(), emptyList(), scannedRecordCount = 0)
        }

        val issues = buildList {
            records.forEach { record ->
                addAll(validateRecord(record))
                unusualDistance(record)?.let(::add)
                if (provenanceEnabled) {
                    unknownProvenance(record, provenanceStore)?.let(::add)
                }
            }
            addAll(findPossibleDuplicates(records))
            addAll(findOverlappingSegments(records))
        }.distinctBy { it.id }
            .sortedWith(
                compareByDescending<TransitHealthIssue> { it.severity.ordinal }
                    .thenBy { it.localRecordId }
                    .thenBy { it.code.ordinal }
            )

        return TransitHealthScanResult(
            issues = issues,
            corrections = correctionUseCase.propose(records, issues),
            scannedRecordCount = records.size
        )
    }

    private fun validateRecord(record: TripEntity): List<TransitHealthIssue> {
        val result = validationUseCase.validate(
            TransitRecordValidationInput(
                segments = listOf(record.toValidationSegment()),
                actualTimesRequired = true
            )
        )
        return result.issues.map { it.toHealthIssue(record.id) }
    }

    private fun TripEntity.toValidationSegment(): TransitRecordSegmentInput =
        TransitRecordSegmentInput(
            boardingStop = binisDuragi.orEmpty(),
            alightingStop = inisDuragi.orEmpty(),
            plannedDeparture = planlananBinis.orEmpty(),
            plannedArrival = planlananInis.orEmpty(),
            actualDeparture = gercekBinis.orEmpty(),
            actualArrival = gercekInis.orEmpty(),
            boardingStopId = fromStopId.orEmpty(),
            alightingStopId = toStopId.orEmpty(),
            distanceKm = mesafe,
            rmvDistanceKm = rmvMesafeKm,
            orsDistanceKm = orsMesafeKm,
            storedPlannedDurationMinutes = planlananYolSuresi?.toIntOrNull(),
            storedActualDurationMinutes = gercekYolSuresi?.toIntOrNull()
        )

    private fun ValidationIssue.toHealthIssue(localRecordId: String): TransitHealthIssue =
        TransitHealthIssue(
            code = code.toHealthCode(),
            severity = severity.toHealthSeverity(),
            localRecordId = localRecordId,
            target = TransitHealthFieldTarget(
                field = target.field,
                fieldId = target.field.toRawFieldId()
            ),
            detail = detail
        )

    private fun ValidationIssueCode.toHealthCode(): TransitHealthIssueCode = when (this) {
        ValidationIssueCode.SAME_STOP -> TransitHealthIssueCode.SAME_STOP
        ValidationIssueCode.INVALID_PLANNED_TIME,
        ValidationIssueCode.INCOMPLETE_PLANNED_TIMES -> TransitHealthIssueCode.INVALID_PLANNED_TIME
        ValidationIssueCode.INVALID_ACTUAL_TIME -> TransitHealthIssueCode.INVALID_ACTUAL_TIME
        ValidationIssueCode.INCOMPLETE_ACTUAL_TIMES,
        ValidationIssueCode.MISSING_ACTUAL_TIMES -> TransitHealthIssueCode.MISSING_ACTUAL_TIME
        ValidationIssueCode.PLANNED_TIME_ORDER -> TransitHealthIssueCode.PLANNED_TIME_ORDER
        ValidationIssueCode.ACTUAL_TIME_ORDER -> TransitHealthIssueCode.ACTUAL_TIME_ORDER
        ValidationIssueCode.NEGATIVE_DURATION -> TransitHealthIssueCode.NEGATIVE_DURATION
        ValidationIssueCode.UNUSUAL_DURATION -> TransitHealthIssueCode.UNUSUAL_DURATION
        ValidationIssueCode.STORED_DURATION_MISMATCH -> TransitHealthIssueCode.STORED_DURATION_MISMATCH
        ValidationIssueCode.INVALID_DISTANCE -> TransitHealthIssueCode.INVALID_DISTANCE
        ValidationIssueCode.INCONSISTENT_DISTANCE -> TransitHealthIssueCode.ROUTE_DISTANCE_MISMATCH
        ValidationIssueCode.PLANNED_SEGMENT_OVERLAP,
        ValidationIssueCode.ACTUAL_SEGMENT_OVERLAP -> TransitHealthIssueCode.OVERLAPPING_SEGMENT
    }

    private fun ValidationSeverity.toHealthSeverity(): TransitHealthSeverity = when (this) {
        ValidationSeverity.WARNING -> TransitHealthSeverity.WARNING
        ValidationSeverity.CRITICAL -> TransitHealthSeverity.CRITICAL
    }

    private fun TransitValidationField.toRawFieldId(): String? = when (this) {
        TransitValidationField.BOARDING_STOP -> TransitRecordProvenanceResolver.FIELD_BOARDING_STOP
        TransitValidationField.ALIGHTING_STOP -> TransitRecordProvenanceResolver.FIELD_ALIGHTING_STOP
        TransitValidationField.PLANNED_DEPARTURE -> TransitRecordProvenanceResolver.FIELD_PLANNED_DEPARTURE
        TransitValidationField.PLANNED_ARRIVAL -> TransitRecordProvenanceResolver.FIELD_PLANNED_ARRIVAL
        TransitValidationField.ACTUAL_DEPARTURE -> TransitRecordProvenanceResolver.FIELD_ACTUAL_DEPARTURE
        TransitValidationField.ACTUAL_ARRIVAL -> TransitRecordProvenanceResolver.FIELD_ACTUAL_ARRIVAL
        TransitValidationField.DISTANCE -> TransitRecordProvenanceResolver.FIELD_DISTANCE
        TransitValidationField.SEGMENTS,
        TransitValidationField.RECORD -> null
    }

    private fun unusualDistance(record: TripEntity): TransitHealthIssue? {
        val distances = listOf(record.mesafe, record.orsMesafeKm, record.rmvMesafeKm)
            .mapNotNull(TransitRecordCalculations::parseDistanceKm)
            .distinct()
        val extreme = distances.firstOrNull {
            it < minimumReasonableDistanceKm || it > maximumReasonableDistanceKm
        } ?: return null
        return TransitHealthIssue(
            code = TransitHealthIssueCode.EXTREME_DISTANCE,
            severity = TransitHealthSeverity.WARNING,
            localRecordId = record.id,
            target = TransitHealthFieldTarget(
                TransitValidationField.DISTANCE,
                TransitRecordProvenanceResolver.FIELD_DISTANCE
            ),
            detail = TransitRecordCalculations.formatDistanceKm(extreme)
        )
    }

    private fun unknownProvenance(
        record: TripEntity,
        store: TransitRecordProvenanceStore?
    ): TransitHealthIssue? {
        val row = record.toMap()
        val resolved = provenanceResolver.resolveFields(
            userId = record.userId,
            localRecordId = record.id,
            record = row,
            store = store
        )
        val unknownFields = resolved.filter { (fieldId, provenance) ->
            row[fieldId]?.toString()?.isNotBlank() == true &&
                (provenance.source == TransitFieldSource.UNKNOWN ||
                    provenance.freshness == TransitFieldFreshness.UNKNOWN)
        }.keys
        if (unknownFields.isEmpty()) return null

        val firstField = unknownFields.first()
        return TransitHealthIssue(
            code = TransitHealthIssueCode.UNKNOWN_PROVENANCE,
            severity = TransitHealthSeverity.INFO,
            localRecordId = record.id,
            target = TransitHealthFieldTarget(
                field = rawFieldTarget(firstField),
                fieldId = firstField
            ),
            detail = unknownFields.sorted().joinToString(",")
        )
    }

    private fun findPossibleDuplicates(records: List<TripEntity>): List<TransitHealthIssue> {
        return duplicateCandidateUseCase.findCandidates(records).flatMap { candidate ->
            listOf(
                candidate.firstRecordId to candidate.secondRecordId,
                candidate.secondRecordId to candidate.firstRecordId
            ).map { (localId, relatedId) ->
                TransitHealthIssue(
                    code = TransitHealthIssueCode.POSSIBLE_DUPLICATE,
                    severity = TransitHealthSeverity.WARNING,
                    localRecordId = localId,
                    target = TransitHealthFieldTarget(TransitValidationField.RECORD),
                    detail = candidate.reasons.joinToString(",") { it.name },
                    relatedRecordIds = setOf(relatedId)
                )
            }
        }
    }

    /** Sweep-line detection: grouping/sorting is O(n log n), the sweep and output are O(n). */
    private fun findOverlappingSegments(records: List<TripEntity>): List<TransitHealthIssue> {
        val intervals = records.mapNotNull(::toInterval)
        return intervals.groupBy { it.segmentKey }.values.flatMap { group ->
            val sorted = group.sortedWith(compareBy<TripInterval> { it.startMinute }.thenBy { it.endMinute })
            val issues = mutableListOf<TransitHealthIssue>()
            var furthest: TripInterval? = null
            for (current in sorted) {
                val active = furthest
                if (active != null && current.startMinute < active.endMinute) {
                    val overlapMinutes = active.endMinute - current.startMinute
                    issues += overlapIssue(current, active, overlapMinutes)
                    issues += overlapIssue(active, current, overlapMinutes)
                }
                if (active == null || current.endMinute > active.endMinute) furthest = current
            }
            issues
        }
    }

    private fun toInterval(record: TripEntity): TripInterval? {
        val date = parseDate(record.tarih) ?: return null
        val actualComplete = !record.gercekBinis.isNullOrBlank() && !record.gercekInis.isNullOrBlank()
        val departure = if (actualComplete) record.gercekBinis else record.planlananBinis
        val arrival = if (actualComplete) record.gercekInis else record.planlananInis
        val departureMinutes = TransitTimeUtils.parseMinutesOrNull(departure) ?: return null
        val arrivalMinutes = TransitTimeUtils.parseMinutesOrNull(arrival) ?: return null
        val duration = TransitTimeUtils.computeDuration(departure.orEmpty(), arrival.orEmpty())
        if (duration <= 0) return null
        if (arrivalMinutes < departureMinutes && duration > MAX_CROSS_MIDNIGHT_MINUTES) return null

        val segmentKey = listOf(record.hat, record.binisDuragi, record.inisDuragi)
            .joinToString("|") { normalize(it) }
        if (segmentKey.replace("|", "").isBlank()) return null
        val start = date.toEpochDay() * MINUTES_PER_DAY + departureMinutes
        return TripInterval(record.id, segmentKey, start, start + duration)
    }

    private fun overlapIssue(
        record: TripInterval,
        related: TripInterval,
        overlapMinutes: Long
    ): TransitHealthIssue = TransitHealthIssue(
        code = TransitHealthIssueCode.OVERLAPPING_SEGMENT,
        severity = TransitHealthSeverity.WARNING,
        localRecordId = record.localRecordId,
        target = TransitHealthFieldTarget(TransitValidationField.SEGMENTS),
        detail = "$overlapMinutes dk",
        relatedRecordIds = setOf(related.localRecordId)
    )

    private fun parseDate(raw: String?): LocalDate? = runCatching {
        LocalDate.parse(raw.orEmpty(), DATE_FORMATTER)
    }.getOrNull()

    private fun normalize(raw: String?): String = raw.orEmpty()
        .trim()
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE_REGEX, " ")

    private fun rawFieldTarget(fieldId: String): TransitValidationField = when (fieldId) {
        TransitRecordProvenanceResolver.FIELD_BOARDING_STOP -> TransitValidationField.BOARDING_STOP
        TransitRecordProvenanceResolver.FIELD_ALIGHTING_STOP -> TransitValidationField.ALIGHTING_STOP
        TransitRecordProvenanceResolver.FIELD_PLANNED_DEPARTURE -> TransitValidationField.PLANNED_DEPARTURE
        TransitRecordProvenanceResolver.FIELD_PLANNED_ARRIVAL -> TransitValidationField.PLANNED_ARRIVAL
        TransitRecordProvenanceResolver.FIELD_ACTUAL_DEPARTURE -> TransitValidationField.ACTUAL_DEPARTURE
        TransitRecordProvenanceResolver.FIELD_ACTUAL_ARRIVAL -> TransitValidationField.ACTUAL_ARRIVAL
        TransitRecordProvenanceResolver.FIELD_DISTANCE,
        TransitRecordCalculations.FIELD_ORS_DISTANCE_KM,
        TransitRecordCalculations.FIELD_RMV_DISTANCE_KM -> TransitValidationField.DISTANCE
        else -> TransitValidationField.RECORD
    }

    private data class TripInterval(
        val localRecordId: String,
        val segmentKey: String,
        val startMinute: Long,
        val endMinute: Long
    )

    companion object {
        private const val DEFAULT_MIN_DISTANCE_KM = 0.05
        private const val DEFAULT_MAX_DISTANCE_KM = 500.0
        private const val MINUTES_PER_DAY = 24L * 60L
        private const val MAX_CROSS_MIDNIGHT_MINUTES = 12 * 60
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT)
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
