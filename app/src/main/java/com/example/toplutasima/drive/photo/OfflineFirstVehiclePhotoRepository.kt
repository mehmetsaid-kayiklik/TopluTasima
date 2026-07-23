package com.example.toplutasima.drive.photo

import android.net.Uri
import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DrivePhotoOperationEntity
import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import com.example.toplutasima.drive.DriveFeatureFlags
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import shared.vehiclephoto.contract.VehiclePhotoContractSpec
import shared.vehiclephoto.contract.VehiclePhotoRevision
import shared.vehiclephoto.contract.VehiclePhotoSource

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstVehiclePhotoRepository(
    private val database: AppDatabase,
    private val currentUserId: () -> String?,
    authenticatedUidChanges: Flow<String?>,
    private val preparer: VehiclePhotoPreparer,
    private val remote: VehiclePhotoRemoteDataSource,
    private val fileStore: VehiclePhotoFileStore,
    private val scheduler: VehiclePhotoSyncScheduler,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_VEHICLE_PHOTOS
) : VehiclePhotoRepository {
    private val ownerChanges = authenticatedUidChanges
        .map { it?.takeIf(String::isNotBlank) }
        .distinctUntilChanged()
    private val photoDao get() = database.driveVehiclePhotoDao()
    private val operationDao get() = database.drivePhotoOperationDao()
    private val vehicleDao get() = database.driveVehicleDao()

    override fun observePhotos(vehicleId: String): Flow<List<DriveVehiclePhoto>> {
        if (!enabled || vehicleId.isBlank()) return flowOf(emptyList())
        return ownerChanges.flatMapLatest { ownerUid ->
            if (ownerUid == null) flowOf(emptyList())
            else photoDao.observeForVehicle(ownerUid, vehicleId).map { rows ->
                rows.filter { it.deletedAt == null }.map(DriveVehiclePhotoEntity::toDomain)
            }
        }
    }

    override suspend fun add(vehicleId: String, source: Uri): VehiclePhotoMutationResult {
        val ownerUid = requireOwner()
        requireActiveVehicle(ownerUid, vehicleId)
        val photoId = newId()
        val prepared = preparer.prepare(ownerUid, vehicleId, photoId, source)
        var persisted = false
        try {
            requireSameOwner(ownerUid)
            val createdOperations = mutableListOf<DrivePhotoOperationEntity>()
            var created: DriveVehiclePhotoEntity? = null
            database.withTransaction {
                val vehicle = vehicleDao.getActiveVehicle(ownerUid, vehicleId)
                    ?: throw VehiclePhotoFailure.VehicleDeleted()
                val active = photoDao.getActiveForVehicle(ownerUid, vehicleId)
                val timestamp = now()
                val uploadOperationId = newId()
                val isFirst = active.isEmpty()
                val duplicate = active.any { it.contentHash == prepared.contentHash }
                val entity = DriveVehiclePhotoEntity(
                    ownerUid = ownerUid,
                    photoId = photoId,
                    vehicleId = vehicleId,
                    localUri = null,
                    localPreparedPath = prepared.path,
                    storagePath = VehiclePhotoContractSpec.storagePath(ownerUid, vehicleId, photoId),
                    contentHash = prepared.contentHash,
                    mimeType = prepared.mimeType,
                    width = prepared.width,
                    height = prepared.height,
                    sizeBytes = prepared.sizeBytes,
                    sortOrder = (active.maxOfOrNull { it.sortOrder } ?: -10) + 10,
                    isPrimary = isFirst,
                    schemaVersion = VehiclePhotoContractSpec.CURRENT_SCHEMA_VERSION,
                    revision = 1L,
                    operationId = uploadOperationId,
                    source = VehiclePhotoSource.TOPLU_TASIMA.wireValue,
                    clientUpdatedAt = timestamp,
                    serverUpdatedAtSeconds = null,
                    serverUpdatedAtNanos = null,
                    deletedAt = null,
                    uploadState = VehiclePhotoUploadState.PENDING_UPLOAD.name,
                    remoteState = VehiclePhotoRemoteState.LOCAL_ONLY.name,
                    createdAt = timestamp,
                    updatedAt = timestamp,
                    lastErrorCode = null,
                    healthCode = VehiclePhotoHealthCode.PHOTO_DUPLICATE_CONTENT.name.takeIf { duplicate }
                )
                photoDao.upsert(entity)
                createdOperations += operation(entity, VehiclePhotoOperationType.UPLOAD, uploadOperationId)
                if (operationDao.insert(createdOperations.last()) == -1L) {
                    throw VehiclePhotoFailure.PhotoMetadataConflict()
                }
                if (isFirst) {
                    val primaryOperationId = newId()
                    val primaryRevision = vehicle.primaryPhotoRevision + 1L
                    vehicleDao.upsert(
                        vehicle.copy(
                            primaryPhotoId = photoId,
                            primaryPhotoRevision = primaryRevision,
                            primaryPhotoOperationId = primaryOperationId
                        )
                    )
                    val primaryOperation = primaryOperation(
                        ownerUid = ownerUid,
                        vehicleId = vehicleId,
                        operationId = primaryOperationId,
                        operationPhotoId = photoId,
                        targetPhotoId = photoId,
                        revision = primaryRevision,
                        timestamp = timestamp,
                        contentHash = prepared.contentHash
                    )
                    createdOperations += primaryOperation
                    if (operationDao.insert(primaryOperation) == -1L) {
                        throw VehiclePhotoFailure.PhotoMetadataConflict()
                    }
                }
                created = entity
            }
            persisted = true
            requireSameOwner(ownerUid)
            createdOperations.forEach(::schedule)
            return VehiclePhotoMutationResult(created?.toDomain(), createdOperations.map { it.operationId })
        } catch (cancelled: CancellationException) {
            if (!persisted) fileStore.deletePhoto(ownerUid, vehicleId, photoId)
            throw cancelled
        } catch (error: Exception) {
            if (!persisted) fileStore.deletePhoto(ownerUid, vehicleId, photoId)
            throw error
        }
    }

    override suspend fun delete(vehicleId: String, photoId: String): VehiclePhotoMutationResult =
        mutateExisting(vehicleId, photoId) { ownerUid, vehicle, existing, timestamp ->
            val deleteOperationId = newId()
            val tombstone = existing.copy(
                isPrimary = false,
                revision = VehiclePhotoRevision.next(existing.revision),
                operationId = deleteOperationId,
                clientUpdatedAt = timestamp,
                deletedAt = timestamp,
                uploadState = VehiclePhotoUploadState.PENDING_DELETE.name,
                updatedAt = timestamp,
                lastErrorCode = null
            )
            photoDao.upsert(tombstone)
            val operations = mutableListOf(operation(tombstone, VehiclePhotoOperationType.DELETE, deleteOperationId))
            if (operationDao.insert(operations.last()) == -1L) throw VehiclePhotoFailure.PhotoMetadataConflict()
            if (existing.isPrimary || vehicle.primaryPhotoId == photoId) {
                val replacement = photoDao.deterministicReplacement(ownerUid, vehicleId, photoId)
                val primaryOperationId = newId()
                val primaryRevision = vehicle.primaryPhotoRevision + 1L
                photoDao.projectPrimary(ownerUid, vehicleId, replacement?.photoId, timestamp)
                vehicleDao.upsert(
                    vehicle.copy(
                        primaryPhotoId = replacement?.photoId,
                        primaryPhotoRevision = primaryRevision,
                        primaryPhotoOperationId = primaryOperationId
                    )
                )
                val primaryOperation = primaryOperation(
                    ownerUid,
                    vehicleId,
                    primaryOperationId,
                    replacement?.photoId ?: photoId,
                    replacement?.photoId,
                    primaryRevision,
                    timestamp,
                    replacement?.contentHash
                )
                operations += primaryOperation
                if (operationDao.insert(primaryOperation) == -1L) throw VehiclePhotoFailure.PhotoMetadataConflict()
            }
            VehiclePhotoMutationResult(null, operations.map { it.operationId }) to operations
        }

    override suspend fun setPrimary(vehicleId: String, photoId: String): VehiclePhotoMutationResult =
        mutateExisting(vehicleId, photoId) { ownerUid, vehicle, existing, timestamp ->
            if (vehicle.primaryPhotoId == photoId && existing.isPrimary) {
                return@mutateExisting VehiclePhotoMutationResult(existing.toDomain(), emptyList()) to emptyList()
            }
            val operationId = newId()
            val revision = vehicle.primaryPhotoRevision + 1L
            photoDao.projectPrimary(ownerUid, vehicleId, photoId, timestamp)
            vehicleDao.upsert(
                vehicle.copy(
                    primaryPhotoId = photoId,
                    primaryPhotoRevision = revision,
                    primaryPhotoOperationId = operationId
                )
            )
            val operation = primaryOperation(
                ownerUid,
                vehicleId,
                operationId,
                photoId,
                photoId,
                revision,
                timestamp,
                existing.contentHash
            )
            if (operationDao.insert(operation) == -1L) throw VehiclePhotoFailure.PhotoMetadataConflict()
            VehiclePhotoMutationResult(existing.copy(isPrimary = true).toDomain(), listOf(operationId)) to
                listOf(operation)
        }

    override suspend fun move(
        vehicleId: String,
        photoId: String,
        direction: Int
    ): VehiclePhotoMutationResult {
        if (direction !in setOf(-1, 1)) throw VehiclePhotoFailure.PhotoMetadataConflict()
        val ownerUid = requireOwner()
        val operations = mutableListOf<DrivePhotoOperationEntity>()
        var resultPhoto: DriveVehiclePhotoEntity? = null
        database.withTransaction {
            requireActiveVehicle(ownerUid, vehicleId)
            val active = photoDao.getActiveForVehicle(ownerUid, vehicleId)
            val index = active.indexOfFirst { it.photoId == photoId }
            if (index < 0) throw VehiclePhotoFailure.PhotoSourceUnavailable()
            val targetIndex = index + direction
            if (targetIndex !in active.indices) {
                resultPhoto = active[index]
                return@withTransaction
            }
            val timestamp = now()
            val first = active[index]
            val second = active[targetIndex]
            listOf(first.copy(sortOrder = second.sortOrder), second.copy(sortOrder = first.sortOrder)).forEach { row ->
                val operationId = newId()
                val updated = row.copy(
                    revision = VehiclePhotoRevision.next(row.revision),
                    operationId = operationId,
                    clientUpdatedAt = timestamp,
                    uploadState = VehiclePhotoUploadState.PENDING_METADATA.name,
                    updatedAt = timestamp,
                    lastErrorCode = null
                )
                photoDao.upsert(updated)
                val operation = operation(updated, VehiclePhotoOperationType.UPDATE_METADATA, operationId)
                if (operationDao.insert(operation) == -1L) throw VehiclePhotoFailure.PhotoMetadataConflict()
                operations += operation
                if (updated.photoId == photoId) resultPhoto = updated
            }
        }
        requireSameOwner(ownerUid)
        operations.forEach(::schedule)
        return VehiclePhotoMutationResult(resultPhoto?.toDomain(), operations.map { it.operationId })
    }

    override suspend fun retry(vehicleId: String, photoId: String): VehiclePhotoMutationResult =
        mutateExisting(vehicleId, photoId, allowDeleted = true) { _, _, existing, timestamp ->
            val operationId = newId()
            val type = when {
                existing.deletedAt != null -> VehiclePhotoOperationType.DELETE
                existing.remoteState != VehiclePhotoRemoteState.PRESENT.name -> VehiclePhotoOperationType.UPLOAD
                else -> VehiclePhotoOperationType.UPDATE_METADATA
            }
            val updated = existing.copy(
                revision = VehiclePhotoRevision.next(existing.revision),
                operationId = operationId,
                clientUpdatedAt = timestamp,
                uploadState = when (type) {
                    VehiclePhotoOperationType.DELETE -> VehiclePhotoUploadState.PENDING_DELETE.name
                    VehiclePhotoOperationType.UPLOAD -> VehiclePhotoUploadState.PENDING_UPLOAD.name
                    else -> VehiclePhotoUploadState.PENDING_METADATA.name
                },
                updatedAt = timestamp,
                lastErrorCode = null
            )
            photoDao.upsert(updated)
            val operation = operation(updated, type, operationId)
            if (operationDao.insert(operation) == -1L) throw VehiclePhotoFailure.PhotoMetadataConflict()
            VehiclePhotoMutationResult(updated.toDomain(), listOf(operationId)) to listOf(operation)
        }

    override suspend fun ensureLocalCopies(vehicleId: String) {
        if (!enabled) return
        val ownerUid = requireOwner()
        val photos = photoDao.getActiveForVehicle(ownerUid, vehicleId)
        photos.forEach { photo ->
            if (fileStore.isScopedPath(photo.localPreparedPath, ownerUid, vehicleId) &&
                photo.localPreparedPath?.let { java.io.File(it).isFile } == true
            ) return@forEach
            try {
                val path = remote.downloadToCache(ownerUid, photo)
                requireSameOwner(ownerUid)
                photoDao.updateLocalCopy(ownerUid, photo.photoId, path, null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: VehiclePhotoFailure.PhotoStorageObjectMissing) {
                photoDao.updateLocalCopy(
                    ownerUid,
                    photo.photoId,
                    null,
                    VehiclePhotoHealthCode.PHOTO_STORAGE_OBJECT_MISSING.name
                )
            } catch (_: Exception) {
                // The persisted projection remains authoritative; UI exposes a retry state.
            }
        }
    }

    override suspend fun schedulePendingOperations() {
        if (!enabled) return
        val ownerUid = requireOwner()
        operationDao.pending(ownerUid, now(), MAX_PENDING_SCHEDULE).forEach(::schedule)
    }

    private suspend fun mutateExisting(
        vehicleId: String,
        photoId: String,
        allowDeleted: Boolean = false,
        block: suspend (
            ownerUid: String,
            vehicle: com.example.toplutasima.data.local.entity.DriveVehicleEntity,
            photo: DriveVehiclePhotoEntity,
            timestamp: Long
        ) -> Pair<VehiclePhotoMutationResult, List<DrivePhotoOperationEntity>>
    ): VehiclePhotoMutationResult {
        val ownerUid = requireOwner()
        lateinit var result: VehiclePhotoMutationResult
        var operations: List<DrivePhotoOperationEntity> = emptyList()
        database.withTransaction {
            val vehicle = requireActiveVehicle(ownerUid, vehicleId)
            val photo = photoDao.get(ownerUid, photoId)
                ?.takeIf { it.vehicleId == vehicleId }
                ?: throw VehiclePhotoFailure.PhotoSourceUnavailable()
            if (!allowDeleted && photo.deletedAt != null) throw VehiclePhotoFailure.PhotoSourceUnavailable()
            val mutation = block(ownerUid, vehicle, photo, now())
            result = mutation.first
            operations = mutation.second
        }
        requireSameOwner(ownerUid)
        operations.forEach(::schedule)
        return result
    }

    private fun operation(
        photo: DriveVehiclePhotoEntity,
        type: VehiclePhotoOperationType,
        operationId: String
    ): DrivePhotoOperationEntity = DrivePhotoOperationEntity(
        ownerUid = photo.ownerUid,
        operationId = operationId,
        photoId = photo.photoId,
        vehicleId = photo.vehicleId,
        type = type.name,
        targetRevision = photo.revision,
        targetPrimaryPhotoId = null,
        expectedContentHash = photo.contentHash,
        state = VehiclePhotoOperationState.PENDING.name,
        createdAt = photo.clientUpdatedAt,
        updatedAt = photo.clientUpdatedAt,
        attemptCount = 0,
        nextAttemptAt = 0L,
        claimedAt = null,
        lastErrorCode = null
    )

    private fun primaryOperation(
        ownerUid: String,
        vehicleId: String,
        operationId: String,
        operationPhotoId: String,
        targetPhotoId: String?,
        revision: Long,
        timestamp: Long,
        contentHash: String?
    ) = DrivePhotoOperationEntity(
        ownerUid = ownerUid,
        operationId = operationId,
        photoId = operationPhotoId,
        vehicleId = vehicleId,
        type = VehiclePhotoOperationType.SET_PRIMARY.name,
        targetRevision = revision,
        targetPrimaryPhotoId = targetPhotoId,
        expectedContentHash = contentHash,
        state = VehiclePhotoOperationState.PENDING.name,
        createdAt = timestamp,
        updatedAt = timestamp,
        attemptCount = 0,
        nextAttemptAt = 0L,
        claimedAt = null,
        lastErrorCode = null
    )

    private fun schedule(operation: DrivePhotoOperationEntity) {
        scheduler.enqueue(
            operation.ownerUid,
            operation.operationId,
            operation.photoId,
            operation.vehicleId
        )
    }

    private fun requireOwner(): String {
        if (!enabled) throw VehiclePhotoFailure.PhotoUploadFatal()
        return currentUserId()?.takeIf(String::isNotBlank)
            ?: throw VehiclePhotoFailure.AuthenticationChanged()
    }

    private fun requireSameOwner(ownerUid: String) {
        if (currentUserId() != ownerUid) throw VehiclePhotoFailure.AuthenticationChanged()
    }

    private suspend fun requireActiveVehicle(
        ownerUid: String,
        vehicleId: String
    ): com.example.toplutasima.data.local.entity.DriveVehicleEntity =
        vehicleDao.getVehicle(ownerUid, vehicleId)?.let { vehicle ->
            if (vehicle.deletedAt != null) throw VehiclePhotoFailure.VehicleDeleted()
            vehicle
        } ?: throw VehiclePhotoFailure.VehicleNotFound()

    private companion object {
        const val MAX_PENDING_SCHEDULE = 500
    }
}
