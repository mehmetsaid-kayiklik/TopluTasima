package com.example.toplutasima.drive.photo

import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import java.time.Instant
import java.util.Locale
import shared.vehiclephoto.contract.VehiclePhotoContract
import shared.vehiclephoto.contract.VehiclePhotoSource
import shared.vehiclephoto.contract.VehiclePhotoContractSpec

enum class VehiclePhotoUploadState {
    PENDING_UPLOAD,
    UPLOADING,
    PENDING_METADATA,
    PENDING_DELETE,
    DELETING,
    SYNCED,
    RETRYABLE_ERROR,
    FATAL_ERROR,
    SUPERSEDED,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): VehiclePhotoUploadState =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehiclePhotoRemoteState {
    LOCAL_ONLY,
    PRESENT,
    TOMBSTONED,
    STORAGE_MISSING,
    METADATA_MISSING,
    UNSUPPORTED,
    UNKNOWN;

    companion object {
        fun fromStorage(value: String?): VehiclePhotoRemoteState =
            entries.firstOrNull { it.name == value?.uppercase(Locale.ROOT) } ?: UNKNOWN
    }
}

enum class VehiclePhotoOperationType {
    UPLOAD,
    DELETE,
    UPDATE_METADATA,
    SET_PRIMARY
}

enum class VehiclePhotoOperationState {
    PENDING,
    RUNNING,
    RETRY,
    SUCCEEDED,
    FATAL,
    SUPERSEDED
}

enum class VehiclePhotoHealthCode {
    PHOTO_STORAGE_OBJECT_MISSING,
    PHOTO_METADATA_MISSING,
    PHOTO_UPLOAD_STUCK,
    PHOTO_DELETE_STUCK,
    PHOTO_INVALID_DIMENSIONS,
    PHOTO_INVALID_SIZE,
    PHOTO_UNSUPPORTED_MIME_TYPE,
    PHOTO_DUPLICATE_CONTENT,
    PHOTO_PRIMARY_CONFLICT,
    PHOTO_ORPHANED_FROM_VEHICLE,
    PHOTO_UNSUPPORTED_SCHEMA,
    PHOTO_CACHE_SCOPE_MISMATCH
}

data class DriveVehiclePhoto(
    val ownerUid: String,
    val photoId: String,
    val vehicleId: String,
    val localPreparedPath: String?,
    val storagePath: String?,
    val contentHash: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val sortOrder: Int,
    val isPrimary: Boolean,
    val revision: Long,
    val operationId: String,
    val uploadState: VehiclePhotoUploadState,
    val remoteState: VehiclePhotoRemoteState,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val lastErrorCode: String?,
    val healthCode: VehiclePhotoHealthCode?
)

data class PreparedVehiclePhoto(
    val path: String,
    val contentHash: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

sealed class VehiclePhotoFailure : Exception() {
    class PhotoSourceUnavailable : VehiclePhotoFailure()
    class PhotoDecodeFailed : VehiclePhotoFailure()
    class PhotoTooLarge : VehiclePhotoFailure()
    class UnsupportedPhotoType : VehiclePhotoFailure()
    class PhotoPreparationFailed : VehiclePhotoFailure()
    class PhotoUploadRetryable : VehiclePhotoFailure()
    class PhotoUploadFatal : VehiclePhotoFailure()
    class PhotoDeleteRetryable : VehiclePhotoFailure()
    class PhotoDeleteFatal : VehiclePhotoFailure()
    class PhotoMetadataConflict : VehiclePhotoFailure()
    class PhotoStorageObjectMissing : VehiclePhotoFailure()
    class VehicleNotFound : VehiclePhotoFailure()
    class VehicleDeleted : VehiclePhotoFailure()
    class AuthenticationChanged : VehiclePhotoFailure()
    class AccountMismatch : VehiclePhotoFailure()
    class UnsupportedPhotoSchema : VehiclePhotoFailure()
}

data class VehiclePhotoMutationResult(
    val photo: DriveVehiclePhoto?,
    val operationIds: List<String>
)

data class VehiclePhotoSyncResult(
    val processed: Boolean,
    val retryRequired: Boolean,
    val fatal: Boolean
)

fun DriveVehiclePhotoEntity.toDomain(): DriveVehiclePhoto = DriveVehiclePhoto(
    ownerUid = ownerUid,
    photoId = photoId,
    vehicleId = vehicleId,
    localPreparedPath = localPreparedPath,
    storagePath = storagePath,
    contentHash = contentHash,
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    sortOrder = sortOrder,
    isPrimary = isPrimary,
    revision = revision,
    operationId = operationId,
    uploadState = VehiclePhotoUploadState.fromStorage(uploadState),
    remoteState = VehiclePhotoRemoteState.fromStorage(remoteState),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    lastErrorCode = lastErrorCode,
    healthCode = healthCode?.let { value ->
        VehiclePhotoHealthCode.entries.firstOrNull { it.name == value }
    }
)

fun DriveVehiclePhotoEntity.toContract(): VehiclePhotoContract = VehiclePhotoContract(
    ownerUid = ownerUid,
    photoId = photoId,
    vehicleId = vehicleId,
    storagePath = storagePath,
    contentHash = contentHash,
    mimeType = mimeType,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    sortOrder = sortOrder,
    isPrimary = isPrimary,
    schemaVersion = schemaVersion,
    revision = revision,
    operationId = operationId,
    source = VehiclePhotoSource.fromWire(source),
    clientUpdatedAt = clientUpdatedAt,
    deletedAt = deletedAt
)

fun VehiclePhotoContract.toEntity(
    localPreparedPath: String?,
    serverSeconds: Long?,
    serverNanos: Int?,
    uploadState: VehiclePhotoUploadState,
    remoteState: VehiclePhotoRemoteState,
    createdAt: Long,
    healthCode: VehiclePhotoHealthCode? = null
): DriveVehiclePhotoEntity = DriveVehiclePhotoEntity(
    ownerUid = ownerUid,
    photoId = photoId,
    vehicleId = vehicleId,
    localUri = null,
    localPreparedPath = localPreparedPath,
    storagePath = storagePath,
    contentHash = contentHash,
    mimeType = mimeType ?: VehiclePhotoContractSpec.OUTPUT_MIME_TYPE,
    width = width,
    height = height,
    sizeBytes = sizeBytes,
    sortOrder = sortOrder,
    isPrimary = isPrimary,
    schemaVersion = schemaVersion,
    revision = revision,
    operationId = operationId,
    source = source.wireValue,
    clientUpdatedAt = clientUpdatedAt,
    serverUpdatedAtSeconds = serverSeconds,
    serverUpdatedAtNanos = serverNanos,
    deletedAt = deletedAt,
    uploadState = uploadState.name,
    remoteState = remoteState.name,
    createdAt = createdAt,
    updatedAt = clientUpdatedAt,
    lastErrorCode = null,
    healthCode = healthCode?.name
)
