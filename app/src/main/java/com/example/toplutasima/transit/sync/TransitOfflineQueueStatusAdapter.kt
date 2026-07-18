package com.example.toplutasima.transit.sync

import android.content.Context
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.transit.TransitFeatureFlags

/** Keeps OfflineQueueStore free of UI concerns while translating queue lifecycle events. */
object TransitOfflineQueueStatusAdapter {
    fun onUserChanged(context: Context, userId: String?) {
        if (!TransitFeatureFlags.SYNC_RECEIPTS && !TransitFeatureFlags.SYNC_DELETE_RECEIPTS) return
        TransitSyncStatusStore.get(context).onUserChanged(userId)
    }

    fun onQueued(context: Context, action: OfflineQueueStore.QueuedAction) {
        if (!receiptEnabled(action)) return
        val store = TransitSyncStatusStore.get(context)
        if (action.isDeleteAction) {
            store.markDeletePending(
                userId = action.userId,
                recordId = action.recordId,
                firestoreDocId = action.deleteFirestoreDocId,
                queueActionId = action.id
            )
        } else {
            store.markPending(
                userId = action.userId,
                recordId = action.recordId,
                queueActionId = action.id
            )
        }
    }

    fun onSyncing(context: Context, action: OfflineQueueStore.QueuedAction) {
        if (!receiptEnabled(action)) return
        val store = TransitSyncStatusStore.get(context)
        if (action.isDeleteAction) {
            store.markDeleting(action.userId, action.recordId, action.id)
        } else {
            store.markSyncing(action.userId, action.recordId, action.id)
        }
    }

    fun onSynced(context: Context, action: OfflineQueueStore.QueuedAction) {
        if (!receiptEnabled(action)) return
        val store = TransitSyncStatusStore.get(context)
        if (action.isDeleteAction) {
            store.markDeleted(action.userId, action.recordId, action.id)
        } else {
            store.markSynced(action.userId, action.recordId, action.id)
        }
    }

    fun onTemporaryFailure(
        context: Context,
        action: OfflineQueueStore.QueuedAction,
        error: Throwable
    ) {
        if (!receiptEnabled(action)) return
        val store = TransitSyncStatusStore.get(context)
        if (action.isDeleteAction) {
            store.markDeleteTemporaryError(action.userId, action.recordId, error.message, action.id)
        } else {
            store.markTemporaryError(action.userId, action.recordId, error.message, action.id)
        }
    }

    fun onWorkerRetry(context: Context, detail: String) {
        if (!TransitFeatureFlags.SYNC_RECEIPTS && !TransitFeatureFlags.SYNC_DELETE_RECEIPTS) return
        val userId = runCatching { CurrentUserProvider.currentUserIdOrNull() }.getOrNull() ?: return
        TransitSyncStatusStore.get(context).markPendingForUserAsTemporaryError(userId, detail)
    }

    fun onWorkerPermanentFailure(context: Context, detail: String) {
        if (!TransitFeatureFlags.SYNC_RECEIPTS && !TransitFeatureFlags.SYNC_DELETE_RECEIPTS) return
        val userId = runCatching { CurrentUserProvider.currentUserIdOrNull() }.getOrNull() ?: return
        TransitSyncStatusStore.get(context).markPendingForUserAsPermanentError(userId, detail)
    }

    private fun receiptEnabled(action: OfflineQueueStore.QueuedAction): Boolean =
        if (action.isDeleteAction) {
            TransitFeatureFlags.SYNC_DELETE_RECEIPTS
        } else {
            TransitFeatureFlags.SYNC_RECEIPTS
        }
}
