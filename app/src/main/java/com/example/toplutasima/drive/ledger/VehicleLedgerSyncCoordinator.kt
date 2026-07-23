package com.example.toplutasima.drive.ledger

import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveFieldProvenanceEntity
import com.example.toplutasima.data.local.entity.DriveLedgerConflictEntity
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncReceiptEntity
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import shared.vehicleledger.contract.VehicleLedgerConflictResolver
import shared.vehicleledger.contract.VehicleLedgerEntityType
import shared.vehicleledger.contract.VehicleLedgerWinnerReason

internal data class VehicleLedgerSyncRunResult(
    val processedCount: Int,
    val pulledCount: Int,
    val retryRequired: Boolean,
    val permanentFailureCount: Int
) {
    operator fun plus(other: VehicleLedgerSyncRunResult) = VehicleLedgerSyncRunResult(
        processedCount + other.processedCount,
        pulledCount + other.pulledCount,
        retryRequired || other.retryRequired,
        permanentFailureCount + other.permanentFailureCount
    )

    companion object {
        val EMPTY = VehicleLedgerSyncRunResult(0, 0, false, 0)
    }
}

internal fun interface VehicleLedgerSyncCoordinator {
    suspend fun synchronize(ownerUid: String): VehicleLedgerSyncRunResult

    data object NoOp : VehicleLedgerSyncCoordinator {
        override suspend fun synchronize(ownerUid: String) = VehicleLedgerSyncRunResult.EMPTY
    }
}

internal class RoomVehicleLedgerSyncCoordinator(
    private val database: AppDatabase,
    private val remote: VehicleLedgerRemoteDataSource = FirestoreVehicleLedgerRemoteDataSource(),
    private val currentUserId: () -> String?,
    private val now: () -> Long = System::currentTimeMillis
) : VehicleLedgerSyncCoordinator {

    override suspend fun synchronize(ownerUid: String): VehicleLedgerSyncRunResult {
        require(ownerUid.isNotBlank())
        assertOwner(ownerUid)
        var result = VehicleLedgerSyncRunResult.EMPTY
        for (type in COLLECTION_TYPES) {
            result += try {
                pull(ownerUid, type)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (isRetryable(error)) VehicleLedgerSyncRunResult(0, 0, true, 0)
                else VehicleLedgerSyncRunResult(0, 0, false, 1)
            }
        }
        result += push(ownerUid)
        return result
    }

    private suspend fun pull(
        ownerUid: String,
        type: VehicleLedgerEntityType
    ): VehicleLedgerSyncRunResult {
        assertOwner(ownerUid)
        val metadata = database.driveLedgerSyncMetadataDao().get(ownerUid, type.name)
        val cursor = metadata?.cursorSeconds?.let { seconds ->
            val nanos = metadata.cursorNanos ?: return@let null
            val documentId = metadata.cursorDocumentId ?: return@let null
            VehicleLedgerRemoteCursor(seconds, nanos, documentId)
        }
        val initial = metadata?.initialHydrationCompleted != true
        val batch = if (initial) remote.fetchInitial(ownerUid, type)
        else remote.fetchIncremental(ownerUid, type, cursor)
        assertOwner(ownerUid)
        val timestamp = now()
        database.withTransaction {
            batch.records.forEach { applyInbound(ownerUid, type, it, timestamp) }
            batch.unsupported.forEach {
                writeInboundProblemReceipt(
                    ownerUid, type, it.recordId, it.vehicleId.orEmpty(), it.schemaVersion.toLong(),
                    VehicleLedgerHealthCode.LEDGER_UNSUPPORTED_SCHEMA.name, timestamp
                )
            }
            batch.invalid.forEach {
                writeInboundProblemReceipt(
                    ownerUid, type, it.recordId, it.vehicleId.orEmpty(), null,
                    it.issueCodes.sorted().joinToString(",").take(160), timestamp
                )
            }
            val next = batch.cursor ?: cursor
            database.driveLedgerSyncMetadataDao().upsert(
                DriveLedgerSyncMetadataEntity(
                    ownerUid, type.name, true, next?.seconds, next?.nanoseconds,
                    next?.documentId, timestamp, timestamp
                )
            )
        }
        return VehicleLedgerSyncRunResult(
            processedCount = batch.records.size + batch.unsupported.size + batch.invalid.size,
            pulledCount = batch.records.size,
            retryRequired = false,
            permanentFailureCount = batch.invalid.size
        )
    }

    private suspend fun applyInbound(
        ownerUid: String,
        type: VehicleLedgerEntityType,
        remoteRecord: VehicleLedgerRecord,
        timestamp: Long
    ) {
        val vehicle = database.driveVehicleDao().getVehicle(ownerUid, remoteRecord.envelope.vehicleId)
        val health = when {
            vehicle == null -> VehicleLedgerHealthCode.LEDGER_VEHICLE_NOT_FOUND
            vehicle.deletedAt != null && remoteRecord.envelope.deletedAt == null ->
                VehicleLedgerHealthCode.LEDGER_VEHICLE_DELETED
            else -> null
        }
        val local = getRecord(ownerUid, type, remoteRecord.recordId)
        if (local == null) {
            upsertRecord(remoteRecord, VehicleLedgerSyncState.SYNCED, health)
            writeProvenance(remoteRecord, type)
            writeReceipt(remoteRecord, type, VehicleLedgerReceiptStatus.APPLIED, "REMOTE_PULL", timestamp)
            return
        }
        val resolution = resolve(local, remoteRecord, ownerUid, remoteRecord.envelope.vehicleId,
            vehicle?.deletedAt != null)
        if (resolution.idempotent) {
            upsertRecord(remoteRecord, VehicleLedgerSyncState.SYNCED, health)
            markOperationTerminal(ownerUid, local.envelope.operationId,
                VehicleLedgerOperationState.SUCCEEDED, null, timestamp)
            writeReceipt(remoteRecord, type, VehicleLedgerReceiptStatus.IDEMPOTENT, "REMOTE_PULL", timestamp)
            return
        }
        val conflict = resolution.conflict && local.envelope.operationId != remoteRecord.envelope.operationId
        if (conflict) writeConflict(ownerUid, type, local, remoteRecord, resolution.reason, timestamp)
        if (resolution.winner == remoteRecord) {
            upsertRecord(
                remoteRecord,
                if (conflict) VehicleLedgerSyncState.CONFLICT else VehicleLedgerSyncState.SYNCED,
                health
            )
            if (conflict) {
                database.driveLedgerOperationDao().markConflict(
                    ownerUid, local.envelope.operationId,
                    VehicleLedgerHealthCode.LEDGER_CONFLICT_UNRESOLVED.name, timestamp
                )
                database.driveLedgerOperationDao().get(ownerUid, local.envelope.operationId)?.let {
                    writeReceiptForOperation(
                        it, VehicleLedgerReceiptStatus.CONFLICT, "REMOTE_PULL",
                        VehicleLedgerHealthCode.LEDGER_CONFLICT_UNRESOLVED.name, remoteRecord
                    )
                }
            }
            writeProvenance(remoteRecord, type)
            writeReceipt(remoteRecord, type,
                if (conflict) VehicleLedgerReceiptStatus.CONFLICT else VehicleLedgerReceiptStatus.APPLIED,
                "REMOTE_PULL", timestamp)
        } else {
            writeReceipt(local, type, VehicleLedgerReceiptStatus.CONFLICT, "LOCAL_OUTBOX", timestamp,
                winningOperationId = local.envelope.operationId)
        }
    }

    private suspend fun push(ownerUid: String): VehicleLedgerSyncRunResult {
        var result = VehicleLedgerSyncRunResult.EMPTY
        val workerId = UUID.randomUUID().toString()
        while (true) {
            assertOwner(ownerUid)
            val operations = database.driveLedgerOperationDao().pending(ownerUid, now(), PUSH_BATCH_SIZE)
            if (operations.isEmpty()) break
            var progress = false
            operations.forEach { operation ->
                assertOwner(ownerUid)
                val claimed = database.driveLedgerOperationDao().claim(
                    ownerUid, operation.operationId, now(), workerId, now() - CLAIM_STALE_MS
                )
                if (claimed == 0) return@forEach
                progress = true
                result += processClaim(ownerUid, operation, workerId)
            }
            if (!progress || operations.size < PUSH_BATCH_SIZE) break
        }
        return result
    }

    private suspend fun processClaim(
        ownerUid: String,
        operation: DriveLedgerOperationEntity,
        workerId: String
    ): VehicleLedgerSyncRunResult = try {
        assertOwner(ownerUid)
        val vehicle = database.driveVehicleDao().getVehicle(ownerUid, operation.vehicleId)
        if (vehicle == null || vehicle.deletedAt != null) {
            finish(
                operation, workerId, VehicleLedgerOperationState.SUPERSEDED,
                if (vehicle == null) VehicleLedgerHealthCode.LEDGER_VEHICLE_NOT_FOUND.name
                else VehicleLedgerHealthCode.LEDGER_VEHICLE_DELETED.name,
                VehicleLedgerReceiptStatus.SUPERSEDED, "LOCAL_OUTBOX"
            )
            return VehicleLedgerSyncRunResult(1, 0, false, 0)
        }
        if (operation.entityType == VehicleLedgerEntityType.ODOMETER_MIRROR.name) {
            return processMirror(ownerUid, operation, workerId)
        }
        val type = runCatching { VehicleLedgerEntityType.valueOf(operation.entityType) }.getOrNull()
            ?: return fatal(operation, workerId, "INVALID_ENTITY_TYPE")
        val local = getRecord(ownerUid, type, operation.recordId)
            ?: return fatal(operation, workerId, "RECORD_MISSING")
        if (local.envelope.operationId != operation.operationId ||
            local.envelope.revision != operation.targetRevision
        ) {
            finish(operation, workerId, VehicleLedgerOperationState.SUPERSEDED, null,
                VehicleLedgerReceiptStatus.SUPERSEDED, "LOCAL_OUTBOX", local)
            return VehicleLedgerSyncRunResult(1, 0, false, 0)
        }
        val remoteResult = remote.apply(ownerUid, operation, local)
        assertOwner(ownerUid)
        when (remoteResult) {
            is VehicleLedgerRemoteApplyResult.Applied -> {
                completeApplied(operation, workerId, remoteResult.remote, VehicleLedgerReceiptStatus.APPLIED)
                VehicleLedgerSyncRunResult(1, 0, false, 0)
            }
            is VehicleLedgerRemoteApplyResult.AlreadyApplied -> {
                completeApplied(operation, workerId, remoteResult.remote, VehicleLedgerReceiptStatus.IDEMPOTENT)
                VehicleLedgerSyncRunResult(1, 0, false, 0)
            }
            is VehicleLedgerRemoteApplyResult.RemoteWon -> {
                database.withTransaction {
                    writeConflict(ownerUid, type, local, remoteResult.remote,
                        VehicleLedgerWinnerReason.HIGHER_REVISION, now())
                    upsertRecord(remoteResult.remote, VehicleLedgerSyncState.CONFLICT,
                        VehicleLedgerHealthCode.LEDGER_CONFLICT_UNRESOLVED)
                    finish(operation, workerId, VehicleLedgerOperationState.CONFLICT,
                        VehicleLedgerHealthCode.LEDGER_CONFLICT_UNRESOLVED.name,
                        VehicleLedgerReceiptStatus.CONFLICT, "LOCAL_OUTBOX", remoteResult.remote)
                }
                VehicleLedgerSyncRunResult(1, 0, false, 1)
            }
            is VehicleLedgerRemoteApplyResult.Conflict -> {
                database.withTransaction {
                    writeConflict(ownerUid, type, local, remoteResult.remote,
                        VehicleLedgerWinnerReason.OPERATION_ID, now())
                    finish(operation, workerId, VehicleLedgerOperationState.CONFLICT,
                        remoteResult.reason, VehicleLedgerReceiptStatus.CONFLICT,
                        "LOCAL_OUTBOX", remoteResult.remote)
                }
                VehicleLedgerSyncRunResult(1, 0, false, 1)
            }
            VehicleLedgerRemoteApplyResult.VehicleNotFound,
            VehicleLedgerRemoteApplyResult.VehicleDeleted -> {
                finish(operation, workerId, VehicleLedgerOperationState.SUPERSEDED,
                    if (remoteResult == VehicleLedgerRemoteApplyResult.VehicleNotFound)
                        VehicleLedgerHealthCode.LEDGER_VEHICLE_NOT_FOUND.name
                    else VehicleLedgerHealthCode.LEDGER_VEHICLE_DELETED.name,
                    VehicleLedgerReceiptStatus.SUPERSEDED, "LOCAL_OUTBOX", local)
                VehicleLedgerSyncRunResult(1, 0, false, 0)
            }
            VehicleLedgerRemoteApplyResult.UnsupportedSchema ->
                fatal(operation, workerId, VehicleLedgerHealthCode.LEDGER_UNSUPPORTED_SCHEMA.name, local)
            VehicleLedgerRemoteApplyResult.InvalidRemoteData ->
                fatal(operation, workerId, "INVALID_REMOTE_DATA", local)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        if (isRetryable(error)) retry(operation, workerId, error)
        else fatal(operation, workerId, "FATAL_REMOTE_FAILURE")
    }

    private suspend fun processMirror(
        ownerUid: String,
        operation: DriveLedgerOperationEntity,
        workerId: String
    ): VehicleLedgerSyncRunResult {
        val current = CurrentOdometerSelector.select(
            database.driveOdometerEntryDao().getAll(ownerUid)
                .filter { it.vehicleId == operation.vehicleId }
                .map { it.toContract() }
        )
        if (current?.quality != shared.vehicleledger.contract.OdometerQuality.CONFIRMED) {
            finish(operation, workerId, VehicleLedgerOperationState.SUPERSEDED,
                null, VehicleLedgerReceiptStatus.SUPERSEDED, "MIRROR_RECONCILIATION")
            return VehicleLedgerSyncRunResult(1, 0, false, 0)
        }
        val kilometers = VehicleLedgerUnits.metersToCompatibilityKilometers(current.odometerMeters)
        return when (remote.reconcileOdometerMirror(
            ownerUid, operation.vehicleId, kilometers, operation.targetRevision, operation.operationId
        )) {
            OdometerMirrorRemoteResult.Applied, OdometerMirrorRemoteResult.AlreadyApplied -> {
                assertOwner(ownerUid)
                database.withTransaction {
                    database.driveVehicleDao().setCurrentOdometerMirror(ownerUid, operation.vehicleId, kilometers)
                    finish(operation, workerId, VehicleLedgerOperationState.SUCCEEDED, null,
                        VehicleLedgerReceiptStatus.APPLIED, "MIRROR_RECONCILIATION")
                }
                VehicleLedgerSyncRunResult(1, 0, false, 0)
            }
            OdometerMirrorRemoteResult.RemoteWon -> {
                finish(operation, workerId, VehicleLedgerOperationState.CONFLICT,
                    VehicleLedgerHealthCode.ODOMETER_MIRROR_STALE.name,
                    VehicleLedgerReceiptStatus.CONFLICT, "MIRROR_RECONCILIATION")
                VehicleLedgerSyncRunResult(1, 0, false, 1)
            }
            OdometerMirrorRemoteResult.VehicleNotFound,
            OdometerMirrorRemoteResult.VehicleDeleted -> {
                finish(operation, workerId, VehicleLedgerOperationState.SUPERSEDED,
                    VehicleLedgerHealthCode.LEDGER_VEHICLE_DELETED.name,
                    VehicleLedgerReceiptStatus.SUPERSEDED, "MIRROR_RECONCILIATION")
                VehicleLedgerSyncRunResult(1, 0, false, 0)
            }
        }
    }

    private suspend fun completeApplied(
        operation: DriveLedgerOperationEntity,
        workerId: String,
        remoteRecord: VehicleLedgerRecord,
        receiptStatus: VehicleLedgerReceiptStatus
    ) {
        database.withTransaction {
            val current = getRecord(operation.ownerUid,
                VehicleLedgerEntityType.valueOf(operation.entityType), operation.recordId)
            if (current?.envelope?.operationId == operation.operationId) {
                upsertRecord(remoteRecord, VehicleLedgerSyncState.SYNCED, null)
                writeProvenance(remoteRecord, VehicleLedgerEntityType.valueOf(operation.entityType))
            }
            finish(operation, workerId, VehicleLedgerOperationState.SUCCEEDED, null,
                receiptStatus, "LOCAL_OUTBOX", remoteRecord)
        }
    }

    private suspend fun retry(
        operation: DriveLedgerOperationEntity,
        workerId: String,
        error: Exception
    ): VehicleLedgerSyncRunResult {
        val attempt = operation.attemptCount + 1
        val code = when (error) {
            is IOException -> "NETWORK_RETRYABLE"
            is FirebaseFirestoreException -> "FIRESTORE_${error.code.name}"
            else -> "REMOTE_RETRYABLE"
        }
        database.driveLedgerOperationDao().finishClaim(
            operation.ownerUid, operation.operationId, workerId,
            VehicleLedgerOperationState.RETRY.name, attempt, now() + backoff(attempt), code, now()
        )
        writeReceiptForOperation(operation, VehicleLedgerReceiptStatus.RETRY, "LOCAL_OUTBOX", code, null)
        return VehicleLedgerSyncRunResult(0, 0, true, 0)
    }

    private suspend fun fatal(
        operation: DriveLedgerOperationEntity,
        workerId: String,
        code: String,
        record: VehicleLedgerRecord? = null
    ): VehicleLedgerSyncRunResult {
        finish(operation, workerId, VehicleLedgerOperationState.FATAL, code,
            VehicleLedgerReceiptStatus.FATAL, "LOCAL_OUTBOX", record)
        return VehicleLedgerSyncRunResult(0, 0, false, 1)
    }

    private suspend fun finish(
        operation: DriveLedgerOperationEntity,
        workerId: String,
        state: VehicleLedgerOperationState,
        safeErrorCode: String?,
        receiptStatus: VehicleLedgerReceiptStatus,
        provenance: String,
        record: VehicleLedgerRecord? = null
    ) {
        database.driveLedgerOperationDao().finishClaim(
            operation.ownerUid, operation.operationId, workerId, state.name,
            operation.attemptCount + 1, now(), safeErrorCode, now()
        )
        writeReceiptForOperation(operation, receiptStatus, provenance, safeErrorCode, record)
    }

    private suspend fun markOperationTerminal(
        ownerUid: String,
        operationId: String,
        state: VehicleLedgerOperationState,
        code: String?,
        timestamp: Long
    ) {
        val operation = database.driveLedgerOperationDao().get(ownerUid, operationId) ?: return
        database.driveLedgerOperationDao().upsert(
            operation.copy(
                state = state.name, claimedAt = null, claimedBy = null,
                safeErrorCode = code, updatedAt = timestamp
            )
        )
    }

    private suspend fun getRecord(
        ownerUid: String,
        type: VehicleLedgerEntityType,
        recordId: String
    ): VehicleLedgerRecord? = when (type) {
        VehicleLedgerEntityType.ODOMETER -> database.driveOdometerEntryDao().get(ownerUid, recordId)
            ?.toContract()?.let(VehicleLedgerRecord::Odometer)
        VehicleLedgerEntityType.EXPENSE -> database.driveExpenseDao().get(ownerUid, recordId)
            ?.toContract()?.let(VehicleLedgerRecord::Expense)
        VehicleLedgerEntityType.REMINDER -> database.driveReminderDao().get(ownerUid, recordId)
            ?.toContract()?.let(VehicleLedgerRecord::Reminder)
        else -> null
    }

    private suspend fun upsertRecord(
        record: VehicleLedgerRecord,
        state: VehicleLedgerSyncState,
        health: VehicleLedgerHealthCode?
    ) = when (record) {
        is VehicleLedgerRecord.Odometer -> database.driveOdometerEntryDao().upsert(record.value.toEntity(state, health))
        is VehicleLedgerRecord.Expense -> database.driveExpenseDao().upsert(record.value.toEntity(state, health))
        is VehicleLedgerRecord.Reminder -> database.driveReminderDao().upsert(record.value.toEntity(state, health))
    }

    private fun resolve(
        local: VehicleLedgerRecord,
        remote: VehicleLedgerRecord,
        ownerUid: String,
        vehicleId: String,
        vehicleDeleted: Boolean
    ) = VehicleLedgerConflictResolver.resolve(
        local, remote, ownerUid, vehicleId, vehicleDeleted
    ) { it.envelope }

    private suspend fun writeConflict(
        ownerUid: String,
        type: VehicleLedgerEntityType,
        local: VehicleLedgerRecord,
        remote: VehicleLedgerRecord,
        reason: VehicleLedgerWinnerReason,
        timestamp: Long
    ) {
        val winner = resolve(local, remote, ownerUid, local.envelope.vehicleId, false).winner
        val conflictId = stableUuid(
            "${type.name}:${local.recordId}:${local.envelope.operationId}:${remote.envelope.operationId}"
        )
        database.driveLedgerConflictDao().upsert(
            DriveLedgerConflictEntity(
                ownerUid, conflictId, type.name, local.recordId, local.envelope.vehicleId,
                local.envelope.operationId, remote.envelope.operationId,
                local.envelope.revision, remote.envelope.revision,
                snapshot(local), snapshot(remote), winner?.envelope?.operationId.orEmpty(),
                reason.name, timestamp, null
            )
        )
    }

    private suspend fun writeProvenance(record: VehicleLedgerRecord, type: VehicleLedgerEntityType) {
        database.driveFieldProvenanceDao().upsertAll(record.fields().keys
            .filterNot { it.startsWith("_") }
            .map {
                DriveFieldProvenanceEntity(
                    record.envelope.ownerUid, type.name, record.recordId, it,
                    record.envelope.source.name, record.envelope.clientUpdatedAt
                )
            })
    }

    private suspend fun writeReceipt(
        record: VehicleLedgerRecord,
        type: VehicleLedgerEntityType,
        status: VehicleLedgerReceiptStatus,
        provenance: String,
        timestamp: Long,
        winningOperationId: String? = record.envelope.operationId
    ) {
        database.driveLedgerSyncReceiptDao().upsert(
            DriveLedgerSyncReceiptEntity(
                record.envelope.ownerUid, stableUuid("receipt:${record.envelope.operationId}"),
                record.envelope.operationId, null, type.name, record.recordId,
                record.envelope.vehicleId, "PULL", status.name, provenance,
                record.envelope.revision, winningOperationId, 1, timestamp, timestamp, null
            )
        )
    }

    private suspend fun writeInboundProblemReceipt(
        ownerUid: String,
        type: VehicleLedgerEntityType,
        recordId: String,
        vehicleId: String,
        revision: Long?,
        code: String,
        timestamp: Long
    ) {
        val operationId = stableUuid("inbound-problem:${type.name}:$recordId:$revision:$code")
        database.driveLedgerSyncReceiptDao().upsert(
            DriveLedgerSyncReceiptEntity(
                ownerUid, stableUuid("receipt:$operationId"), operationId, null, type.name,
                recordId, vehicleId, "PULL", VehicleLedgerReceiptStatus.FATAL.name,
                "REMOTE_PULL", revision, null, 1, timestamp, timestamp, code
            )
        )
    }

    private suspend fun writeReceiptForOperation(
        operation: DriveLedgerOperationEntity,
        status: VehicleLedgerReceiptStatus,
        provenance: String,
        error: String?,
        record: VehicleLedgerRecord?
    ) {
        val timestamp = now()
        database.driveLedgerSyncReceiptDao().upsert(
            DriveLedgerSyncReceiptEntity(
                operation.ownerUid, stableUuid("receipt:${operation.operationId}"), operation.operationId,
                operation.logicalBatchId, operation.entityType, operation.recordId, operation.vehicleId,
                operation.kind, status.name, provenance, operation.targetRevision,
                record?.envelope?.operationId, operation.attemptCount + 1,
                operation.createdAt, timestamp, error
            )
        )
    }

    private fun snapshot(record: VehicleLedgerRecord): String = Json.encodeToString(
        JsonObject.serializer(),
        JsonObject(record.fields().mapValues { (_, value) ->
            when (value) {
                null -> JsonNull
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            }
        })
    )

    private fun assertOwner(ownerUid: String) {
        if (currentUserId()?.takeIf(String::isNotBlank) != ownerUid) {
            throw CancellationException("Drive ledger account changed")
        }
    }

    private fun isRetryable(error: Exception): Boolean = when (error) {
        is IOException -> true
        is FirebaseFirestoreException -> error.code in setOf(
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.CANCELLED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.INTERNAL,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.UNKNOWN
        )
        else -> false
    }

    private fun backoff(attempt: Int): Long {
        val exponent = (attempt - 1).coerceIn(0, 9)
        return (BASE_BACKOFF_MS * (1L shl exponent)).coerceAtMost(MAX_BACKOFF_MS)
    }

    private fun stableUuid(value: String): String = UUID.nameUUIDFromBytes(
        value.toByteArray(StandardCharsets.UTF_8)
    ).toString()

    private companion object {
        val COLLECTION_TYPES = listOf(
            VehicleLedgerEntityType.ODOMETER,
            VehicleLedgerEntityType.EXPENSE,
            VehicleLedgerEntityType.REMINDER
        )
        const val PUSH_BATCH_SIZE = 50
        const val CLAIM_STALE_MS = 15L * 60L * 1_000L
        const val BASE_BACKOFF_MS = 30_000L
        const val MAX_BACKOFF_MS = 6L * 60L * 60L * 1_000L
    }
}
