package com.example.toplutasima.drive.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.toplutasima.R
import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveSyncReceiptStatus
import com.example.toplutasima.drive.model.DriveFieldSource
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@StringRes
internal fun VehicleFuelType.labelResource(language: AppLanguage): Int = when (this) {
    VehicleFuelType.PETROL -> language.selectResource(
        R.string.drive_fuel_petrol,
        R.string.drive_fuel_petrol_de,
        R.string.drive_fuel_petrol_en
    )
    VehicleFuelType.DIESEL -> language.selectResource(
        R.string.drive_fuel_diesel,
        R.string.drive_fuel_diesel_de,
        R.string.drive_fuel_diesel_en
    )
    VehicleFuelType.LPG -> language.selectResource(
        R.string.drive_fuel_lpg,
        R.string.drive_fuel_lpg_de,
        R.string.drive_fuel_lpg_en
    )
    VehicleFuelType.HYBRID -> language.selectResource(
        R.string.drive_fuel_hybrid,
        R.string.drive_fuel_hybrid_de,
        R.string.drive_fuel_hybrid_en
    )
    VehicleFuelType.PLUG_IN_HYBRID -> language.selectResource(
        R.string.drive_fuel_plug_in_hybrid,
        R.string.drive_fuel_plug_in_hybrid_de,
        R.string.drive_fuel_plug_in_hybrid_en
    )
    VehicleFuelType.ELECTRIC -> language.selectResource(
        R.string.drive_fuel_electric,
        R.string.drive_fuel_electric_de,
        R.string.drive_fuel_electric_en
    )
    VehicleFuelType.OTHER -> language.selectResource(
        R.string.drive_fuel_other,
        R.string.drive_fuel_other_de,
        R.string.drive_fuel_other_en
    )
    VehicleFuelType.UNKNOWN -> language.selectResource(
        R.string.drive_fuel_unknown,
        R.string.drive_fuel_unknown_de,
        R.string.drive_fuel_unknown_en
    )
}

@StringRes
internal fun DriveTripPurpose.labelResource(language: AppLanguage): Int = when (this) {
    DriveTripPurpose.PERSONAL -> language.selectResource(
        R.string.drive_purpose_personal,
        R.string.drive_purpose_personal_de,
        R.string.drive_purpose_personal_en
    )
    DriveTripPurpose.BUSINESS -> language.selectResource(
        R.string.drive_purpose_business,
        R.string.drive_purpose_business_de,
        R.string.drive_purpose_business_en
    )
    DriveTripPurpose.COMMUTE -> language.selectResource(
        R.string.drive_purpose_commute,
        R.string.drive_purpose_commute_de,
        R.string.drive_purpose_commute_en
    )
    DriveTripPurpose.OTHER -> language.selectResource(
        R.string.drive_purpose_other,
        R.string.drive_purpose_other_de,
        R.string.drive_purpose_other_en
    )
    DriveTripPurpose.UNCLASSIFIED -> language.selectResource(
        R.string.drive_purpose_unclassified,
        R.string.drive_purpose_unclassified_de,
        R.string.drive_purpose_unclassified_en
    )
}

@Composable
internal fun driveStringResource(@StringRes resourceId: Int): String = stringResource(resourceId)

internal fun formatDriveDistance(value: Double?, language: AppLanguage): String =
    value?.let { "${formatDriveNumber(it, language)} km" } ?: "—"

internal fun formatDriveNumber(value: Double, language: AppLanguage): String =
    NumberFormat.getNumberInstance(language.numberLocale()).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
        isGroupingUsed = true
    }.format(value)

internal fun formatDriveInstant(
    instant: Instant?,
    language: AppLanguage,
    zoneId: ZoneId = ZoneId.systemDefault()
): String = instant?.atZone(zoneId)?.format(
    DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm", language.numberLocale())
) ?: S.driveNoUsage(language)

internal fun DriveSyncState.displayText(language: AppLanguage): String = when (this) {
    DriveSyncState.LOCAL_PENDING -> S.driveSyncPending(language)
    DriveSyncState.SYNCING -> S.driveSyncing(language)
    DriveSyncState.SYNCED -> S.driveSynced(language)
    DriveSyncState.RETRYABLE_ERROR -> S.driveSyncRetry(language)
    DriveSyncState.PERMANENT_ERROR -> S.driveSyncFailed(language)
    DriveSyncState.UNKNOWN -> S.driveSyncUnknown(language)
}

internal fun DriveSyncReceiptStatus.displayText(language: AppLanguage): String = when (this) {
    DriveSyncReceiptStatus.STARTED -> S.driveSyncing(language)
    DriveSyncReceiptStatus.SUCCEEDED -> S.driveSynced(language)
    DriveSyncReceiptStatus.RETRY -> S.driveSyncRetry(language)
    DriveSyncReceiptStatus.FATAL -> S.driveSyncFailed(language)
    DriveSyncReceiptStatus.SUPERSEDED -> S.driveSyncPending(language)
    DriveSyncReceiptStatus.UNKNOWN -> S.driveSyncUnknown(language)
}

internal fun DriveFieldSource.displayText(language: AppLanguage): String = when (this) {
    DriveFieldSource.LOCAL -> S.driveProvenanceLocal(language)
    DriveFieldSource.REMOTE -> S.driveProvenanceRemote(language)
    DriveFieldSource.MERGED -> S.driveProvenanceMerged(language)
    DriveFieldSource.UNKNOWN -> S.driveProvenanceUnknown(language)
}

internal fun DriveOdometerSource.displayText(language: AppLanguage): String = when (this) {
    DriveOdometerSource.MANUAL -> S.driveOdometerManual(language)
    DriveOdometerSource.ESTIMATED -> S.driveOdometerEstimated(language)
    DriveOdometerSource.UNAVAILABLE -> S.driveOdometerUnavailable(language)
}

internal fun DriveFormError.displayText(language: AppLanguage): String = when (this) {
    DriveFormError.REQUIRED -> S.driveFieldRequired(language)
    DriveFormError.INVALID_NUMBER -> S.driveInvalidNumber(language)
    DriveFormError.INVALID_DATE -> S.driveInvalidDate(language)
    DriveFormError.INVALID_TIME -> S.driveInvalidTime(language)
    DriveFormError.DISPLAY_NAME_REQUIRED -> S.driveDisplayNameRequired(language)
    DriveFormError.MODEL_YEAR_OUT_OF_RANGE -> S.driveModelYearInvalid(language)
    DriveFormError.NEGATIVE_ODOMETER -> S.driveNegativeOdometer(language)
    DriveFormError.CURRENT_ODOMETER_BEFORE_INITIAL -> S.driveCurrentBeforeInitial(language)
    DriveFormError.VEHICLE_REQUIRED -> S.driveVehicleRequired(language)
    DriveFormError.VEHICLE_NOT_FOUND -> S.driveVehicleNotFound(language)
    DriveFormError.END_BEFORE_START -> S.driveEndBeforeStart(language)
    DriveFormError.NEGATIVE_DISTANCE -> S.driveNegativeDistance(language)
    DriveFormError.END_ODOMETER_BEFORE_START -> S.driveEndOdometerBeforeStart(language)
    DriveFormError.DISTANCE_REQUIRED -> S.driveDistanceRequired(language)
    DriveFormError.DISTANCE_ODOMETER_MISMATCH -> S.driveDistanceMismatch(language)
}

internal fun DriveUiMessage.displayText(language: AppLanguage): String = when (this) {
    DriveUiMessage.AUTHENTICATION_REQUIRED -> S.driveAuthRequired(language)
    DriveUiMessage.OWNERSHIP_MISMATCH -> S.driveOwnershipMismatch(language)
    DriveUiMessage.RECORD_NOT_FOUND -> S.driveRecordNotFound(language)
    DriveUiMessage.RECORD_DELETED -> S.driveRecordDeleted(language)
    DriveUiMessage.DATABASE_FAILURE -> S.driveDatabaseFailure(language)
    DriveUiMessage.SCHEDULING_FAILURE -> S.driveSchedulingFailure(language)
    DriveUiMessage.UNKNOWN_FAILURE -> S.driveUnknownFailure(language)
    DriveUiMessage.VEHICLE_SAVED -> S.driveVehicleSaved(language)
    DriveUiMessage.TRIP_SAVED -> S.driveTripSaved(language)
    DriveUiMessage.VEHICLE_DELETED -> S.driveVehicleDeleted(language)
    DriveUiMessage.TRIP_DELETED -> S.driveTripDeleted(language)
    DriveUiMessage.BULK_VEHICLES_DELETED -> S.driveBulkDeleted(language)
}

private fun AppLanguage.numberLocale(): Locale = resourceLocale()

@StringRes
private fun AppLanguage.selectResource(
    @StringRes turkish: Int,
    @StringRes german: Int,
    @StringRes english: Int
): Int = when (this) {
    AppLanguage.TR -> turkish
    AppLanguage.DE -> german
    AppLanguage.EN -> english
}

private fun AppLanguage.resourceLocale(): Locale = when (this) {
    AppLanguage.TR -> Locale.forLanguageTag("tr-TR")
    AppLanguage.DE -> Locale.GERMANY
    AppLanguage.EN -> Locale.ENGLISH
}
