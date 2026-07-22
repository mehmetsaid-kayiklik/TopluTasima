package com.example.toplutasima.drive.assignment

import com.example.toplutasima.data.local.entity.DriveVehicleAssignmentEntity
import shared.vehicleassignment.contract.AssignmentServerTimestamp
import shared.vehicleassignment.contract.VehicleAssignmentContract
import shared.vehicleassignment.contract.VehicleAssignmentSource
import java.time.Instant

enum class VehicleAssignmentSyncState {
    PENDING,
    SYNCED,
    RETRY,
    FATAL,
    CONFLICT,
    UNSUPPORTED;

    companion object {
        fun fromStorage(value: String?): VehicleAssignmentSyncState =
            entries.firstOrNull { it.name == value } ?: FATAL
    }
}

enum class VehicleAssignmentHealthCode {
    ASSIGNED_PERSON_NOT_FOUND,
    ASSIGNED_PERSON_DELETED,
    ASSIGNED_PERSON_NOT_SHARED,
    PERSON_ID_DOCUMENT_ID_MISMATCH,
    ASSIGNMENT_VEHICLE_NOT_FOUND,
    ASSIGNMENT_SCHEMA_UNSUPPORTED;

    companion object {
        fun fromStorage(value: String?): VehicleAssignmentHealthCode? =
            entries.firstOrNull { it.name == value }
    }
}

data class VehicleAssignment(
    val ownerUid: String,
    val contract: VehicleAssignmentContract,
    val serverUpdatedAt: AssignmentServerTimestamp?,
    val syncState: VehicleAssignmentSyncState,
    val healthCode: VehicleAssignmentHealthCode?,
    val conflictOperationId: String?,
    val lastErrorCode: String?
) {
    val activePersonId: String?
        get() = contract.personId.takeIf { contract.deletedAt == null }
}

sealed interface VehicleAssignmentFailure {
    data object AuthenticationChanged : VehicleAssignmentFailure
    data object AccountMismatch : VehicleAssignmentFailure
    data object VehicleNotFound : VehicleAssignmentFailure
    data object VehicleDeleted : VehicleAssignmentFailure
    data object PersonNotFound : VehicleAssignmentFailure
    data object PersonDeleted : VehicleAssignmentFailure
    data object PersonNotShared : VehicleAssignmentFailure
    data object InvalidPersonIdentity : VehicleAssignmentFailure
    data object AssignmentConflict : VehicleAssignmentFailure
    data object UnsupportedAssignmentSchema : VehicleAssignmentFailure
    data object RetryableRemoteFailure : VehicleAssignmentFailure
    data object FatalRemoteFailure : VehicleAssignmentFailure
    data object LocalStorageFailure : VehicleAssignmentFailure
}

sealed interface VehicleAssignmentMutationResult {
    data class Success(val assignment: VehicleAssignment) : VehicleAssignmentMutationResult
    data class LocalSavedSyncSchedulingFailed(
        val assignment: VehicleAssignment
    ) : VehicleAssignmentMutationResult
    data class Rejected(val failure: VehicleAssignmentFailure) : VehicleAssignmentMutationResult
}

data class VehiclePersonDirectoryEntry(
    val personId: String,
    val displayName: String?,
    val sharedWithTransit: Boolean,
    val archived: Boolean,
    val deleted: Boolean,
    val identityValid: Boolean
) {
    val selectable: Boolean
        get() = sharedWithTransit && !archived && !deleted && identityValid
}

internal fun DriveVehicleAssignmentEntity.toDomain(): VehicleAssignment = VehicleAssignment(
    ownerUid = ownerUid,
    contract = VehicleAssignmentContract(
        vehicleId = vehicleId,
        personId = personId,
        schemaVersion = schemaVersion,
        revision = revision,
        operationId = operationId,
        source = VehicleAssignmentSource.fromWire(source),
        clientUpdatedAt = clientUpdatedAt,
        deletedAt = deletedAt
    ),
    serverUpdatedAt = if (serverUpdatedAtSeconds != null && serverUpdatedAtNanos != null) {
        AssignmentServerTimestamp(serverUpdatedAtSeconds, serverUpdatedAtNanos)
    } else {
        null
    },
    syncState = VehicleAssignmentSyncState.fromStorage(syncState),
    healthCode = VehicleAssignmentHealthCode.fromStorage(healthCode),
    conflictOperationId = conflictOperationId,
    lastErrorCode = lastErrorCode
)

internal fun VehicleAssignmentContract.toEntity(
    ownerUid: String,
    serverUpdatedAt: AssignmentServerTimestamp?,
    syncState: VehicleAssignmentSyncState,
    healthCode: VehicleAssignmentHealthCode? = null,
    conflictOperationId: String? = null,
    lastErrorCode: String? = null
): DriveVehicleAssignmentEntity = DriveVehicleAssignmentEntity(
    ownerUid = ownerUid,
    vehicleId = vehicleId,
    personId = personId,
    schemaVersion = schemaVersion,
    revision = revision,
    operationId = operationId,
    source = source.wireValue,
    clientUpdatedAt = clientUpdatedAt,
    serverUpdatedAtSeconds = serverUpdatedAt?.seconds,
    serverUpdatedAtNanos = serverUpdatedAt?.nanoseconds,
    deletedAt = deletedAt,
    syncState = syncState.name,
    healthCode = healthCode?.name,
    conflictOperationId = conflictOperationId,
    lastErrorCode = lastErrorCode
)

internal fun Long.toInstant(): Instant = Instant.ofEpochMilli(this)
