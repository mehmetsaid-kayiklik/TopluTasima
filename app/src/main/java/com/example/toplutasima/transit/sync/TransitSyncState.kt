package com.example.toplutasima.transit.sync

import kotlinx.serialization.Serializable

@Serializable
enum class TransitSyncPhase {
    LOCAL_SAVING,
    LOCAL_SAFE,
    PENDING,
    SYNCING,
    SYNCED,
    TEMPORARY_ERROR,
    PERMANENT_ERROR,
    LOCAL_DELETED,
    DELETE_PENDING,
    DELETING,
    DELETED,
    DELETE_TEMPORARY_ERROR,
    DELETE_PERMANENT_ERROR
}

@Serializable
enum class TransitSyncOperation {
    UPSERT,
    DELETE
}

@Serializable
enum class TransitDeleteDisposition {
    PENDING_REMOTE,
    REMOTE_DELETED,
    LOCAL_ONLY
}

/** Schema-free deletion metadata persisted with the UID-scoped receipt. */
@Serializable
data class TransitDeleteMetadata(
    val firestoreDocId: String? = null,
    val initiatedAtEpochMillis: Long,
    val disposition: TransitDeleteDisposition = TransitDeleteDisposition.PENDING_REMOTE
)

@Serializable
data class TransitSyncState(
    val userId: String,
    val recordId: String,
    val phase: TransitSyncPhase,
    val updatedAtEpochMillis: Long,
    val attemptCount: Int = 0,
    val queueActionId: String? = null,
    val detail: String? = null,
    val operation: TransitSyncOperation = TransitSyncOperation.UPSERT,
    val deleteMetadata: TransitDeleteMetadata? = null
)
