package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "drive_vehicle_assignments",
    primaryKeys = ["ownerUid", "vehicleId"],
    indices = [
        Index(value = ["ownerUid", "personId"]),
        Index(value = ["ownerUid", "syncState", "clientUpdatedAt"]),
        Index(value = ["ownerUid", "deletedAt"]),
        Index(value = ["ownerUid", "operationId"], unique = true),
        Index(value = ["ownerUid", "healthCode"])
    ]
)
data class DriveVehicleAssignmentEntity(
    val ownerUid: String,
    val vehicleId: String,
    val personId: String?,
    val schemaVersion: Int,
    val revision: Long,
    val operationId: String,
    val source: String,
    val clientUpdatedAt: Long,
    val serverUpdatedAtSeconds: Long?,
    val serverUpdatedAtNanos: Int?,
    val deletedAt: Long?,
    val syncState: String,
    val healthCode: String? = null,
    val conflictOperationId: String? = null,
    val lastErrorCode: String? = null
)

@Entity(
    tableName = "drive_assignment_operations",
    primaryKeys = ["ownerUid", "operationId"],
    indices = [
        Index(value = ["ownerUid", "vehicleId", "targetRevision"], unique = true),
        Index(value = ["ownerUid", "state", "nextAttemptAt", "createdAt"]),
        Index(value = ["ownerUid", "vehicleId", "createdAt"])
    ]
)
data class DriveAssignmentOperationEntity(
    val ownerUid: String,
    val operationId: String,
    val vehicleId: String,
    val personId: String?,
    val schemaVersion: Int,
    val targetRevision: Long,
    val source: String,
    val clientUpdatedAt: Long,
    val deletedAt: Long?,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attemptCount: Int = 0,
    val nextAttemptAt: Long? = null,
    val lastErrorCode: String? = null
)

@Entity(tableName = "drive_assignment_sync_metadata")
data class DriveAssignmentSyncMetadataEntity(
    @PrimaryKey val ownerUid: String,
    val initialHydrationCompleted: Boolean,
    val cursorSeconds: Long?,
    val cursorNanos: Int?,
    val cursorDocumentId: String?,
    val lastSuccessfulPullAt: Long?,
    val updatedAt: Long
)

@Entity(
    tableName = "drive_assignment_sync_receipts",
    primaryKeys = ["ownerUid", "receiptId"],
    indices = [
        Index(value = ["ownerUid", "operationId"], unique = true),
        Index(value = ["ownerUid", "status", "startedAt"]),
        Index(value = ["ownerUid", "vehicleId", "startedAt"])
    ]
)
data class DriveAssignmentSyncReceiptEntity(
    val ownerUid: String,
    val receiptId: String,
    val operationId: String?,
    val vehicleId: String?,
    val kind: String,
    val status: String,
    val source: String?,
    val revision: Long?,
    val winningOperationId: String?,
    val startedAt: Long,
    val finishedAt: Long?,
    val attemptCount: Int,
    val errorCode: String?
)
