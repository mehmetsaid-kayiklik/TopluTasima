package com.example.toplutasima.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.network.firestore.FirestoreHelper
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.sync.TransitOfflineQueueStatusAdapter
import com.example.toplutasima.transit.sync.TransitDeleteDisposition
import com.example.toplutasima.transit.sync.TransitSyncStatusStore
import com.example.toplutasima.worker.OfflineSyncWorker
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.IOException
import java.security.GeneralSecurityException
import java.util.UUID

object OfflineQueueStore {
    private const val PREFS_NAME = "offline_queue"
    private const val KEY_ACTIONS = "pending_actions"
    private const val UNIQUE_WORK_NAME = "offline_queue_processor"
    private const val TYPE_SAVE_TRIP = "saveTrip"
    private const val TYPE_UPDATE_ACTUAL = "updateActual"
    private const val TYPE_UPDATE_RECORD = "updateRecord"
    private const val TYPE_DELETE_TRIP = "deleteTrip"
    private const val KEY_DELETE_FIRESTORE_DOC_ID = "firestoreDocId"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val tripRemoteDataSource by lazy { FirestoreTripRemoteDataSource() }
    // WorkManager and queue callers run in the app process; these guards coordinate that
    // process while SharedPreferences remains the durable source of truth.
    private val drainMutex = Mutex()
    private val queueStateLock = Any()

    @Serializable
    data class QueuedAction(
        val id: String = UUID.randomUUID().toString(),
        val userId: String = "",
        val type: String,
        val recordId: String = "",
        val payload: String,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        val isDeleteAction: Boolean
            get() = type == TYPE_DELETE_TRIP

        val deleteFirestoreDocId: String?
            get() = if (!isDeleteAction) {
                null
            } else {
                runCatching {
                    JSONObject(payload).optString(KEY_DELETE_FIRESTORE_DOC_ID).takeIf { it.isNotBlank() }
                }.getOrNull()
            }
    }

    fun pendingCount(context: Context): Int = synchronized(queueStateLock) {
        val currentUserId = CurrentUserProvider.currentUserIdOrNull() ?: return@synchronized 0
        val actions = load(context)
        val safeActions = actions.filter { action ->
            if (action.userId.isBlank()) {
                logOwnerlessAction(action)
                false
            } else {
                true
            }
        }
        if (safeActions.size != actions.size) save(context, safeActions)
        safeActions.count { it.userId == currentUserId }
    }

    fun enqueueSaveTrip(
        context: Context,
        data: Map<String, Any?>,
        userId: String = CurrentUserProvider.requireUserId()
    ) {
        val queuedData = data.toMutableMap()
        val firestoreDocId = queuedData["firestoreDocId"]
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: tripRemoteDataSource.newTripDocumentId()
        queuedData["firestoreDocId"] = firestoreDocId
        enqueue(
            context = context,
            action = QueuedAction(
                userId = userId,
                type = TYPE_SAVE_TRIP,
                recordId = queuedData["id"]?.toString().orEmpty(),
                payload = JSONObject(queuedData).toString()
            )
        )
    }

    fun enqueueUpdateActual(
        context: Context,
        recordId: String,
        actualDep: String?,
        actualArr: String?,
        userId: String = CurrentUserProvider.requireUserId()
    ) {
        enqueue(
            context = context,
            action = QueuedAction(
                userId = userId,
                type = TYPE_UPDATE_ACTUAL,
                recordId = recordId,
                payload = JSONObject(
                    mapOf(
                        "actualDep" to actualDep.orEmpty(),
                        "actualArr" to actualArr.orEmpty()
                    )
                ).toString()
            )
        )
    }

    fun enqueueUpdateRecord(
        context: Context,
        recordId: String,
        fields: Map<String, Any>,
        userId: String = CurrentUserProvider.requireUserId()
    ) {
        enqueue(
            context = context,
            action = QueuedAction(
                userId = userId,
                type = TYPE_UPDATE_RECORD,
                recordId = recordId,
                payload = JSONObject(fields).toString()
            )
        )
    }

    fun enqueueDeleteTrip(
        context: Context,
        recordId: String,
        firestoreDocId: String?,
        userId: String = CurrentUserProvider.requireUserId()
    ): QueuedAction {
        require(recordId.isNotBlank()) { "Offline delete recordId must not be blank" }
        return enqueue(
            context = context,
            action = QueuedAction(
                userId = userId,
                type = TYPE_DELETE_TRIP,
                recordId = recordId,
                payload = JSONObject(
                    mapOf(KEY_DELETE_FIRESTORE_DOC_ID to firestoreDocId.orEmpty())
                ).toString()
            )
        )
    }

    fun retryDelete(context: Context, userId: String, recordId: String): Boolean {
        if (userId.isBlank() || recordId.isBlank()) return false
        if (CurrentUserProvider.currentUserIdOrNull() != userId) return false
        val existing = synchronized(queueStateLock) {
            load(context).lastOrNull {
                it.userId == userId && it.recordId == recordId && it.isDeleteAction
            }
        }
        if (existing != null) {
            TransitOfflineQueueStatusAdapter.onQueued(context, existing)
            scheduleSync(context)
            return true
        }
        val tombstone = TransitSyncStatusStore.get(context)
            .deletionTombstonesForUser(userId)
            .firstOrNull { it.recordId == recordId }
            ?: return false
        enqueueDeleteTrip(
            context = context,
            recordId = recordId,
            firestoreDocId = tombstone.deleteMetadata?.firestoreDocId,
            userId = userId
        )
        return true
    }

    fun discardPendingDelete(context: Context, userId: String, recordId: String): Boolean {
        if (userId.isBlank() || recordId.isBlank()) return false
        if (CurrentUserProvider.currentUserIdOrNull() != userId) return false
        val removed = synchronized(queueStateLock) {
            val actions = load(context)
            val remaining = actions.filterNot {
                it.userId == userId && it.recordId == recordId && it.isDeleteAction
            }
            if (remaining.size != actions.size) save(context, remaining)
            remaining.size != actions.size
        }
        TransitSyncStatusStore.get(context).markDeleteLocalOnly(
            userId = userId,
            recordId = recordId,
            detail = "Cloud delete discarded by user"
        )
        return removed
    }

    fun scheduleSync(context: Context) {
        val request = OneTimeWorkRequestBuilder<OfflineSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        // A drain processes a snapshot. APPEND_OR_REPLACE guarantees that items added while
        // the current worker is finishing still get a successor instead of KEEP dropping it.
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun onAuthenticatedUserChanged(context: Context, userId: String?) {
        val appContext = context.applicationContext
        TransitOfflineQueueStatusAdapter.onUserChanged(appContext, userId)
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        android.util.Log.i(TAG, "Offline queue user changed; uid=${userId ?: "signed-out"}")
        if (!userId.isNullOrBlank() &&
            CurrentUserProvider.currentUserIdOrNull() == userId
        ) {
            restorePendingDeleteActions(appContext, userId)
            if (pendingCount(appContext) > 0) scheduleSync(appContext)
        }
    }

    private fun restorePendingDeleteActions(context: Context, userId: String) {
        val tombstones = TransitSyncStatusStore.get(context)
            .deletionTombstonesForUser(userId)
            .filter {
                it.deleteMetadata?.disposition == TransitDeleteDisposition.PENDING_REMOTE
            }
        if (tombstones.isEmpty()) return
        val existingRecordIds = synchronized(queueStateLock) {
            load(context)
                .filter { it.userId == userId && it.isDeleteAction }
                .mapTo(mutableSetOf()) { it.recordId }
        }
        tombstones.forEach { state ->
            if (state.recordId !in existingRecordIds) {
                enqueueDeleteTrip(
                    context = context,
                    recordId = state.recordId,
                    firestoreDocId = state.deleteMetadata?.firestoreDocId,
                    userId = userId
                )
            }
        }
    }

    suspend fun drain(context: Context): Int {
        val currentUserId = CurrentUserProvider.currentUserIdOrNull() ?: run {
            android.util.Log.w(TAG, "Offline queue drain skipped: no authenticated user")
            return 0
        }
        return drainQueue(
            currentUserId = currentUserId,
            loadActions = { load(context) },
            saveActions = { actions -> save(context, actions) },
            processAction = { action -> processAction(action, currentUserId) },
            onProcessing = { action ->
                TransitOfflineQueueStatusAdapter.onSyncing(context, action)
            },
            onProcessed = { action ->
                TransitOfflineQueueStatusAdapter.onSynced(context, action)
            },
            onProcessFailed = { action, error ->
                TransitOfflineQueueStatusAdapter.onTemporaryFailure(context, action, error)
            }
        )
    }

    internal suspend fun drainQueue(
        currentUserId: String,
        loadActions: () -> List<QueuedAction>,
        saveActions: (List<QueuedAction>) -> Unit,
        processAction: suspend (QueuedAction) -> Unit,
        onSuperseded: (QueuedAction, QueuedAction) -> Unit = ::logSupersededAction,
        onMismatched: (QueuedAction, String) -> Unit = ::logMismatchedAction,
        onOwnerless: (QueuedAction) -> Unit = ::logOwnerlessAction,
        onProcessing: (QueuedAction) -> Unit = {},
        onProcessed: (QueuedAction) -> Unit = {},
        onProcessFailed: (QueuedAction, Throwable) -> Unit = { _, _ -> }
    ): Int = drainMutex.withLock {
        require(currentUserId.isNotBlank()) { "currentUserId must not be blank" }
        val actions = synchronized(queueStateLock) { loadActions() }
        if (actions.isEmpty()) return@withLock 0
        val ownerlessActions = actions.filter { it.userId.isBlank() }
        ownerlessActions.forEach(onOwnerless)
        val deferredActions = actions.filter { it.userId.isNotBlank() && it.userId != currentUserId }
        deferredActions.forEach { onMismatched(it, currentUserId) }
        val ownedActions = actions.filter { it.userId == currentUserId }
        val actionsToProcess = newestActionsByRecord(ownedActions, onSuperseded)
        var synced = 0
        val remaining = mutableListOf<QueuedAction>()
        for (action in actionsToProcess) {
            onProcessing(action)
            try {
                processAction(action)
                synced++
                onProcessed(action)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                remaining += action
                onProcessFailed(action, error)
            }
        }

        val originalIds = actions.mapTo(mutableSetOf()) { it.id }
        val remainingIds = (deferredActions + remaining).mapTo(mutableSetOf()) { it.id }
        synchronized(queueStateLock) {
            val latestActions = loadActions()
            saveActions(
                latestActions.filter { action ->
                    action.id !in originalIds || action.id in remainingIds
                }
            )
        }
        synced
    }

    private fun newestActionsByRecord(
        actions: List<QueuedAction>,
        onSuperseded: (QueuedAction, QueuedAction) -> Unit
    ): List<QueuedAction> {
        val latestByRecordId = mutableMapOf<String, IndexedQueuedAction>()
        actions.forEachIndexed { index, action ->
            val recordId = action.recordId.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val candidate = IndexedQueuedAction(index, action)
            val current = latestByRecordId[recordId]
            val shouldReplace = when {
                current == null -> true
                current.action.isDeleteAction && !candidate.action.isDeleteAction -> false
                !current.action.isDeleteAction && candidate.action.isDeleteAction -> true
                else -> candidate.isNewerThan(current)
            }
            if (shouldReplace) {
                latestByRecordId[recordId] = candidate
            }
        }

        return actions.filterIndexed { index, action ->
            val recordId = action.recordId.takeIf { it.isNotBlank() } ?: return@filterIndexed true
            val latest = latestByRecordId.getValue(recordId)
            if (index == latest.index) {
                true
            } else {
                onSuperseded(action, latest.action)
                false
            }
        }
    }

    private fun logSupersededAction(older: QueuedAction, newer: QueuedAction) {
        android.util.Log.w(
            "OfflineQueueStore",
            "Dropping superseded action id=${older.id} type=${older.type} recordId=${older.recordId}; " +
                "newerActionId=${newer.id} newerType=${newer.type} createdAt=${newer.createdAt}"
        )
    }

    private fun logMismatchedAction(action: QueuedAction, currentUserId: String) {
        android.util.Log.w(
            TAG,
            "Deferring offline action id=${action.id} type=${action.type} ownerUid=${action.userId}; " +
                "currentUid=$currentUserId"
        )
    }

    private fun logOwnerlessAction(action: QueuedAction) {
        android.util.Log.w(
            TAG,
            "Dropping legacy offline action without owner UID id=${action.id} type=${action.type}"
        )
    }

    private data class IndexedQueuedAction(
        val index: Int,
        val action: QueuedAction
    ) {
        fun isNewerThan(other: IndexedQueuedAction): Boolean =
            action.createdAt > other.action.createdAt ||
                (action.createdAt == other.action.createdAt && index > other.index)
    }

    private suspend fun processAction(action: QueuedAction, expectedUserId: String) {
        check(action.userId == expectedUserId) { "Offline action owner does not match drain user" }
        check(CurrentUserProvider.currentUserIdOrNull() == expectedUserId) {
            "Authenticated user changed during offline queue drain"
        }
        when (action.type) {
            TYPE_SAVE_TRIP -> tripRemoteDataSource.saveTrip(action.payload.toAnyMap(), action.userId)
            TYPE_UPDATE_ACTUAL -> {
                val payload = JSONObject(action.payload)
                tripRemoteDataSource.updateActual(
                    tripId = action.recordId,
                    actualDep = payload.optString("actualDep").ifBlank { null },
                    actualArr = payload.optString("actualArr").ifBlank { null },
                    userId = action.userId
                )
            }
            TYPE_UPDATE_RECORD -> tripRemoteDataSource.updateExistingRecord(
                tripId = action.recordId,
                fields = action.payload.toAnyMap().filterValues { it != null }.mapValues { it.value as Any },
                userId = action.userId
            )
            TYPE_DELETE_TRIP -> {
                val documentIds = listOfNotNull(
                    action.deleteFirestoreDocId,
                    action.recordId.takeIf { it.isNotBlank() }
                ).distinct()
                documentIds.forEach { documentId ->
                    check(tripRemoteDataSource.deleteTrip(documentId)) {
                        "Firestore trip delete failed for document $documentId"
                    }
                }
                val legacyMatches = FirestoreHelper.tripsCollection(action.userId)
                    .whereEqualTo("id", action.recordId)
                    .get()
                    .await()
                legacyMatches.documents.forEach { document ->
                    document.reference.delete().await()
                }
            }
            else -> Unit
        }
    }

    fun clear(context: Context) {
        synchronized(queueStateLock) {
            val currentUserId = CurrentUserProvider.currentUserIdOrNull() ?: return@synchronized
            save(context, load(context).filterNot { it.userId == currentUserId })
        }
    }

    private fun enqueue(context: Context, action: QueuedAction): QueuedAction {
        require(action.userId.isNotBlank()) { "Offline action userId must not be blank" }
        if (!action.isDeleteAction &&
            TransitFeatureFlags.SYNC_DELETE_RECEIPTS &&
            TransitSyncStatusStore.get(context).isDeletionTombstoned(action.userId, action.recordId)
        ) {
            android.util.Log.w(
                TAG,
                "Ignoring ${action.type} for tombstoned transit record ${action.recordId}"
            )
            return action
        }
        val effectiveAction = synchronized(queueStateLock) {
            val actions = load(context)
            val pendingDelete = actions.lastOrNull {
                it.userId == action.userId &&
                    it.recordId == action.recordId &&
                    it.isDeleteAction
            }
            when {
                action.isDeleteAction && pendingDelete != null -> pendingDelete
                !action.isDeleteAction && pendingDelete != null -> {
                    logSupersededAction(action, pendingDelete)
                    pendingDelete
                }
                else -> action.also { save(context, actions + it) }
            }
        }
        TransitOfflineQueueStatusAdapter.onQueued(context, effectiveAction)
        scheduleSync(context)
        return effectiveAction
    }

    private fun load(context: Context): List<QueuedAction> {
        val raw = prefs(context).getString(KEY_ACTIONS, null) ?: return emptyList()
        return try { json.decodeFromString(raw) } catch (_: Exception) { emptyList() }
    }

    private fun save(context: Context, actions: List<QueuedAction>) {
        prefs(context).edit().putString(KEY_ACTIONS, json.encodeToString(actions)).apply()
    }

    private fun prefs(context: Context): android.content.SharedPreferences {
        val appContext = context.applicationContext
        return openEncryptedStorageWithRecovery(
            openEncrypted = {
                createEncryptedPreferences(appContext)
            },
            resetEncryptedStorage = {
                appContext.deleteSharedPreferences(PREFS_NAME)
            },
            logError = { message, error -> android.util.Log.e(TAG, message, error) }
        )
    }

    private fun createEncryptedPreferences(context: Context): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun String.toAnyMap(): Map<String, Any?> {
        val obj = JSONObject(this)
        val out = LinkedHashMap<String, Any?>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.get(key)
            out[key] = if (value == JSONObject.NULL) null else value
        }
        return out
    }

    private const val TAG = "OfflineQueueStore"
}

internal fun <T> openEncryptedStorageWithRecovery(
    openEncrypted: () -> T,
    resetEncryptedStorage: () -> Unit,
    logError: (String, Throwable) -> Unit
): T {
    try {
        return openEncrypted()
    } catch (error: Exception) {
        if (error !is GeneralSecurityException && error !is IOException) throw error
        logError(
            "Encrypted offline queue failed to open; recreating encrypted storage. " +
                "Unreadable pending actions may be lost. Plaintext fallback is disabled.",
            error
        )
    }

    try {
        resetEncryptedStorage()
        return openEncrypted()
    } catch (recoveryError: Exception) {
        logError(
            "Encrypted offline queue recovery failed; refusing to use plaintext storage.",
            recoveryError
        )
        throw IllegalStateException(
            "Encrypted offline queue storage is unavailable",
            recoveryError
        )
    }
}
