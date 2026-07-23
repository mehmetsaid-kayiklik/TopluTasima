package com.example.toplutasima.drive.validation

import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveVehicleDraft
import java.time.Year
import kotlin.math.abs

enum class DriveValidationCode {
    AUTHENTICATION_REQUIRED,
    OWNERSHIP_MISMATCH,
    DISPLAY_NAME_REQUIRED,
    MODEL_YEAR_OUT_OF_RANGE,
    NEGATIVE_ODOMETER,
    CURRENT_ODOMETER_BEFORE_INITIAL,
    NEGATIVE_PURCHASE_PRICE,
    INVALID_CURRENCY_CODE,
    INVALID_COUNTRY_CODE,
    INVALID_ENGINE_VALUE,
    VEHICLE_REQUIRED,
    VEHICLE_NOT_FOUND,
    END_BEFORE_START,
    NEGATIVE_DISTANCE,
    END_ODOMETER_BEFORE_START,
    DISTANCE_REQUIRED,
    DISTANCE_ODOMETER_MISMATCH,
    INVALID_DECIMAL,
    RECORD_NOT_FOUND,
    DELETED_RECORD,
    CASCADE_CONFIRMATION_REQUIRED
}

data class DriveValidationIssue(
    val code: DriveValidationCode,
    val field: String
)

data class DriveTripValidationResult(
    val issues: List<DriveValidationIssue>,
    val resolvedDistanceKm: Double?
) {
    val isValid: Boolean get() = issues.isEmpty() && resolvedDistanceKm != null
}

class DriveVehicleValidator(
    private val currentYear: () -> Int = { Year.now().value }
) {
    fun validate(draft: DriveVehicleDraft): List<DriveValidationIssue> = buildList {
        if (draft.displayName.isBlank()) {
            add(DriveValidationIssue(DriveValidationCode.DISPLAY_NAME_REQUIRED, "displayName"))
        }
        draft.modelYear?.let { year ->
            if (year !in EARLIEST_MODEL_YEAR..(currentYear() + MAX_FUTURE_MODEL_YEARS)) {
                add(DriveValidationIssue(DriveValidationCode.MODEL_YEAR_OUT_OF_RANGE, "modelYear"))
            }
        }
        if (draft.initialOdometerKm?.let { !it.isFinite() || it < 0.0 } == true) {
            add(DriveValidationIssue(DriveValidationCode.NEGATIVE_ODOMETER, "initialOdometerKm"))
        }
        if (draft.currentOdometerKm?.let { !it.isFinite() || it < 0.0 } == true) {
            add(DriveValidationIssue(DriveValidationCode.NEGATIVE_ODOMETER, "currentOdometerKm"))
        }
        val initial = draft.initialOdometerKm
        val current = draft.currentOdometerKm
        if (initial != null && current != null && current < initial) {
            add(
                DriveValidationIssue(
                    DriveValidationCode.CURRENT_ODOMETER_BEFORE_INITIAL,
                    "currentOdometerKm"
                )
            )
        }
        if (draft.purchasePriceMinor?.let { it < 0L } == true) {
            add(DriveValidationIssue(DriveValidationCode.NEGATIVE_PURCHASE_PRICE, "purchasePriceMinor"))
        }
        draft.currencyCode?.trim()?.takeIf(String::isNotEmpty)?.let { code ->
            if (!code.matches(Regex("^[A-Za-z]{3}$"))) {
                add(DriveValidationIssue(DriveValidationCode.INVALID_CURRENCY_CODE, "currencyCode"))
            }
        }
        draft.countryCode?.trim()?.takeIf(String::isNotEmpty)?.let { code ->
            if (!code.matches(Regex("^[A-Za-z]{2}$"))) {
                add(DriveValidationIssue(DriveValidationCode.INVALID_COUNTRY_CODE, "countryCode"))
            }
        }
        if (draft.engineDisplacementCc?.let { it < 0 } == true) {
            add(DriveValidationIssue(DriveValidationCode.INVALID_ENGINE_VALUE, "engineDisplacementCc"))
        }
        if (draft.enginePowerKw?.let { it < 0 } == true) {
            add(DriveValidationIssue(DriveValidationCode.INVALID_ENGINE_VALUE, "enginePowerKw"))
        }
    }

    private companion object {
        const val EARLIEST_MODEL_YEAR = 1886
        const val MAX_FUTURE_MODEL_YEARS = 1
    }
}

class DriveTripValidator(
    private val consistencyToleranceKm: Double = DEFAULT_CONSISTENCY_TOLERANCE_KM
) {
    fun validate(
        draft: DriveTripDraft,
        vehicleExistsForOwner: Boolean
    ): DriveTripValidationResult {
        val issues = buildList {
            if (draft.vehicleId.isBlank()) {
                add(DriveValidationIssue(DriveValidationCode.VEHICLE_REQUIRED, "vehicleId"))
            } else if (!vehicleExistsForOwner) {
                add(DriveValidationIssue(DriveValidationCode.VEHICLE_NOT_FOUND, "vehicleId"))
            }
            if (draft.endedAt?.isBefore(draft.startedAt) == true) {
                add(DriveValidationIssue(DriveValidationCode.END_BEFORE_START, "endedAt"))
            }
            if (draft.distanceKm?.let { !it.isFinite() || it < 0.0 } == true) {
                add(DriveValidationIssue(DriveValidationCode.NEGATIVE_DISTANCE, "distanceKm"))
            }
            if (draft.startOdometerKm?.let { !it.isFinite() || it < 0.0 } == true) {
                add(DriveValidationIssue(DriveValidationCode.NEGATIVE_ODOMETER, "startOdometerKm"))
            }
            if (draft.endOdometerKm?.let { !it.isFinite() || it < 0.0 } == true) {
                add(DriveValidationIssue(DriveValidationCode.NEGATIVE_ODOMETER, "endOdometerKm"))
            }

            val start = draft.startOdometerKm
            val end = draft.endOdometerKm
            if (start != null && end != null) {
                if (end < start) {
                    add(
                        DriveValidationIssue(
                            DriveValidationCode.END_ODOMETER_BEFORE_START,
                            "endOdometerKm"
                        )
                    )
                } else if (
                    draft.distanceKm != null &&
                    abs((end - start) - draft.distanceKm) > consistencyToleranceKm
                ) {
                    add(
                        DriveValidationIssue(
                            DriveValidationCode.DISTANCE_ODOMETER_MISMATCH,
                            "distanceKm"
                        )
                    )
                }
            }

            if (draft.distanceKm == null && (start == null || end == null)) {
                add(DriveValidationIssue(DriveValidationCode.DISTANCE_REQUIRED, "distanceKm"))
            }
        }

        val resolvedDistance = when {
            issues.any {
                it.code == DriveValidationCode.NEGATIVE_DISTANCE ||
                    it.code == DriveValidationCode.END_ODOMETER_BEFORE_START ||
                    it.code == DriveValidationCode.DISTANCE_REQUIRED ||
                    it.code == DriveValidationCode.DISTANCE_ODOMETER_MISMATCH
            } -> null
            draft.distanceKm != null -> draft.distanceKm
            draft.startOdometerKm != null && draft.endOdometerKm != null ->
                draft.endOdometerKm - draft.startOdometerKm
            else -> null
        }
        return DriveTripValidationResult(issues, resolvedDistance)
    }

    private companion object {
        const val DEFAULT_CONSISTENCY_TOLERANCE_KM = 0.1
    }
}

sealed interface DriveDecimalParseResult {
    data object Empty : DriveDecimalParseResult
    data class Valid(val value: Double) : DriveDecimalParseResult
    data object Invalid : DriveDecimalParseResult
}

object DriveDecimalParser {
    fun parse(raw: String): DriveDecimalParseResult {
        val compact = raw.trim().replace(" ", "")
        if (compact.isEmpty()) return DriveDecimalParseResult.Empty
        if (compact.count { it == ',' } > 1 || compact.count { it == '.' } > 1) {
            return DriveDecimalParseResult.Invalid
        }
        if (',' in compact && '.' in compact) return DriveDecimalParseResult.Invalid
        val normalized = compact.replace(',', '.')
        val value = normalized.toDoubleOrNull() ?: return DriveDecimalParseResult.Invalid
        return if (value.isFinite()) DriveDecimalParseResult.Valid(value) else DriveDecimalParseResult.Invalid
    }
}
