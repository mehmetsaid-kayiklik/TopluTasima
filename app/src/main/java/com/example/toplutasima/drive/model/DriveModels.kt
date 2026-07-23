package com.example.toplutasima.drive.model

import java.time.Instant
import java.util.Locale

enum class VehicleFuelType {
    PETROL,
    DIESEL,
    LPG,
    HYBRID,
    PLUG_IN_HYBRID,
    ELECTRIC,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): VehicleFuelType =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleTransmissionType {
    MANUAL,
    AUTOMATIC,
    SEMI_AUTOMATIC,
    CVT,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): VehicleTransmissionType =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehicleBodyType {
    HATCHBACK,
    SEDAN,
    STATION_WAGON,
    SUV,
    COUPE,
    CONVERTIBLE,
    VAN,
    PICKUP,
    OTHER,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): VehicleBodyType =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class DriveTripPurpose {
    PERSONAL,
    BUSINESS,
    COMMUTE,
    OTHER,
    UNCLASSIFIED;

    companion object {
        fun fromStorage(value: String?): DriveTripPurpose =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNCLASSIFIED
    }
}

enum class DriveTripEntrySource {
    MANUAL,
    AUTOMATIC,
    IMPORTED,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): DriveTripEntrySource =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class DriveSyncState {
    LOCAL_PENDING,
    SYNCING,
    SYNCED,
    RETRYABLE_ERROR,
    PERMANENT_ERROR,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): DriveSyncState =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

data class DriveVehicle(
    val id: String,
    val ownerUid: String,
    val displayName: String,
    val brand: String? = null,
    val model: String? = null,
    val licensePlate: String? = null,
    val modelYear: Int? = null,
    val fuelType: VehicleFuelType = VehicleFuelType.UNKNOWN,
    val initialOdometerKm: Double? = null,
    val currentOdometerKm: Double? = null,
    val assignedPersonId: String? = null,
    val notes: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val deletePending: Boolean = false,
    val syncState: DriveSyncState = DriveSyncState.LOCAL_PENDING,
    val countryCode: String? = null,
    val transmissionType: VehicleTransmissionType = VehicleTransmissionType.UNKNOWN,
    val bodyType: VehicleBodyType = VehicleBodyType.UNKNOWN,
    val color: String? = null,
    val vin: String? = null,
    val engineDisplacementCc: Int? = null,
    val enginePowerKw: Int? = null,
    val purchaseDate: Instant? = null,
    val purchasePriceMinor: Long? = null,
    val currencyCode: String? = null,
    val primaryPhotoId: String? = null,
    val trimLevel: String? = null,
    val engineCode: String? = null,
    val registrationDate: Instant? = null,
    val inspectionDueDate: Instant? = null,
    val insuranceDueDate: Instant? = null,
    val tireSize: String? = null,
    val schemaVersion: Int = DRIVE_VEHICLE_SCHEMA_VERSION,
    val primaryPhotoRevision: Long = 0L,
    val primaryPhotoOperationId: String? = null
)

data class DriveTrip(
    val id: String,
    val ownerUid: String,
    val vehicleId: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val distanceKm: Double,
    val purpose: DriveTripPurpose = DriveTripPurpose.UNCLASSIFIED,
    val startLocationName: String? = null,
    val endLocationName: String? = null,
    val notes: String? = null,
    val entrySource: DriveTripEntrySource = DriveTripEntrySource.MANUAL,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
    val deletePending: Boolean = false,
    val syncState: DriveSyncState = DriveSyncState.LOCAL_PENDING
)

data class DriveVehicleDraft(
    val displayName: String,
    val brand: String? = null,
    val model: String? = null,
    val licensePlate: String? = null,
    val modelYear: Int? = null,
    val fuelType: VehicleFuelType = VehicleFuelType.UNKNOWN,
    val initialOdometerKm: Double? = null,
    val currentOdometerKm: Double? = null,
    val assignedPersonId: String? = null,
    val notes: String? = null,
    val countryCode: String? = null,
    val transmissionType: VehicleTransmissionType = VehicleTransmissionType.UNKNOWN,
    val bodyType: VehicleBodyType = VehicleBodyType.UNKNOWN,
    val color: String? = null,
    val vin: String? = null,
    val engineDisplacementCc: Int? = null,
    val enginePowerKw: Int? = null,
    val purchaseDate: Instant? = null,
    val purchasePriceMinor: Long? = null,
    val currencyCode: String? = null,
    val trimLevel: String? = null,
    val engineCode: String? = null,
    val registrationDate: Instant? = null,
    val inspectionDueDate: Instant? = null,
    val insuranceDueDate: Instant? = null,
    val tireSize: String? = null
)

const val DRIVE_VEHICLE_SCHEMA_VERSION = 2

data class DriveTripDraft(
    val vehicleId: String,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
    val distanceKm: Double? = null,
    val purpose: DriveTripPurpose = DriveTripPurpose.UNCLASSIFIED,
    val startLocationName: String? = null,
    val endLocationName: String? = null,
    val notes: String? = null
)

enum class DriveOdometerSource {
    MANUAL,
    ESTIMATED,
    UNAVAILABLE
}

data class DriveVehicleSummary(
    val totalDistanceKm: Double,
    val tripCount: Int,
    val lastUsedAt: Instant?,
    val initialOdometerKm: Double?,
    val manualCurrentOdometerKm: Double?,
    val estimatedCurrentOdometerKm: Double?,
    val displayedCurrentOdometerKm: Double?,
    val displayedOdometerSource: DriveOdometerSource,
    val hasOdometerInconsistency: Boolean
)

data class DriveVehicleOverview(
    val vehicle: DriveVehicle,
    val summary: DriveVehicleSummary
)
