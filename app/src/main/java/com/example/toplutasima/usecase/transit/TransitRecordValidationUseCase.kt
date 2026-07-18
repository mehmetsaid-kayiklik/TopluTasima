package com.example.toplutasima.usecase.transit

import com.example.toplutasima.domain.transit.validation.TransitRecordSegmentInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationInput
import com.example.toplutasima.domain.transit.validation.TransitRecordValidationResult
import com.example.toplutasima.domain.transit.validation.TransitValidationField
import com.example.toplutasima.domain.transit.validation.ValidationFieldTarget
import com.example.toplutasima.domain.transit.validation.ValidationIssue
import com.example.toplutasima.domain.transit.validation.ValidationIssueCode
import com.example.toplutasima.domain.transit.validation.ValidationSeverity
import com.example.toplutasima.usecase.DataHealthChecker
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Saf, transit-özel kayıt öncesi doğrulama. */
class TransitRecordValidationUseCase(
    private val unusualDurationMinutes: Int = DEFAULT_UNUSUAL_DURATION_MINUTES,
    private val maximumCrossMidnightDurationMinutes: Int = DEFAULT_MAX_CROSS_MIDNIGHT_MINUTES,
    private val distanceRatioWarningThreshold: Double = DEFAULT_DISTANCE_RATIO_THRESHOLD,
    private val distanceDifferenceWarningKm: Double = DEFAULT_DISTANCE_DIFFERENCE_KM
) {
    fun validate(input: TransitRecordValidationInput): TransitRecordValidationResult {
        val issues = buildList {
            input.segments.forEachIndexed { index, segment ->
                addAll(validateSegment(index, segment, input.actualTimesRequired))
            }
            addAll(validateSegmentOverlaps(input.segments, planned = true))
            addAll(validateSegmentOverlaps(input.segments, planned = false))
        }.distinctBy { it.id }

        return TransitRecordValidationResult(issues = issues)
    }

    /**
     * Yalnız kullanıcı eylemini işleyen UI katmanından çağrılmalıdır.
     * Sadece uyarılar kabul edilir; kritik bulgular bu API ile dahi aşılamaz.
     */
    fun applyUserOverride(
        result: TransitRecordValidationResult,
        acknowledgedIssueIds: Set<String>
    ): TransitRecordValidationResult {
        val overridableIds = result.issues
            .asSequence()
            .filter { it.canOverride }
            .map { it.id }
            .toSet()
        val acceptedIds = acknowledgedIssueIds.intersect(overridableIds)
        return result.copy(
            userAcknowledgedWarningIds =
                (result.userAcknowledgedWarningIds + acceptedIds).intersect(overridableIds)
        )
    }

    private fun validateSegment(
        index: Int,
        segment: TransitRecordSegmentInput,
        actualTimesRequired: Boolean
    ): List<ValidationIssue> = buildList {
        if (sameStop(segment)) {
            add(
                issue(
                    code = ValidationIssueCode.SAME_STOP,
                    severity = ValidationSeverity.CRITICAL,
                    field = TransitValidationField.ALIGHTING_STOP,
                    segmentIndex = index,
                    detail = segment.alightingStop
                )
            )
        }

        addAll(
            validateTimePair(
                segmentIndex = index,
                departure = segment.plannedDeparture,
                arrival = segment.plannedArrival,
                planned = true,
                required = false,
                storedDurationMinutes = segment.storedPlannedDurationMinutes
            )
        )
        addAll(
            validateTimePair(
                segmentIndex = index,
                departure = segment.actualDeparture,
                arrival = segment.actualArrival,
                planned = false,
                required = actualTimesRequired,
                storedDurationMinutes = segment.storedActualDurationMinutes
            )
        )
        addAll(validateDistance(index, segment))
    }

    private fun validateTimePair(
        segmentIndex: Int,
        departure: String,
        arrival: String,
        planned: Boolean,
        required: Boolean,
        storedDurationMinutes: Int?
    ): List<ValidationIssue> = buildList {
        val departureBlank = departure.isBlank()
        val arrivalBlank = arrival.isBlank()
        val departureField = if (planned) {
            TransitValidationField.PLANNED_DEPARTURE
        } else {
            TransitValidationField.ACTUAL_DEPARTURE
        }
        val arrivalField = if (planned) {
            TransitValidationField.PLANNED_ARRIVAL
        } else {
            TransitValidationField.ACTUAL_ARRIVAL
        }

        if (departureBlank && arrivalBlank) {
            if (required) {
                add(
                    issue(
                        code = ValidationIssueCode.MISSING_ACTUAL_TIMES,
                        severity = ValidationSeverity.WARNING,
                        field = departureField,
                        segmentIndex = segmentIndex
                    )
                )
            }
            return@buildList
        }

        if (departureBlank || arrivalBlank) {
            add(
                issue(
                    code = if (planned) {
                        ValidationIssueCode.INCOMPLETE_PLANNED_TIMES
                    } else {
                        ValidationIssueCode.INCOMPLETE_ACTUAL_TIMES
                    },
                    severity = ValidationSeverity.CRITICAL,
                    field = if (departureBlank) departureField else arrivalField,
                    segmentIndex = segmentIndex
                )
            )
            return@buildList
        }

        val departureMinutes = parseMinutes(departure)
        val arrivalMinutes = parseMinutes(arrival)
        if (departureMinutes == null || arrivalMinutes == null) {
            add(
                issue(
                    code = if (planned) {
                        ValidationIssueCode.INVALID_PLANNED_TIME
                    } else {
                        ValidationIssueCode.INVALID_ACTUAL_TIME
                    },
                    severity = ValidationSeverity.CRITICAL,
                    field = if (departureMinutes == null) departureField else arrivalField,
                    segmentIndex = segmentIndex,
                    detail = if (departureMinutes == null) departure else arrival
                )
            )
            return@buildList
        }

        if (storedDurationMinutes != null && storedDurationMinutes < 0) {
            add(
                issue(
                    code = ValidationIssueCode.NEGATIVE_DURATION,
                    severity = ValidationSeverity.CRITICAL,
                    field = arrivalField,
                    segmentIndex = segmentIndex,
                    detail = storedDurationMinutes.toString()
                )
            )
            return@buildList
        }

        val durationMinutes = TransitTimeUtils.computeDuration(departure, arrival)
        val signedDuration = arrivalMinutes - departureMinutes
        if (signedDuration < 0 && durationMinutes > maximumCrossMidnightDurationMinutes) {
            add(
                issue(
                    code = if (planned) {
                        ValidationIssueCode.PLANNED_TIME_ORDER
                    } else {
                        ValidationIssueCode.ACTUAL_TIME_ORDER
                    },
                    severity = ValidationSeverity.CRITICAL,
                    field = arrivalField,
                    segmentIndex = segmentIndex,
                    detail = "$departure–$arrival"
                )
            )
            return@buildList
        }

        if (durationMinutes == 0 || durationMinutes > unusualDurationMinutes) {
            add(
                issue(
                    code = ValidationIssueCode.UNUSUAL_DURATION,
                    severity = ValidationSeverity.WARNING,
                    field = arrivalField,
                    segmentIndex = segmentIndex,
                    detail = "$durationMinutes dk"
                )
            )
        }

        if (
            storedDurationMinutes != null &&
            hasDurationMismatchFromExistingHealthRule(departure, arrival, storedDurationMinutes)
        ) {
            add(
                issue(
                    code = ValidationIssueCode.STORED_DURATION_MISMATCH,
                    severity = ValidationSeverity.WARNING,
                    field = arrivalField,
                    segmentIndex = segmentIndex,
                    detail = "$storedDurationMinutes/$durationMinutes dk"
                )
            )
        }
    }

    private fun validateDistance(
        segmentIndex: Int,
        segment: TransitRecordSegmentInput
    ): List<ValidationIssue> = buildList {
        val suppliedDistances = listOf(
            "distance" to segment.distanceKm,
            "rmv" to segment.rmvDistanceKm,
            "ors" to segment.orsDistanceKm
        ).filter { (_, value) -> isSuppliedDistance(value) }

        suppliedDistances.forEach { (source, value) ->
            if (TransitRecordCalculations.parseDistanceKm(value) == null) {
                add(
                    issue(
                        code = ValidationIssueCode.INVALID_DISTANCE,
                        severity = ValidationSeverity.CRITICAL,
                        field = TransitValidationField.DISTANCE,
                        segmentIndex = segmentIndex,
                        detail = "$source=${value.toString()}"
                    )
                )
            }
        }

        val rmv = TransitRecordCalculations.parseDistanceKm(segment.rmvDistanceKm)
        val ors = TransitRecordCalculations.parseDistanceKm(segment.orsDistanceKm)
        if (rmv != null && ors != null) {
            val difference = abs(rmv - ors)
            val ratio = max(rmv, ors) / min(rmv, ors)
            if (difference >= distanceDifferenceWarningKm && ratio >= distanceRatioWarningThreshold) {
                add(
                    issue(
                        code = ValidationIssueCode.INCONSISTENT_DISTANCE,
                        severity = ValidationSeverity.WARNING,
                        field = TransitValidationField.DISTANCE,
                        segmentIndex = segmentIndex,
                        detail = "RMV=${formatKm(rmv)}, ORS=${formatKm(ors)}"
                    )
                )
            }
        }
    }

    private fun validateSegmentOverlaps(
        segments: List<TransitRecordSegmentInput>,
        planned: Boolean
    ): List<ValidationIssue> = buildList {
        var previousDepartureAbsolute: Int? = null
        var previousArrivalAbsolute: Int? = null

        segments.forEachIndexed { index, segment ->
            val departure = if (planned) segment.plannedDeparture else segment.actualDeparture
            val arrival = if (planned) segment.plannedArrival else segment.actualArrival
            val departureMinutes = parseMinutes(departure)
            val arrivalMinutes = parseMinutes(arrival)
            if (departureMinutes == null || arrivalMinutes == null) {
                previousDepartureAbsolute = null
                previousArrivalAbsolute = null
                return@forEachIndexed
            }

            val duration = TransitTimeUtils.computeDuration(departure, arrival)
            if (
                arrivalMinutes < departureMinutes &&
                duration > maximumCrossMidnightDurationMinutes
            ) {
                previousDepartureAbsolute = null
                previousArrivalAbsolute = null
                return@forEachIndexed
            }

            var departureAbsolute = departureMinutes
            val previousDeparture = previousDepartureAbsolute
            if (previousDeparture != null) {
                while (departureAbsolute < previousDeparture) departureAbsolute += MINUTES_PER_DAY
            }
            val arrivalAbsolute = departureAbsolute + duration
            val previousArrival = previousArrivalAbsolute
            if (previousArrival != null && departureAbsolute < previousArrival) {
                add(
                    issue(
                        code = if (planned) {
                            ValidationIssueCode.PLANNED_SEGMENT_OVERLAP
                        } else {
                            ValidationIssueCode.ACTUAL_SEGMENT_OVERLAP
                        },
                        severity = ValidationSeverity.CRITICAL,
                        field = TransitValidationField.SEGMENTS,
                        segmentIndex = index,
                        detail = "${previousArrival - departureAbsolute} dk"
                    )
                )
            }
            previousDepartureAbsolute = departureAbsolute
            previousArrivalAbsolute = max(previousArrival ?: arrivalAbsolute, arrivalAbsolute)
        }
    }

    private fun sameStop(segment: TransitRecordSegmentInput): Boolean {
        val sameId = segment.boardingStopId.isNotBlank() &&
            segment.alightingStopId.isNotBlank() &&
            segment.boardingStopId == segment.alightingStopId
        val boardingName = normalizeStopName(segment.boardingStop)
        val alightingName = normalizeStopName(segment.alightingStop)
        val sameName = boardingName.isNotBlank() && boardingName == alightingName
        return sameId || sameName
    }

    private fun normalizeStopName(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace(WHITESPACE_REGEX, " ")

    private fun parseMinutes(value: String): Int? {
        return TransitTimeUtils.parseMinutesOrNull(value)
    }

    private fun hasDurationMismatchFromExistingHealthRule(
        departure: String,
        arrival: String,
        storedDurationMinutes: Int
    ): Boolean {
        val healthInput = mapOf<String, Any>(
            "id" to "pre-save-validation",
            "hat" to "transit",
            "binisDuragi" to "from",
            "inisDuragi" to "to",
            "tarih" to "01.01.2000",
            "gercekBinis" to departure,
            "gercekInis" to arrival,
            "gercekYolSuresi" to storedDurationMinutes.toString(),
            "yearMonth" to "2000-01",
            "sortDate" to "2000-01-01"
        )
        return DataHealthChecker.analyzeTrips(listOf(healthInput)).any {
            it.type == DataHealthChecker.HealthIssueType.INCONSISTENT_DURATION
        }
    }

    private fun isSuppliedDistance(value: Any?): Boolean {
        val text = value?.toString()?.trim().orEmpty()
        return text.isNotBlank() && text.lowercase(Locale.ROOT) !in UNKNOWN_DISTANCE_VALUES
    }

    private fun issue(
        code: ValidationIssueCode,
        severity: ValidationSeverity,
        field: TransitValidationField,
        segmentIndex: Int,
        detail: String = ""
    ) = ValidationIssue(
        code = code,
        severity = severity,
        target = ValidationFieldTarget(field = field, segmentIndex = segmentIndex),
        detail = detail
    )

    private fun formatKm(value: Double): String = TransitRecordCalculations.formatDistanceKm(value)

    companion object {
        const val DEFAULT_UNUSUAL_DURATION_MINUTES = 6 * 60
        const val DEFAULT_MAX_CROSS_MIDNIGHT_MINUTES = 12 * 60
        const val DEFAULT_DISTANCE_RATIO_THRESHOLD = 2.5
        const val DEFAULT_DISTANCE_DIFFERENCE_KM = 2.0
        private const val MINUTES_PER_DAY = 24 * 60
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val UNKNOWN_DISTANCE_VALUES = setOf("bilinmiyor", "unknown", "-")
    }
}
