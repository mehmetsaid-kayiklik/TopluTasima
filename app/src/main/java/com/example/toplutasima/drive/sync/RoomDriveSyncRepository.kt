package com.example.toplutasima.drive.sync

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.dao.DriveSyncOperationDao
import com.example.toplutasima.data.local.dao.DriveTripDao
import com.example.toplutasima.data.local.dao.DriveVehicleDao
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.data.remote.DriveRemoteDataSource
import com.example.toplutasima.drive.data.remote.FirestoreDriveRemoteDataSource
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.repository.DriveSyncRepository
import com.example.toplutasima.drive.repository.DriveSyncRunResult
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.assignment.RoomVehicleAssignmentSyncCoordinator
import com.example.toplutasima.drive.assignment.VehicleAssignmentSyncCoordinator
import com.example.toplutasima.drive.photo.VehiclePhotoPullCoordinator
import com.example.toplutasima.drive.ledger.RoomVehicleLedgerSyncCoordinator
import com.example.toplutasima.drive.ledger.VehicleLedgerSyncCoordinator
import kotlinx.coroutines.CancellationException

internal fun interface DriveSyncTransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}

private class RoomDriveSyncTransactionRunner(
    private val database: RoomDatabase
) : DriveSyncTransactionRunner {
    override suspend fun run(block: suspend () -> Unit) {
        database.withTransaction { block() }
    }
}

class RoomDriveSyncRepository internal constructor(
    private val vehicleDao: DriveVehicleDao,
    private val tripDao: DriveTripDao,
    private val operationDao: DriveSyncOperationDao,
    private val remoteDataSource: DriveRemoteDataSource,
    private val transactionRunner: DriveSyncTransactionRunner,
    private val currentUserId: () -> String?,
    private val now: () -> Long,
    private val pullCoordinator: DrivePullCoordinator = DrivePullCoordinator.NO_OP,
    private val receiptStore: DriveSyncReceiptStore = DriveSyncReceiptStore.NO_OP,
    private val assignmentSyncCoordinator: VehicleAssignmentSyncCoordinator =
        VehicleAssignmentSyncCoordinator.NoOp,
    private val photoPullCoordinator: VehiclePhotoPullCoordinator = VehiclePhotoPullCoordinator.NoOp,
    private val ledgerSyncCoordinator: VehicleLedgerSyncCoordinator = VehicleLedgerSyncCoordinator.NoOp
) : DriveSyncRepository {
    internal constructor(
        database: AppDatabase,
        remoteDataSource: DriveRemoteDataSource = FirestoreDriveRemoteDataSource(),
        assignmentSyncCoordinator: VehicleAssignmentSyncCoordinator =
            if (DriveFeatureFlags.DRIVE_PERSON_DIRECTORY) {
                RoomVehicleAssignmentSyncCoordinator(database)
            } else {
                VehicleAssignmentSyncCoordinator.NoOp
            },
        photoPullCoordinator: VehiclePhotoPullCoordinator = VehiclePhotoPullCoordinator.NoOp,
        ledgerSyncCoordinator: VehicleLedgerSyncCoordinator =
            if (DriveFeatureFlags.DRIVE_VEHICLE_LEDGER) {
                RoomVehicleLedgerSyncCoordinator(
                    database = database,
                    currentUserId = CurrentUserProvider::currentUserIdOrNull
                )
            } else {
                VehicleLedgerSyncCoordinator.NoOp
            }
    ) : this(
        vehicleDao = database.driveVehicleDao(),
        tripDao = database.driveTripDao(),
        operationDao = database.driveSyncOperationDao(),
        remoteDataSource = remoteDataSource,
        transactionRunner = RoomDriveSyncTransactionRunner(database),
        currentUserId = CurrentUserProvider::currentUserIdOrNull,
        now = System::currentTimeMillis,
        pullCoordinator = RoomDrivePullCoordinator(
            database = database,
            vehicleDao = database.driveVehicleDao(),
            tripDao = database.driveTripDao(),
            operationDao = database.driveSyncOperationDao(),
            metadataDao = database.driveSyncMetadataDao(),
            provenanceDao = database.driveFieldProvenanceDao(),
            remoteDataSource = remoteDataSource,
            receiptStore = RoomDriveSyncReceiptStore(database.driveSyncReceiptDao()),
            currentUserId = CurrentUserProvider::currentUserIdOrNull,
            now = System::currentTimeMillis
        ),
        receiptStore = RoomDriveSyncReceiptStore(database.driveSyncReceiptDao()),
        assignmentSyncCoordinator = assignmentSyncCoordinator,
        photoPullCoordinator = photoPullCoordinator,
        ledgerSyncCoordinator = ledgerSyncCoordinator
    )

    override suspend fun synchronize(ownerUid: String): DriveSyncRunResult {
        require(ownerUid.isNotBlank()) { "Drive sync owner must not be blank" }
        assertAuthenticatedOwner(ownerUid)

        val assignmentPull = assignmentSyncCoordinator.pull(ownerUid)
        if (assignmentPull.retryRequired) {
            return DriveSyncRunResult(
                processedCount = assignmentPull.processedCount,
                retryRequired = true,
                permanentFailureCount = assignmentPull.permanentFailureCount,
                pulledCount = assignmentPull.pulledCount
            )
        }

        val pullResult = pullCoordinator.pull(ownerUid)
        if (pullResult.retryRequired || pullResult.permanentFailureCount > 0) {
            return DriveSyncRunResult(
                processedCount = assignmentPull.processedCount,
                retryRequired = pullResult.retryRequired,
                permanentFailureCount =
                    assignmentPull.permanentFailureCount + pullResult.permanentFailureCount,
                pulledCount = assignmentPull.pulledCount + pullResult.pulledCount
            )
        }

        val photoPull = photoPullCoordinator.pull(ownerUid)
        if (photoPull.retryRequired || photoPull.permanentFailureCount > 0) {
            return DriveSyncRunResult(
                processedCount = assignmentPull.processedCount,
                retryRequired = photoPull.retryRequired,
                permanentFailureCount = assignmentPull.permanentFailureCount +
                    pullResult.permanentFailureCount + photoPull.permanentFailureCount,
                pulledCount = assignmentPull.pulledCount + pullResult.pulledCount + photoPull.pulledCount
            )
        }

        val assignmentPush = assignmentSyncCoordinator.pushAndReconcile(ownerUid)
        val ledgerSync = ledgerSyncCoordinator.synchronize(ownerUid)

        var processedCount = assignmentPull.processedCount + assignmentPush.processedCount +
            ledgerSync.processedCount
        var retryRequired = assignmentPush.retryRequired || ledgerSync.retryRequired
        var permanentFailureCount =
            assignmentPull.permanentFailureCount + assignmentPush.permanentFailureCount +
                ledgerSync.permanentFailureCount

        while (true) {
            assertAuthenticatedOwner(ownerUid)
            val operations = operationDao.getRetryEligibleOperations(
                userId = ownerUid,
                now = now(),
                limit = BATCH_SIZE
            )
            if (operations.isEmpty()) break

            var madeProgress = false
            for (operation in operations) {
                assertAuthenticatedOwner(ownerUid)
                receiptStore.startOutbound(operation, now())
                when (val outcome = processOperation(ownerUid, operation)) {
                    DriveOperationOutcome.PROCESSED -> {
                        processedCount++
                        madeProgress = true
                        receiptStore.succeed(ownerUid, operation.operationId, now())
                    }
                    DriveOperationOutcome.RETRY_LATER -> {
                        retryRequired = true
                        madeProgress = true
                        receiptStore.retry(
                            ownerUid,
                            operation.operationId,
                            now(),
                            currentFailureCode(operation)
                        )
                    }
                    DriveOperationOutcome.PERMANENT_FAILURE -> {
                        permanentFailureCount++
                        madeProgress = true
                        receiptStore.fatal(
                            ownerUid,
                            operation.operationId,
                            now(),
                            currentFailureCode(operation)
                        )
                    }
                    DriveOperationOutcome.SUPERSEDED -> {
                        madeProgress = true
                        receiptStore.superseded(ownerUid, operation.operationId, now())
                    }
                }
            }
            if (!madeProgress || operations.size < BATCH_SIZE) break
        }

        return DriveSyncRunResult(
            processedCount = processedCount,
            retryRequired = retryRequired,
            permanentFailureCount = permanentFailureCount,
            pulledCount = assignmentPull.pulledCount + pullResult.pulledCount + photoPull.pulledCount +
                ledgerSync.pulledCount
        )
    }

    private suspend fun currentFailureCode(operation: DriveSyncOperationEntity): String =
        operationDao.getOperation(
            operation.userId,
            operation.entityType,
            operation.recordId
        )?.lastErrorCode ?: DriveSyncFailureCode.UNKNOWN.name

    private suspend fun processOperation(
        ownerUid: String,
        operation: DriveSyncOperationEntity
    ): DriveOperationOutcome {
        if (operation.userId != ownerUid) {
            return recordPermanentFailure(operation, DriveSyncFailureCode.CORRUPT_OPERATION)
        }
        val operationType = DriveSyncOperationType.fromStorage(operation.operationType)
            ?: return recordPermanentFailure(operation, DriveSyncFailureCode.CORRUPT_OPERATION)
        if (operation.entityType != operationType.entityType.name) {
            return recordPermanentFailure(operation, DriveSyncFailureCode.CORRUPT_OPERATION)
        }

        return try {
            when (operationType.entityType) {
                DriveSyncEntityType.VEHICLE -> processVehicle(ownerUid, operation, operationType)
                DriveSyncEntityType.TRIP -> processTrip(ownerUid, operation, operationType)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            val failure = classifyDriveSyncFailure(error)
            if (failure.retryable) {
                recordRetryableFailure(operation, failure.code)
            } else {
                recordPermanentFailure(operation, failure.code)
            }
        }
    }

    private suspend fun processVehicle(
        ownerUid: String,
        operation: DriveSyncOperationEntity,
        operationType: DriveSyncOperationType
    ): DriveOperationOutcome {
        val vehicle = vehicleDao.getVehicle(ownerUid, operation.recordId)
            ?: return recordPermanentFailure(operation, DriveSyncFailureCode.RECORD_MISSING)
        if (vehicle.userId != ownerUid) {
            return recordPermanentFailure(operation, DriveSyncFailureCode.CORRUPT_OPERATION)
        }
        if (operationType == DriveSyncOperationType.DELETE_VEHICLE) {
            val dependencyState = vehicleDeleteDependencyState(ownerUid, vehicle.id)
            if (dependencyState != null) {
                return if (dependencyState == DriveSyncState.PERMANENT_ERROR) {
                    recordPermanentFailure(operation, DriveSyncFailureCode.DEPENDENCY_PENDING)
                } else {
                    recordRetryableFailure(operation, DriveSyncFailureCode.DEPENDENCY_PENDING)
                }
            }
        }
        markVehicleSyncState(vehicle, DriveSyncState.SYNCING)
        val remoteResult = when (operationType) {
            DriveSyncOperationType.CREATE_VEHICLE,
            DriveSyncOperationType.UPDATE_VEHICLE -> remoteDataSource.upsertVehicle(
                ownerUid = ownerUid,
                vehicle = vehicle,
                operationId = operation.operationId
            )
            DriveSyncOperationType.DELETE_VEHICLE -> remoteDataSource.tombstoneVehicle(
                ownerUid = ownerUid,
                vehicle = vehicle,
                operationId = operation.operationId
            )
            else -> return recordPermanentFailure(
                operation,
                DriveSyncFailureCode.CORRUPT_OPERATION
            )
        }
        assertAuthenticatedOwner(ownerUid)
        return completeVehicle(operation, vehicle, remoteResult)
    }

    private suspend fun processTrip(
        ownerUid: String,
        operation: DriveSyncOperationEntity,
        operationType: DriveSyncOperationType
    ): DriveOperationOutcome {
        val trip = tripDao.getTrip(ownerUid, operation.recordId)
            ?: return recordPermanentFailure(operation, DriveSyncFailureCode.RECORD_MISSING)
        if (trip.userId != ownerUid) {
            return recordPermanentFailure(operation, DriveSyncFailureCode.CORRUPT_OPERATION)
        }
        val vehicle = vehicleDao.getVehicle(ownerUid, trip.vehicleId)
            ?: return recordPermanentFailure(operation, DriveSyncFailureCode.RECORD_MISSING)
        if (vehicle.userId != ownerUid) {
            return recordPermanentFailure(operation, DriveSyncFailureCode.CORRUPT_OPERATION)
        }
        val isTripUpsert = operationType == DriveSyncOperationType.CREATE_DRIVE_TRIP ||
            operationType == DriveSyncOperationType.UPDATE_DRIVE_TRIP
        if (isTripUpsert && vehicle.syncState != DriveSyncState.SYNCED.name) {
            return if (vehicle.syncState == DriveSyncState.PERMANENT_ERROR.name) {
                recordPermanentFailure(operation, DriveSyncFailureCode.DEPENDENCY_PENDING)
            } else {
                recordRetryableFailure(operation, DriveSyncFailureCode.DEPENDENCY_PENDING)
            }
        }
        markTripSyncState(trip, DriveSyncState.SYNCING)
        val remoteResult = when (operationType) {
            DriveSyncOperationType.CREATE_DRIVE_TRIP,
            DriveSyncOperationType.UPDATE_DRIVE_TRIP -> {
                if (vehicle.deletedAt != null || trip.deletedAt != null) {
                    return recordPermanentFailure(operation, DriveSyncFailureCode.INVALID_DATA)
                }
                remoteDataSource.upsertTrip(
                    ownerUid = ownerUid,
                    trip = trip,
                    operationId = operation.operationId
                )
            }
            DriveSyncOperationType.DELETE_DRIVE_TRIP -> remoteDataSource.tombstoneTrip(
                ownerUid = ownerUid,
                trip = trip,
                operationId = operation.operationId
            )
            else -> return recordPermanentFailure(
                operation,
                DriveSyncFailureCode.CORRUPT_OPERATION
            )
        }
        assertAuthenticatedOwner(ownerUid)
        return completeTrip(operation, trip, remoteResult)
    }

    private suspend fun completeVehicle(
        operation: DriveSyncOperationEntity,
        vehicle: DriveVehicleEntity,
        remoteResult: DriveRemoteWriteResult
    ): DriveOperationOutcome {
        var completed = false
        transactionRunner.run {
            val current = operationDao.getOperation(
                operation.userId,
                operation.entityType,
                operation.recordId
            )
            if (current?.operationId != operation.operationId) return@run
            when (remoteResult) {
                DriveRemoteWriteResult.Applied,
                DriveRemoteWriteResult.AlreadyApplied -> {
                    vehicleDao.setSyncStateIfUnchanged(
                        userId = operation.userId,
                        id = vehicle.id,
                        expectedUpdatedAt = vehicle.updatedAt,
                        syncState = DriveSyncState.SYNCED.name
                    )
                }
                is DriveRemoteWriteResult.DeletePrecedence -> {
                    vehicleDao.markDeleted(
                        userId = operation.userId,
                        id = vehicle.id,
                        deletedAt = remoteResult.deletedAtEpochMillis,
                        updatedAt = remoteResult.deletedAtEpochMillis,
                        syncState = DriveSyncState.SYNCED.name
                    )
                }
            }
            completed = operationDao.deleteIfCurrent(
                userId = operation.userId,
                entityType = operation.entityType,
                recordId = operation.recordId,
                operationId = operation.operationId
            ) > 0
        }
        return if (completed) DriveOperationOutcome.PROCESSED else DriveOperationOutcome.SUPERSEDED
    }

    private suspend fun completeTrip(
        operation: DriveSyncOperationEntity,
        trip: DriveTripEntity,
        remoteResult: DriveRemoteWriteResult
    ): DriveOperationOutcome {
        var completed = false
        transactionRunner.run {
            val current = operationDao.getOperation(
                operation.userId,
                operation.entityType,
                operation.recordId
            )
            if (current?.operationId != operation.operationId) return@run
            when (remoteResult) {
                DriveRemoteWriteResult.Applied,
                DriveRemoteWriteResult.AlreadyApplied -> {
                    tripDao.setSyncStateIfUnchanged(
                        userId = operation.userId,
                        id = trip.id,
                        expectedUpdatedAt = trip.updatedAt,
                        syncState = DriveSyncState.SYNCED.name
                    )
                }
                is DriveRemoteWriteResult.DeletePrecedence -> {
                    tripDao.markDeleted(
                        userId = operation.userId,
                        id = trip.id,
                        deletedAt = remoteResult.deletedAtEpochMillis,
                        updatedAt = remoteResult.deletedAtEpochMillis,
                        syncState = DriveSyncState.SYNCED.name
                    )
                }
            }
            completed = operationDao.deleteIfCurrent(
                userId = operation.userId,
                entityType = operation.entityType,
                recordId = operation.recordId,
                operationId = operation.operationId
            ) > 0
        }
        return if (completed) DriveOperationOutcome.PROCESSED else DriveOperationOutcome.SUPERSEDED
    }

    private suspend fun vehicleDeleteDependencyState(
        ownerUid: String,
        vehicleId: String
    ): DriveSyncState? {
        val linkedTrips = tripDao.getTripsForVehicle(ownerUid, vehicleId)
        if (linkedTrips.any { it.deletedAt == null }) return DriveSyncState.PERMANENT_ERROR
        if (linkedTrips.any { it.syncState == DriveSyncState.PERMANENT_ERROR.name }) {
            return DriveSyncState.PERMANENT_ERROR
        }
        return if (linkedTrips.any { it.syncState != DriveSyncState.SYNCED.name }) {
            DriveSyncState.LOCAL_PENDING
        } else {
            null
        }
    }

    private suspend fun markVehicleSyncState(
        vehicle: DriveVehicleEntity,
        state: DriveSyncState
    ) {
        vehicleDao.setSyncStateIfUnchanged(
            userId = vehicle.userId,
            id = vehicle.id,
            expectedUpdatedAt = vehicle.updatedAt,
            syncState = state.name
        )
    }

    private suspend fun markTripSyncState(
        trip: DriveTripEntity,
        state: DriveSyncState
    ) {
        tripDao.setSyncStateIfUnchanged(
            userId = trip.userId,
            id = trip.id,
            expectedUpdatedAt = trip.updatedAt,
            syncState = state.name
        )
    }

    private suspend fun recordRetryableFailure(
        operation: DriveSyncOperationEntity,
        code: DriveSyncFailureCode
    ): DriveOperationOutcome {
        val attemptedAt = now()
        val attemptNumber = operation.attemptCount + 1
        var recorded = false
        transactionRunner.run {
            recorded = operationDao.recordAttemptIfCurrent(
                userId = operation.userId,
                entityType = operation.entityType,
                recordId = operation.recordId,
                operationId = operation.operationId,
                updatedAt = attemptedAt,
                lastErrorCode = code.name,
                retryEligible = true,
                nextAttemptAt = attemptedAt + retryDelayMillis(attemptNumber)
            ) > 0
            if (recorded) {
                setTargetSyncState(operation, DriveSyncState.RETRYABLE_ERROR)
            }
        }
        return if (recorded) {
            DriveOperationOutcome.RETRY_LATER
        } else {
            DriveOperationOutcome.SUPERSEDED
        }
    }

    private suspend fun recordPermanentFailure(
        operation: DriveSyncOperationEntity,
        code: DriveSyncFailureCode
    ): DriveOperationOutcome {
        var recorded = false
        transactionRunner.run {
            recorded = operationDao.recordAttemptIfCurrent(
                userId = operation.userId,
                entityType = operation.entityType,
                recordId = operation.recordId,
                operationId = operation.operationId,
                updatedAt = now(),
                lastErrorCode = code.name,
                retryEligible = false,
                nextAttemptAt = null
            ) > 0
            if (recorded) {
                setTargetSyncState(operation, DriveSyncState.PERMANENT_ERROR)
            }
        }
        return if (recorded) {
            DriveOperationOutcome.PERMANENT_FAILURE
        } else {
            DriveOperationOutcome.SUPERSEDED
        }
    }

    private suspend fun setTargetSyncState(
        operation: DriveSyncOperationEntity,
        state: DriveSyncState
    ) {
        when (operation.entityType) {
            DriveSyncEntityType.VEHICLE.name -> {
                val vehicle = vehicleDao.getVehicle(operation.userId, operation.recordId) ?: return
                markVehicleSyncState(vehicle, state)
            }
            DriveSyncEntityType.TRIP.name -> {
                val trip = tripDao.getTrip(operation.userId, operation.recordId) ?: return
                markTripSyncState(trip, state)
            }
        }
    }

    private fun assertAuthenticatedOwner(ownerUid: String) {
        if (currentUserId() != ownerUid) {
            throw CancellationException("Authenticated user changed during drive sync")
        }
    }

    private fun retryDelayMillis(attemptNumber: Int): Long {
        val exponent = (attemptNumber - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        return (INITIAL_RETRY_DELAY_MS shl exponent).coerceAtMost(MAX_RETRY_DELAY_MS)
    }

    private enum class DriveOperationOutcome {
        PROCESSED,
        RETRY_LATER,
        PERMANENT_FAILURE,
        SUPERSEDED
    }

    private companion object {
        const val BATCH_SIZE = 50
        const val INITIAL_RETRY_DELAY_MS = 30_000L
        const val MAX_RETRY_DELAY_MS = 6L * 60L * 60L * 1000L
        const val MAX_BACKOFF_EXPONENT = 10
    }
}
