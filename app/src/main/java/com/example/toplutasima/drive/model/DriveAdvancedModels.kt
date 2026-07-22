package com.example.toplutasima.drive.model

import java.time.Instant
import java.util.Locale

enum class DriveFieldSource {
    LOCAL,
    REMOTE,
    MERGED,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): DriveFieldSource =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

data class DriveFieldProvenance(
    val entityType: String,
    val recordId: String,
    val fieldName: String,
    val source: DriveFieldSource,
    val updatedAt: Instant
)

enum class DriveSyncReceiptStatus {
    STARTED,
    SUCCEEDED,
    RETRY,
    FATAL,
    SUPERSEDED,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): DriveSyncReceiptStatus =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class DriveSyncReceiptKind {
    INITIAL_PULL,
    INCREMENTAL_PULL,
    OUTBOUND,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): DriveSyncReceiptKind =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

data class DriveSyncReceipt(
    val receiptId: String,
    val kind: DriveSyncReceiptKind,
    val entityType: String?,
    val recordId: String?,
    val operationType: String?,
    val status: DriveSyncReceiptStatus,
    val startedAt: Instant,
    val finishedAt: Instant?,
    val attemptCount: Int,
    val errorCode: String?
)

enum class DriveHealthSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class DriveHealthCode {
    MISSING_LICENSE_PLATE,
    NEGATIVE_ODOMETER,
    CURRENT_ODOMETER_BEFORE_INITIAL,
    ORPHAN_TRIP,
    END_BEFORE_START,
    NEGATIVE_DISTANCE,
    ODOMETER_DISTANCE_MISMATCH,
    POSSIBLE_DUPLICATE,
    UNKNOWN_PROVENANCE,
    ASSIGNED_PERSON_NOT_FOUND,
    ASSIGNED_PERSON_DELETED,
    ASSIGNED_PERSON_NOT_SHARED,
    PERSON_ID_DOCUMENT_ID_MISMATCH,
    ASSIGNMENT_VEHICLE_NOT_FOUND,
    ASSIGNMENT_SCHEMA_UNSUPPORTED
}

data class DriveHealthIssue(
    val entityType: String,
    val recordId: String,
    val vehicleId: String?,
    val code: DriveHealthCode,
    val severity: DriveHealthSeverity
)

enum class DriveVehicleAssignmentFilter {
    ALL,
    ASSIGNED,
    UNASSIGNED
}

enum class DriveVehicleSort {
    NAME,
    LICENSE_PLATE,
    FUEL_TYPE,
    ASSIGNED_PERSON,
    LAST_USED,
    TOTAL_DISTANCE
}

data class DriveVehicleListCriteria(
    val query: String = "",
    val fuelType: VehicleFuelType? = null,
    val assignment: DriveVehicleAssignmentFilter = DriveVehicleAssignmentFilter.ALL,
    val sort: DriveVehicleSort = DriveVehicleSort.NAME,
    val descending: Boolean = false
)
