package com.example.toplutasima.drive.assignment

import androidx.room.withTransaction
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveAssignmentOperationEntity
import com.example.toplutasima.data.local.entity.DriveAssignmentSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveAssignmentSyncReceiptEntity
import com.example.toplutasima.drive.sync.classifyDriveSyncFailure
import java.util.UUID
import kotlinx.coroutines.CancellationException
import shared.vehicleassignment.contract.AssignmentServerTimestamp
import shared.vehicleassignment.contract.VersionedVehicleAssignment
import shared.vehicleassignment.contract.VehicleAssignmentConflictResolver

internal data class VehicleAssignmentSyncPhaseResult(
    val processedCount: Int = 0,
    val pulledCount: Int = 0,
    val retryRequired: Boolean = false,
    val permanentFailureCount: Int = 0
) {
    operator fun plus(other: VehicleAssignmentSyncPhaseResult) = VehicleAssignmentSyncPhaseResult(
        processedCount = processedCount + other.processedCount,
        pulledCount = pulledCount + other.pulledCount,
        retryRequired = retryRequired || other.retryRequired,
        permanentFailureCount = permanentFailureCount + other.permanentFailureCount
    )
}

internal interface VehicleAssignmentSyncCoordinator {
    suspend fun pull(ownerUid: String): VehicleAssignmentSyncPhaseResult
    suspend fun pushAndReconcile(ownerUid: String): VehicleAssignmentSyncPhaseResult

    data object NoOp : VehicleAssignmentSyncCoordinator {
        override suspend fun pull(ownerUid: String) = VehicleAssignmentSyncPhaseResult()
        override suspend fun pushAndReconcile(ownerUid: String) = VehicleAssignmentSyncPhaseResult()
    }
}

internal class RoomVehicleAssignmentSyncCoordinator(
    private val database: AppDatabase,
    private val remote: VehicleAssignmentRemoteDataSource =
        FirestoreVehicleAssignmentRemoteDataSource(),
    private val directoryRefresher: VehiclePersonDirectoryRefresher? = null,
    private val currentUserId: () -> String? = CurrentUserProvider::currentUserIdOrNull,
    private val now: () -> Long = System::currentTimeMillis
) : VehicleAssignmentSyncCoordinator {
    private val assignmentDao get() = database.driveVehicleAssignmentDao()
    private val operationDao get() = database.driveAssignmentOperationDao()
    private val metadataDao get() = database.driveAssignmentSyncMetadataDao()
    private val receiptDao get() = database.driveAssignmentSyncReceiptDao()
    private val vehicleDao get() = database.driveVehicleDao()

    override suspend fun pull(ownerUid: String): VehicleAssignmentSyncPhaseResult {
        assertOwner(ownerUid)
        val metadata = metadataDao.get(ownerUid)
        val initial = metadata?.initialHydrationCompleted != true
        val receiptId = "assignment-pull:${UUID.randomUUID()}"
        receiptDao.upsert(
            receipt(
                ownerUid = ownerUid,
                receiptId = receiptId,
                kind = if (initial) "INITIAL_PULL" else "INCREMENTAL_PULL",
                status = "STARTED"
            )
        )
        return try {
            val batch = if (initial) {
                remote.fetchInitial(ownerUid)
            } else {
                remote.fetchIncremental(ownerUid, metadata.toCursor())
            }
            assertOwner(ownerUid)
            var appliedCount = 0
            database.withTransaction {
                batch.assignments.forEach { incoming ->
                    val local = assignmentDao.get(ownerUid, incoming.contract.vehicleId)
                    val pending = operationDao.latestForVehicle(ownerUid, incoming.contract.vehicleId)
                    val acceptIncoming = if (local == null) {
                        true
                    } else {
                        val resolution = VehicleAssignmentConflictResolver.resolve(
                            VersionedVehicleAssignment(local.toDomain().contract, local.serverTimestamp()),
                            VersionedVehicleAssignment(incoming.contract, incoming.serverUpdatedAt),
                            vehicleDeleted = vehicleDao.getVehicle(ownerUid, incoming.contract.vehicleId)
                                ?.deletedAt != null
                        )
                        resolution.winner?.assignment?.operationId == incoming.contract.operationId
                    }
                    if (acceptIncoming) {
                        val conflict = pending?.operationId
                            ?.takeIf { it != incoming.contract.operationId }
                        val entity = incoming.contract.toEntity(
                            ownerUid = ownerUid,
                            serverUpdatedAt = incoming.serverUpdatedAt,
                            syncState = if (conflict == null) {
                                VehicleAssignmentSyncState.SYNCED
                            } else {
                                VehicleAssignmentSyncState.CONFLICT
                            },
                            conflictOperationId = conflict
                        )
                        assignmentDao.upsert(entity)
                        vehicleDao.setAssignmentMirror(
                            ownerUid,
                            incoming.contract.vehicleId,
                            incoming.contract.personId.takeIf { incoming.contract.deletedAt == null }
                        )
                        if (pending?.operationId == incoming.contract.operationId) {
                            operationDao.delete(ownerUid, pending.operationId)
                        }
                        appliedCount++
                    }
                }
                batch.unsupported.forEach { unsupported ->
                    assignmentDao.get(ownerUid, unsupported.vehicleId)?.let { local ->
                        assignmentDao.upsert(
                            local.copy(
                                syncState = VehicleAssignmentSyncState.UNSUPPORTED.name,
                                healthCode = VehicleAssignmentHealthCode.ASSIGNMENT_SCHEMA_UNSUPPORTED.name,
                                lastErrorCode = "UNSUPPORTED_SCHEMA_${unsupported.schemaVersion}"
                            )
                        )
                    }
                    receiptDao.upsert(
                        receipt(
                            ownerUid = ownerUid,
                            receiptId = "unsupported:${unsupported.vehicleId}:${unsupported.schemaVersion}",
                            vehicleId = unsupported.vehicleId,
                            kind = "INBOUND",
                            status = "UNSUPPORTED",
                            errorCode = "ASSIGNMENT_SCHEMA_UNSUPPORTED"
                        )
                    )
                }
                batch.invalid.forEach { invalid ->
                    assignmentDao.get(ownerUid, invalid.vehicleId)?.let { local ->
                        assignmentDao.upsert(
                            local.copy(
                                syncState = VehicleAssignmentSyncState.FATAL.name,
                                lastErrorCode = invalid.issueCodes.sorted().joinToString("|")
                            )
                        )
                    }
                    receiptDao.upsert(
                        receipt(
                            ownerUid = ownerUid,
                            receiptId = "invalid:${invalid.vehicleId}",
                            vehicleId = invalid.vehicleId,
                            kind = "INBOUND",
                            status = "FATAL",
                            errorCode = "INVALID_ASSIGNMENT"
                        )
                    )
                }
                val nextCursor = batch.cursor ?: metadata.toCursor()
                metadataDao.upsert(
                    DriveAssignmentSyncMetadataEntity(
                        ownerUid = ownerUid,
                        initialHydrationCompleted = true,
                        cursorSeconds = nextCursor?.seconds,
                        cursorNanos = nextCursor?.nanoseconds,
                        cursorDocumentId = nextCursor?.documentId,
                        lastSuccessfulPullAt = now(),
                        updatedAt = now()
                    )
                )
                receiptDao.upsert(
                    receipt(
                        ownerUid = ownerUid,
                        receiptId = receiptId,
                        kind = if (initial) "INITIAL_PULL" else "INCREMENTAL_PULL",
                        status = "SUCCEEDED",
                        finishedAt = now()
                    )
                )
            }
            assertOwner(ownerUid)
            VehicleAssignmentSyncPhaseResult(
                pulledCount = appliedCount,
                permanentFailureCount = batch.invalid.size + batch.unsupported.size
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = classifyDriveSyncFailure(error)
            receiptDao.upsert(
                receipt(
                    ownerUid = ownerUid,
                    receiptId = receiptId,
                    kind = if (initial) "INITIAL_PULL" else "INCREMENTAL_PULL",
                    status = if (failure.retryable) "RETRY" else "FATAL",
                    finishedAt = now(),
                    errorCode = failure.code.name
                )
            )
            VehicleAssignmentSyncPhaseResult(
                retryRequired = failure.retryable,
                permanentFailureCount = if (failure.retryable) 0 else 1
            )
        }
    }

    override suspend fun pushAndReconcile(ownerUid: String): VehicleAssignmentSyncPhaseResult {
        assertOwner(ownerUid)
        var result = reconcileMirrors(ownerUid)
        var processed = 0
        var retry = result.retryRequired
        var permanent = result.permanentFailureCount
        while (true) {
            assertOwner(ownerUid)
            val operations = operationDao.pending(ownerUid, now(), OUTBOUND_BATCH_SIZE)
            if (operations.isEmpty()) break
            for (operation in operations) {
                val operationResult = pushOne(ownerUid, operation)
                processed += operationResult.processedCount
                retry = retry || operationResult.retryRequired
                permanent += operationResult.permanentFailureCount
            }
            if (operations.size < OUTBOUND_BATCH_SIZE) break
        }

        if (!retry) {
            try {
                directoryRefresher?.refresh()
                assertOwner(ownerUid)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = classifyDriveSyncFailure(error)
                receiptDao.upsert(
                    receipt(
                        ownerUid = ownerUid,
                        receiptId = "person-health:${now()}",
                        kind = "HEALTH",
                        status = if (failure.retryable) "RETRY" else "FATAL",
                        finishedAt = now(),
                        errorCode = failure.code.name
                    )
                )
                retry = retry || failure.retryable
                if (!failure.retryable) permanent++
            }
        }
        result = VehicleAssignmentSyncPhaseResult(
            processedCount = processed,
            retryRequired = retry,
            permanentFailureCount = permanent
        )
        return result
    }

    private suspend fun pushOne(
        ownerUid: String,
        operation: DriveAssignmentOperationEntity
    ): VehicleAssignmentSyncPhaseResult {
        val started = now()
        receiptDao.upsert(
            receipt(
                ownerUid = ownerUid,
                receiptId = operation.operationId,
                operationId = operation.operationId,
                vehicleId = operation.vehicleId,
                kind = "OUTBOUND",
                status = "STARTED",
                source = operation.source,
                revision = operation.targetRevision,
                startedAt = started,
                attemptCount = operation.attemptCount
            )
        )
        return try {
            val remoteResult = remote.apply(ownerUid, operation)
            assertOwner(ownerUid)
            database.withTransaction {
                when (remoteResult) {
                    is VehicleAssignmentRemoteApplyResult.Applied -> {
                        completeSuccess(ownerUid, operation, remoteResult.assignment)
                        receiptDao.upsert(
                            receipt(
                                ownerUid = ownerUid,
                                receiptId = operation.operationId,
                                operationId = operation.operationId,
                                vehicleId = operation.vehicleId,
                                kind = "OUTBOUND",
                                status = if (remoteResult.mirrorPending) {
                                    "SUCCEEDED_MIRROR_PENDING"
                                } else {
                                    "SUCCEEDED"
                                },
                                source = operation.source,
                                revision = operation.targetRevision,
                                winningOperationId = operation.operationId,
                                startedAt = started,
                                finishedAt = now(),
                                attemptCount = operation.attemptCount + 1,
                                errorCode = remoteResult.conflictOperationId?.let { "CONFLICT_WON" }
                            )
                        )
                    }
                    is VehicleAssignmentRemoteApplyResult.AlreadyApplied -> {
                        completeSuccess(ownerUid, operation, remoteResult.assignment)
                        receiptDao.upsert(
                            receipt(
                                ownerUid = ownerUid,
                                receiptId = operation.operationId,
                                operationId = operation.operationId,
                                vehicleId = operation.vehicleId,
                                kind = "OUTBOUND",
                                status = "IDEMPOTENT_SUCCESS",
                                source = operation.source,
                                revision = operation.targetRevision,
                                winningOperationId = operation.operationId,
                                startedAt = started,
                                finishedAt = now(),
                                attemptCount = operation.attemptCount + 1
                            )
                        )
                    }
                    is VehicleAssignmentRemoteApplyResult.RemoteWon -> {
                        assignmentDao.upsert(
                            remoteResult.assignment.contract.toEntity(
                                ownerUid = ownerUid,
                                serverUpdatedAt = remoteResult.assignment.serverUpdatedAt,
                                syncState = VehicleAssignmentSyncState.CONFLICT,
                                conflictOperationId = operation.operationId,
                                lastErrorCode = "ASSIGNMENT_CONFLICT"
                            )
                        )
                        vehicleDao.setAssignmentMirror(
                            ownerUid,
                            operation.vehicleId,
                            remoteResult.assignment.contract.personId.takeIf {
                                remoteResult.assignment.contract.deletedAt == null
                            }
                        )
                        operationDao.recordAttempt(
                            ownerUid,
                            operation.operationId,
                            "FATAL",
                            null,
                            "ASSIGNMENT_CONFLICT",
                            now()
                        )
                        finishFatalReceipt(
                            ownerUid,
                            operation,
                            started,
                            "CONFLICT",
                            remoteResult.assignment.contract.operationId
                        )
                    }
                    VehicleAssignmentRemoteApplyResult.VehicleNotFound,
                    VehicleAssignmentRemoteApplyResult.VehicleDeleted -> {
                        markFatal(
                            ownerUid,
                            operation,
                            VehicleAssignmentHealthCode.ASSIGNMENT_VEHICLE_NOT_FOUND,
                            if (remoteResult == VehicleAssignmentRemoteApplyResult.VehicleDeleted) {
                                "VEHICLE_DELETED"
                            } else {
                                "VEHICLE_NOT_FOUND"
                            },
                            started
                        )
                    }
                    VehicleAssignmentRemoteApplyResult.UnsupportedSchema -> markFatal(
                        ownerUid,
                        operation,
                        VehicleAssignmentHealthCode.ASSIGNMENT_SCHEMA_UNSUPPORTED,
                        "ASSIGNMENT_SCHEMA_UNSUPPORTED",
                        started
                    )
                    VehicleAssignmentRemoteApplyResult.InvalidRemoteData -> markFatal(
                        ownerUid,
                        operation,
                        null,
                        "INVALID_REMOTE_DATA",
                        started
                    )
                }
            }
            when (remoteResult) {
                is VehicleAssignmentRemoteApplyResult.Applied,
                is VehicleAssignmentRemoteApplyResult.AlreadyApplied ->
                    VehicleAssignmentSyncPhaseResult(processedCount = 1)
                else -> VehicleAssignmentSyncPhaseResult(permanentFailureCount = 1)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val failure = classifyDriveSyncFailure(error)
            val nextAttempt = if (failure.retryable) now() + retryDelay(operation.attemptCount) else null
            operationDao.recordAttempt(
                ownerUid,
                operation.operationId,
                if (failure.retryable) "RETRY" else "FATAL",
                nextAttempt,
                failure.code.name,
                now()
            )
            assignmentDao.get(ownerUid, operation.vehicleId)?.let { current ->
                assignmentDao.upsert(
                    current.copy(
                        syncState = if (failure.retryable) {
                            VehicleAssignmentSyncState.RETRY.name
                        } else {
                            VehicleAssignmentSyncState.FATAL.name
                        },
                        lastErrorCode = failure.code.name
                    )
                )
            }
            receiptDao.upsert(
                receipt(
                    ownerUid = ownerUid,
                    receiptId = operation.operationId,
                    operationId = operation.operationId,
                    vehicleId = operation.vehicleId,
                    kind = "OUTBOUND",
                    status = if (failure.retryable) "RETRY" else "FATAL",
                    source = operation.source,
                    revision = operation.targetRevision,
                    startedAt = started,
                    finishedAt = now(),
                    attemptCount = operation.attemptCount + 1,
                    errorCode = failure.code.name
                )
            )
            VehicleAssignmentSyncPhaseResult(
                retryRequired = failure.retryable,
                permanentFailureCount = if (failure.retryable) 0 else 1
            )
        }
    }

    private suspend fun completeSuccess(
        ownerUid: String,
        operation: DriveAssignmentOperationEntity,
        remoteAssignment: RemoteVehicleAssignment
    ) {
        val current = assignmentDao.get(ownerUid, operation.vehicleId)
        if (current?.operationId == operation.operationId) {
            assignmentDao.upsert(
                remoteAssignment.contract.toEntity(
                    ownerUid = ownerUid,
                    serverUpdatedAt = remoteAssignment.serverUpdatedAt,
                    syncState = VehicleAssignmentSyncState.SYNCED
                )
            )
            vehicleDao.setAssignmentMirror(
                ownerUid,
                operation.vehicleId,
                remoteAssignment.contract.personId.takeIf {
                    remoteAssignment.contract.deletedAt == null
                }
            )
        }
        operationDao.delete(ownerUid, operation.operationId)
    }

    private suspend fun markFatal(
        ownerUid: String,
        operation: DriveAssignmentOperationEntity,
        healthCode: VehicleAssignmentHealthCode?,
        errorCode: String,
        startedAt: Long
    ) {
        assignmentDao.get(ownerUid, operation.vehicleId)?.let { current ->
            assignmentDao.upsert(
                current.copy(
                    syncState = VehicleAssignmentSyncState.FATAL.name,
                    healthCode = healthCode?.name ?: current.healthCode,
                    lastErrorCode = errorCode
                )
            )
        }
        operationDao.recordAttempt(
            ownerUid,
            operation.operationId,
            "FATAL",
            null,
            errorCode,
            now()
        )
        finishFatalReceipt(ownerUid, operation, startedAt, errorCode, null)
    }

    private suspend fun finishFatalReceipt(
        ownerUid: String,
        operation: DriveAssignmentOperationEntity,
        startedAt: Long,
        errorCode: String,
        winningOperationId: String?
    ) {
        receiptDao.upsert(
            receipt(
                ownerUid = ownerUid,
                receiptId = operation.operationId,
                operationId = operation.operationId,
                vehicleId = operation.vehicleId,
                kind = "OUTBOUND",
                status = if (errorCode == "CONFLICT") "CONFLICT" else "FATAL",
                source = operation.source,
                revision = operation.targetRevision,
                winningOperationId = winningOperationId,
                startedAt = startedAt,
                finishedAt = now(),
                attemptCount = operation.attemptCount + 1,
                errorCode = errorCode
            )
        )
    }

    private suspend fun reconcileMirrors(ownerUid: String): VehicleAssignmentSyncPhaseResult {
        val assignments = assignmentDao.getAll(ownerUid).associateBy { it.vehicleId }
        var retry = false
        for (vehicle in vehicleDao.getActiveVehiclesSnapshot(ownerUid)) {
            val canonical = assignments[vehicle.id]
            val activePersonId = canonical?.personId.takeIf { canonical?.deletedAt == null }
            if (vehicle.assignedPersonId == activePersonId) continue
            vehicleDao.setAssignmentMirror(ownerUid, vehicle.id, activePersonId)
            try {
                remote.reconcileVehicleMirror(ownerUid, vehicle.id, activePersonId)
                assertOwner(ownerUid)
                receiptDao.upsert(
                    receipt(
                        ownerUid = ownerUid,
                        receiptId = "mirror:${vehicle.id}:${canonical?.revision ?: 0L}",
                        vehicleId = vehicle.id,
                        kind = "MIRROR",
                        status = "SUCCEEDED",
                        revision = canonical?.revision,
                        finishedAt = now()
                    )
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val failure = classifyDriveSyncFailure(error)
                retry = retry || failure.retryable
                receiptDao.upsert(
                    receipt(
                        ownerUid = ownerUid,
                        receiptId = "mirror:${vehicle.id}:${canonical?.revision ?: 0L}",
                        vehicleId = vehicle.id,
                        kind = "MIRROR",
                        status = if (failure.retryable) "RETRY" else "FATAL",
                        revision = canonical?.revision,
                        finishedAt = now(),
                        errorCode = failure.code.name
                    )
                )
            }
        }
        return VehicleAssignmentSyncPhaseResult(retryRequired = retry)
    }

    private fun DriveAssignmentSyncMetadataEntity?.toCursor(): AssignmentRemoteCursor? {
        val value = this ?: return null
        val seconds = value.cursorSeconds ?: return null
        val nanos = value.cursorNanos ?: return null
        val documentId = value.cursorDocumentId ?: return null
        return AssignmentRemoteCursor(seconds, nanos, documentId)
    }

    private fun com.example.toplutasima.data.local.entity.DriveVehicleAssignmentEntity.serverTimestamp():
        AssignmentServerTimestamp? =
        if (serverUpdatedAtSeconds != null && serverUpdatedAtNanos != null) {
            AssignmentServerTimestamp(serverUpdatedAtSeconds, serverUpdatedAtNanos)
        } else {
            null
        }

    private fun receipt(
        ownerUid: String,
        receiptId: String,
        operationId: String? = null,
        vehicleId: String? = null,
        kind: String,
        status: String,
        source: String? = null,
        revision: Long? = null,
        winningOperationId: String? = null,
        startedAt: Long = now(),
        finishedAt: Long? = null,
        attemptCount: Int = 0,
        errorCode: String? = null
    ) = DriveAssignmentSyncReceiptEntity(
        ownerUid = ownerUid,
        receiptId = receiptId,
        operationId = operationId,
        vehicleId = vehicleId,
        kind = kind,
        status = status,
        source = source,
        revision = revision,
        winningOperationId = winningOperationId,
        startedAt = startedAt,
        finishedAt = finishedAt,
        attemptCount = attemptCount,
        errorCode = errorCode
    )

    private fun assertOwner(expectedOwnerUid: String) {
        if (expectedOwnerUid.isBlank() || currentUserId() != expectedOwnerUid) {
            throw CancellationException("Authenticated owner changed during assignment sync")
        }
    }

    private fun retryDelay(attemptCount: Int): Long {
        val exponent = attemptCount.coerceIn(0, 8)
        return (30_000L shl exponent).coerceAtMost(6 * 60 * 60 * 1_000L)
    }

    private companion object {
        const val OUTBOUND_BATCH_SIZE = 50
    }
}
