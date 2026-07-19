package com.example.toplutasima.transit.history

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.toplutasima.data.openEncryptedStorageWithRecovery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Bounded, encrypted and UID-scoped transit change history.
 *
 * Storage failures are intentionally non-fatal: the transit write that produced a history event
 * must remain successful even when this auxiliary ledger cannot be read or persisted.
 */
class TransitChangeHistoryStore internal constructor(
    private val readRaw: () -> String?,
    private val writeRaw: (String) -> Boolean,
    private val clearRaw: () -> Unit = {},
    val enabled: Boolean,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val maximumEventsPerUser: Int = DEFAULT_MAX_EVENTS_PER_USER,
    private val maximumEventsPerRecord: Int = DEFAULT_MAX_EVENTS_PER_RECORD,
    private val maximumTotalEvents: Int = DEFAULT_MAX_TOTAL_EVENTS,
    private val maximumEncodedBytes: Int = DEFAULT_MAX_ENCODED_BYTES
) {
    init {
        require(maximumEventsPerUser > 0)
        require(maximumEventsPerRecord > 0)
        require(maximumTotalEvents > 0)
        require(maximumEncodedBytes > 0)
    }

    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _events = MutableStateFlow(if (enabled) loadPersisted() else emptyList())

    fun observeRecord(userId: String, recordId: String): Flow<List<TransitChangeEvent>> =
        _events.map { values ->
            if (!enabled || userId.isBlank() || recordId.isBlank()) {
                emptyList()
            } else {
                values.filter { it.userId == userId && it.recordId == recordId }
                    .sortedWith(HISTORY_ORDER)
            }
        }.distinctUntilChanged()

    fun observeUser(userId: String): Flow<List<TransitChangeEvent>> =
        _events.map { values ->
            if (!enabled || userId.isBlank()) emptyList()
            else values.filter { it.userId == userId }.sortedWith(HISTORY_ORDER)
        }.distinctUntilChanged()

    internal fun snapshotForUser(userId: String): List<TransitChangeEvent> =
        if (!enabled || userId.isBlank()) emptyList()
        else _events.value.filter { it.userId == userId }.sortedWith(HISTORY_ORDER)

    fun eventsForRecord(userId: String, recordId: String): List<TransitChangeEvent> =
        if (!enabled || userId.isBlank() || recordId.isBlank()) {
            emptyList()
        } else {
            _events.value.filter { it.userId == userId && it.recordId == recordId }
                .sortedWith(HISTORY_ORDER)
        }

    /** Refreshes events written by a background worker through another store instance. */
    fun reload() {
        if (!enabled) return
        synchronized(lock) {
            _events.value = loadPersisted()
        }
    }

    fun append(draft: TransitChangeEventDraft): TransitHistoryAppendResult {
        if (!enabled) return TransitHistoryAppendResult(TransitHistoryAppendOutcome.DISABLED)
        if (draft.userId.isBlank() || draft.recordId.isBlank() || draft.occurredAtEpochMillis < 0L) {
            return TransitHistoryAppendResult(TransitHistoryAppendOutcome.INVALID)
        }

        val changes = sanitizeChanges(draft.changes)
        if (draft.operation in OPERATIONS_REQUIRING_DIFF && changes.isEmpty()) {
            return TransitHistoryAppendResult(TransitHistoryAppendOutcome.INVALID)
        }
        val eventId = TransitChangeEventId.create(draft, changes)

        synchronized(lock) {
            _events.value.firstOrNull { it.eventId == eventId }?.let { existing ->
                return TransitHistoryAppendResult(
                    outcome = TransitHistoryAppendOutcome.DUPLICATE,
                    event = existing
                )
            }

            val event = TransitChangeEvent(
                eventId = eventId,
                userId = draft.userId,
                recordId = draft.recordId,
                operation = draft.operation,
                occurredAtEpochMillis = draft.occurredAtEpochMillis,
                recordedAtEpochMillis = nowEpochMillis().coerceAtLeast(0L),
                source = draft.source,
                changes = changes,
                syncStatus = draft.syncStatus
            )
            val bounded = prune(_events.value + event)
            val persisted = persist(bounded)
            _events.value = bounded
            return TransitHistoryAppendResult(
                outcome = if (persisted) {
                    TransitHistoryAppendOutcome.ADDED
                } else {
                    TransitHistoryAppendOutcome.ADDED_MEMORY_ONLY
                },
                event = event
            )
        }
    }

    /** Updates an existing event in place, so worker retries never create user-visible rows. */
    fun updateSyncStatus(
        userId: String,
        recordId: String,
        eventId: String,
        syncStatus: TransitHistorySyncStatus
    ): Boolean {
        if (!enabled || userId.isBlank() || recordId.isBlank() || eventId.isBlank()) return false
        synchronized(lock) {
            val index = _events.value.indexOfFirst {
                it.userId == userId && it.recordId == recordId && it.eventId == eventId
            }
            if (index < 0) return false
            val current = _events.value[index]
            if (current.syncStatus == syncStatus) return true
            val next = _events.value.toMutableList().apply {
                this[index] = current.copy(syncStatus = syncStatus)
            }.sortedWith(HISTORY_ORDER)
            persist(next)
            _events.value = next
            return true
        }
    }

    /** Advances pending delete-sync rows without producing retry duplicates. */
    fun updatePendingDeleteSyncForUser(
        userId: String,
        syncStatus: TransitHistorySyncStatus
    ): Int {
        if (!enabled || userId.isBlank()) return 0
        synchronized(lock) {
            var changed = 0
            val next = _events.value.map { event ->
                if (
                    event.userId == userId &&
                    event.operation == TransitChangeOperation.DELETE_SYNC &&
                    event.syncStatus in PENDING_DELETE_SYNC_STATUSES &&
                    event.syncStatus != syncStatus
                ) {
                    changed += 1
                    event.copy(syncStatus = syncStatus)
                } else {
                    event
                }
            }
            if (changed > 0) {
                val ordered = next.sortedWith(HISTORY_ORDER)
                persist(ordered)
                _events.value = ordered
            }
            return changed
        }
    }

    /** Updates the latest write event for one record; queue retries mutate rather than append. */
    fun updateLatestRecordSyncStatus(
        userId: String,
        recordId: String,
        syncStatus: TransitHistorySyncStatus
    ): Boolean {
        if (!enabled || userId.isBlank() || recordId.isBlank()) return false
        synchronized(lock) {
            val candidate = _events.value
                .filter {
                    it.userId == userId &&
                        it.recordId == recordId &&
                        it.operation != TransitChangeOperation.DELETE_SYNC &&
                        it.syncStatus != TransitHistorySyncStatus.NOT_APPLICABLE
                }
                .maxWithOrNull(HISTORY_ORDER)
                ?: return false
            return updateSyncStatus(userId, recordId, candidate.eventId, syncStatus)
        }
    }

    private fun loadPersisted(): List<TransitChangeEvent> {
        val raw = runCatching(readRaw).getOrNull().orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString<List<TransitChangeEvent>>(raw)
                .asSequence()
                .filter { it.userId.isNotBlank() && it.recordId.isNotBlank() && it.eventId.isNotBlank() }
                .map(::sanitizeLoadedEvent)
                .distinctBy { it.eventId }
                .toList()
                .let(::prune)
        }.getOrElse {
            runCatching(clearRaw)
            emptyList()
        }
    }

    private fun sanitizeLoadedEvent(event: TransitChangeEvent): TransitChangeEvent = event.copy(
        changes = sanitizeChanges(event.changes),
        occurredAtEpochMillis = event.occurredAtEpochMillis.coerceAtLeast(0L),
        recordedAtEpochMillis = event.recordedAtEpochMillis.coerceAtLeast(0L)
    )

    private fun sanitizeChanges(changes: List<TransitFieldChange>): List<TransitFieldChange> =
        changes.asSequence()
            .filter { it.fieldId in TransitRecordDiffUseCase.TRACKED_FIELDS }
            .distinctBy { it.fieldId }
            .take(MAX_CHANGES_PER_EVENT)
            .map { change ->
                change.copy(
                    oldValue = sanitizeValue(change.oldValue),
                    newValue = sanitizeValue(change.newValue),
                    oldProvenance = change.oldProvenance?.takeIf { it.isDurable },
                    newProvenance = change.newProvenance?.takeIf { it.isDurable }
                )
            }
            .toList()

    private fun sanitizeValue(value: TransitHistoryValue): TransitHistoryValue = when (value.state) {
        TransitHistoryValueState.KNOWN -> value.value
            ?.let { TransitHistoryValue.known(it.take(TransitHistoryValue.MAX_VALUE_LENGTH)) }
            ?: TransitHistoryValue.empty()
        TransitHistoryValueState.EMPTY -> TransitHistoryValue.empty()
        TransitHistoryValueState.UNKNOWN -> TransitHistoryValue.unknown()
    }

    private fun prune(values: List<TransitChangeEvent>): List<TransitChangeEvent> {
        val recordBounded = values
            .groupBy { it.userId to it.recordId }
            .flatMap { (_, recordEvents) ->
                recordEvents.sortedWith(HISTORY_ORDER_DESCENDING).take(maximumEventsPerRecord)
            }
        val userBounded = recordBounded
            .groupBy { it.userId }
            .flatMap { (_, userEvents) ->
                userEvents.sortedWith(HISTORY_ORDER_DESCENDING).take(maximumEventsPerUser)
            }
            .sortedWith(HISTORY_ORDER_DESCENDING)
            .take(maximumTotalEvents)
            .toMutableList()

        while (userBounded.size > 1 && encodedSize(userBounded) > maximumEncodedBytes) {
            userBounded.removeAt(userBounded.lastIndex)
        }
        return userBounded.sortedWith(HISTORY_ORDER)
    }

    private fun encodedSize(values: List<TransitChangeEvent>): Int = runCatching {
        json.encodeToString(values).toByteArray(Charsets.UTF_8).size
    }.getOrDefault(Int.MAX_VALUE)

    private fun persist(values: List<TransitChangeEvent>): Boolean = runCatching {
        val raw = json.encodeToString(values)
        if (raw.toByteArray(Charsets.UTF_8).size > maximumEncodedBytes) return@runCatching false
        writeRaw(raw)
    }.getOrDefault(false)

    companion object {
        private const val PREFS_NAME = "transit_change_history"
        private const val KEY_EVENTS = "events"
        private const val TAG = "TransitChangeHistory"
        private const val MAX_CHANGES_PER_EVENT = 24
        private const val DEFAULT_MAX_EVENTS_PER_USER = 500
        private const val DEFAULT_MAX_EVENTS_PER_RECORD = 80
        private const val DEFAULT_MAX_TOTAL_EVENTS = 1_000
        private const val DEFAULT_MAX_ENCODED_BYTES = 512 * 1_024

        private val OPERATIONS_REQUIRING_DIFF = setOf(
            TransitChangeOperation.MANUAL_EDIT,
            TransitChangeOperation.REMOTE_UPDATE,
            TransitChangeOperation.AUTOMATIC_HEALTH_CORRECTION,
            TransitChangeOperation.USER_APPROVED_BULK_CORRECTION,
            TransitChangeOperation.PROVENANCE_CHANGE,
            TransitChangeOperation.DUPLICATE_MERGE,
            TransitChangeOperation.UNDO
        )
        private val PENDING_DELETE_SYNC_STATUSES = setOf(
            TransitHistorySyncStatus.PENDING,
            TransitHistorySyncStatus.SYNCING,
            TransitHistorySyncStatus.TEMPORARY_ERROR
        )

        private val HISTORY_ORDER = Comparator<TransitChangeEvent> { first, second ->
            compareValuesBy(
                first,
                second,
                TransitChangeEvent::occurredAtEpochMillis,
                { operationOrder(it.operation) },
                TransitChangeEvent::recordedAtEpochMillis,
                TransitChangeEvent::eventId
            )
        }
        private val HISTORY_ORDER_DESCENDING = HISTORY_ORDER.reversed()

        /** Does not open encrypted storage at all when the build-time feature gate is disabled. */
        fun create(context: Context, enabled: Boolean): TransitChangeHistoryStore {
            if (!enabled) {
                return TransitChangeHistoryStore(
                    readRaw = { null },
                    writeRaw = { false },
                    enabled = false
                )
            }
            val appContext = context.applicationContext
            val preferences = runCatching { openPreferences(appContext) }.getOrElse { error ->
                Log.e(TAG, "Encrypted history unavailable; continuing without durable history", error)
                return TransitChangeHistoryStore(
                    readRaw = { null },
                    writeRaw = { false },
                    enabled = true
                )
            }
            return TransitChangeHistoryStore(
                readRaw = { runCatching { preferences.getString(KEY_EVENTS, null) }.getOrNull() },
                writeRaw = { value ->
                    runCatching { preferences.edit().putString(KEY_EVENTS, value).commit() }
                        .getOrDefault(false)
                },
                clearRaw = { runCatching { preferences.edit().remove(KEY_EVENTS).commit() } },
                enabled = true
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
                logError = { message, error -> Log.e(TAG, message, error) }
            )

        private fun operationOrder(operation: TransitChangeOperation): Int = when (operation) {
            TransitChangeOperation.CREATE -> 0
            TransitChangeOperation.MANUAL_EDIT,
            TransitChangeOperation.REMOTE_UPDATE,
            TransitChangeOperation.AUTOMATIC_HEALTH_CORRECTION,
            TransitChangeOperation.USER_APPROVED_BULK_CORRECTION,
            TransitChangeOperation.PROVENANCE_CHANGE,
            TransitChangeOperation.DUPLICATE_MERGE,
            TransitChangeOperation.UNDO -> 10
            TransitChangeOperation.LOCAL_DELETE -> 20
            TransitChangeOperation.DELETE_SYNC -> 30
            TransitChangeOperation.DELETE_ROLLBACK -> 40
        }
    }
}
