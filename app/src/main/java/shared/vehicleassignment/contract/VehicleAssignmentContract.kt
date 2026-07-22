package shared.vehicleassignment.contract

/**
 * Firebase/Android bağımlılığı olmayan Sprint 6A kişi-araç ilişki sözleşmesi.
 * Bu dosya Bellek projesindeki eş dosyayla byte-for-byte aynı tutulur.
 */
object VehicleAssignmentContractSpec {
    const val USERS_COLLECTION = "users"
    const val VEHICLES_COLLECTION = "vehicles"
    const val PERSONS_COLLECTION = "persons"
    const val ASSIGNMENTS_COLLECTION = "vehicleAssignments"

    const val FIELD_VEHICLE_ID = "vehicleId"
    const val FIELD_PERSON_ID = "personId"
    const val FIELD_SCHEMA_VERSION = "schemaVersion"
    const val FIELD_REVISION = "revision"
    const val FIELD_OPERATION_ID = "operationId"
    const val FIELD_SOURCE = "source"
    const val FIELD_CLIENT_UPDATED_AT = "clientUpdatedAt"
    const val FIELD_SERVER_UPDATED_AT = "_serverUpdatedAt"
    const val FIELD_DELETED_AT = "deletedAt"

    const val CURRENT_SCHEMA_VERSION = 1
    const val MIN_SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_SUPPORTED_SCHEMA_VERSION = 1
    const val MAX_ID_LENGTH = 128
    const val MAX_OPERATION_ID_LENGTH = 160

    val assignmentFields: Set<String> = setOf(
        FIELD_VEHICLE_ID,
        FIELD_PERSON_ID,
        FIELD_SCHEMA_VERSION,
        FIELD_REVISION,
        FIELD_OPERATION_ID,
        FIELD_SOURCE,
        FIELD_CLIENT_UPDATED_AT,
        FIELD_SERVER_UPDATED_AT,
        FIELD_DELETED_AT
    )

    fun assignmentPath(ownerUid: String, vehicleId: String): String =
        "$USERS_COLLECTION/$ownerUid/$ASSIGNMENTS_COLLECTION/$vehicleId"
}

enum class VehicleAssignmentSource(val wireValue: String) {
    BELLEK("BELLEK"),
    TOPLU_TASIMA("TOPLU_TASIMA"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromWire(value: Any?): VehicleAssignmentSource =
            entries.firstOrNull { it.wireValue == value?.toString()?.uppercase() } ?: UNKNOWN
    }
}

enum class VehicleAssignmentState {
    ACTIVE,
    TOMBSTONE
}

enum class VehicleAssignmentValidationIssue {
    INVALID_DOCUMENT_ID,
    INVALID_VEHICLE_ID,
    INVALID_PERSON_ID,
    INVALID_SCHEMA_VERSION,
    UNSUPPORTED_SCHEMA_VERSION,
    INVALID_REVISION,
    INVALID_OPERATION_ID,
    INVALID_CLIENT_UPDATED_AT,
    INVALID_DELETED_AT
}

data class VehicleAssignmentContract(
    val vehicleId: String,
    val personId: String?,
    val schemaVersion: Int,
    val revision: Long,
    val operationId: String,
    val source: VehicleAssignmentSource,
    val clientUpdatedAt: Long,
    val deletedAt: Long?
) {
    val state: VehicleAssignmentState
        get() = if (deletedAt == null) VehicleAssignmentState.ACTIVE else VehicleAssignmentState.TOMBSTONE

    fun validate(documentId: String = vehicleId): Set<VehicleAssignmentValidationIssue> = buildSet {
        if (!OpaqueDocumentId.isValid(documentId) || documentId != vehicleId) {
            add(VehicleAssignmentValidationIssue.INVALID_DOCUMENT_ID)
        }
        if (!OpaqueDocumentId.isValid(vehicleId)) {
            add(VehicleAssignmentValidationIssue.INVALID_VEHICLE_ID)
        }
        if (state == VehicleAssignmentState.ACTIVE && !OpaqueDocumentId.isValid(personId)) {
            add(VehicleAssignmentValidationIssue.INVALID_PERSON_ID)
        }
        if (schemaVersion <= 0) {
            add(VehicleAssignmentValidationIssue.INVALID_SCHEMA_VERSION)
        } else if (schemaVersion !in
            VehicleAssignmentContractSpec.MIN_SUPPORTED_SCHEMA_VERSION..
                VehicleAssignmentContractSpec.MAX_SUPPORTED_SCHEMA_VERSION
        ) {
            add(VehicleAssignmentValidationIssue.UNSUPPORTED_SCHEMA_VERSION)
        }
        if (revision < 0L) add(VehicleAssignmentValidationIssue.INVALID_REVISION)
        if (!AssignmentOperationId.isValid(operationId)) {
            add(VehicleAssignmentValidationIssue.INVALID_OPERATION_ID)
        }
        if (clientUpdatedAt < 0L) {
            add(VehicleAssignmentValidationIssue.INVALID_CLIENT_UPDATED_AT)
        }
        if (deletedAt != null && deletedAt < 0L) {
            add(VehicleAssignmentValidationIssue.INVALID_DELETED_AT)
        }
    }

    /** `_serverUpdatedAt` istemci tarafından saat değeri olarak yazılmaz; adapter server timestamp ekler. */
    fun toFirestoreFields(): Map<String, Any?> = mapOf(
        VehicleAssignmentContractSpec.FIELD_VEHICLE_ID to vehicleId,
        VehicleAssignmentContractSpec.FIELD_PERSON_ID to personId,
        VehicleAssignmentContractSpec.FIELD_SCHEMA_VERSION to schemaVersion,
        VehicleAssignmentContractSpec.FIELD_REVISION to revision,
        VehicleAssignmentContractSpec.FIELD_OPERATION_ID to operationId,
        VehicleAssignmentContractSpec.FIELD_SOURCE to source.wireValue,
        VehicleAssignmentContractSpec.FIELD_CLIENT_UPDATED_AT to clientUpdatedAt,
        VehicleAssignmentContractSpec.FIELD_DELETED_AT to deletedAt
    )
}

object OpaqueDocumentId {
    fun isValid(value: String?): Boolean {
        if (value == null || value.isBlank() || value.length > VehicleAssignmentContractSpec.MAX_ID_LENGTH) {
            return false
        }
        return value == value.trim() && '/' !in value && value.none(Char::isISOControl)
    }
}

object AssignmentOperationId {
    fun isValid(value: String?): Boolean {
        if (value == null || value.isBlank() ||
            value.length > VehicleAssignmentContractSpec.MAX_OPERATION_ID_LENGTH
        ) {
            return false
        }
        return value == value.trim() && value.none { it.isISOControl() || it == '/' }
    }

    fun isDuplicate(first: String, second: String): Boolean = first == second
}

object AssignmentRevision {
    fun next(highestKnownRevision: Long): Long {
        require(highestKnownRevision >= 0L) { "Revision cannot be negative" }
        check(highestKnownRevision < Long.MAX_VALUE) { "Revision overflow" }
        return highestKnownRevision + 1L
    }
}

data class AssignmentServerTimestamp(
    val seconds: Long,
    val nanoseconds: Int
) : Comparable<AssignmentServerTimestamp> {
    init {
        require(nanoseconds in 0..999_999_999)
    }

    override fun compareTo(other: AssignmentServerTimestamp): Int =
        compareValuesBy(this, other, AssignmentServerTimestamp::seconds, AssignmentServerTimestamp::nanoseconds)

    companion object {
        fun fromEpochMillis(value: Long): AssignmentServerTimestamp = AssignmentServerTimestamp(
            seconds = Math.floorDiv(value, 1_000L),
            nanoseconds = (Math.floorMod(value, 1_000L) * 1_000_000L).toInt()
        )
    }
}

enum class VehicleAssignmentParseWarning {
    UNKNOWN_SOURCE,
    MISSING_SERVER_UPDATED_AT,
    UNKNOWN_FIELDS_PRESERVED
}

sealed interface VehicleAssignmentParseResult {
    data class Valid(
        val assignment: VehicleAssignmentContract,
        val serverUpdatedAt: AssignmentServerTimestamp?,
        val unknownFields: Map<String, Any?>,
        val warnings: Set<VehicleAssignmentParseWarning>
    ) : VehicleAssignmentParseResult

    data class Invalid(
        val issues: Set<VehicleAssignmentValidationIssue>,
        val unknownFields: Map<String, Any?>
    ) : VehicleAssignmentParseResult

    data class Unsupported(
        val schemaVersion: Int,
        val rawFields: Map<String, Any?>
    ) : VehicleAssignmentParseResult
}

object VehicleAssignmentParser {
    fun parse(documentId: String, fields: Map<String, Any?>): VehicleAssignmentParseResult {
        val unknown = fields.filterKeys { it !in VehicleAssignmentContractSpec.assignmentFields }
        val schemaVersion = fields.integralLong(VehicleAssignmentContractSpec.FIELD_SCHEMA_VERSION)?.toInt()
            ?: return VehicleAssignmentParseResult.Invalid(
                setOf(VehicleAssignmentValidationIssue.INVALID_SCHEMA_VERSION),
                unknown
            )
        if (schemaVersion !in
            VehicleAssignmentContractSpec.MIN_SUPPORTED_SCHEMA_VERSION..
                VehicleAssignmentContractSpec.MAX_SUPPORTED_SCHEMA_VERSION
        ) {
            return VehicleAssignmentParseResult.Unsupported(schemaVersion, fields.toMap())
        }

        val vehicleId = fields[VehicleAssignmentContractSpec.FIELD_VEHICLE_ID] as? String
        val personValue = fields[VehicleAssignmentContractSpec.FIELD_PERSON_ID]
        val personId = personValue as? String
        val revision = fields.integralLong(VehicleAssignmentContractSpec.FIELD_REVISION)
        val operationId = fields[VehicleAssignmentContractSpec.FIELD_OPERATION_ID] as? String
        val clientUpdatedAt = fields.integralLong(VehicleAssignmentContractSpec.FIELD_CLIENT_UPDATED_AT)
        val deletedValue = fields[VehicleAssignmentContractSpec.FIELD_DELETED_AT]
        val deletedAt = if (deletedValue == null) null else fields.integralLong(
            VehicleAssignmentContractSpec.FIELD_DELETED_AT
        )
        val structuralIssues = buildSet {
            if (vehicleId == null) add(VehicleAssignmentValidationIssue.INVALID_VEHICLE_ID)
            if (personValue != null && personId == null) {
                add(VehicleAssignmentValidationIssue.INVALID_PERSON_ID)
            }
            if (revision == null) add(VehicleAssignmentValidationIssue.INVALID_REVISION)
            if (operationId == null) add(VehicleAssignmentValidationIssue.INVALID_OPERATION_ID)
            if (clientUpdatedAt == null) {
                add(VehicleAssignmentValidationIssue.INVALID_CLIENT_UPDATED_AT)
            }
            if (deletedValue != null && deletedAt == null) {
                add(VehicleAssignmentValidationIssue.INVALID_DELETED_AT)
            }
        }
        if (structuralIssues.isNotEmpty()) {
            return VehicleAssignmentParseResult.Invalid(structuralIssues, unknown)
        }

        val source = VehicleAssignmentSource.fromWire(fields[VehicleAssignmentContractSpec.FIELD_SOURCE])
        val assignment = VehicleAssignmentContract(
            vehicleId = requireNotNull(vehicleId),
            personId = personId,
            schemaVersion = schemaVersion,
            revision = requireNotNull(revision),
            operationId = requireNotNull(operationId),
            source = source,
            clientUpdatedAt = requireNotNull(clientUpdatedAt),
            deletedAt = deletedAt
        )
        val issues = assignment.validate(documentId)
        if (issues.isNotEmpty()) return VehicleAssignmentParseResult.Invalid(issues, unknown)

        val serverTimestamp = parseServerTimestamp(
            fields[VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT]
        )
        val warnings = buildSet {
            if (source == VehicleAssignmentSource.UNKNOWN) add(VehicleAssignmentParseWarning.UNKNOWN_SOURCE)
            if (serverTimestamp == null) add(VehicleAssignmentParseWarning.MISSING_SERVER_UPDATED_AT)
            if (unknown.isNotEmpty()) add(VehicleAssignmentParseWarning.UNKNOWN_FIELDS_PRESERVED)
        }
        return VehicleAssignmentParseResult.Valid(assignment, serverTimestamp, unknown, warnings)
    }

    private fun parseServerTimestamp(value: Any?): AssignmentServerTimestamp? = when (value) {
        is Byte, is Short, is Int, is Long ->
            AssignmentServerTimestamp.fromEpochMillis((value as Number).toLong())
        is Map<*, *> -> {
            val seconds = (value["seconds"] as? Number)?.toLong()
                ?: (value["_seconds"] as? Number)?.toLong()
            val nanos = (value["nanoseconds"] as? Number)?.toInt()
                ?: (value["_nanoseconds"] as? Number)?.toInt()
            if (seconds == null || nanos == null || nanos !in 0..999_999_999) null
            else AssignmentServerTimestamp(seconds, nanos)
        }
        else -> null
    }

    private fun Map<String, Any?>.integralLong(field: String): Long? = when (val value = get(field)) {
        is Byte, is Short, is Int, is Long -> (value as Number).toLong()
        is Float -> value.takeIf { it.isFinite() && it % 1f == 0f }?.toLong()
        is Double -> value.takeIf { it.isFinite() && it % 1.0 == 0.0 }?.toLong()
        else -> null
    }
}

data class VersionedVehicleAssignment(
    val assignment: VehicleAssignmentContract,
    val serverUpdatedAt: AssignmentServerTimestamp?
)

enum class VehicleAssignmentResolutionReason {
    SAME_OPERATION,
    VEHICLE_TOMBSTONE,
    HIGHER_REVISION,
    NEWER_SERVER_TIMESTAMP,
    OPERATION_ID_TIE_BREAK
}

data class VehicleAssignmentResolution(
    val winner: VersionedVehicleAssignment?,
    val loser: VersionedVehicleAssignment?,
    val reason: VehicleAssignmentResolutionReason
)

object VehicleAssignmentConflictResolver {
    fun resolve(
        first: VersionedVehicleAssignment,
        second: VersionedVehicleAssignment,
        vehicleDeleted: Boolean = false
    ): VehicleAssignmentResolution {
        if (vehicleDeleted) {
            return VehicleAssignmentResolution(null, first, VehicleAssignmentResolutionReason.VEHICLE_TOMBSTONE)
        }
        if (AssignmentOperationId.isDuplicate(
                first.assignment.operationId,
                second.assignment.operationId
            )
        ) {
            val winner = if (compareVersions(first, second) >= 0) first else second
            return VehicleAssignmentResolution(winner, null, VehicleAssignmentResolutionReason.SAME_OPERATION)
        }
        val revisionComparison = first.assignment.revision.compareTo(second.assignment.revision)
        if (revisionComparison != 0) {
            return ordered(first, second, revisionComparison, VehicleAssignmentResolutionReason.HIGHER_REVISION)
        }
        val timestampComparison = compareNullable(first.serverUpdatedAt, second.serverUpdatedAt)
        if (timestampComparison != 0) {
            return ordered(
                first,
                second,
                timestampComparison,
                VehicleAssignmentResolutionReason.NEWER_SERVER_TIMESTAMP
            )
        }
        val operationComparison = first.assignment.operationId.compareTo(second.assignment.operationId)
        return ordered(
            first,
            second,
            operationComparison,
            VehicleAssignmentResolutionReason.OPERATION_ID_TIE_BREAK
        )
    }

    private fun compareVersions(
        first: VersionedVehicleAssignment,
        second: VersionedVehicleAssignment
    ): Int = first.assignment.revision.compareTo(second.assignment.revision)
        .takeIf { it != 0 }
        ?: compareNullable(first.serverUpdatedAt, second.serverUpdatedAt).takeIf { it != 0 }
        ?: first.assignment.operationId.compareTo(second.assignment.operationId)

    private fun compareNullable(
        first: AssignmentServerTimestamp?,
        second: AssignmentServerTimestamp?
    ): Int = when {
        first == null && second == null -> 0
        first == null -> -1
        second == null -> 1
        else -> first.compareTo(second)
    }

    private fun ordered(
        first: VersionedVehicleAssignment,
        second: VersionedVehicleAssignment,
        comparison: Int,
        reason: VehicleAssignmentResolutionReason
    ): VehicleAssignmentResolution = if (comparison >= 0) {
        VehicleAssignmentResolution(first, second, reason)
    } else {
        VehicleAssignmentResolution(second, first, reason)
    }
}

/** İki projedeki compatibility testlerinin kullandığı değişmez Firestore map fixture'ları. */
object VehicleAssignmentGoldenFixtures {
    val legacyVehicle: Map<String, Any?> = mapOf(
        "id" to "vehicle-1",
        "displayName" to "Eski araç",
        "assignedPersonId" to "person-old"
    )
    val currentVehicle: Map<String, Any?> = legacyVehicle + mapOf(
        "ownerUid" to "uid-1",
        "_serverUpdatedAt" to 1_700_000_000_000L,
        "futureVehicleField" to "preserve-me"
    )
    val assignmentV1: Map<String, Any?> = mapOf(
        "vehicleId" to "vehicle-1",
        "personId" to "person-1",
        "schemaVersion" to 1,
        "revision" to 3L,
        "operationId" to "operation-3",
        "source" to "BELLEK",
        "clientUpdatedAt" to 1_700_000_000_000L,
        "_serverUpdatedAt" to 1_700_000_001_000L,
        "deletedAt" to null
    )
    val unknownSource: Map<String, Any?> = assignmentV1 + ("source" to "FUTURE_APP")
    val unknownExtraField: Map<String, Any?> = assignmentV1 + ("futureField" to "preserve-me")
    val tombstone: Map<String, Any?> = assignmentV1 + mapOf(
        "revision" to 4L,
        "operationId" to "operation-4",
        "deletedAt" to 1_700_000_002_000L
    )
    val missingServerTimestamp: Map<String, Any?> =
        assignmentV1 - VehicleAssignmentContractSpec.FIELD_SERVER_UPDATED_AT
    val staleMirrorVehicle: Map<String, Any?> = currentVehicle + ("assignedPersonId" to "person-stale")
    val personIdMismatch: Map<String, Any?> = mapOf(
        "documentId" to "person-document",
        "id" to "person-payload"
    )
}
