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
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.worker.OfflineSyncWorker
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    )

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
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        android.util.Log.i(TAG, "Offline queue user changed; uid=${userId ?: "signed-out"}")
        if (!userId.isNullOrBlank() &&
            CurrentUserProvider.currentUserIdOrNull() == userId &&
            pendingCount(appContext) > 0
        ) {
            scheduleSync(appContext)
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
            processAction = { action -> processAction(action, currentUserId) }
        )
    }

    internal suspend fun drainQueue(
        currentUserId: String,
        loadActions: () -> List<QueuedAction>,
        saveActions: (List<QueuedAction>) -> Unit,
        processAction: suspend (QueuedAction) -> Unit,
        onSuperseded: (QueuedAction, QueuedAction) -> Unit = ::logSupersededAction,
        onMismatched: (QueuedAction, String) -> Unit = ::logMismatchedAction,
        onOwnerless: (QueuedAction) -> Unit = ::logOwnerlessAction
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
            try {
                processAction(action)
                synced++
            } catch (_: Exception) {
                remaining += action
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
            if (current == null || candidate.isNewerThan(current)) {
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
            else -> Unit
        }
    }

    fun clear(context: Context) {
        synchronized(queueStateLock) {
            val currentUserId = CurrentUserProvider.currentUserIdOrNull() ?: return@synchronized
            save(context, load(context).filterNot { it.userId == currentUserId })
        }
    }

    private fun enqueue(context: Context, action: QueuedAction) {
        require(action.userId.isNotBlank()) { "Offline action userId must not be blank" }
        synchronized(queueStateLock) {
            save(context, load(context) + action)
        }
        scheduleSync(context)
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
