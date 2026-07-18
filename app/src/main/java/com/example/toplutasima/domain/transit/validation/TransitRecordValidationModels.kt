package com.example.toplutasima.domain.transit.validation

enum class ValidationSeverity {
    WARNING,
    CRITICAL
}

enum class TransitValidationField {
    BOARDING_STOP,
    ALIGHTING_STOP,
    PLANNED_DEPARTURE,
    PLANNED_ARRIVAL,
    ACTUAL_DEPARTURE,
    ACTUAL_ARRIVAL,
    DISTANCE,
    SEGMENTS,
    RECORD
}

enum class ValidationIssueCode {
    SAME_STOP,
    INVALID_PLANNED_TIME,
    INVALID_ACTUAL_TIME,
    INCOMPLETE_PLANNED_TIMES,
    INCOMPLETE_ACTUAL_TIMES,
    MISSING_ACTUAL_TIMES,
    PLANNED_TIME_ORDER,
    ACTUAL_TIME_ORDER,
    NEGATIVE_DURATION,
    UNUSUAL_DURATION,
    STORED_DURATION_MISMATCH,
    INVALID_DISTANCE,
    INCONSISTENT_DISTANCE,
    PLANNED_SEGMENT_OVERLAP,
    ACTUAL_SEGMENT_OVERLAP
}

data class ValidationFieldTarget(
    val field: TransitValidationField,
    val segmentIndex: Int? = null
)

/**
 * UI'dan bağımsız transit kayıt doğrulama bulgusu.
 *
 * [detail] kullanıcıya gösterilecek son metin yerine, ekranın yerelleştirebileceği kısa bağlamdır.
 * Karar mantığı [code], [severity] ve [target] üzerinden yürür.
 */
data class ValidationIssue(
    val code: ValidationIssueCode,
    val severity: ValidationSeverity,
    val target: ValidationFieldTarget,
    val detail: String = ""
) {
    val id: String = buildString {
        append(code.name)
        append(':')
        append(target.segmentIndex ?: "record")
        append(':')
        append(target.field.name)
    }

    val canOverride: Boolean
        get() = severity == ValidationSeverity.WARNING
}

data class TransitRecordSegmentInput(
    val boardingStop: String,
    val alightingStop: String,
    val plannedDeparture: String = "",
    val plannedArrival: String = "",
    val actualDeparture: String = "",
    val actualArrival: String = "",
    val boardingStopId: String = "",
    val alightingStopId: String = "",
    val distanceKm: Any? = null,
    val rmvDistanceKm: Any? = null,
    val orsDistanceKm: Any? = null,
    val storedPlannedDurationMinutes: Int? = null,
    val storedActualDurationMinutes: Int? = null
)

data class TransitRecordValidationInput(
    val segments: List<TransitRecordSegmentInput>,
    /**
     * Plan oluşturulurken false, tamamlanmış bir kayıt kaydedilirken true olmalıdır.
     * Böylece aktif yolculuğun henüz oluşmamış gerçek saatleri yanlışlıkla engellenmez.
     */
    val actualTimesRequired: Boolean = false
)

data class TransitRecordValidationResult(
    val issues: List<ValidationIssue>,
    val userAcknowledgedWarningIds: Set<String> = emptySet()
) {
    val criticalIssues: List<ValidationIssue>
        get() = issues.filter { it.severity == ValidationSeverity.CRITICAL }

    val pendingWarnings: List<ValidationIssue>
        get() = issues.filter {
            it.severity == ValidationSeverity.WARNING && it.id !in userAcknowledgedWarningIds
        }

    val canSave: Boolean
        get() = criticalIssues.isEmpty() && pendingWarnings.isEmpty()

    val requiresUserConfirmation: Boolean
        get() = criticalIssues.isEmpty() && pendingWarnings.isNotEmpty()
}
