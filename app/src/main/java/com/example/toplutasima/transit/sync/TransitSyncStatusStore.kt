package com.example.toplutasima.transit.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.openEncryptedStorageWithRecovery
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Durable, UID-scoped sync receipt ledger for transit records.
 *
 * It intentionally does not infer cloud success from a successful Room write. Queue and worker
 * callbacks advance the same record through pending, syncing and terminal states.
 */
class TransitSyncStatusStore internal constructor(
    private val readRaw: () -> String?,
    private val writeRaw: (String) -> Unit,
    initialUserId: String?,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _activeUserId = MutableStateFlow(initialUserId?.takeIf { it.isNotBlank() })
    private val _states = MutableStateFlow(loadPersisted())

    val states: StateFlow<Map<String, TransitSyncState>> = _states.asStateFlow()

    fun onUserChanged(userId: String?) {
        _activeUserId.value = userId?.takeIf { it.isNotBlank() }
    }

    fun observeRecord(recordId: String): Flow<TransitSyncState?> =
        combine(_activeUserId, _states) { userId, states ->
            if (userId == null || recordId.isBlank()) null else states[key(userId, recordId)]
        }.distinctUntilChanged()

    fun observeRecords(recordIds: Collection<String>): Flow<List<TransitSyncState>> {
        val stableIds = recordIds.filter { it.isNotBlank() }.distinct().toSet()
        return combine(_activeUserId, _states) { userId, states ->
            if (userId == null || stableIds.isEmpty()) {
                emptyList()
            } else {
                stableIds.mapNotNull { states[key(userId, it)] }
            }
        }.distinctUntilChanged()
    }

    fun observeDeletionReceipts(): Flow<List<TransitSyncState>> =
        combine(_activeUserId, _states) { userId, states ->
            if (userId == null) {
                emptyList()
            } else {
                states.values
                    .asSequence()
                    .filter { it.userId == userId && it.operation == TransitSyncOperation.DELETE }
                    .sortedByDescending { it.updatedAtEpochMillis }
                    .toList()
            }
        }.distinctUntilChanged()

    internal fun snapshotForUser(userId: String): List<TransitSyncState> =
        _states.value.values.filter { it.userId == userId }

    /** Privacy-scoped read model for portable transit export and audit UI. */
    fun stateForRecord(userId: String, recordId: String): TransitSyncState? =
        if (userId.isBlank() || recordId.isBlank()) null else _states.value[key(userId, recordId)]

    /** Returns only stable local IDs; queue payloads and Firestore document IDs never leave here. */
    fun tombstonedRecordIds(userId: String): Set<String> =
        if (userId.isBlank()) emptySet() else deletionTombstonesForUser(userId)
            .mapTo(linkedSetOf()) { it.recordId }

    internal fun deletionTombstonesForUser(userId: String): List<TransitSyncState> =
        _states.value.values.filter {
            it.userId == userId &&
                it.operation == TransitSyncOperation.DELETE &&
                it.deleteMetadata != null
        }

    fun isDeletionTombstoned(
        userId: String,
        recordId: String? = null,
        firestoreDocId: String? = null
    ): Boolean {
        if (userId.isBlank()) return false
        val stableRecordId = recordId?.takeIf { it.isNotBlank() }
        val stableDocumentId = firestoreDocId?.takeIf { it.isNotBlank() }
        if (stableRecordId == null && stableDocumentId == null) return false
        return deletionTombstonesForUser(userId).any { state ->
            (stableRecordId != null && state.recordId == stableRecordId) ||
                (stableDocumentId != null &&
                    state.deleteMetadata?.firestoreDocId?.takeIf { it.isNotBlank() } == stableDocumentId)
        }
    }

    fun markLocalSaving(userId: String, recordId: String) =
        transition(userId, recordId, TransitSyncPhase.LOCAL_SAVING)

    fun markLocalSafe(userId: String, recordId: String) =
        transition(userId, recordId, TransitSyncPhase.LOCAL_SAFE)

    fun markPending(userId: String, recordId: String, queueActionId: String? = null) =
        transition(
            userId = userId,
            recordId = recordId,
            phase = TransitSyncPhase.PENDING,
            queueActionId = queueActionId
        )

    fun markSyncing(userId: String, recordId: String, queueActionId: String? = null) =
        transition(
            userId = userId,
            recordId = recordId,
            phase = TransitSyncPhase.SYNCING,
            queueActionId = queueActionId,
            incrementAttempt = true
        )

    fun markSynced(userId: String, recordId: String, queueActionId: String? = null) =
        transition(
            userId,
            recordId,
            TransitSyncPhase.SYNCED,
            queueActionId = queueActionId,
            requireQueueActionMatch = queueActionId != null
        )

    fun markTemporaryError(
        userId: String,
        recordId: String,
        detail: String? = null,
        queueActionId: String? = null
    ) = transition(
        userId,
        recordId,
        TransitSyncPhase.TEMPORARY_ERROR,
        queueActionId = queueActionId,
        detail = detail,
        requireQueueActionMatch = queueActionId != null
    )

    fun markPermanentError(userId: String, recordId: String, detail: String? = null) =
        transition(userId, recordId, TransitSyncPhase.PERMANENT_ERROR, detail = detail)

    fun markLocalDeleted(
        userId: String,
        recordId: String,
        firestoreDocId: String? = null
    ) = transitionDelete(
        userId = userId,
        recordId = recordId,
        phase = TransitSyncPhase.LOCAL_DELETED,
        firestoreDocId = firestoreDocId,
        resetDelete = true
    )

    fun markDeletePending(
        userId: String,
        recordId: String,
        firestoreDocId: String? = null,
        queueActionId: String? = null
    ) = transitionDelete(
        userId = userId,
        recordId = recordId,
        phase = TransitSyncPhase.DELETE_PENDING,
        firestoreDocId = firestoreDocId,
        queueActionId = queueActionId,
        disposition = TransitDeleteDisposition.PENDING_REMOTE
    )

    fun markDeleting(
        userId: String,
        recordId: String,
        queueActionId: String? = null
    ) = transitionDelete(
        userId = userId,
        recordId = recordId,
        phase = TransitSyncPhase.DELETING,
        queueActionId = queueActionId,
        incrementAttempt = true,
        requireQueueActionMatch = queueActionId != null
    )

    fun markDeleted(
        userId: String,
        recordId: String,
        queueActionId: String? = null
    ) = transitionDelete(
        userId = userId,
        recordId = recordId,
        phase = TransitSyncPhase.DELETED,
        queueActionId = queueActionId,
        disposition = TransitDeleteDisposition.REMOTE_DELETED,
        requireQueueActionMatch = queueActionId != null
    )

    fun markDeleteTemporaryError(
        userId: String,
        recordId: String,
        detail: String? = null,
        queueActionId: String? = null
    ) = transitionDelete(
        userId = userId,
        recordId = recordId,
        phase = TransitSyncPhase.DELETE_TEMPORARY_ERROR,
        queueActionId = queueActionId,
        detail = detail,
        requireQueueActionMatch = queueActionId != null
    )

    fun markDeletePermanentError(
        userId: String,
        recordId: String,
        detail: String? = null,
        queueActionId: String? = null
    ) = transitionDelete(
        userId = userId,
        recordId = recordId,
        phase = TransitSyncPhase.DELETE_PERMANENT_ERROR,
        queueActionId = queueActionId,
        detail = detail,
        requireQueueActionMatch = queueActionId != null
    )

    fun markDeleteLocalOnly(userId: String, recordId: String, detail: String? = null) =
        transitionDelete(
            userId = userId,
            recordId = recordId,
            phase = TransitSyncPhase.LOCAL_DELETED,
            detail = detail,
            disposition = TransitDeleteDisposition.LOCAL_ONLY
        )

    fun markPendingForUserAsTemporaryError(userId: String, detail: String?) {
        transitionPendingForUser(
            userId = userId,
            upsertTarget = TransitSyncPhase.TEMPORARY_ERROR,
            deleteTarget = TransitSyncPhase.DELETE_TEMPORARY_ERROR,
            detail = detail
        )
    }

    fun markPendingForUserAsPermanentError(userId: String, detail: String?) {
        transitionPendingForUser(
            userId = userId,
            upsertTarget = TransitSyncPhase.PERMANENT_ERROR,
            deleteTarget = TransitSyncPhase.DELETE_PERMANENT_ERROR,
            detail = detail
        )
    }

    private fun transitionPendingForUser(
        userId: String,
        upsertTarget: TransitSyncPhase,
        deleteTarget: TransitSyncPhase,
        detail: String?
    ) {
        if (userId.isBlank()) return
        synchronized(lock) {
            val updated = _states.value.toMutableMap()
            var changed = false
            updated.entries.toList().forEach { (key, state) ->
                val target = when {
                    state.userId != userId -> null
                    state.operation == TransitSyncOperation.DELETE &&
                        state.phase in DELETE_RETRYABLE_PHASES -> deleteTarget
                    state.operation == TransitSyncOperation.UPSERT &&
                        state.phase in UPSERT_RETRYABLE_PHASES -> upsertTarget
                    else -> null
                }
                if (target != null) {
                    updated[key] = state.copy(
                        phase = target,
                        updatedAtEpochMillis = now(),
                        detail = detail
                    )
                    changed = true
                }
            }
            if (changed) persistAndPublish(updated)
        }
    }

    private fun transition(
        userId: String,
        recordId: String,
        phase: TransitSyncPhase,
        queueActionId: String? = null,
        detail: String? = null,
        incrementAttempt: Boolean = false,
        requireQueueActionMatch: Boolean = false
    ) {
        if (userId.isBlank() || recordId.isBlank()) return
        synchronized(lock) {
            val mapKey = key(userId, recordId)
            val previous = _states.value[mapKey]
            // A deletion tombstone is authoritative. Late create/update callbacks must never
            // regress it to a regular synced state or make the record eligible for re-display.
            if (previous?.operation == TransitSyncOperation.DELETE) return
            if (requireQueueActionMatch &&
                previous?.queueActionId != null &&
                previous.queueActionId != queueActionId
            ) {
                return
            }
            val updated = previous?.copy(
                phase = phase,
                updatedAtEpochMillis = now(),
                attemptCount = previous.attemptCount + if (incrementAttempt) 1 else 0,
                queueActionId = queueActionId ?: previous.queueActionId,
                detail = detail,
                operation = TransitSyncOperation.UPSERT,
                deleteMetadata = null
            ) ?: TransitSyncState(
                userId = userId,
                recordId = recordId,
                phase = phase,
                updatedAtEpochMillis = now(),
                attemptCount = if (incrementAttempt) 1 else 0,
                queueActionId = queueActionId,
                detail = detail,
                operation = TransitSyncOperation.UPSERT
            )
            if (updated == previous) return
            val next = _states.value.toMutableMap().apply { put(mapKey, updated) }
            persistAndPublish(prune(next))
        }
    }

    private fun transitionDelete(
        userId: String,
        recordId: String,
        phase: TransitSyncPhase,
        firestoreDocId: String? = null,
        queueActionId: String? = null,
        detail: String? = null,
        disposition: TransitDeleteDisposition? = null,
        incrementAttempt: Boolean = false,
        requireQueueActionMatch: Boolean = false,
        resetDelete: Boolean = false
    ) {
        if (userId.isBlank() || recordId.isBlank()) return
        synchronized(lock) {
            val mapKey = key(userId, recordId)
            val previous = _states.value[mapKey]
            if (requireQueueActionMatch &&
                previous?.operation == TransitSyncOperation.DELETE &&
                previous.queueActionId != null &&
                previous.queueActionId != queueActionId
            ) {
                return
            }
            val previousMetadata = previous?.deleteMetadata
            val metadata = TransitDeleteMetadata(
                firestoreDocId = firestoreDocId?.takeIf { it.isNotBlank() }
                    ?: previousMetadata?.firestoreDocId,
                initiatedAtEpochMillis = if (resetDelete || previousMetadata == null) {
                    now()
                } else {
                    previousMetadata.initiatedAtEpochMillis
                },
                disposition = disposition ?: previousMetadata?.disposition
                    ?: TransitDeleteDisposition.PENDING_REMOTE
            )
            val attemptIncrement = if (
                incrementAttempt &&
                !(previous?.phase == phase && previous.queueActionId == queueActionId)
            ) {
                1
            } else {
                0
            }
            val updated = TransitSyncState(
                userId = userId,
                recordId = recordId,
                phase = phase,
                updatedAtEpochMillis = now(),
                attemptCount = if (resetDelete) 0 else (previous?.attemptCount ?: 0) + attemptIncrement,
                queueActionId = queueActionId ?: if (resetDelete) null else previous?.queueActionId,
                detail = detail,
                operation = TransitSyncOperation.DELETE,
                deleteMetadata = metadata
            )
            val equivalent = previous?.copy(updatedAtEpochMillis = updated.updatedAtEpochMillis) == updated
            if (equivalent) return
            val next = _states.value.toMutableMap().apply { put(mapKey, updated) }
            persistAndPublish(prune(next))
        }
    }

    private fun loadPersisted(): Map<String, TransitSyncState> {
        val raw = runCatching(readRaw).getOrNull().orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<List<TransitSyncState>>(raw)
                .filter { it.userId.isNotBlank() && it.recordId.isNotBlank() }
                .associateBy { key(it.userId, it.recordId) }
        }.getOrDefault(emptyMap())
    }

    private fun persistAndPublish(value: Map<String, TransitSyncState>) {
        writeRaw(json.encodeToString(value.values.toList()))
        _states.value = value
    }

    private fun prune(states: Map<String, TransitSyncState>): Map<String, TransitSyncState> =
        states.values
            .groupBy { it.userId }
            .flatMap { (_, userStates) ->
                val deletionStates = userStates.filter { it.operation == TransitSyncOperation.DELETE }
                val durableTombstones = deletionStates.filter {
                    it.deleteMetadata?.disposition != TransitDeleteDisposition.REMOTE_DELETED
                }
                val confirmedDeletes = deletionStates
                    .filter { it.deleteMetadata?.disposition == TransitDeleteDisposition.REMOTE_DELETED }
                    .sortedByDescending { it.updatedAtEpochMillis }
                    .take(MAX_CONFIRMED_DELETES_PER_USER)
                val upserts = userStates
                    .filter { it.operation == TransitSyncOperation.UPSERT }
                    .sortedByDescending { it.updatedAtEpochMillis }
                    .take(MAX_RECORDS_PER_USER)
                durableTombstones + confirmedDeletes + upserts
            }
            .associateBy { key(it.userId, it.recordId) }

    companion object {
        private const val PREFS_NAME = "transit_sync_receipts"
        private const val KEY_STATES = "record_states"
        private const val MAX_RECORDS_PER_USER = 500
        private const val MAX_CONFIRMED_DELETES_PER_USER = 500
        private val UPSERT_RETRYABLE_PHASES = setOf(
            TransitSyncPhase.PENDING,
            TransitSyncPhase.SYNCING,
            TransitSyncPhase.TEMPORARY_ERROR
        )
        private val DELETE_RETRYABLE_PHASES = setOf(
            TransitSyncPhase.LOCAL_DELETED,
            TransitSyncPhase.DELETE_PENDING,
            TransitSyncPhase.DELETING,
            TransitSyncPhase.DELETE_TEMPORARY_ERROR
        )

        @Volatile
        private var instance: TransitSyncStatusStore? = null

        fun get(context: Context): TransitSyncStatusStore =
            instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }

        private fun create(context: Context): TransitSyncStatusStore {
            val preferences = openPreferences(context)
            return TransitSyncStatusStore(
                readRaw = { preferences.getString(KEY_STATES, null) },
                writeRaw = { preferences.edit().putString(KEY_STATES, it).apply() },
                initialUserId = CurrentUserProvider.currentUserIdOrNull()
            )
        }

        private fun openPreferences(context: Context): SharedPreferences =
            openEncryptedStorageWithRecovery(
                openEncrypted = {
                    val masterKey = MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                },
                resetEncryptedStorage = { context.deleteSharedPreferences(PREFS_NAME) },
                logError = { message, error -> android.util.Log.e("TransitSyncStatus", message, error) }
            )

        internal fun resetSingletonForTests() {
            instance = null
        }
    }
}

private fun key(userId: String, recordId: String): String = "$userId|$recordId"
