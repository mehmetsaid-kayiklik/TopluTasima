package com.example.toplutasima.drive.photo

import android.net.Uri
import com.example.toplutasima.data.local.entity.DrivePhotoOperationEntity
import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import shared.vehiclephoto.contract.VehiclePhotoConflictResolver
import shared.vehiclephoto.contract.VehiclePhotoContractSpec
import shared.vehiclephoto.contract.VehiclePhotoParseResult
import shared.vehiclephoto.contract.VehiclePhotoParser
import shared.vehiclephoto.contract.VehiclePhotoServerTimestamp
import shared.vehiclephoto.contract.VersionedVehiclePhoto

data class VehiclePhotoRemoteCursor(
    val seconds: Long,
    val nanos: Int,
    val documentPath: String
)

data class VehiclePhotoRemoteBatch(
    val photos: List<VersionedVehiclePhoto>,
    val unsupported: List<Pair<String, Int>>,
    val invalidPhotoIds: List<String>,
    val cursor: VehiclePhotoRemoteCursor?
) {
    companion object {
        val EMPTY = VehiclePhotoRemoteBatch(emptyList(), emptyList(), emptyList(), null)
    }
}

sealed interface VehiclePhotoRemoteResult {
    data class Applied(val serverTimestamp: VehiclePhotoServerTimestamp?) : VehiclePhotoRemoteResult
    data class AlreadyApplied(val serverTimestamp: VehiclePhotoServerTimestamp?) : VehiclePhotoRemoteResult
    data class RemoteWon(val remote: VersionedVehiclePhoto) : VehiclePhotoRemoteResult
    data class PrimaryRemoteWon(
        val primaryPhotoId: String?,
        val revision: Long,
        val operationId: String?
    ) : VehiclePhotoRemoteResult
    data object DeleteWins : VehiclePhotoRemoteResult
    data object VehicleNotFound : VehiclePhotoRemoteResult
    data object VehicleDeleted : VehiclePhotoRemoteResult
    data object PhotoMetadataMissing : VehiclePhotoRemoteResult
    data object UnsupportedSchema : VehiclePhotoRemoteResult
    data object InvalidRemoteData : VehiclePhotoRemoteResult
}

interface VehiclePhotoRemoteDataSource {
    suspend fun fetchInitial(ownerUid: String): VehiclePhotoRemoteBatch = VehiclePhotoRemoteBatch.EMPTY
    suspend fun fetchIncremental(ownerUid: String, cursor: VehiclePhotoRemoteCursor?): VehiclePhotoRemoteBatch =
        VehiclePhotoRemoteBatch.EMPTY

    suspend fun upload(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult

    suspend fun updateMetadata(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult

    suspend fun delete(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult

    suspend fun setPrimary(
        ownerUid: String,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult

    suspend fun downloadToCache(ownerUid: String, photo: DriveVehiclePhotoEntity): String
}

class FirebaseVehiclePhotoRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
    private val fileStore: VehiclePhotoFileStore
) : VehiclePhotoRemoteDataSource {

    override suspend fun fetchInitial(ownerUid: String): VehiclePhotoRemoteBatch = fetch(ownerUid, null)

    override suspend fun fetchIncremental(
        ownerUid: String,
        cursor: VehiclePhotoRemoteCursor?
    ): VehiclePhotoRemoteBatch = fetch(ownerUid, cursor)

    override suspend fun upload(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult {
        validateOperation(ownerUid, photo, operation, requiresTombstone = false)
        if (!vehicleIsActive(ownerUid, photo.vehicleId)) return VehiclePhotoRemoteResult.VehicleDeleted
        val prepared = photo.localPreparedPath?.let(::File)
            ?.takeIf { it.isFile && fileStore.isScopedPath(it.absolutePath, ownerUid, photo.vehicleId) }
            ?: throw VehiclePhotoFailure.PhotoSourceUnavailable()
        if (sha256(prepared) != photo.contentHash) throw VehiclePhotoFailure.PhotoPreparationFailed()
        val metadata = StorageMetadata.Builder()
            .setContentType(VehiclePhotoContractSpec.OUTPUT_MIME_TYPE)
            .setCustomMetadata("vehicleId", photo.vehicleId)
            .setCustomMetadata("photoId", photo.photoId)
            .setCustomMetadata("contentHash", requireNotNull(photo.contentHash))
            .setCustomMetadata("schemaVersion", photo.schemaVersion.toString())
            .build()
        storage.reference.child(requireNotNull(photo.storagePath))
            .putFile(Uri.fromFile(prepared), metadata)
            .await()
        return writeMetadata(ownerUid, photo)
    }

    override suspend fun updateMetadata(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult {
        validateOperation(ownerUid, photo, operation, requiresTombstone = false)
        return writeMetadata(ownerUid, photo)
    }

    override suspend fun delete(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult {
        validateOperation(ownerUid, photo, operation, requiresTombstone = true)
        val reference = photoDocument(ownerUid, photo.vehicleId, photo.photoId)
        val result = firestore.runTransaction { transaction ->
            val vehicle = transaction.get(vehicleDocument(ownerUid, photo.vehicleId))
            if (!vehicle.exists()) return@runTransaction TransactionResult.VehicleNotFound
            val snapshot = transaction.get(reference)
            if (snapshot.getString(VehiclePhotoContractSpec.FIELD_OPERATION_ID) == photo.operationId &&
                snapshot.epochMillisOrNull(VehiclePhotoContractSpec.FIELD_DELETED_AT) != null
            ) return@runTransaction TransactionResult.AlreadyApplied
            transaction.set(
                reference,
                photo.toContract().toFirestoreFields() + mapOf(
                    VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            TransactionResult.Applied
        }.await()
        val storagePath = photo.storagePath
        if (!storagePath.isNullOrBlank()) {
            try {
                storage.reference.child(storagePath).delete().await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: StorageException) {
                if (error.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw error
            }
        }
        return when (result) {
            TransactionResult.VehicleNotFound -> VehiclePhotoRemoteResult.VehicleNotFound
            TransactionResult.AlreadyApplied -> VehiclePhotoRemoteResult.AlreadyApplied(readServerTimestamp(reference))
            else -> VehiclePhotoRemoteResult.Applied(readServerTimestamp(reference))
        }
    }

    override suspend fun setPrimary(
        ownerUid: String,
        operation: DrivePhotoOperationEntity
    ): VehiclePhotoRemoteResult {
        require(operation.ownerUid == ownerUid && operation.type == VehiclePhotoOperationType.SET_PRIMARY.name)
        val vehicleReference = vehicleDocument(ownerUid, operation.vehicleId)
        val result = firestore.runTransaction { transaction ->
            val vehicle = transaction.get(vehicleReference)
            if (!vehicle.exists()) return@runTransaction TransactionResult.VehicleNotFound
            if (vehicle.epochMillisOrNull(VehiclePhotoContractSpec.FIELD_DELETED_AT) != null) {
                return@runTransaction TransactionResult.VehicleDeleted
            }
            val currentOperationId = vehicle.getString(FIELD_PRIMARY_PHOTO_OPERATION_ID)
            val currentRevision = vehicle.getLong(FIELD_PRIMARY_PHOTO_REVISION) ?: 0L
            if (currentOperationId == operation.operationId) return@runTransaction TransactionResult.AlreadyApplied
            if (currentRevision > operation.targetRevision ||
                (currentRevision == operation.targetRevision &&
                    currentOperationId != null && currentOperationId >= operation.operationId)
            ) return@runTransaction TransactionResult.PrimaryRemoteWon(
                vehicle.getString(FIELD_PRIMARY_PHOTO_ID),
                currentRevision,
                currentOperationId
            )

            val targetId = operation.targetPrimaryPhotoId
            val targetReference = targetId?.let { photoDocument(ownerUid, operation.vehicleId, it) }
            if (targetReference != null) {
                val target = transaction.get(targetReference)
                if (!target.exists() || target.epochMillisOrNull(VehiclePhotoContractSpec.FIELD_DELETED_AT) != null) {
                    return@runTransaction TransactionResult.PhotoMetadataMissing
                }
            }
            val previousId = vehicle.getString(FIELD_PRIMARY_PHOTO_ID)
            val previousReference = previousId
                ?.takeIf { it != targetId }
                ?.let { photoDocument(ownerUid, operation.vehicleId, it) }
            previousReference?.let { previous ->
                val previousSnapshot = transaction.get(previous)
                if (previousSnapshot.exists()) {
                    transaction.set(
                        previous,
                        mapOf(
                            VehiclePhotoContractSpec.FIELD_IS_PRIMARY to false,
                            VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
                        ),
                        SetOptions.merge()
                    )
                }
            }
            targetReference?.let { target ->
                transaction.set(
                    target,
                    mapOf(
                        VehiclePhotoContractSpec.FIELD_IS_PRIMARY to true,
                        VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
                    ),
                    SetOptions.merge()
                )
            }
            transaction.set(
                vehicleReference,
                mapOf(
                    FIELD_PRIMARY_PHOTO_ID to targetId,
                    FIELD_PRIMARY_PHOTO_REVISION to operation.targetRevision,
                    FIELD_PRIMARY_PHOTO_OPERATION_ID to operation.operationId,
                    FIELD_UPDATED_AT to operation.updatedAt,
                    VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            TransactionResult.Applied
        }.await()
        return when (result) {
            TransactionResult.Applied -> VehiclePhotoRemoteResult.Applied(null)
            TransactionResult.AlreadyApplied -> VehiclePhotoRemoteResult.AlreadyApplied(null)
            is TransactionResult.PrimaryRemoteWon -> VehiclePhotoRemoteResult.PrimaryRemoteWon(
                result.primaryPhotoId,
                result.revision,
                result.operationId
            )
            TransactionResult.VehicleNotFound -> VehiclePhotoRemoteResult.VehicleNotFound
            TransactionResult.VehicleDeleted -> VehiclePhotoRemoteResult.VehicleDeleted
            TransactionResult.PhotoMetadataMissing -> VehiclePhotoRemoteResult.PhotoMetadataMissing
            else -> VehiclePhotoRemoteResult.InvalidRemoteData
        }
    }

    override suspend fun downloadToCache(ownerUid: String, photo: DriveVehiclePhotoEntity): String {
        require(photo.ownerUid == ownerUid && photo.deletedAt == null)
        val expectedPath = VehiclePhotoContractSpec.storagePath(ownerUid, photo.vehicleId, photo.photoId)
        if (photo.storagePath != expectedPath) throw VehiclePhotoFailure.PhotoMetadataConflict()
        val hash = photo.contentHash ?: throw VehiclePhotoFailure.PhotoMetadataConflict()
        val target = fileStore.cacheFile(ownerUid, photo.vehicleId, photo.photoId, hash)
        if (target.isFile && target.length() == photo.sizeBytes && sha256(target) == hash) {
            return target.absolutePath
        }
        val temporary = File(target.parentFile, ".${target.name}.downloading")
        try {
            target.parentFile?.mkdirs()
            storage.reference.child(expectedPath).getFile(temporary).await()
            if (sha256(temporary) != hash) throw VehiclePhotoFailure.PhotoMetadataConflict()
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            return target.absolutePath
        } catch (cancelled: CancellationException) {
            temporary.delete()
            throw cancelled
        } catch (error: StorageException) {
            temporary.delete()
            if (error.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                throw VehiclePhotoFailure.PhotoStorageObjectMissing()
            }
            throw error
        } catch (known: VehiclePhotoFailure) {
            temporary.delete()
            throw known
        } catch (error: Exception) {
            temporary.delete()
            throw error
        }
    }

    private suspend fun writeMetadata(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity
    ): VehiclePhotoRemoteResult {
        val reference = photoDocument(ownerUid, photo.vehicleId, photo.photoId)
        val transactionResult = firestore.runTransaction { transaction ->
            val vehicle = transaction.get(vehicleDocument(ownerUid, photo.vehicleId))
            if (!vehicle.exists()) return@runTransaction TransactionResult.VehicleNotFound
            if (vehicle.epochMillisOrNull(VehiclePhotoContractSpec.FIELD_DELETED_AT) != null) {
                return@runTransaction TransactionResult.VehicleDeleted
            }
            val currentSnapshot = transaction.get(reference)
            if (currentSnapshot.exists()) {
                val parsed = currentSnapshot.parse(ownerUid)
                when (parsed) {
                    is VehiclePhotoParseResult.Unsupported -> return@runTransaction TransactionResult.UnsupportedSchema
                    is VehiclePhotoParseResult.Invalid -> return@runTransaction TransactionResult.InvalidRemoteData
                    is VehiclePhotoParseResult.Valid -> {
                        val current = parsed.value
                        if (current.photo.operationId == photo.operationId) {
                            return@runTransaction TransactionResult.AlreadyApplied
                        }
                        if (current.photo.deletedAt != null) return@runTransaction TransactionResult.DeleteWins
                        if (current.photo.revision > photo.revision ||
                            (current.photo.revision == photo.revision &&
                                current.photo.operationId >= photo.operationId)
                        ) return@runTransaction TransactionResult.RemoteWon
                    }
                }
            }
            transaction.set(
                reference,
                photo.toContract().toFirestoreFields() + mapOf(
                    VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            TransactionResult.Applied
        }.await()
        return when (transactionResult) {
            TransactionResult.Applied -> VehiclePhotoRemoteResult.Applied(readServerTimestamp(reference))
            TransactionResult.AlreadyApplied -> VehiclePhotoRemoteResult.AlreadyApplied(readServerTimestamp(reference))
            TransactionResult.RemoteWon -> {
                val remote = reference.get().await().parse(ownerUid)
                if (remote is VehiclePhotoParseResult.Valid) VehiclePhotoRemoteResult.RemoteWon(remote.value)
                else VehiclePhotoRemoteResult.InvalidRemoteData
            }
            TransactionResult.DeleteWins -> VehiclePhotoRemoteResult.DeleteWins
            TransactionResult.VehicleNotFound -> VehiclePhotoRemoteResult.VehicleNotFound
            TransactionResult.VehicleDeleted -> VehiclePhotoRemoteResult.VehicleDeleted
            TransactionResult.UnsupportedSchema -> VehiclePhotoRemoteResult.UnsupportedSchema
            TransactionResult.InvalidRemoteData -> VehiclePhotoRemoteResult.InvalidRemoteData
            TransactionResult.PhotoMetadataMissing -> VehiclePhotoRemoteResult.PhotoMetadataMissing
            is TransactionResult.PrimaryRemoteWon -> VehiclePhotoRemoteResult.PrimaryRemoteWon(
                transactionResult.primaryPhotoId,
                transactionResult.revision,
                transactionResult.operationId
            )
        }
    }

    private suspend fun fetch(ownerUid: String, initialCursor: VehiclePhotoRemoteCursor?): VehiclePhotoRemoteBatch {
        require(ownerUid.isNotBlank())
        val documents = mutableListOf<DocumentSnapshot>()
        var cursor = initialCursor
        do {
            var query: Query = firestore.collectionGroup(VehiclePhotoContractSpec.PHOTOS_COLLECTION)
                .whereEqualTo(VehiclePhotoContractSpec.FIELD_OWNER_UID, ownerUid)
                .orderBy(VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT)
                .orderBy(FieldPath.documentId())
                .limit(PAGE_SIZE)
            cursor?.let {
                query = query.startAfter(Timestamp(it.seconds, it.nanos), it.documentPath)
            }
            val page = query.get().await().documents
            documents += page
            cursor = page.lastOrNull()?.remoteCursor() ?: cursor
        } while (page.size == PAGE_SIZE.toInt())

        val valid = mutableListOf<VersionedVehiclePhoto>()
        val unsupported = mutableListOf<Pair<String, Int>>()
        val invalid = mutableListOf<String>()
        documents.forEach { snapshot ->
            when (val parsed = snapshot.parse(ownerUid)) {
                is VehiclePhotoParseResult.Valid -> valid += parsed.value
                is VehiclePhotoParseResult.Unsupported -> unsupported += snapshot.id to parsed.schemaVersion
                is VehiclePhotoParseResult.Invalid -> invalid += snapshot.id
            }
        }
        return VehiclePhotoRemoteBatch(valid, unsupported, invalid, cursor)
    }

    private suspend fun vehicleIsActive(ownerUid: String, vehicleId: String): Boolean {
        val vehicle = vehicleDocument(ownerUid, vehicleId).get().await()
        return vehicle.exists() && vehicle.id == vehicleId &&
            vehicle.epochMillisOrNull(VehiclePhotoContractSpec.FIELD_DELETED_AT) == null
    }

    private fun validateOperation(
        ownerUid: String,
        photo: DriveVehiclePhotoEntity,
        operation: DrivePhotoOperationEntity,
        requiresTombstone: Boolean
    ) {
        require(photo.ownerUid == ownerUid && operation.ownerUid == ownerUid)
        require(photo.photoId == operation.photoId && photo.vehicleId == operation.vehicleId)
        require((photo.deletedAt != null) == requiresTombstone)
        require(photo.toContract().validate(photo.photoId).isEmpty())
    }

    private fun DocumentSnapshot.parse(ownerUid: String): VehiclePhotoParseResult {
        val fields = data.orEmpty().toMutableMap()
        getTimestamp(VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT)?.let {
            fields[VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT] = mapOf(
                "seconds" to it.seconds,
                "nanoseconds" to it.nanoseconds
            )
        }
        return VehiclePhotoParser.parse(id, fields, ownerUid)
    }

    private fun DocumentSnapshot.remoteCursor(): VehiclePhotoRemoteCursor? =
        getTimestamp(VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT)?.let {
            VehiclePhotoRemoteCursor(it.seconds, it.nanoseconds, reference.path)
        }

    private suspend fun readServerTimestamp(
        reference: com.google.firebase.firestore.DocumentReference
    ): VehiclePhotoServerTimestamp? = reference.get().await()
        .getTimestamp(VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT)
        ?.let { VehiclePhotoServerTimestamp(it.seconds, it.nanoseconds) }

    private fun DocumentSnapshot.epochMillisOrNull(field: String): Long? = when (val value = get(field)) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    private fun vehicleDocument(ownerUid: String, vehicleId: String) = firestore
        .collection(VehiclePhotoContractSpec.USERS_COLLECTION).document(ownerUid)
        .collection(VehiclePhotoContractSpec.VEHICLES_COLLECTION).document(vehicleId)

    private fun photoDocument(ownerUid: String, vehicleId: String, photoId: String) =
        vehicleDocument(ownerUid, vehicleId)
            .collection(VehiclePhotoContractSpec.PHOTOS_COLLECTION).document(photoId)

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private sealed interface TransactionResult {
        data object Applied : TransactionResult
        data object AlreadyApplied : TransactionResult
        data object RemoteWon : TransactionResult
        data class PrimaryRemoteWon(
            val primaryPhotoId: String?,
            val revision: Long,
            val operationId: String?
        ) : TransactionResult
        data object DeleteWins : TransactionResult
        data object VehicleNotFound : TransactionResult
        data object VehicleDeleted : TransactionResult
        data object PhotoMetadataMissing : TransactionResult
        data object UnsupportedSchema : TransactionResult
        data object InvalidRemoteData : TransactionResult
    }

    private companion object {
        const val PAGE_SIZE = 200L
        const val FIELD_PRIMARY_PHOTO_ID = "primaryPhotoId"
        const val FIELD_PRIMARY_PHOTO_REVISION = "primaryPhotoRevision"
        const val FIELD_PRIMARY_PHOTO_OPERATION_ID = "primaryPhotoOperationId"
        const val FIELD_UPDATED_AT = "updatedAt"
    }
}
