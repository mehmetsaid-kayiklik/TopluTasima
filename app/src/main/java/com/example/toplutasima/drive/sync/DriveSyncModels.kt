package com.example.toplutasima.drive.sync

enum class DriveSyncEntityType {
    VEHICLE,
    TRIP
}

enum class DriveSyncOperationType {
    CREATE_VEHICLE,
    UPDATE_VEHICLE,
    DELETE_VEHICLE,
    CREATE_DRIVE_TRIP,
    UPDATE_DRIVE_TRIP,
    DELETE_DRIVE_TRIP;

    companion object {
        fun fromStorage(value: String?): DriveSyncOperationType? =
            entries.firstOrNull { it.name == value }
    }
}

val DriveSyncOperationType.entityType: DriveSyncEntityType
    get() = when (this) {
        DriveSyncOperationType.CREATE_VEHICLE,
        DriveSyncOperationType.UPDATE_VEHICLE,
        DriveSyncOperationType.DELETE_VEHICLE -> DriveSyncEntityType.VEHICLE

        DriveSyncOperationType.CREATE_DRIVE_TRIP,
        DriveSyncOperationType.UPDATE_DRIVE_TRIP,
        DriveSyncOperationType.DELETE_DRIVE_TRIP -> DriveSyncEntityType.TRIP
    }

val DriveSyncOperationType.isDelete: Boolean
    get() = this == DriveSyncOperationType.DELETE_VEHICLE ||
        this == DriveSyncOperationType.DELETE_DRIVE_TRIP

internal enum class DriveSyncFailureCode {
    AUTH_CHANGED,
    NETWORK,
    RATE_LIMITED,
    SERVICE_UNAVAILABLE,
    PERMISSION_DENIED,
    INVALID_DATA,
    RECORD_MISSING,
    DEPENDENCY_PENDING,
    CORRUPT_OPERATION,
    UNKNOWN
}

internal sealed interface DriveRemoteWriteResult {
    data object Applied : DriveRemoteWriteResult
    data object AlreadyApplied : DriveRemoteWriteResult
    data class DeletePrecedence(val deletedAtEpochMillis: Long) : DriveRemoteWriteResult
}
