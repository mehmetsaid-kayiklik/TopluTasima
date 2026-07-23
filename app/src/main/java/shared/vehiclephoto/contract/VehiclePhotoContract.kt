package shared.vehiclephoto.contract

/** Android ve Firebase SDK bağımlılığı olmayan Sprint 6B araç fotoğrafı sözleşmesi. */
object VehiclePhotoContractSpec {
    const val USERS_COLLECTION = "users"
    const val VEHICLES_COLLECTION = "vehicles"
    const val PHOTOS_COLLECTION = "photos"
    const val CURRENT_SCHEMA_VERSION = 1
    const val MAX_LONG_EDGE_PX = 2_048
    const val MAX_PREPARED_BYTES = 5L * 1_024L * 1_024L
    const val OUTPUT_MIME_TYPE = "image/jpeg"

    const val FIELD_OWNER_UID = "ownerUid"
    const val FIELD_PHOTO_ID = "photoId"
    const val FIELD_VEHICLE_ID = "vehicleId"
    const val FIELD_STORAGE_PATH = "storagePath"
    const val FIELD_CONTENT_HASH = "contentHash"
    const val FIELD_MIME_TYPE = "mimeType"
    const val FIELD_WIDTH = "width"
    const val FIELD_HEIGHT = "height"
    const val FIELD_SIZE_BYTES = "sizeBytes"
    const val FIELD_SORT_ORDER = "sortOrder"
    const val FIELD_IS_PRIMARY = "isPrimary"
    const val FIELD_SCHEMA_VERSION = "schemaVersion"
    const val FIELD_REVISION = "revision"
    const val FIELD_OPERATION_ID = "operationId"
    const val FIELD_SOURCE = "source"
    const val FIELD_CLIENT_UPDATED_AT = "clientUpdatedAt"
    const val FIELD_SERVER_UPDATED_AT = "_serverUpdatedAt"
    const val FIELD_DELETED_AT = "deletedAt"

    val REQUIRED_FIELDS = setOf(
        FIELD_OWNER_UID,
        FIELD_PHOTO_ID,
        FIELD_VEHICLE_ID,
        FIELD_STORAGE_PATH,
        FIELD_CONTENT_HASH,
        FIELD_MIME_TYPE,
        FIELD_WIDTH,
        FIELD_HEIGHT,
        FIELD_SIZE_BYTES,
        FIELD_SORT_ORDER,
        FIELD_IS_PRIMARY,
        FIELD_SCHEMA_VERSION,
        FIELD_REVISION,
        FIELD_OPERATION_ID,
        FIELD_SOURCE,
        FIELD_CLIENT_UPDATED_AT,
        FIELD_SERVER_UPDATED_AT,
        FIELD_DELETED_AT
    )

    fun firestorePath(ownerUid: String, vehicleId: String, photoId: String): String =
        "$USERS_COLLECTION/$ownerUid/$VEHICLES_COLLECTION/$vehicleId/$PHOTOS_COLLECTION/$photoId"

    fun storagePath(ownerUid: String, vehicleId: String, photoId: String): String =
        "$USERS_COLLECTION/$ownerUid/$VEHICLES_COLLECTION/$vehicleId/$PHOTOS_COLLECTION/$photoId.jpg"
}

enum class VehiclePhotoSource(val wireValue: String) {
    TOPLU_TASIMA("TOPLU_TASIMA"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromWire(value: String?): VehiclePhotoSource =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

enum class VehiclePhotoState {
    ACTIVE,
    TOMBSTONE
}

data class VehiclePhotoContract(
    val ownerUid: String,
    val photoId: String,
    val vehicleId: String,
    val storagePath: String?,
    val contentHash: String?,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val sortOrder: Int,
    val isPrimary: Boolean,
    val schemaVersion: Int,
    val revision: Long,
    val operationId: String,
    val source: VehiclePhotoSource,
    val clientUpdatedAt: Long,
    val deletedAt: Long?
) {
    val state: VehiclePhotoState
        get() = if (deletedAt == null) VehiclePhotoState.ACTIVE else VehiclePhotoState.TOMBSTONE

    fun toFirestoreFields(): Map<String, Any?> = linkedMapOf(
        VehiclePhotoContractSpec.FIELD_OWNER_UID to ownerUid,
        VehiclePhotoContractSpec.FIELD_PHOTO_ID to photoId,
        VehiclePhotoContractSpec.FIELD_VEHICLE_ID to vehicleId,
        VehiclePhotoContractSpec.FIELD_STORAGE_PATH to storagePath,
        VehiclePhotoContractSpec.FIELD_CONTENT_HASH to contentHash,
        VehiclePhotoContractSpec.FIELD_MIME_TYPE to mimeType,
        VehiclePhotoContractSpec.FIELD_WIDTH to width,
        VehiclePhotoContractSpec.FIELD_HEIGHT to height,
        VehiclePhotoContractSpec.FIELD_SIZE_BYTES to sizeBytes,
        VehiclePhotoContractSpec.FIELD_SORT_ORDER to sortOrder,
        VehiclePhotoContractSpec.FIELD_IS_PRIMARY to isPrimary,
        VehiclePhotoContractSpec.FIELD_SCHEMA_VERSION to schemaVersion,
        VehiclePhotoContractSpec.FIELD_REVISION to revision,
        VehiclePhotoContractSpec.FIELD_OPERATION_ID to operationId,
        VehiclePhotoContractSpec.FIELD_SOURCE to source.wireValue,
        VehiclePhotoContractSpec.FIELD_CLIENT_UPDATED_AT to clientUpdatedAt,
        VehiclePhotoContractSpec.FIELD_DELETED_AT to deletedAt
    )

    fun validate(documentId: String = photoId): List<VehiclePhotoValidationIssue> = buildList {
        if (!VehiclePhotoOpaqueId.isValid(ownerUid)) add(VehiclePhotoValidationIssue.INVALID_OWNER_ID)
        if (!VehiclePhotoOpaqueId.isValid(photoId) || documentId != photoId) {
            add(VehiclePhotoValidationIssue.PHOTO_ID_DOCUMENT_ID_MISMATCH)
        }
        if (!VehiclePhotoOpaqueId.isValid(vehicleId)) add(VehiclePhotoValidationIssue.INVALID_VEHICLE_ID)
        if (schemaVersion != VehiclePhotoContractSpec.CURRENT_SCHEMA_VERSION) {
            add(VehiclePhotoValidationIssue.UNSUPPORTED_SCHEMA)
        }
        if (revision < 0L) add(VehiclePhotoValidationIssue.NEGATIVE_REVISION)
        if (!VehiclePhotoOperationId.isValid(operationId)) {
            add(VehiclePhotoValidationIssue.INVALID_OPERATION_ID)
        }
        if (clientUpdatedAt < 0L) add(VehiclePhotoValidationIssue.INVALID_CLIENT_TIMESTAMP)
        if (sortOrder < 0) add(VehiclePhotoValidationIssue.INVALID_SORT_ORDER)
        if (deletedAt?.let { it < 0L } == true) add(VehiclePhotoValidationIssue.INVALID_DELETED_AT)
        if (state == VehiclePhotoState.ACTIVE) {
            val expectedPath = VehiclePhotoContractSpec.storagePath(ownerUid, vehicleId, photoId)
            if (storagePath != expectedPath) add(VehiclePhotoValidationIssue.INVALID_STORAGE_PATH)
            if (contentHash?.matches(Regex("^[a-f0-9]{64}$")) != true) {
                add(VehiclePhotoValidationIssue.INVALID_CONTENT_HASH)
            }
            if (mimeType != VehiclePhotoContractSpec.OUTPUT_MIME_TYPE) {
                add(VehiclePhotoValidationIssue.UNSUPPORTED_MIME_TYPE)
            }
            if (width == null || height == null || width !in 1..VehiclePhotoContractSpec.MAX_LONG_EDGE_PX ||
                height !in 1..VehiclePhotoContractSpec.MAX_LONG_EDGE_PX
            ) {
                add(VehiclePhotoValidationIssue.INVALID_DIMENSIONS)
            }
            if (sizeBytes == null || sizeBytes !in 1..VehiclePhotoContractSpec.MAX_PREPARED_BYTES) {
                add(VehiclePhotoValidationIssue.INVALID_SIZE)
            }
        }
    }.distinct()
}

enum class VehiclePhotoValidationIssue {
    INVALID_OWNER_ID,
    PHOTO_ID_DOCUMENT_ID_MISMATCH,
    INVALID_VEHICLE_ID,
    UNSUPPORTED_SCHEMA,
    NEGATIVE_REVISION,
    INVALID_OPERATION_ID,
    INVALID_CLIENT_TIMESTAMP,
    INVALID_SORT_ORDER,
    INVALID_DELETED_AT,
    INVALID_STORAGE_PATH,
    INVALID_CONTENT_HASH,
    UNSUPPORTED_MIME_TYPE,
    INVALID_DIMENSIONS,
    INVALID_SIZE
}

object VehiclePhotoOpaqueId {
    fun isValid(value: String?): Boolean =
        value != null && value.isNotBlank() && value.length <= 128 && '/' !in value && '\\' !in value
}

object VehiclePhotoOperationId {
    fun isValid(value: String?): Boolean =
        value != null && value.isNotBlank() && value.length <= 160 && '/' !in value && '\\' !in value
}

object VehiclePhotoRevision {
    fun next(highestKnownRevision: Long): Long {
        require(highestKnownRevision >= 0L) { "Photo revision cannot be negative" }
        check(highestKnownRevision < Long.MAX_VALUE) { "Photo revision exhausted" }
        return highestKnownRevision + 1L
    }
}

data class VehiclePhotoServerTimestamp(val seconds: Long, val nanoseconds: Int) :
    Comparable<VehiclePhotoServerTimestamp> {
    init {
        require(nanoseconds in 0..999_999_999)
    }

    override fun compareTo(other: VehiclePhotoServerTimestamp): Int =
        compareValuesBy(this, other, VehiclePhotoServerTimestamp::seconds, VehiclePhotoServerTimestamp::nanoseconds)
}

data class VersionedVehiclePhoto(
    val photo: VehiclePhotoContract,
    val serverUpdatedAt: VehiclePhotoServerTimestamp?
)

sealed interface VehiclePhotoParseResult {
    data class Valid(
        val value: VersionedVehiclePhoto,
        val unknownFields: Map<String, Any?>
    ) : VehiclePhotoParseResult

    data class Unsupported(val schemaVersion: Int, val rawFields: Map<String, Any?>) :
        VehiclePhotoParseResult

    data class Invalid(val issues: List<VehiclePhotoValidationIssue>) : VehiclePhotoParseResult
}

object VehiclePhotoParser {
    fun parse(
        documentId: String,
        fields: Map<String, Any?>,
        expectedOwnerUid: String? = null
    ): VehiclePhotoParseResult {
        val schemaVersion = fields.int(VehiclePhotoContractSpec.FIELD_SCHEMA_VERSION)
            ?: return VehiclePhotoParseResult.Invalid(listOf(VehiclePhotoValidationIssue.UNSUPPORTED_SCHEMA))
        if (schemaVersion != VehiclePhotoContractSpec.CURRENT_SCHEMA_VERSION) {
            return VehiclePhotoParseResult.Unsupported(schemaVersion, fields.toMap())
        }
        val ownerUid = fields.string(VehiclePhotoContractSpec.FIELD_OWNER_UID).orEmpty()
        val contract = VehiclePhotoContract(
            ownerUid = ownerUid,
            photoId = fields.string(VehiclePhotoContractSpec.FIELD_PHOTO_ID).orEmpty(),
            vehicleId = fields.string(VehiclePhotoContractSpec.FIELD_VEHICLE_ID).orEmpty(),
            storagePath = fields.string(VehiclePhotoContractSpec.FIELD_STORAGE_PATH),
            contentHash = fields.string(VehiclePhotoContractSpec.FIELD_CONTENT_HASH),
            mimeType = fields.string(VehiclePhotoContractSpec.FIELD_MIME_TYPE),
            width = fields.int(VehiclePhotoContractSpec.FIELD_WIDTH),
            height = fields.int(VehiclePhotoContractSpec.FIELD_HEIGHT),
            sizeBytes = fields.long(VehiclePhotoContractSpec.FIELD_SIZE_BYTES),
            sortOrder = fields.int(VehiclePhotoContractSpec.FIELD_SORT_ORDER) ?: -1,
            isPrimary = fields[VehiclePhotoContractSpec.FIELD_IS_PRIMARY] as? Boolean ?: false,
            schemaVersion = schemaVersion,
            revision = fields.long(VehiclePhotoContractSpec.FIELD_REVISION) ?: -1L,
            operationId = fields.string(VehiclePhotoContractSpec.FIELD_OPERATION_ID).orEmpty(),
            source = VehiclePhotoSource.fromWire(fields.string(VehiclePhotoContractSpec.FIELD_SOURCE)),
            clientUpdatedAt = fields.long(VehiclePhotoContractSpec.FIELD_CLIENT_UPDATED_AT) ?: -1L,
            deletedAt = fields.long(VehiclePhotoContractSpec.FIELD_DELETED_AT)
        )
        val issues = contract.validate(documentId).toMutableList()
        if (expectedOwnerUid != null && ownerUid != expectedOwnerUid) {
            issues += VehiclePhotoValidationIssue.INVALID_OWNER_ID
        }
        if (issues.isNotEmpty()) return VehiclePhotoParseResult.Invalid(issues.distinct())
        return VehiclePhotoParseResult.Valid(
            value = VersionedVehiclePhoto(
                photo = contract,
                serverUpdatedAt = fields.serverTimestamp(VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT)
            ),
            unknownFields = fields.filterKeys { it !in VehiclePhotoContractSpec.REQUIRED_FIELDS }
        )
    }
}

enum class VehiclePhotoWinnerReason {
    VEHICLE_TOMBSTONE,
    PHOTO_TOMBSTONE,
    HIGHER_REVISION,
    SERVER_TIMESTAMP,
    OPERATION_ID,
    IDENTICAL
}

data class VehiclePhotoResolution(
    val winner: VersionedVehiclePhoto?,
    val reason: VehiclePhotoWinnerReason,
    val conflict: Boolean
)

object VehiclePhotoConflictResolver {
    fun resolve(
        local: VersionedVehiclePhoto,
        remote: VersionedVehiclePhoto,
        vehicleDeleted: Boolean
    ): VehiclePhotoResolution {
        if (vehicleDeleted) return VehiclePhotoResolution(null, VehiclePhotoWinnerReason.VEHICLE_TOMBSTONE, true)
        if (local.photo.operationId == remote.photo.operationId) {
            return VehiclePhotoResolution(remote, VehiclePhotoWinnerReason.IDENTICAL, false)
        }
        if (local.photo.deletedAt != null || remote.photo.deletedAt != null) {
            val winner = when {
                local.photo.deletedAt != null && remote.photo.deletedAt == null -> local
                remote.photo.deletedAt != null && local.photo.deletedAt == null -> remote
                else -> compareVersion(local, remote)
            }
            return VehiclePhotoResolution(winner, VehiclePhotoWinnerReason.PHOTO_TOMBSTONE, true)
        }
        val winner = compareVersion(local, remote)
        val reason = when {
            local.photo.revision != remote.photo.revision -> VehiclePhotoWinnerReason.HIGHER_REVISION
            local.serverUpdatedAt != remote.serverUpdatedAt -> VehiclePhotoWinnerReason.SERVER_TIMESTAMP
            else -> VehiclePhotoWinnerReason.OPERATION_ID
        }
        return VehiclePhotoResolution(winner, reason, true)
    }

    private fun compareVersion(
        first: VersionedVehiclePhoto,
        second: VersionedVehiclePhoto
    ): VersionedVehiclePhoto = when {
        first.photo.revision != second.photo.revision ->
            if (first.photo.revision > second.photo.revision) first else second
        first.serverUpdatedAt != second.serverUpdatedAt ->
            if (compareValues(first.serverUpdatedAt, second.serverUpdatedAt) >= 0) first else second
        first.photo.operationId >= second.photo.operationId -> first
        else -> second
    }
}

object VehiclePhotoGoldenFixtures {
    val active: Map<String, Any?> = linkedMapOf(
        VehiclePhotoContractSpec.FIELD_OWNER_UID to "owner-fixture",
        VehiclePhotoContractSpec.FIELD_PHOTO_ID to "photo-fixture",
        VehiclePhotoContractSpec.FIELD_VEHICLE_ID to "vehicle-fixture",
        VehiclePhotoContractSpec.FIELD_STORAGE_PATH to
            "users/owner-fixture/vehicles/vehicle-fixture/photos/photo-fixture.jpg",
        VehiclePhotoContractSpec.FIELD_CONTENT_HASH to "a".repeat(64),
        VehiclePhotoContractSpec.FIELD_MIME_TYPE to "image/jpeg",
        VehiclePhotoContractSpec.FIELD_WIDTH to 1_600,
        VehiclePhotoContractSpec.FIELD_HEIGHT to 900,
        VehiclePhotoContractSpec.FIELD_SIZE_BYTES to 456_789L,
        VehiclePhotoContractSpec.FIELD_SORT_ORDER to 0,
        VehiclePhotoContractSpec.FIELD_IS_PRIMARY to true,
        VehiclePhotoContractSpec.FIELD_SCHEMA_VERSION to 1,
        VehiclePhotoContractSpec.FIELD_REVISION to 1L,
        VehiclePhotoContractSpec.FIELD_OPERATION_ID to "operation-fixture",
        VehiclePhotoContractSpec.FIELD_SOURCE to "TOPLU_TASIMA",
        VehiclePhotoContractSpec.FIELD_CLIENT_UPDATED_AT to 1_700_000_000_000L,
        VehiclePhotoContractSpec.FIELD_SERVER_UPDATED_AT to mapOf("seconds" to 1_700_000_000L, "nanoseconds" to 0),
        VehiclePhotoContractSpec.FIELD_DELETED_AT to null,
        "futureField" to "preserve-on-merge"
    )

    val tombstone: Map<String, Any?> = active + mapOf(
        VehiclePhotoContractSpec.FIELD_IS_PRIMARY to false,
        VehiclePhotoContractSpec.FIELD_REVISION to 2L,
        VehiclePhotoContractSpec.FIELD_OPERATION_ID to "delete-operation-fixture",
        VehiclePhotoContractSpec.FIELD_DELETED_AT to 1_700_000_100_000L
    )
}

private fun Map<String, Any?>.string(field: String): String? = (this[field] as? String)?.takeIf { it.isNotBlank() }
private fun Map<String, Any?>.long(field: String): Long? = (this[field] as? Number)?.toLong()
private fun Map<String, Any?>.int(field: String): Int? = (this[field] as? Number)?.toInt()
private fun Map<String, Any?>.serverTimestamp(field: String): VehiclePhotoServerTimestamp? {
    val map = this[field] as? Map<*, *> ?: return null
    val seconds = (map["seconds"] as? Number)?.toLong() ?: return null
    val nanoseconds = (map["nanoseconds"] as? Number)?.toInt() ?: return null
    return runCatching { VehiclePhotoServerTimestamp(seconds, nanoseconds) }.getOrNull()
}
