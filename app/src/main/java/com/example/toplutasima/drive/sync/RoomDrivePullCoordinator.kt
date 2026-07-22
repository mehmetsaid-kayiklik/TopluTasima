package com.example.toplutasima.drive.sync

import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.dao.DriveFieldProvenanceDao
import com.example.toplutasima.data.local.dao.DriveSyncMetadataDao
import com.example.toplutasima.data.local.dao.DriveSyncOperationDao
import com.example.toplutasima.data.local.dao.DriveTripDao
import com.example.toplutasima.data.local.dao.DriveVehicleDao
import com.example.toplutasima.data.local.entity.DriveSyncMetadataEntity
import com.example.toplutasima.drive.data.remote.DriveRemoteDataSource
import com.example.toplutasima.drive.model.DriveFieldSource
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.provenance.provenanceEntities
import java.util.concurrent.atomic.AtomicLong
import java.util.UUID
import kotlinx.coroutines.CancellationException

internal class RoomDrivePullCoordinator(
    private val database: AppDatabase,
    private val vehicleDao: DriveVehicleDao,
    private val tripDao: DriveTripDao,
    private val operationDao: DriveSyncOperationDao,
    private val metadataDao: DriveSyncMetadataDao,
    private val provenanceDao: DriveFieldProvenanceDao,
    private val remoteDataSource: DriveRemoteDataSource,
    private val receiptStore: DriveSyncReceiptStore,
    private val currentUserId: () -> String?,
    private val now: () -> Long
) : DrivePullCoordinator {
    private val receiptSequence = AtomicLong(0L)

    override suspend fun pull(ownerUid: String): DrivePullRunResult {
        require(ownerUid.isNotBlank()) { "Drive pull owner must not be blank" }
        assertAuthenticatedOwner(ownerUid)
        val metadata = metadataDao.get(ownerUid)
        val mode = if (metadata?.initialHydrationCompleted == true) {
            DrivePullMode.INCREMENTAL
        } else {
            DrivePullMode.INITIAL
        }
        val startedAt = now()
        val receiptId = "pull-${mode.name.lowercase()}-$startedAt-${receiptSequence.incrementAndGet()}"
        receiptStore.startPull(ownerUid, receiptId, mode, startedAt)
        return try {
            val batch = when (mode) {
                DrivePullMode.INITIAL -> remoteDataSource.fetchInitial(ownerUid)
                DrivePullMode.INCREMENTAL -> remoteDataSource.fetchIncremental(
                    ownerUid = ownerUid,
                    vehicleCursor = metadata.vehicleCursorOrNull(),
                    tripCursor = metadata.tripCursorOrNull()
                )
            }
            assertAuthenticatedOwner(ownerUid)
            val pulledCount = applyAtomically(ownerUid, metadata, mode, batch)
            receiptStore.succeed(ownerUid, receiptId, now())
            DrivePullRunResult(pulledCount = pulledCount)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = classifyDriveSyncFailure(error)
            if (failure.retryable) {
                receiptStore.retry(ownerUid, receiptId, now(), failure.code.name)
                DrivePullRunResult(pulledCount = 0, retryRequired = true)
            } else {
                receiptStore.fatal(ownerUid, receiptId, now(), failure.code.name)
                DrivePullRunResult(pulledCount = 0, permanentFailureCount = 1)
            }
        }
    }

    private suspend fun applyAtomically(
        ownerUid: String,
        currentMetadata: DriveSyncMetadataEntity?,
        mode: DrivePullMode,
        batch: DriveRemotePullBatch
    ): Int {
        var appliedCount = 0
        database.withTransaction {
            assertAuthenticatedOwner(ownerUid)
            batch.vehicles.forEach { remoteRecord ->
                val remote = remoteRecord.entity
                require(remote.userId == ownerUid) { "Drive owner mismatch" }
                val local = vehicleDao.getVehicle(ownerUid, remote.id)
                val operation = operationDao.getOperation(
                    ownerUid,
                    DriveSyncEntityType.VEHICLE.name,
                    remote.id
                )
                val localProvenance = provenanceDao.getForRecord(
                    ownerUid,
                    DriveSyncEntityType.VEHICLE.name,
                    remote.id
                ).associate { it.fieldName to DriveFieldSource.fromStorage(it.source) }
                val resolution = DriveConflictResolver.resolveVehicle(
                    local,
                    remote,
                    operation,
                    localProvenance
                )
                if (remote.deletedAt != null) {
                    val activeTripIds = tripDao.getActiveTripIdsForVehicle(ownerUid, remote.id)
                    tripDao.markDeletedForVehicle(
                        ownerUid,
                        remote.id,
                        remote.deletedAt,
                        maxOf(remote.updatedAt, remote.deletedAt),
                        DriveSyncState.SYNCED.name
                    )
                    activeTripIds.forEach { tripId ->
                        operationDao.deleteForRecord(
                            ownerUid,
                            DriveSyncEntityType.TRIP.name,
                            tripId
                        )
                    }
                }
                val resolvedVehicle = if (
                    remote.deletedAt == null &&
                    resolution.entity.deletedAt != null &&
                    operation == null
                ) {
                    operationDao.upsert(
                        DriveSyncPlanner.plan(
                            existing = null,
                            requestedType = DriveSyncOperationType.DELETE_VEHICLE,
                            userId = ownerUid,
                            recordId = remote.id,
                            now = now(),
                            operationId = UUID.randomUUID().toString()
                        )
                    )
                    resolution.entity.copy(syncState = DriveSyncState.LOCAL_PENDING.name)
                } else {
                    resolution.entity
                }
                vehicleDao.upsert(resolvedVehicle)
                provenanceDao.upsertAll(
                    provenanceEntities(
                        ownerUid,
                        DriveSyncEntityType.VEHICLE.name,
                        remote.id,
                        resolution.provenance.keys,
                        DriveFieldSource.UNKNOWN,
                        resolvedVehicle.updatedAt
                    ).map { entity ->
                        entity.copy(source = resolution.provenance.getValue(entity.fieldName).name)
                    }
                )
                if (resolution.clearPendingOperation) {
                    operationDao.deleteForRecord(
                        ownerUid,
                        DriveSyncEntityType.VEHICLE.name,
                        remote.id
                    )
                }
                appliedCount++
            }

            batch.trips.forEach { remoteRecord ->
                val remoteRecordEntity = remoteRecord.entity
                require(remoteRecordEntity.userId == ownerUid) { "Drive owner mismatch" }
                val parent = vehicleDao.getVehicle(ownerUid, remoteRecordEntity.vehicleId)
                require(parent != null) { "Remote drive trip parent missing" }
                val remote = if (parent.deletedAt != null && remoteRecordEntity.deletedAt == null) {
                    remoteRecordEntity.copy(
                        deletedAt = parent.deletedAt,
                        updatedAt = maxOf(remoteRecordEntity.updatedAt, parent.updatedAt),
                        syncState = DriveSyncState.SYNCED.name
                    )
                } else {
                    remoteRecordEntity
                }
                val local = tripDao.getTrip(ownerUid, remote.id)
                val operation = operationDao.getOperation(
                    ownerUid,
                    DriveSyncEntityType.TRIP.name,
                    remote.id
                )
                val localProvenance = provenanceDao.getForRecord(
                    ownerUid,
                    DriveSyncEntityType.TRIP.name,
                    remote.id
                ).associate { it.fieldName to DriveFieldSource.fromStorage(it.source) }
                val resolution = DriveConflictResolver.resolveTrip(
                    local,
                    remote,
                    operation,
                    localProvenance
                )
                val resolvedTrip = if (
                    remote.deletedAt == null &&
                    resolution.entity.deletedAt != null &&
                    operation == null
                ) {
                    operationDao.upsert(
                        DriveSyncPlanner.plan(
                            existing = null,
                            requestedType = DriveSyncOperationType.DELETE_DRIVE_TRIP,
                            userId = ownerUid,
                            recordId = remote.id,
                            now = now(),
                            operationId = UUID.randomUUID().toString()
                        )
                    )
                    resolution.entity.copy(syncState = DriveSyncState.LOCAL_PENDING.name)
                } else {
                    resolution.entity
                }
                tripDao.upsert(resolvedTrip)
                provenanceDao.upsertAll(
                    provenanceEntities(
                        ownerUid,
                        DriveSyncEntityType.TRIP.name,
                        remote.id,
                        resolution.provenance.keys,
                        DriveFieldSource.UNKNOWN,
                        resolvedTrip.updatedAt
                    ).map { entity ->
                        entity.copy(source = resolution.provenance.getValue(entity.fieldName).name)
                    }
                )
                if (resolution.clearPendingOperation) {
                    operationDao.deleteForRecord(
                        ownerUid,
                        DriveSyncEntityType.TRIP.name,
                        remote.id
                    )
                }
                appliedCount++
            }

            assertAuthenticatedOwner(ownerUid)
            metadataDao.upsert(
                DriveSyncMetadataEntity(
                    userId = ownerUid,
                    initialHydrationCompleted = mode == DrivePullMode.INITIAL ||
                        currentMetadata?.initialHydrationCompleted == true,
                    vehicleCursorSeconds = batch.vehicleCursor?.seconds
                        ?: currentMetadata?.vehicleCursorSeconds,
                    vehicleCursorNanos = batch.vehicleCursor?.nanoseconds
                        ?: currentMetadata?.vehicleCursorNanos,
                    vehicleCursorDocumentId = batch.vehicleCursor?.documentId
                        ?: currentMetadata?.vehicleCursorDocumentId,
                    tripCursorSeconds = batch.tripCursor?.seconds
                        ?: currentMetadata?.tripCursorSeconds,
                    tripCursorNanos = batch.tripCursor?.nanoseconds
                        ?: currentMetadata?.tripCursorNanos,
                    tripCursorDocumentId = batch.tripCursor?.documentId
                        ?: currentMetadata?.tripCursorDocumentId,
                    lastSuccessfulPullAt = now(),
                    updatedAt = now()
                )
            )
        }
        return appliedCount
    }

    private fun DriveSyncMetadataEntity?.vehicleCursorOrNull(): DriveRemoteCursor? =
        cursorOrNull(
            this?.vehicleCursorSeconds,
            this?.vehicleCursorNanos,
            this?.vehicleCursorDocumentId
        )

    private fun DriveSyncMetadataEntity?.tripCursorOrNull(): DriveRemoteCursor? =
        cursorOrNull(
            this?.tripCursorSeconds,
            this?.tripCursorNanos,
            this?.tripCursorDocumentId
        )

    private fun cursorOrNull(seconds: Long?, nanos: Int?, documentId: String?): DriveRemoteCursor? =
        if (seconds != null && nanos != null && !documentId.isNullOrBlank()) {
            DriveRemoteCursor(seconds, nanos, documentId)
        } else {
            null
        }

    private fun assertAuthenticatedOwner(ownerUid: String) {
        if (currentUserId() != ownerUid) {
            throw CancellationException("Authenticated user changed during drive pull")
        }
    }
}
