package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "drive_vehicle_photos",
    primaryKeys = ["ownerUid", "photoId"],
    indices = [
        Index(value = ["ownerUid", "vehicleId"]),
        Index(value = ["ownerUid", "uploadState"]),
        Index(value = ["ownerUid", "deletedAt"]),
        Index(value = ["ownerUid", "vehicleId", "sortOrder"]),
        Index(value = ["ownerUid", "contentHash"]),
        Index(value = ["ownerUid", "serverUpdatedAtSeconds", "serverUpdatedAtNanos", "photoId"])
    ]
)
data class DriveVehiclePhotoEntity(
    val ownerUid: String,
    val photoId: String,
    val vehicleId: String,
    val localUri: String?,
    val localPreparedPath: String?,
    val storagePath: String?,
    val contentHash: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val sortOrder: Int,
    val isPrimary: Boolean,
    val schemaVersion: Int,
    val revision: Long,
    val operationId: String,
    val source: String,
    val clientUpdatedAt: Long,
    val serverUpdatedAtSeconds: Long?,
    val serverUpdatedAtNanos: Int?,
    val deletedAt: Long?,
    val uploadState: String,
    val remoteState: String,
    val createdAt: Long,
    val updatedAt: Long,
    val lastErrorCode: String?,
    val healthCode: String?
)

@Entity(
    tableName = "drive_photo_operations",
    primaryKeys = ["ownerUid", "operationId"],
    indices = [
        Index(value = ["ownerUid", "photoId"]),
        Index(value = ["ownerUid", "vehicleId"]),
        Index(value = ["ownerUid", "state", "nextAttemptAt", "createdAt"]),
        Index(value = ["ownerUid", "photoId", "type", "targetRevision"], unique = true)
    ]
)
data class DrivePhotoOperationEntity(
    val ownerUid: String,
    val operationId: String,
    val photoId: String,
    val vehicleId: String,
    val type: String,
    val targetRevision: Long,
    val targetPrimaryPhotoId: String?,
    val expectedContentHash: String?,
    val state: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attemptCount: Int,
    val nextAttemptAt: Long,
    val claimedAt: Long?,
    val lastErrorCode: String?
)

@Entity(tableName = "drive_photo_sync_metadata", primaryKeys = ["ownerUid"])
data class DrivePhotoSyncMetadataEntity(
    val ownerUid: String,
    val initialHydrationCompleted: Boolean,
    val cursorSeconds: Long?,
    val cursorNanos: Int?,
    val cursorDocumentPath: String?,
    val lastSuccessfulPullAt: Long?,
    val updatedAt: Long
)

@Entity(
    tableName = "drive_photo_sync_receipts",
    primaryKeys = ["ownerUid", "receiptId"],
    indices = [
        Index(value = ["ownerUid", "operationId"], unique = true),
        Index(value = ["ownerUid", "vehicleId", "createdAt"]),
        Index(value = ["ownerUid", "status", "createdAt"])
    ]
)
data class DrivePhotoSyncReceiptEntity(
    val ownerUid: String,
    val receiptId: String,
    val operationId: String,
    val photoId: String,
    val vehicleId: String,
    val kind: String,
    val status: String,
    val provenance: String,
    val revision: Long,
    val winningOperationId: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val finishedAt: Long?,
    val errorCode: String?
)
