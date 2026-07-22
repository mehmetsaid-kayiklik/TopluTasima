package com.example.toplutasima.drive.assignment

import com.example.toplutasima.data.local.entity.DriveAssignmentOperationEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import shared.vehicleassignment.contract.AssignmentServerTimestamp
import shared.vehicleassignment.contract.VehicleAssignmentContract
import shared.vehicleassignment.contract.VehicleAssignmentContractSpec
import shared.vehicleassignment.contract.VehicleAssignmentParseResult
import shared.vehicleassignment.contract.VehicleAssignmentParser
import shared.vehicleassignment.contract.VehicleAssignmentSource

internal data class AssignmentRemoteCursor(
    val seconds: Long,
    val nanoseconds: Int,
    val documentId: String
) : Comparable<AssignmentRemoteCursor> {
    override fun compareTo(other: AssignmentRemoteCursor): Int =
        compareValuesBy(
            this,
            other,
            AssignmentRemoteCursor::seconds,
            AssignmentRemoteCursor::nanoseconds,
            AssignmentRemoteCursor::documentId
        )
}

internal data class RemoteVehicleAssignment(
    val contract: VehicleAssignmentContract,
    val serverUpdatedAt: AssignmentServerTimestamp?,
    val cursor: AssignmentRemoteCursor?
)

internal data class UnsupportedRemoteAssignment(
    val vehicleId: String,
    val schemaVersion: Int
)

internal data class InvalidRemoteAssignment(
    val vehicleId: String,
    val issueCodes: Set<String>
)

internal data class VehicleAssignmentPullBatch(
    val assignments: List<RemoteVehicleAssignment>,
    val unsupported: List<UnsupportedRemoteAssignment>,
    val invalid: List<InvalidRemoteAssignment>,
    val cursor: AssignmentRemoteCursor?
)

internal sealed interface VehicleAssignmentRemoteApplyResult {
    data class Applied(
        val assignment: RemoteVehicleAssignment,
        val mirrorPending: Boolean,
        val conflictOperationId: String?
    ) : VehicleAssignmentRemoteApplyResult

    data class AlreadyApplied(
        val assignment: RemoteVehicleAssignment,
        val mirrorPending: Boolean
    ) : VehicleAssignmentRemoteApplyResult

    data class RemoteWon(val assignment: RemoteVehicleAssignment) : VehicleAssignmentRemoteApplyResult
    data object VehicleNotFound : VehicleAssignmentRemoteApplyResult
    data object VehicleDeleted : VehicleAssignmentRemoteApplyResult
    data object UnsupportedSchema : VehicleAssignmentRemoteApplyResult
    data object InvalidRemoteData : VehicleAssignmentRemoteApplyResult
}

internal interface VehicleAssignmentRemoteDataSource {
    suspend fun fetchInitial(ownerUid: String): VehicleAssignmentPullBatch
    suspend fun fetchIncremental(
        ownerUid: String,
        cursor: AssignmentRemoteCursor?
    ): VehicleAssignmentPullBatch

    suspend fun apply(
        ownerUid: String,
        operation: DriveAssignmentOperationEntity
    ): VehicleAssignmentRemoteApplyResult

    suspend fun reconcileVehicleMirror(
        ownerUid: String,
        vehicleId: String,
        activePersonId: String?
    )
}

internal class FirestoreVehicleAssignmentRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : VehicleAssignmentRemoteDataSource {
    override suspend fun fetchInitial(ownerUid: String): VehicleAssignmentPullBatch {
        val documents = mutableListOf<DocumentSnapshot>()
        var lastDocumentId: String? = null
        do {
            var query: Query = assignmentCollection(ownerUid)
                .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
                .limit(PAGE_SIZE.toLong())
            lastDocumentId?.let { query = query.startAfter(it) }
            val page = query.get().await().documents
            documents += page
            lastDocumentId = page.lastOrNull()?.id
        } while (page.size == PAGE_SIZE)
        return documents.toPullBatch()
    }

    override suspend fun fetchIncremental(
        ownerUid: String,
        cursor: AssignmentRemoteCursor?
    ): VehicleAssignmentPullBatch {
        val documents = mutableListOf<DocumentSnapshot>()
        var pageCursor = cursor
        do {
            var query: Query = assignmentCollection(ownerUid)
                .orderBy(
                    VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT,
                    Query.Direction.ASCENDING
                )
                .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
                .limit(PAGE_SIZE.toLong())
            pageCursor?.let {
                query = query.startAfter(
                    Timestamp(it.seconds, it.nanoseconds),
                    it.documentId
                )
            }
            val page = query.get().await().documents
            documents += page
            pageCursor = page.lastOrNull()?.cursorOrNull() ?: pageCursor
        } while (page.size == PAGE_SIZE)
        return documents.toPullBatch()
    }

    override suspend fun apply(
        ownerUid: String,
        operation: DriveAssignmentOperationEntity
    ): VehicleAssignmentRemoteApplyResult {
        require(ownerUid.isNotBlank() && operation.ownerUid == ownerUid)
        val contract = operation.toContract()
        require(contract.validate(operation.vehicleId).isEmpty())
        val vehicle = vehicleDocument(ownerUid, operation.vehicleId)
        val assignment = assignmentDocument(ownerUid, operation.vehicleId)

        val transactionResult = firestore.runTransaction { transaction ->
            val vehicleSnapshot = transaction.get(vehicle)
            if (!vehicleSnapshot.exists()) return@runTransaction TransactionResult.VehicleNotFound
            if (vehicleSnapshot.getString(FIELD_ID) != operation.vehicleId) {
                return@runTransaction TransactionResult.VehicleNotFound
            }
            val payloadOwner = vehicleSnapshot.getString(FIELD_OWNER_UID)
            if (payloadOwner != null && payloadOwner != ownerUid) {
                return@runTransaction TransactionResult.VehicleNotFound
            }
            if (vehicleSnapshot.epochMillisOrNull(VehicleAssignmentContractSpec.FIELD_DELETED_AT) != null) {
                return@runTransaction TransactionResult.VehicleDeleted
            }

            val currentSnapshot = transaction.get(assignment)
            val current = if (currentSnapshot.exists()) currentSnapshot.toParsedAssignment() else null
            when (current) {
                is SnapshotAssignment.Unsupported -> return@runTransaction TransactionResult.UnsupportedSchema
                is SnapshotAssignment.Invalid -> return@runTransaction TransactionResult.InvalidRemoteData
                is SnapshotAssignment.Valid -> {
                    if (current.value.contract.operationId == operation.operationId) {
                        return@runTransaction TransactionResult.AlreadyApplied
                    }
                    if (current.value.contract.revision > operation.targetRevision) {
                        return@runTransaction TransactionResult.RemoteWon
                    }
                }
                null -> Unit
            }

            val conflictOperationId = (current as? SnapshotAssignment.Valid)
                ?.value
                ?.contract
                ?.takeIf { it.revision == operation.targetRevision }
                ?.operationId
            transaction.set(
                assignment,
                contract.toFirestoreFields() + mapOf(
                    VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT to
                        FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            TransactionResult.Applied(conflictOperationId)
        }.await()

        if (transactionResult == TransactionResult.VehicleNotFound) {
            return VehicleAssignmentRemoteApplyResult.VehicleNotFound
        }
        if (transactionResult == TransactionResult.VehicleDeleted) {
            return VehicleAssignmentRemoteApplyResult.VehicleDeleted
        }
        if (transactionResult == TransactionResult.UnsupportedSchema) {
            return VehicleAssignmentRemoteApplyResult.UnsupportedSchema
        }
        if (transactionResult == TransactionResult.InvalidRemoteData) {
            return VehicleAssignmentRemoteApplyResult.InvalidRemoteData
        }

        val remote = readValidAssignment(assignment)
            ?: return VehicleAssignmentRemoteApplyResult.InvalidRemoteData
        if (transactionResult == TransactionResult.RemoteWon) {
            return VehicleAssignmentRemoteApplyResult.RemoteWon(remote)
        }
        val mirrorPending = try {
            reconcileVehicleMirror(
                ownerUid = ownerUid,
                vehicleId = operation.vehicleId,
                activePersonId = contract.personId.takeIf { contract.deletedAt == null }
            )
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            true
        }
        return when (transactionResult) {
            is TransactionResult.Applied -> VehicleAssignmentRemoteApplyResult.Applied(
                assignment = remote,
                mirrorPending = mirrorPending,
                conflictOperationId = transactionResult.conflictOperationId
            )
            TransactionResult.AlreadyApplied -> VehicleAssignmentRemoteApplyResult.AlreadyApplied(
                assignment = remote,
                mirrorPending = mirrorPending
            )
            else -> VehicleAssignmentRemoteApplyResult.InvalidRemoteData
        }
    }

    override suspend fun reconcileVehicleMirror(
        ownerUid: String,
        vehicleId: String,
        activePersonId: String?
    ) {
        vehicleDocument(ownerUid, vehicleId).set(
            mapOf(
                FIELD_ASSIGNED_PERSON_ID to activePersonId,
                VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    private suspend fun readValidAssignment(
        reference: DocumentReference
    ): RemoteVehicleAssignment? = when (val parsed = reference.get().await().toParsedAssignment()) {
        is SnapshotAssignment.Valid -> parsed.value
        else -> null
    }

    private fun List<DocumentSnapshot>.toPullBatch(): VehicleAssignmentPullBatch {
        val valid = mutableListOf<RemoteVehicleAssignment>()
        val unsupported = mutableListOf<UnsupportedRemoteAssignment>()
        val invalid = mutableListOf<InvalidRemoteAssignment>()
        forEach { snapshot ->
            when (val parsed = snapshot.toParsedAssignment()) {
                is SnapshotAssignment.Valid -> valid += parsed.value
                is SnapshotAssignment.Unsupported -> unsupported += UnsupportedRemoteAssignment(
                    snapshot.id,
                    parsed.schemaVersion
                )
                is SnapshotAssignment.Invalid -> invalid += InvalidRemoteAssignment(
                    snapshot.id,
                    parsed.issueCodes
                )
            }
        }
        return VehicleAssignmentPullBatch(
            assignments = valid,
            unsupported = unsupported,
            invalid = invalid,
            cursor = valid.mapNotNull(RemoteVehicleAssignment::cursor).maxOrNull()
        )
    }

    private fun DocumentSnapshot.toParsedAssignment(): SnapshotAssignment {
        val raw = data.orEmpty().toMutableMap()
        getTimestamp(VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT)?.let { timestamp ->
            raw[VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT] = mapOf(
                "seconds" to timestamp.seconds,
                "nanoseconds" to timestamp.nanoseconds
            )
        }
        return when (val result = VehicleAssignmentParser.parse(id, raw)) {
            is VehicleAssignmentParseResult.Valid -> SnapshotAssignment.Valid(
                RemoteVehicleAssignment(
                    contract = result.assignment,
                    serverUpdatedAt = result.serverUpdatedAt,
                    cursor = cursorOrNull()
                )
            )
            is VehicleAssignmentParseResult.Unsupported ->
                SnapshotAssignment.Unsupported(result.schemaVersion)
            is VehicleAssignmentParseResult.Invalid -> SnapshotAssignment.Invalid(
                result.issues.mapTo(linkedSetOf()) { it.name }
            )
        }
    }

    private fun DocumentSnapshot.cursorOrNull(): AssignmentRemoteCursor? =
        getTimestamp(VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT)?.let {
            AssignmentRemoteCursor(it.seconds, it.nanoseconds, id)
        }

    private fun DocumentSnapshot.epochMillisOrNull(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    private fun DriveAssignmentOperationEntity.toContract(): VehicleAssignmentContract =
        VehicleAssignmentContract(
            vehicleId = vehicleId,
            personId = personId,
            schemaVersion = schemaVersion,
            revision = targetRevision,
            operationId = operationId,
            source = VehicleAssignmentSource.fromWire(source),
            clientUpdatedAt = clientUpdatedAt,
            deletedAt = deletedAt
        )

    private fun assignmentCollection(ownerUid: String): CollectionReference =
        userDocument(ownerUid).collection(VehicleAssignmentContractSpec.ASSIGNMENTS_COLLECTION)

    private fun assignmentDocument(ownerUid: String, vehicleId: String): DocumentReference =
        assignmentCollection(ownerUid).document(vehicleId)

    private fun vehicleDocument(ownerUid: String, vehicleId: String): DocumentReference =
        userDocument(ownerUid).collection(VehicleAssignmentContractSpec.VEHICLES_COLLECTION)
            .document(vehicleId)

    private fun userDocument(ownerUid: String): DocumentReference {
        require(ownerUid.isNotBlank())
        return firestore.collection(VehicleAssignmentContractSpec.USERS_COLLECTION).document(ownerUid)
    }

    private sealed interface SnapshotAssignment {
        data class Valid(val value: RemoteVehicleAssignment) : SnapshotAssignment
        data class Unsupported(val schemaVersion: Int) : SnapshotAssignment
        data class Invalid(val issueCodes: Set<String>) : SnapshotAssignment
    }

    private sealed interface TransactionResult {
        data class Applied(val conflictOperationId: String?) : TransactionResult
        data object AlreadyApplied : TransactionResult
        data object RemoteWon : TransactionResult
        data object VehicleNotFound : TransactionResult
        data object VehicleDeleted : TransactionResult
        data object UnsupportedSchema : TransactionResult
        data object InvalidRemoteData : TransactionResult
    }

    private companion object {
        const val PAGE_SIZE = 200
        const val FIELD_ID = "id"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_ASSIGNED_PERSON_ID = "assignedPersonId"
    }
}
