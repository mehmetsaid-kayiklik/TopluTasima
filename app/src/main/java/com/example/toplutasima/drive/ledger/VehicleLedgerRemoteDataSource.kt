package com.example.toplutasima.drive.ledger

import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import shared.vehicleledger.contract.LedgerServerTimestamp
import shared.vehicleledger.contract.VehicleExpenseContract
import shared.vehicleledger.contract.VehicleLedgerConflictResolver
import shared.vehicleledger.contract.VehicleLedgerContractSpec
import shared.vehicleledger.contract.VehicleLedgerEntityType
import shared.vehicleledger.contract.VehicleLedgerEnvelope
import shared.vehicleledger.contract.VehicleLedgerParseResult
import shared.vehicleledger.contract.VehicleLedgerParser
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract
import shared.vehicleledger.contract.toFields

internal data class VehicleLedgerRemoteCursor(
    val seconds: Long,
    val nanoseconds: Int,
    val documentId: String
) : Comparable<VehicleLedgerRemoteCursor> {
    override fun compareTo(other: VehicleLedgerRemoteCursor): Int = compareValuesBy(
        this, other, VehicleLedgerRemoteCursor::seconds,
        VehicleLedgerRemoteCursor::nanoseconds, VehicleLedgerRemoteCursor::documentId
    )
}

internal sealed interface VehicleLedgerRecord {
    val recordId: String
    val envelope: VehicleLedgerEnvelope
    fun fields(): Map<String, Any?>

    data class Odometer(val value: VehicleOdometerEntryContract) : VehicleLedgerRecord {
        override val recordId get() = value.odometerEntryId
        override val envelope get() = value.envelope
        override fun fields() = value.toFields()
    }

    data class Expense(val value: VehicleExpenseContract) : VehicleLedgerRecord {
        override val recordId get() = value.expenseId
        override val envelope get() = value.envelope
        override fun fields() = value.toFields()
    }

    data class Reminder(val value: VehicleReminderContract) : VehicleLedgerRecord {
        override val recordId get() = value.reminderId
        override val envelope get() = value.envelope
        override fun fields() = value.toFields()
    }
}

internal data class UnsupportedVehicleLedgerRecord(
    val recordId: String,
    val vehicleId: String?,
    val schemaVersion: Int
)

internal data class InvalidVehicleLedgerRecord(
    val recordId: String,
    val vehicleId: String?,
    val issueCodes: Set<String>
)

internal data class VehicleLedgerPullBatch(
    val records: List<VehicleLedgerRecord>,
    val unsupported: List<UnsupportedVehicleLedgerRecord>,
    val invalid: List<InvalidVehicleLedgerRecord>,
    val cursor: VehicleLedgerRemoteCursor?,
    val documentCount: Int
)

internal sealed interface VehicleLedgerRemoteApplyResult {
    data class Applied(val remote: VehicleLedgerRecord) : VehicleLedgerRemoteApplyResult
    data class AlreadyApplied(val remote: VehicleLedgerRecord) : VehicleLedgerRemoteApplyResult
    data class RemoteWon(val remote: VehicleLedgerRecord) : VehicleLedgerRemoteApplyResult
    data class Conflict(val remote: VehicleLedgerRecord, val reason: String) : VehicleLedgerRemoteApplyResult
    data object VehicleNotFound : VehicleLedgerRemoteApplyResult
    data object VehicleDeleted : VehicleLedgerRemoteApplyResult
    data object UnsupportedSchema : VehicleLedgerRemoteApplyResult
    data object InvalidRemoteData : VehicleLedgerRemoteApplyResult
}

internal sealed interface OdometerMirrorRemoteResult {
    data object Applied : OdometerMirrorRemoteResult
    data object AlreadyApplied : OdometerMirrorRemoteResult
    data object RemoteWon : OdometerMirrorRemoteResult
    data object VehicleNotFound : OdometerMirrorRemoteResult
    data object VehicleDeleted : OdometerMirrorRemoteResult
}

internal interface VehicleLedgerRemoteDataSource {
    suspend fun fetchInitial(ownerUid: String, entityType: VehicleLedgerEntityType): VehicleLedgerPullBatch
    suspend fun fetchIncremental(
        ownerUid: String,
        entityType: VehicleLedgerEntityType,
        cursor: VehicleLedgerRemoteCursor?
    ): VehicleLedgerPullBatch

    suspend fun apply(
        ownerUid: String,
        operation: DriveLedgerOperationEntity,
        local: VehicleLedgerRecord
    ): VehicleLedgerRemoteApplyResult

    suspend fun reconcileOdometerMirror(
        ownerUid: String,
        vehicleId: String,
        currentOdometerKm: Double?,
        targetRevision: Long,
        operationId: String
    ): OdometerMirrorRemoteResult
}

internal class FirestoreVehicleLedgerRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : VehicleLedgerRemoteDataSource {

    override suspend fun fetchInitial(
        ownerUid: String,
        entityType: VehicleLedgerEntityType
    ): VehicleLedgerPullBatch = fetchAll(ownerUid, entityType, incrementalCursor = null, initial = true)

    override suspend fun fetchIncremental(
        ownerUid: String,
        entityType: VehicleLedgerEntityType,
        cursor: VehicleLedgerRemoteCursor?
    ): VehicleLedgerPullBatch = fetchAll(ownerUid, entityType, cursor, initial = false)

    override suspend fun apply(
        ownerUid: String,
        operation: DriveLedgerOperationEntity,
        local: VehicleLedgerRecord
    ): VehicleLedgerRemoteApplyResult {
        require(ownerUid.isNotBlank() && operation.ownerUid == ownerUid)
        require(local.recordId == operation.recordId && local.envelope.ownerUid == ownerUid)
        val type = VehicleLedgerEntityType.valueOf(operation.entityType)
        val reference = recordDocument(ownerUid, type, operation.recordId)
        val vehicleReference = firestore.collection(USERS).document(ownerUid)
            .collection(VEHICLES).document(operation.vehicleId)
        val transactionResult = firestore.runTransaction { transaction ->
            val vehicle = transaction.get(vehicleReference)
            if (!vehicle.exists() || vehicle.getString(ID) != operation.vehicleId ||
                vehicle.getString(OWNER_UID) != ownerUid
            ) return@runTransaction TxResult.VehicleNotFound
            if (vehicle.epochMillisOrNull(DELETED_AT) != null) return@runTransaction TxResult.VehicleDeleted

            val snapshot = transaction.get(reference)
            val parsed = if (snapshot.exists()) snapshot.parse(type, ownerUid) else null
            when (parsed) {
                is Parsed.Unsupported -> return@runTransaction TxResult.Unsupported
                is Parsed.Invalid -> return@runTransaction TxResult.Invalid
                is Parsed.Valid -> {
                    val resolution = resolve(local, parsed.record, operation.vehicleId)
                    if (resolution.idempotent) return@runTransaction TxResult.AlreadyApplied
                    if (resolution.winner == parsed.record) return@runTransaction TxResult.RemoteWon
                    if (resolution.winner == null) return@runTransaction TxResult.Invalid
                }
                null -> Unit
            }
            transaction.set(
                reference,
                local.fields() + (VehicleLedgerContractSpec.FIELD_SERVER_UPDATED_AT to
                    FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
            TxResult.Applied
        }.await()

        return when (transactionResult) {
            TxResult.VehicleNotFound -> VehicleLedgerRemoteApplyResult.VehicleNotFound
            TxResult.VehicleDeleted -> VehicleLedgerRemoteApplyResult.VehicleDeleted
            TxResult.Unsupported -> VehicleLedgerRemoteApplyResult.UnsupportedSchema
            TxResult.Invalid -> VehicleLedgerRemoteApplyResult.InvalidRemoteData
            TxResult.Applied, TxResult.AlreadyApplied, TxResult.RemoteWon -> {
                val remote = reference.get().await().parse(type, ownerUid)
                val record = (remote as? Parsed.Valid)?.record
                    ?: return VehicleLedgerRemoteApplyResult.InvalidRemoteData
                when (transactionResult) {
                    TxResult.Applied -> VehicleLedgerRemoteApplyResult.Applied(record)
                    TxResult.AlreadyApplied -> VehicleLedgerRemoteApplyResult.AlreadyApplied(record)
                    else -> VehicleLedgerRemoteApplyResult.RemoteWon(record)
                }
            }
        }
    }

    override suspend fun reconcileOdometerMirror(
        ownerUid: String,
        vehicleId: String,
        currentOdometerKm: Double?,
        targetRevision: Long,
        operationId: String
    ): OdometerMirrorRemoteResult {
        val reference = firestore.collection(USERS).document(ownerUid)
            .collection(VEHICLES).document(vehicleId)
        val result = firestore.runTransaction { transaction ->
            val vehicle = transaction.get(reference)
            if (!vehicle.exists() || vehicle.getString(ID) != vehicleId ||
                vehicle.getString(OWNER_UID) != ownerUid
            ) return@runTransaction MirrorTxResult.VehicleNotFound
            if (vehicle.epochMillisOrNull(DELETED_AT) != null) return@runTransaction MirrorTxResult.VehicleDeleted
            val remoteRevision = vehicle.getLong(ODOMETER_PROJECTION_REVISION) ?: 0L
            val remoteOperationId = vehicle.getString(ODOMETER_PROJECTION_OPERATION_ID)
            if (remoteOperationId == operationId) return@runTransaction MirrorTxResult.AlreadyApplied
            if (remoteRevision > targetRevision ||
                (remoteRevision == targetRevision && remoteOperationId != null && remoteOperationId >= operationId)
            ) return@runTransaction MirrorTxResult.RemoteWon
            transaction.set(reference, mapOf(
                CURRENT_ODOMETER_KM to currentOdometerKm,
                ODOMETER_PROJECTION_REVISION to targetRevision,
                ODOMETER_PROJECTION_OPERATION_ID to operationId,
                ODOMETER_PROJECTION_UPDATED_AT to FieldValue.serverTimestamp()
            ), SetOptions.merge())
            MirrorTxResult.Applied
        }.await()
        return when (result) {
            MirrorTxResult.Applied -> OdometerMirrorRemoteResult.Applied
            MirrorTxResult.AlreadyApplied -> OdometerMirrorRemoteResult.AlreadyApplied
            MirrorTxResult.RemoteWon -> OdometerMirrorRemoteResult.RemoteWon
            MirrorTxResult.VehicleNotFound -> OdometerMirrorRemoteResult.VehicleNotFound
            MirrorTxResult.VehicleDeleted -> OdometerMirrorRemoteResult.VehicleDeleted
        }
    }

    private suspend fun fetchAll(
        ownerUid: String,
        entityType: VehicleLedgerEntityType,
        incrementalCursor: VehicleLedgerRemoteCursor?,
        initial: Boolean
    ): VehicleLedgerPullBatch {
        require(ownerUid.isNotBlank())
        val records = mutableListOf<VehicleLedgerRecord>()
        val unsupported = mutableListOf<UnsupportedVehicleLedgerRecord>()
        val invalid = mutableListOf<InvalidVehicleLedgerRecord>()
        var cursor = incrementalCursor
        var initialDocumentId: String? = null
        var count = 0
        do {
            var query: Query = collection(ownerUid, entityType)
            query = if (initial) {
                query.orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            } else {
                query.orderBy(VehicleLedgerContractSpec.FIELD_SERVER_UPDATED_AT, Query.Direction.ASCENDING)
                    .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
            }
            query = query.limit(PAGE_SIZE.toLong())
            if (initial) {
                initialDocumentId?.let { query = query.startAfter(it) }
            } else {
                cursor?.let { query = query.startAfter(Timestamp(it.seconds, it.nanoseconds), it.documentId) }
            }
            val page = query.get().await().documents
            count += page.size
            page.forEach { snapshot ->
                when (val parsed = snapshot.parse(entityType, ownerUid)) {
                    is Parsed.Valid -> records += parsed.record
                    is Parsed.Unsupported -> unsupported += UnsupportedVehicleLedgerRecord(
                        snapshot.id, snapshot.getString(VehicleLedgerContractSpec.FIELD_VEHICLE_ID), parsed.version
                    )
                    is Parsed.Invalid -> invalid += InvalidVehicleLedgerRecord(
                        snapshot.id, snapshot.getString(VehicleLedgerContractSpec.FIELD_VEHICLE_ID), parsed.issues
                    )
                }
            }
            initialDocumentId = page.lastOrNull()?.id
            page.mapNotNull { it.cursorOrNull() }.maxOrNull()?.let { candidate ->
                val current = cursor
                if (current == null || candidate > current) cursor = candidate
            }
        } while (page.size == PAGE_SIZE)
        return VehicleLedgerPullBatch(records, unsupported, invalid, cursor, count)
    }

    private fun collection(ownerUid: String, entityType: VehicleLedgerEntityType) =
        firestore.collection(USERS).document(ownerUid).collection(collectionName(entityType))

    private fun recordDocument(ownerUid: String, entityType: VehicleLedgerEntityType, recordId: String) =
        collection(ownerUid, entityType).document(recordId)

    private fun collectionName(entityType: VehicleLedgerEntityType): String = when (entityType) {
        VehicleLedgerEntityType.ODOMETER -> VehicleLedgerContractSpec.ODOMETER_COLLECTION
        VehicleLedgerEntityType.EXPENSE -> VehicleLedgerContractSpec.EXPENSE_COLLECTION
        VehicleLedgerEntityType.REMINDER -> VehicleLedgerContractSpec.REMINDER_COLLECTION
        else -> error("Unsupported ledger collection")
    }

    private fun DocumentSnapshot.parse(entityType: VehicleLedgerEntityType, ownerUid: String): Parsed {
        val fields = (data ?: emptyMap()).mapValues { (_, value) ->
            if (value is Timestamp) mapOf("seconds" to value.seconds, "nanoseconds" to value.nanoseconds)
            else value
        }
        val result = when (entityType) {
            VehicleLedgerEntityType.ODOMETER -> VehicleLedgerParser.parseOdometer(id, fields, ownerUid)
            VehicleLedgerEntityType.EXPENSE -> VehicleLedgerParser.parseExpense(id, fields, ownerUid)
            VehicleLedgerEntityType.REMINDER -> VehicleLedgerParser.parseReminder(id, fields, ownerUid)
            else -> return Parsed.Invalid(setOf("UNSUPPORTED_COLLECTION"))
        }
        return when (result) {
            is VehicleLedgerParseResult.Valid<*> -> {
                val record = when (val value = result.value) {
                    is VehicleOdometerEntryContract -> VehicleLedgerRecord.Odometer(value)
                    is VehicleExpenseContract -> VehicleLedgerRecord.Expense(value)
                    is VehicleReminderContract -> VehicleLedgerRecord.Reminder(value)
                    else -> return Parsed.Invalid(setOf("INVALID_RECORD_TYPE"))
                }
                if (record.envelope.serverUpdatedAt == null) Parsed.Invalid(setOf("MISSING_SERVER_UPDATED_AT"))
                else Parsed.Valid(record)
            }
            is VehicleLedgerParseResult.Unsupported -> Parsed.Unsupported(result.schemaVersion)
            is VehicleLedgerParseResult.Invalid -> Parsed.Invalid(result.issues.map { it.name }.toSet())
        }
    }

    private fun DocumentSnapshot.cursorOrNull(): VehicleLedgerRemoteCursor? {
        val timestamp = getTimestamp(VehicleLedgerContractSpec.FIELD_SERVER_UPDATED_AT) ?: return null
        return VehicleLedgerRemoteCursor(timestamp.seconds, timestamp.nanoseconds, id)
    }

    private fun resolve(
        local: VehicleLedgerRecord,
        remote: VehicleLedgerRecord,
        vehicleId: String
    ) = when {
        local is VehicleLedgerRecord.Odometer && remote is VehicleLedgerRecord.Odometer ->
            VehicleLedgerConflictResolver.resolve(local, remote, local.envelope.ownerUid, vehicleId, false) { it.envelope }
        local is VehicleLedgerRecord.Expense && remote is VehicleLedgerRecord.Expense ->
            VehicleLedgerConflictResolver.resolve(local, remote, local.envelope.ownerUid, vehicleId, false) { it.envelope }
        local is VehicleLedgerRecord.Reminder && remote is VehicleLedgerRecord.Reminder ->
            VehicleLedgerConflictResolver.resolve(local, remote, local.envelope.ownerUid, vehicleId, false) { it.envelope }
        else -> VehicleLedgerConflictResolver.resolve<VehicleLedgerRecord>(
            null, remote, local.envelope.ownerUid, vehicleId, false
        ) { it.envelope }
    }

    private fun DocumentSnapshot.epochMillisOrNull(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    private sealed interface Parsed {
        data class Valid(val record: VehicleLedgerRecord) : Parsed
        data class Unsupported(val version: Int) : Parsed
        data class Invalid(val issues: Set<String>) : Parsed
    }

    private enum class TxResult { Applied, AlreadyApplied, RemoteWon, VehicleNotFound, VehicleDeleted, Unsupported, Invalid }
    private enum class MirrorTxResult { Applied, AlreadyApplied, RemoteWon, VehicleNotFound, VehicleDeleted }

    private companion object {
        const val USERS = "users"
        const val VEHICLES = "vehicles"
        const val ID = "id"
        const val OWNER_UID = "ownerUid"
        const val DELETED_AT = "deletedAt"
        const val CURRENT_ODOMETER_KM = "currentOdometerKm"
        const val ODOMETER_PROJECTION_REVISION = "_odometerProjectionRevision"
        const val ODOMETER_PROJECTION_OPERATION_ID = "_odometerProjectionOperationId"
        const val ODOMETER_PROJECTION_UPDATED_AT = "_odometerProjectionUpdatedAt"
        const val PAGE_SIZE = 200
    }
}
