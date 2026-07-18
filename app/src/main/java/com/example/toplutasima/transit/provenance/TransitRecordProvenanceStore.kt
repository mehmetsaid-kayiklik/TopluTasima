package com.example.toplutasima.transit.provenance

import com.example.toplutasima.usecase.TransitTimeUtils
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

data class TransitProvenanceRecordKey(
    val userId: String,
    val localRecordId: String
)

data class TransitStoredFieldProvenance(
    val provenance: TransitFieldProvenance,
    val valueFingerprint: String
)

data class TransitRecordProvenanceSnapshot(
    val userId: String,
    val localRecordId: String,
    val fields: Map<String, TransitStoredFieldProvenance>,
    val updatedAtEpochMillis: Long
)

/**
 * Explicitly injected, process-lifetime provenance ledger.
 *
 * The class has no Android dependency, static instance or implicit current-user lookup. Its owner
 * decides the lifetime (normally application scope) and always supplies the UID. A value
 * fingerprint prevents metadata learned for an older value from being attached to a later Room
 * or remote update of the same field.
 */
class TransitRecordProvenanceStore(
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val maximumRecordsPerUser: Int = DEFAULT_MAX_RECORDS_PER_USER
) {
    init {
        require(maximumRecordsPerUser > 0) { "maximumRecordsPerUser must be positive" }
    }

    private val lock = Any()
    private val _snapshots = MutableStateFlow<Map<TransitProvenanceRecordKey, TransitRecordProvenanceSnapshot>>(
        emptyMap()
    )

    val snapshots: StateFlow<Map<TransitProvenanceRecordKey, TransitRecordProvenanceSnapshot>> =
        _snapshots.asStateFlow()

    fun observeRecord(userId: String, localRecordId: String): Flow<TransitRecordProvenanceSnapshot?> {
        val key = validKeyOrNull(userId, localRecordId)
        return snapshots.map { states -> key?.let(states::get) }.distinctUntilChanged()
    }

    fun snapshotForRecord(userId: String, localRecordId: String): TransitRecordProvenanceSnapshot? {
        val key = validKeyOrNull(userId, localRecordId) ?: return null
        return snapshots.value[key]
    }

    fun putKnownFields(
        userId: String,
        localRecordId: String,
        currentValues: Map<String, *>,
        provenanceByField: Map<String, TransitFieldProvenance>
    ) {
        if (provenanceByField.isEmpty()) return
        update(userId, localRecordId) { previous ->
            val nextFields = previous?.fields.orEmpty().toMutableMap()
            provenanceByField.forEach { (fieldId, provenance) ->
                if (fieldId.isBlank() || !currentValues.containsKey(fieldId)) return@forEach
                val next = TransitStoredFieldProvenance(
                    provenance = provenance.copy(fieldId = fieldId),
                    valueFingerprint = valueFingerprint(fieldId, currentValues[fieldId])
                )
                val existing = nextFields[fieldId]
                if (!existing.matchesIgnoringFreshness(next)) nextFields[fieldId] = next
            }
            nextFields
        }
    }

    /** Marks only explicitly changed fields as manual and leaves every other field untouched. */
    fun markManualFields(
        userId: String,
        localRecordId: String,
        currentValues: Map<String, *>,
        changedFieldIds: Set<String>,
        editedAtEpochMillis: Long = nowEpochMillis()
    ) {
        val validChangedFields = changedFieldIds.filterTo(linkedSetOf()) {
            it.isNotBlank() && currentValues.containsKey(it)
        }
        if (validChangedFields.isEmpty()) return

        update(userId, localRecordId) { previous ->
            val nextFields = previous?.fields.orEmpty().toMutableMap()
            validChangedFields.forEach { fieldId ->
                val fingerprint = valueFingerprint(fieldId, currentValues[fieldId])
                val existing = nextFields[fieldId]
                if (
                    existing?.provenance?.source == TransitFieldSource.MANUAL &&
                    existing.valueFingerprint == fingerprint
                ) {
                    return@forEach
                }
                nextFields[fieldId] = TransitStoredFieldProvenance(
                    provenance = TransitFieldProvenance(
                        fieldId = fieldId,
                        source = TransitFieldSource.MANUAL,
                        lastUpdatedAtEpochMillis = editedAtEpochMillis,
                        freshness = TransitFieldFreshness.FRESH
                    ),
                    valueFingerprint = fingerprint
                )
            }
            nextFields
        }
    }

    fun matchingProvenance(
        userId: String,
        localRecordId: String,
        currentValues: Map<String, *>
    ): Map<String, TransitFieldProvenance> {
        val snapshot = snapshotForRecord(userId, localRecordId) ?: return emptyMap()
        return snapshot.fields.mapNotNull { (fieldId, stored) ->
            if (
                currentValues.containsKey(fieldId) &&
                stored.valueFingerprint == valueFingerprint(fieldId, currentValues[fieldId])
            ) {
                fieldId to stored.provenance
            } else {
                null
            }
        }.toMap()
    }

    fun removeRecord(userId: String, localRecordId: String) {
        val key = validKeyOrNull(userId, localRecordId) ?: return
        synchronized(lock) {
            if (key !in _snapshots.value) return
            _snapshots.value = _snapshots.value.toMutableMap().apply { remove(key) }
        }
    }

    fun clearUser(userId: String) {
        if (userId.isBlank()) return
        synchronized(lock) {
            _snapshots.value = _snapshots.value.filterKeys { it.userId != userId }
        }
    }

    private fun update(
        userId: String,
        localRecordId: String,
        transform: (TransitRecordProvenanceSnapshot?) -> Map<String, TransitStoredFieldProvenance>
    ) {
        val key = validKeyOrNull(userId, localRecordId) ?: return
        synchronized(lock) {
            val current = _snapshots.value
            val previous = current[key]
            val nextFields = transform(previous)
            if (nextFields == previous?.fields) return
            val next = TransitRecordProvenanceSnapshot(
                userId = userId,
                localRecordId = localRecordId,
                fields = nextFields.toMap(),
                updatedAtEpochMillis = nowEpochMillis()
            )
            _snapshots.value = prune(current.toMutableMap().apply { put(key, next) })
        }
    }

    private fun prune(
        values: Map<TransitProvenanceRecordKey, TransitRecordProvenanceSnapshot>
    ): Map<TransitProvenanceRecordKey, TransitRecordProvenanceSnapshot> = values.values
        .groupBy { it.userId }
        .flatMap { (_, userSnapshots) ->
            userSnapshots.sortedByDescending { it.updatedAtEpochMillis }.take(maximumRecordsPerUser)
        }
        .associateBy { TransitProvenanceRecordKey(it.userId, it.localRecordId) }

    private fun validKeyOrNull(userId: String, localRecordId: String): TransitProvenanceRecordKey? =
        if (userId.isBlank() || localRecordId.isBlank()) null
        else TransitProvenanceRecordKey(userId, localRecordId)

    companion object {
        private const val DEFAULT_MAX_RECORDS_PER_USER = 1_000
        private val TIME_FIELD_IDS = setOf(
            "planlananBinis",
            "planlananInis",
            "gercekBinis",
            "gercekInis"
        )

        internal fun valueFingerprint(fieldId: String, value: Any?): String {
            val canonical = when {
                value == null -> "<null>"
                fieldId in TIME_FIELD_IDS -> TransitTimeUtils.stripSeconds(value.toString()).trim()
                value is Number -> value.toString().toBigDecimalOrNull()
                    ?.stripTrailingZeros()
                    ?.toPlainString()
                    ?: value.toString()
                value is Boolean -> value.toString()
                else -> value.toString().trim()
            }
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
        }

        private fun TransitStoredFieldProvenance?.matchesIgnoringFreshness(
            other: TransitStoredFieldProvenance
        ): Boolean = this != null &&
            valueFingerprint == other.valueFingerprint &&
            provenance.copy(freshness = TransitFieldFreshness.UNKNOWN) ==
            other.provenance.copy(freshness = TransitFieldFreshness.UNKNOWN)
    }
}
