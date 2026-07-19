package com.example.toplutasima.transit.sync

import android.content.Context
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.history.TransitChangeEventDraft
import com.example.toplutasima.transit.history.TransitChangeHistoryStore
import com.example.toplutasima.transit.history.TransitChangeOperation
import com.example.toplutasima.transit.history.TransitChangeSource
import com.example.toplutasima.transit.history.TransitHistorySyncStatus

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
            updateLatestWriteHistory(context, action, TransitHistorySyncStatus.PENDING)
        }
    }

    fun onSyncing(context: Context, action: OfflineQueueStore.QueuedAction) {
        if (!receiptEnabled(action)) return
        val store = TransitSyncStatusStore.get(context)
        if (action.isDeleteAction) {
            store.markDeleting(action.userId, action.recordId, action.id)
            appendDeleteHistory(
                context = context,
                action = action,
                operation = TransitChangeOperation.DELETE_SYNC,
                status = TransitHistorySyncStatus.SYNCING,
                deduplicationPrefix = "delete-sync"
            )
        } else {
            store.markSyncing(action.userId, action.recordId, action.id)
            updateLatestWriteHistory(context, action, TransitHistorySyncStatus.SYNCING)
        }
    }

    fun onSynced(context: Context, action: OfflineQueueStore.QueuedAction) {
        if (!receiptEnabled(action)) return
        val store = TransitSyncStatusStore.get(context)
        if (action.isDeleteAction) {
            store.markDeleted(action.userId, action.recordId, action.id)
            appendDeleteHistory(
                context = context,
                action = action,
                operation = TransitChangeOperation.DELETE_SYNC,
                status = TransitHistorySyncStatus.SYNCED,
                deduplicationPrefix = "delete-sync"
            )
        } else {
            store.markSynced(action.userId, action.recordId, action.id)
            updateLatestWriteHistory(context, action, TransitHistorySyncStatus.SYNCED)
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
            appendDeleteHistory(
                context = context,
                action = action,
                operation = TransitChangeOperation.DELETE_SYNC,
                status = TransitHistorySyncStatus.TEMPORARY_ERROR,
                deduplicationPrefix = "delete-sync"
            )
        } else {
            store.markTemporaryError(action.userId, action.recordId, error.message, action.id)
            updateLatestWriteHistory(context, action, TransitHistorySyncStatus.TEMPORARY_ERROR)
        }
    }

    fun onWorkerRetry(context: Context, detail: String) {
        if (!TransitFeatureFlags.SYNC_RECEIPTS && !TransitFeatureFlags.SYNC_DELETE_RECEIPTS) return
        val userId = runCatching { CurrentUserProvider.currentUserIdOrNull() }.getOrNull() ?: return
        TransitSyncStatusStore.get(context).markPendingForUserAsTemporaryError(userId, detail)
        if (TransitFeatureFlags.TRANSIT_CHANGE_HISTORY) {
            TransitChangeHistoryStore.create(context, enabled = true)
                .updatePendingDeleteSyncForUser(userId, TransitHistorySyncStatus.TEMPORARY_ERROR)
        }
    }

    fun onWorkerPermanentFailure(context: Context, detail: String) {
        if (!TransitFeatureFlags.SYNC_RECEIPTS && !TransitFeatureFlags.SYNC_DELETE_RECEIPTS) return
        val userId = runCatching { CurrentUserProvider.currentUserIdOrNull() }.getOrNull() ?: return
        TransitSyncStatusStore.get(context).markPendingForUserAsPermanentError(userId, detail)
        if (TransitFeatureFlags.TRANSIT_CHANGE_HISTORY) {
            TransitChangeHistoryStore.create(context, enabled = true)
                .updatePendingDeleteSyncForUser(userId, TransitHistorySyncStatus.PERMANENT_ERROR)
        }
    }

    private fun receiptEnabled(action: OfflineQueueStore.QueuedAction): Boolean =
        if (action.isDeleteAction) {
            TransitFeatureFlags.SYNC_DELETE_RECEIPTS
        } else {
            TransitFeatureFlags.SYNC_RECEIPTS
        }

    private fun appendDeleteHistory(
        context: Context,
        action: OfflineQueueStore.QueuedAction,
        operation: TransitChangeOperation,
        status: TransitHistorySyncStatus,
        deduplicationPrefix: String
    ) {
        if (!TransitFeatureFlags.TRANSIT_CHANGE_HISTORY) return
        val history = TransitChangeHistoryStore.create(context, enabled = true)
        val result = history.append(
            TransitChangeEventDraft(
                userId = action.userId,
                recordId = action.recordId,
                operation = operation,
                occurredAtEpochMillis = System.currentTimeMillis(),
                source = if (operation == TransitChangeOperation.LOCAL_DELETE) {
                    TransitChangeSource.USER
                } else {
                    TransitChangeSource.SYNC_WORKER
                },
                syncStatus = status,
                deduplicationKey = "$deduplicationPrefix:${action.id}"
            )
        )
        result.event?.let { event ->
            history.updateSyncStatus(action.userId, action.recordId, event.eventId, status)
        }
    }

    private fun updateLatestWriteHistory(
        context: Context,
        action: OfflineQueueStore.QueuedAction,
        status: TransitHistorySyncStatus
    ) {
        if (!TransitFeatureFlags.TRANSIT_CHANGE_HISTORY) return
        TransitChangeHistoryStore.create(context, enabled = true)
            .updateLatestRecordSyncStatus(action.userId, action.recordId, status)
    }
}
