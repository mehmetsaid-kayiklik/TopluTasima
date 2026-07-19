package com.example.toplutasima.transit.duplicate

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.toplutasima.data.openEncryptedStorageWithRecovery
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray

@Serializable
data class TransitDuplicateStoredDecision(
    val userId: String,
    @SerialName("fingerprint")
    val candidateFingerprint: String,
    @SerialName("firstId")
    val firstRecordId: String,
    @SerialName("secondId")
    val secondRecordId: String,
    val decision: TransitDuplicateDecision,
    @SerialName("updatedAt")
    val updatedAtEpochMillis: Long
)

/** Bounded encrypted UID-scoped decisions; changed record content creates a new fingerprint. */
class TransitDuplicateDecisionStore internal constructor(
    private val readRaw: () -> String?,
    private val writeRaw: (String) -> Unit,
    private val enabled: Boolean,
    private val maximumDecisionsPerUser: Int = DEFAULT_MAX_PER_USER,
    private val now: () -> Long = System::currentTimeMillis
) : TransitDuplicateDecisionLookup {
    private val lock = Any()

    override fun isKeptSeparate(userId: String, decisionFingerprint: String): Boolean =
        decision(userId, decisionFingerprint)?.decision == TransitDuplicateDecision.KEEP_SEPARATE

    fun decision(userId: String, candidateFingerprint: String): TransitDuplicateStoredDecision? {
        if (!enabled || userId.isBlank() || candidateFingerprint.isBlank()) return null
        return synchronized(lock) {
            load().firstOrNull {
                it.userId == userId && it.candidateFingerprint == candidateFingerprint
            }
        }
    }

    fun record(
        userId: String,
        candidate: TransitDuplicateCandidate,
        decision: TransitDuplicateDecision
    ): Boolean {
        if (!enabled || userId.isBlank() || candidate.decisionFingerprint.isBlank()) return false
        synchronized(lock) {
            val values = load().filterNot {
                it.userId == userId && it.candidateFingerprint == candidate.decisionFingerprint
            }.toMutableList()
            values += TransitDuplicateStoredDecision(
                userId = userId,
                candidateFingerprint = candidate.decisionFingerprint,
                firstRecordId = candidate.firstRecordId,
                secondRecordId = candidate.secondRecordId,
                decision = decision,
                updatedAtEpochMillis = now()
            )
            val pruned = values.groupBy { it.userId }.values.flatMap { decisions ->
                decisions.sortedByDescending { it.updatedAtEpochMillis }.take(maximumDecisionsPerUser)
            }
            return runCatching { writeRaw(encode(pruned)); true }.getOrDefault(false)
        }
    }

    fun decisionsForUser(userId: String): List<TransitDuplicateStoredDecision> =
        if (!enabled || userId.isBlank()) emptyList()
        else synchronized(lock) {
            load().filter { it.userId == userId }.sortedByDescending { it.updatedAtEpochMillis }
        }

    private fun load(): List<TransitDuplicateStoredDecision> = runCatching {
        val raw = readRaw()?.takeIf { it.isNotBlank() } ?: return@runCatching emptyList()
        STORAGE_JSON.parseToJsonElement(raw).jsonArray.mapNotNull { element ->
            runCatching {
                STORAGE_JSON.decodeFromJsonElement<TransitDuplicateStoredDecision>(element)
            }.getOrNull()?.takeIf {
                it.userId.isNotBlank() && it.candidateFingerprint.isNotBlank()
            }
        }
    }.getOrDefault(emptyList())

    private fun encode(values: List<TransitDuplicateStoredDecision>): String =
        STORAGE_JSON.encodeToString(values)

    companion object {
        private const val FILE_NAME = "transit_duplicate_decisions_encrypted"
        private const val KEY = "decisions"
        private const val TAG = "TransitDuplicateDecisionStore"
        private const val DEFAULT_MAX_PER_USER = 300
        private val STORAGE_JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun create(context: Context, enabled: Boolean): TransitDuplicateDecisionStore {
            if (!enabled) {
                return TransitDuplicateDecisionStore(
                    readRaw = { null },
                    writeRaw = { _ -> },
                    enabled = false
                )
            }
            val appContext = context.applicationContext
            val prefs = openEncryptedStorageWithRecovery(
                openEncrypted = {
                    val masterKey = MasterKey.Builder(appContext)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build()
                    EncryptedSharedPreferences.create(
                        appContext,
                        FILE_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                    )
                },
                resetEncryptedStorage = { appContext.deleteSharedPreferences(FILE_NAME) },
                logError = { message, error -> Log.e(TAG, message, error) }
            )
            return TransitDuplicateDecisionStore(
                readRaw = { prefs.getString(KEY, null) },
                writeRaw = { prefs.edit().putString(KEY, it).apply() },
                enabled = enabled
            )
        }
    }
}
