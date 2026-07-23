package com.example.toplutasima.drive.photo

import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DrivePhotoOperationEntity
import com.example.toplutasima.data.local.entity.DrivePhotoSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DrivePhotoSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import com.example.toplutasima.drive.DriveFeatureFlags
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import java.util.UUID
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import shared.vehiclephoto.contract.VehiclePhotoConflictResolver
import shared.vehiclephoto.contract.VehiclePhotoContractSpec
import shared.vehiclephoto.contract.VehiclePhotoResolution
import shared.vehiclephoto.contract.VehiclePhotoRevision
import shared.vehiclephoto.contract.VehiclePhotoSource
import shared.vehiclephoto.contract.VehiclePhotoWinnerReason
import shared.vehiclephoto.contract.VersionedVehiclePhoto

data class VehiclePhotoPullResult(
    val pulledCount: Int,
    val retryRequired: Boolean,
    val permanentFailureCount: Int
) {
    companion object {
        val EMPTY = VehiclePhotoPullResult(0, false, 0)
    }
}

interface VehiclePhotoPullCoordinator {
    suspend fun pull(ownerUid: String): VehiclePhotoPullResult

    data object NoOp : VehiclePhotoPullCoordinator {
        override suspend fun pull(ownerUid: String) = VehiclePhotoPullResult.EMPTY
    }
}

class RoomVehiclePhotoSyncCoordinator(
    private val database: AppDatabase,
    private val remote: VehiclePhotoRemoteDataSource,
    private val fileStore: VehiclePhotoFileStore,
    private val currentUserId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_VEHICLE_PHOTOS
) : VehiclePhotoPullCoordinator {
    private val photoDao get() = database.driveVehiclePhotoDao()
    private val operationDao get() = database.drivePhotoOperationDao()
    private val metadataDao get() = database.drivePhotoSyncMetadataDao()
    private val receiptDao get() = database.drivePhotoSyncReceiptDao()
    private val vehicleDao get() = database.driveVehicleDao()

    override suspend fun pull(ownerUid: String): VehiclePhotoPullResult {
        if (!enabled) return VehiclePhotoPullResult.EMPTY
        requireOwner(ownerUid)
        return try {
            val metadata = metadataDao.get(ownerUid)
            val batch = if (metadata?.initialHydrationCompleted == true) {
                remote.fetchIncremental(
                    ownerUid,
                    metadata.cursorSeconds?.let { seconds ->
                        VehiclePhotoRemoteCursor(
                            seconds,
                            metadata.cursorNanos ?: 0,
                            metadata.cursorDocumentPath.orEmpty()
                        )
                    }
                )
            } else {
                remote.fetchInitial(ownerUid)
            }
            requireOwner(ownerUid)
            database.withTransaction {
                batch.photos.forEach { incoming -> applyRemote(ownerUid, incoming) }
                batch.unsupported.forEach { (photoId, schema) ->
                    photoDao.get(ownerUid, photoId)?.let { local ->
                        photoDao.upsert(
                            local.copy(
                                remoteState = VehiclePhotoRemoteState.UNSUPPORTED.name,
                                healthCode = VehiclePhotoHealthCode.PHOTO_UNSUPPORTED_SCHEMA.name,
                                lastErrorCode = "SCHEMA_$schema"
                            )
                        )
                    }
                }
                metadataDao.upsert(
                    DrivePhotoSyncMetadataEntity(
                        ownerUid = ownerUid,
                        initialHydrationCompleted = true,
                        cursorSeconds = batch.cursor?.seconds ?: metadata?.cursorSeconds,
                        cursorNanos = batch.cursor?.nanos ?: metadata?.cursorNanos,
                        cursorDocumentPath = batch.cursor?.documentPath ?: metadata?.cursorDocumentPath,
                        lastSuccessfulPullAt = now(),
                        updatedAt = now()
                    )
                )
            }
            VehiclePhotoPullResult(
                pulledCount = batch.photos.size,
                retryRequired = false,
                permanentFailureCount = batch.unsupported.size + batch.invalidPhotoIds.size
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: VehiclePhotoFailure.AuthenticationChanged) {
            throw VehiclePhotoFailure.AuthenticationChanged()
        } catch (error: Exception) {
            val classified = VehiclePhotoFailureClassifier.classify(error, delete = false)
            VehiclePhotoPullResult(0, classified.retryable, if (classified.retryable) 0 else 1)
        }
    }

    suspend fun process(
        ownerUid: String,
        operationId: String,
        expectedPhotoId: String,
        expectedVehicleId: String
    ): VehiclePhotoSyncResult {
        if (!enabled) return VehiclePhotoSyncResult(false, false, false)
        requireOwner(ownerUid)
        val timestamp = now()
        val claimed = operationDao.claim(
            ownerUid,
            operationId,
            timestamp,
            timestamp - CLAIM_LEASE_MS
        )
        if (claimed == 0) {
            val existing = operationDao.get(ownerUid, operationId)
                ?: return VehiclePhotoSyncResult(false, false, true)
            return when (existing.state) {
                VehiclePhotoOperationState.SUCCEEDED.name,
                VehiclePhotoOperationState.SUPERSEDED.name -> VehiclePhotoSyncResult(false, false, false)
                VehiclePhotoOperationState.FATAL.name -> VehiclePhotoSyncResult(false, false, true)
                else -> VehiclePhotoSyncResult(false, true, false)
            }
        }
        val operation = operationDao.get(ownerUid, operationId)
            ?: return VehiclePhotoSyncResult(false, false, true)
        if (operation.photoId != expectedPhotoId || operation.vehicleId != expectedVehicleId) {
            return fatal(ownerUid, operation, "OPERATION_SCOPE_MISMATCH", null)
        }

        val photo = photoDao.get(ownerUid, operation.photoId)
        val vehicle = vehicleDao.getVehicle(ownerUid, operation.vehicleId)
        if (photo == null) return fatal(ownerUid, operation, "PHOTO_METADATA_MISSING", null)
        if (vehicle == null || vehicle.deletedAt != null) {
            if (operation.type != VehiclePhotoOperationType.DELETE.name) {
                convertToDelete(ownerUid, operation, photo)
                return VehiclePhotoSyncResult(false, true, false)
            }
        }
        if (operation.type != VehiclePhotoOperationType.SET_PRIMARY.name &&
            operation.targetRevision < photo.revision && operation.operationId != photo.operationId
        ) {
            return supersede(ownerUid, operation, photo, "NEWER_LOCAL_OPERATION")
        }

        markRunning(photo, operation)
        return try {
            val result = when (VehiclePhotoOperationType.valueOf(operation.type)) {
                VehiclePhotoOperationType.UPLOAD -> remote.upload(ownerUid, photo, operation)
                VehiclePhotoOperationType.UPDATE_METADATA -> {
                    if (photo.remoteState == VehiclePhotoRemoteState.PRESENT.name) {
                        remote.updateMetadata(ownerUid, photo, operation)
                    } else {
                        remote.upload(ownerUid, photo, operation)
                    }
                }
                VehiclePhotoOperationType.DELETE -> remote.delete(ownerUid, photo, operation)
                VehiclePhotoOperationType.SET_PRIMARY -> remote.setPrimary(ownerUid, operation)
            }
            requireOwner(ownerUid)
            applyResult(ownerUid, operation, photo, result)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val delete = operation.type == VehiclePhotoOperationType.DELETE.name
            val classified = VehiclePhotoFailureClassifier.classify(error, delete)
            if (classified.retryable) retry(ownerUid, operation, photo, classified.code)
            else fatal(ownerUid, operation, classified.code, photo)
        }
    }

    private suspend fun applyRemote(ownerUid: String, incoming: VersionedVehiclePhoto) {
        val remotePhoto = incoming.photo
        val vehicle = vehicleDao.getVehicle(ownerUid, remotePhoto.vehicleId)
        val local = photoDao.get(ownerUid, remotePhoto.photoId)
        val health = if (vehicle == null || vehicle.deletedAt != null) {
            VehiclePhotoHealthCode.PHOTO_ORPHANED_FROM_VEHICLE
        } else null
        val projectedPrimary = vehicle?.primaryPhotoId == remotePhoto.photoId && remotePhoto.deletedAt == null
        if (local == null) {
            photoDao.upsert(
                remotePhoto.copy(isPrimary = projectedPrimary).toEntity(
                    localPreparedPath = null,
                    serverSeconds = incoming.serverUpdatedAt?.seconds,
                    serverNanos = incoming.serverUpdatedAt?.nanoseconds,
                    uploadState = VehiclePhotoUploadState.SYNCED,
                    remoteState = if (remotePhoto.deletedAt == null) {
                        VehiclePhotoRemoteState.PRESENT
                    } else {
                        VehiclePhotoRemoteState.TOMBSTONED
                    },
                    createdAt = remotePhoto.clientUpdatedAt,
                    healthCode = health
                )
            )
            return
        }
        val localVersion = VersionedVehiclePhoto(
            local.toContract(),
            local.serverUpdatedAtSeconds?.let {
                shared.vehiclephoto.contract.VehiclePhotoServerTimestamp(
                    it,
                    local.serverUpdatedAtNanos ?: 0
                )
            }
        )
        val resolution: VehiclePhotoResolution = VehiclePhotoConflictResolver.resolve(
            localVersion,
            incoming,
            vehicleDeleted = vehicle?.deletedAt != null
        )
        val winner = resolution.winner ?: incoming
        if (winner.photo.operationId == local.operationId && local.uploadState != VehiclePhotoUploadState.SYNCED.name) {
            photoDao.upsert(local.copy(healthCode = health?.name ?: local.healthCode))
            return
        }
        val keepLocalPath = local.localPreparedPath?.takeIf {
            local.contentHash == winner.photo.contentHash &&
                fileStore.isScopedPath(it, ownerUid, winner.photo.vehicleId)
        }
        photoDao.upsert(
            winner.photo.copy(isPrimary = projectedPrimary).toEntity(
                localPreparedPath = keepLocalPath,
                serverSeconds = winner.serverUpdatedAt?.seconds,
                serverNanos = winner.serverUpdatedAt?.nanoseconds,
                uploadState = VehiclePhotoUploadState.SYNCED,
                remoteState = if (winner.photo.deletedAt == null) {
                    VehiclePhotoRemoteState.PRESENT
                } else {
                    VehiclePhotoRemoteState.TOMBSTONED
                },
                createdAt = local.createdAt,
                healthCode = health ?: VehiclePhotoHealthCode.PHOTO_PRIMARY_CONFLICT.takeIf {
                    resolution.reason == VehiclePhotoWinnerReason.OPERATION_ID
                }
            )
        )
    }

    private suspend fun applyResult(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        photo: DriveVehiclePhotoEntity,
        result: VehiclePhotoRemoteResult
    ): VehiclePhotoSyncResult = when (result) {
        is VehiclePhotoRemoteResult.Applied,
        is VehiclePhotoRemoteResult.AlreadyApplied -> succeed(
            ownerUid,
            operation,
            photo,
            (result as? VehiclePhotoRemoteResult.Applied)?.serverTimestamp
                ?: (result as? VehiclePhotoRemoteResult.AlreadyApplied)?.serverTimestamp
        )
        is VehiclePhotoRemoteResult.RemoteWon -> {
            database.withTransaction {
                val remotePhoto = result.remote.photo
                photoDao.upsert(
                    remotePhoto.toEntity(
                        localPreparedPath = photo.localPreparedPath.takeIf {
                            photo.contentHash == remotePhoto.contentHash
                        },
                        serverSeconds = result.remote.serverUpdatedAt?.seconds,
                        serverNanos = result.remote.serverUpdatedAt?.nanoseconds,
                        uploadState = VehiclePhotoUploadState.SYNCED,
                        remoteState = if (remotePhoto.deletedAt == null) VehiclePhotoRemoteState.PRESENT
                        else VehiclePhotoRemoteState.TOMBSTONED,
                        createdAt = photo.createdAt,
                        healthCode = VehiclePhotoHealthCode.PHOTO_PRIMARY_CONFLICT
                    )
                )
                finishOperation(ownerUid, operation, VehiclePhotoOperationState.SUPERSEDED, "REMOTE_WON")
                receipt(ownerUid, operation, "SUPERSEDED", result.remote.photo.operationId, "REMOTE_WON")
            }
            VehiclePhotoSyncResult(true, false, false)
        }
        is VehiclePhotoRemoteResult.PrimaryRemoteWon -> {
            database.withTransaction {
                vehicleDao.getVehicle(ownerUid, operation.vehicleId)?.let { vehicle ->
                    vehicleDao.upsert(
                        vehicle.copy(
                            primaryPhotoId = result.primaryPhotoId,
                            primaryPhotoRevision = result.revision,
                            primaryPhotoOperationId = result.operationId
                        )
                    )
                    photoDao.projectPrimary(ownerUid, operation.vehicleId, result.primaryPhotoId, now())
                }
                finishOperation(ownerUid, operation, VehiclePhotoOperationState.SUPERSEDED, "PRIMARY_REMOTE_WON")
                receipt(ownerUid, operation, "SUPERSEDED", result.operationId, "PRIMARY_REMOTE_WON")
            }
            VehiclePhotoSyncResult(true, false, false)
        }
        VehiclePhotoRemoteResult.DeleteWins -> supersede(ownerUid, operation, photo, "DELETE_WINS")
        VehiclePhotoRemoteResult.VehicleNotFound -> {
            if (operation.type == VehiclePhotoOperationType.DELETE.name) {
                succeed(ownerUid, operation, photo, null)
            } else {
                fatal(ownerUid, operation, "VEHICLE_NOT_FOUND", photo)
            }
        }
        VehiclePhotoRemoteResult.VehicleDeleted -> {
            if (operation.type == VehiclePhotoOperationType.DELETE.name) {
                succeed(ownerUid, operation, photo, null)
            } else {
                convertToDelete(ownerUid, operation, photo)
                VehiclePhotoSyncResult(false, true, false)
            }
        }
        VehiclePhotoRemoteResult.PhotoMetadataMissing ->
            retry(ownerUid, operation, photo, "PHOTO_METADATA_MISSING")
        VehiclePhotoRemoteResult.UnsupportedSchema ->
            fatal(ownerUid, operation, "PHOTO_UNSUPPORTED_SCHEMA", photo)
        VehiclePhotoRemoteResult.InvalidRemoteData ->
            fatal(ownerUid, operation, "PHOTO_METADATA_CONFLICT", photo)
    }

    private suspend fun succeed(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        photo: DriveVehiclePhotoEntity,
        serverTimestamp: shared.vehiclephoto.contract.VehiclePhotoServerTimestamp?
    ): VehiclePhotoSyncResult {
        val isDelete = operation.type == VehiclePhotoOperationType.DELETE.name
        database.withTransaction {
            if (operation.type != VehiclePhotoOperationType.SET_PRIMARY.name) {
                photoDao.upsert(
                    photo.copy(
                        serverUpdatedAtSeconds = serverTimestamp?.seconds ?: photo.serverUpdatedAtSeconds,
                        serverUpdatedAtNanos = serverTimestamp?.nanoseconds ?: photo.serverUpdatedAtNanos,
                        uploadState = VehiclePhotoUploadState.SYNCED.name,
                        remoteState = if (isDelete) VehiclePhotoRemoteState.TOMBSTONED.name
                        else VehiclePhotoRemoteState.PRESENT.name,
                        updatedAt = now(),
                        lastErrorCode = null,
                        healthCode = null
                    )
                )
            }
            finishOperation(ownerUid, operation, VehiclePhotoOperationState.SUCCEEDED, null)
            receipt(ownerUid, operation, "SUCCEEDED", operation.operationId, null)
        }
        if (isDelete) fileStore.deletePhoto(ownerUid, photo.vehicleId, photo.photoId)
        return VehiclePhotoSyncResult(true, false, false)
    }

    private suspend fun retry(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        photo: DriveVehiclePhotoEntity,
        code: String
    ): VehiclePhotoSyncResult {
        val nextAttempt = now() + retryDelay(operation.attemptCount)
        database.withTransaction {
            operationDao.finish(
                ownerUid,
                operation.operationId,
                VehiclePhotoOperationState.RETRY.name,
                now(),
                nextAttempt,
                code
            )
            photoDao.upsert(
                photo.copy(
                    uploadState = VehiclePhotoUploadState.RETRYABLE_ERROR.name,
                    updatedAt = now(),
                    lastErrorCode = code,
                    healthCode = when (operation.type) {
                        VehiclePhotoOperationType.DELETE.name -> VehiclePhotoHealthCode.PHOTO_DELETE_STUCK.name
                        else -> VehiclePhotoHealthCode.PHOTO_UPLOAD_STUCK.name
                    }
                )
            )
        }
        return VehiclePhotoSyncResult(false, true, false)
    }

    private suspend fun fatal(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        code: String,
        photo: DriveVehiclePhotoEntity?
    ): VehiclePhotoSyncResult {
        database.withTransaction {
            operationDao.finish(
                ownerUid,
                operation.operationId,
                VehiclePhotoOperationState.FATAL.name,
                now(),
                0L,
                code
            )
            photo?.let {
                photoDao.upsert(
                    it.copy(
                        uploadState = VehiclePhotoUploadState.FATAL_ERROR.name,
                        updatedAt = now(),
                        lastErrorCode = code,
                        healthCode = healthFor(code)?.name ?: it.healthCode
                    )
                )
            }
            receipt(ownerUid, operation, "FATAL", null, code)
        }
        return VehiclePhotoSyncResult(false, false, true)
    }

    private suspend fun supersede(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        photo: DriveVehiclePhotoEntity,
        code: String
    ): VehiclePhotoSyncResult {
        database.withTransaction {
            finishOperation(ownerUid, operation, VehiclePhotoOperationState.SUPERSEDED, code)
            receipt(ownerUid, operation, "SUPERSEDED", photo.operationId, code)
        }
        return VehiclePhotoSyncResult(true, false, false)
    }

    private suspend fun convertToDelete(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        photo: DriveVehiclePhotoEntity
    ) {
        val timestamp = now()
        val revision = VehiclePhotoRevision.next(maxOf(operation.targetRevision, photo.revision))
        database.withTransaction {
            photoDao.upsert(
                photo.copy(
                    revision = revision,
                    operationId = operation.operationId,
                    clientUpdatedAt = timestamp,
                    deletedAt = photo.deletedAt ?: timestamp,
                    isPrimary = false,
                    uploadState = VehiclePhotoUploadState.PENDING_DELETE.name,
                    updatedAt = timestamp,
                    healthCode = VehiclePhotoHealthCode.PHOTO_ORPHANED_FROM_VEHICLE.name
                )
            )
            operationDao.convert(
                ownerUid,
                operation.operationId,
                VehiclePhotoOperationType.DELETE.name,
                revision,
                timestamp
            )
        }
    }

    private suspend fun markRunning(photo: DriveVehiclePhotoEntity, operation: DrivePhotoOperationEntity) {
        if (operation.type == VehiclePhotoOperationType.SET_PRIMARY.name) return
        photoDao.upsert(
            photo.copy(
                uploadState = if (operation.type == VehiclePhotoOperationType.DELETE.name) {
                    VehiclePhotoUploadState.DELETING.name
                } else {
                    VehiclePhotoUploadState.UPLOADING.name
                },
                updatedAt = now()
            )
        )
    }

    private suspend fun finishOperation(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        state: VehiclePhotoOperationState,
        code: String?
    ) = operationDao.finish(ownerUid, operation.operationId, state.name, now(), 0L, code)

    private suspend fun receipt(
        ownerUid: String,
        operation: DrivePhotoOperationEntity,
        status: String,
        winningOperationId: String?,
        errorCode: String?
    ) {
        receiptDao.insert(
            DrivePhotoSyncReceiptEntity(
                ownerUid = ownerUid,
                receiptId = UUID.randomUUID().toString(),
                operationId = operation.operationId,
                photoId = operation.photoId,
                vehicleId = operation.vehicleId,
                kind = operation.type,
                status = status,
                provenance = VehiclePhotoSource.TOPLU_TASIMA.wireValue,
                revision = operation.targetRevision,
                winningOperationId = winningOperationId,
                attemptCount = operation.attemptCount,
                createdAt = operation.createdAt,
                finishedAt = now(),
                errorCode = errorCode
            )
        )
    }

    private fun requireOwner(ownerUid: String) {
        if (currentUserId()?.takeIf(String::isNotBlank) != ownerUid) {
            throw VehiclePhotoFailure.AuthenticationChanged()
        }
    }

    private fun retryDelay(attempt: Int): Long {
        val exponent = attempt.coerceIn(0, 10)
        return min(MAX_RETRY_DELAY_MS, BASE_RETRY_DELAY_MS * (1L shl exponent))
    }

    private fun healthFor(code: String): VehiclePhotoHealthCode? = when (code) {
        "PHOTO_UNSUPPORTED_SCHEMA" -> VehiclePhotoHealthCode.PHOTO_UNSUPPORTED_SCHEMA
        "PHOTO_METADATA_MISSING" -> VehiclePhotoHealthCode.PHOTO_METADATA_MISSING
        "PHOTO_STORAGE_OBJECT_MISSING" -> VehiclePhotoHealthCode.PHOTO_STORAGE_OBJECT_MISSING
        "VEHICLE_NOT_FOUND" -> VehiclePhotoHealthCode.PHOTO_ORPHANED_FROM_VEHICLE
        else -> null
    }

    private companion object {
        const val CLAIM_LEASE_MS = 15L * 60L * 1_000L
        const val BASE_RETRY_DELAY_MS = 30_000L
        const val MAX_RETRY_DELAY_MS = 6L * 60L * 60L * 1_000L
    }
}

data class ClassifiedVehiclePhotoFailure(val retryable: Boolean, val code: String)

object VehiclePhotoFailureClassifier {
    fun classify(error: Exception, delete: Boolean): ClassifiedVehiclePhotoFailure = when (error) {
        is VehiclePhotoFailure.AuthenticationChanged -> ClassifiedVehiclePhotoFailure(false, "AUTHENTICATION_CHANGED")
        is VehiclePhotoFailure.PhotoStorageObjectMissing ->
            ClassifiedVehiclePhotoFailure(false, "PHOTO_STORAGE_OBJECT_MISSING")
        is VehiclePhotoFailure.UnsupportedPhotoSchema ->
            ClassifiedVehiclePhotoFailure(false, "PHOTO_UNSUPPORTED_SCHEMA")
        is VehiclePhotoFailure.PhotoMetadataConflict ->
            ClassifiedVehiclePhotoFailure(false, "PHOTO_METADATA_CONFLICT")
        is StorageException -> when (error.errorCode) {
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED,
            StorageException.ERROR_UNKNOWN -> ClassifiedVehiclePhotoFailure(
                true,
                if (delete) "PHOTO_DELETE_RETRYABLE" else "PHOTO_UPLOAD_RETRYABLE"
            )
            StorageException.ERROR_OBJECT_NOT_FOUND -> ClassifiedVehiclePhotoFailure(
                retryable = !delete,
                code = "PHOTO_STORAGE_OBJECT_MISSING"
            )
            else -> ClassifiedVehiclePhotoFailure(
                false,
                if (delete) "PHOTO_DELETE_FATAL" else "PHOTO_UPLOAD_FATAL"
            )
        }
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.CANCELLED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.UNAVAILABLE -> ClassifiedVehiclePhotoFailure(true, "PHOTO_REMOTE_RETRYABLE")
            else -> ClassifiedVehiclePhotoFailure(false, "PHOTO_REMOTE_FATAL")
        }
        is FirebaseNetworkException -> ClassifiedVehiclePhotoFailure(true, "PHOTO_NETWORK_RETRYABLE")
        else -> ClassifiedVehiclePhotoFailure(true, "PHOTO_UNKNOWN_RETRYABLE")
    }
}
