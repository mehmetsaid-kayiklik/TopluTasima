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
    val syncState: DriveSyncState = DriveSyncState.LOCAL_PENDING
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
    val notes: String? = null
)

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
