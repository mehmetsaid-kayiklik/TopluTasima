package com.example.toplutasima.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.worker.OfflineSyncWorker
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.util.UUID

object OfflineQueueStore {
    private const val PREFS_NAME = "offline_queue"
    private const val KEY_ACTIONS = "pending_actions"
    private const val TYPE_SAVE_TRIP = "saveTrip"
    private const val TYPE_UPDATE_ACTUAL = "updateActual"
    private const val TYPE_UPDATE_RECORD = "updateRecord"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val tripRemoteDataSource by lazy { FirestoreTripRemoteDataSource() }

    @Serializable
    data class QueuedAction(
        val id: String = UUID.randomUUID().toString(),
        val type: String,
        val recordId: String = "",
        val payload: String,
        val createdAt: Long = System.currentTimeMillis()
    )

    fun pendingCount(context: Context): Int = load(context).size

    fun enqueueSaveTrip(context: Context, data: Map<String, Any?>) {
        enqueue(
            context = context,
            action = QueuedAction(
                type = TYPE_SAVE_TRIP,
                recordId = data["id"]?.toString().orEmpty(),
                payload = JSONObject(data).toString()
            )
        )
    }

    fun enqueueUpdateActual(context: Context, recordId: String, actualDep: String?, actualArr: String?) {
        enqueue(
            context = context,
            action = QueuedAction(
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

    fun enqueueUpdateRecord(context: Context, recordId: String, fields: Map<String, Any>) {
        enqueue(
            context = context,
            action = QueuedAction(
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
        WorkManager.getInstance(context.applicationContext).enqueue(request)
    }

    suspend fun drain(context: Context): Int {
        val actions = load(context)
        if (actions.isEmpty()) return 0
        var synced = 0
        val remaining = mutableListOf<QueuedAction>()
        for (action in actions) {
            try {
                when (action.type) {
                    TYPE_SAVE_TRIP -> tripRemoteDataSource.saveTrip(action.payload.toAnyMap())
                    TYPE_UPDATE_ACTUAL -> {
                        val payload = JSONObject(action.payload)
                        tripRemoteDataSource.updateActual(
                            tripId = action.recordId,
                            actualDep = payload.optString("actualDep").ifBlank { null },
                            actualArr = payload.optString("actualArr").ifBlank { null }
                        )
                    }
                    TYPE_UPDATE_RECORD -> tripRemoteDataSource.updateExistingRecord(
                        tripId = action.recordId,
                        fields = action.payload.toAnyMap().filterValues { it != null }.mapValues { it.value as Any }
                    )
                    else -> Unit
                }
                synced++
            } catch (_: Exception) {
                remaining += action
            }
        }
        save(context, remaining)
        return synced
    }

    fun clear(context: Context) {
        save(context, emptyList())
    }

    private fun enqueue(context: Context, action: QueuedAction) {
        save(context, load(context) + action)
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
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: java.security.GeneralSecurityException) {
            android.util.Log.e("OfflineQueueStore", "EncryptedSharedPreferences fail, fallback to plain", e)
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        } catch (e: java.io.IOException) {
            android.util.Log.e("OfflineQueueStore", "EncryptedSharedPreferences io fail, fallback to plain", e)
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
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
}
