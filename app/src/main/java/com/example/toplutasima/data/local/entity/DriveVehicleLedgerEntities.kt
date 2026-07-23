package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "drive_odometer_entries",
    primaryKeys = ["ownerUid", "odometerEntryId"],
    indices = [
        Index(value = ["ownerUid", "vehicleId", "deletedAt", "observedAt", "odometerEntryId"]),
        Index(value = ["ownerUid", "vehicleId", "odometerSeriesId", "odometerMeters"]),
        Index(
            value = ["ownerUid", "sourceRecordType", "sourceRecordId", "readingRole"]
        ),
        Index(value = ["ownerUid", "syncState", "clientUpdatedAt"]),
        Index(value = ["ownerUid", "operationId"], unique = true)
    ]
)
data class DriveOdometerEntryEntity(
    val ownerUid: String,
    val odometerEntryId: String,
    val vehicleId: String,
    val observedAt: Long?,
    val odometerMeters: Long,
    val quality: String,
    val readingRole: String,
    val odometerSeriesId: String,
    val sourceRecordType: String?,
    val sourceRecordId: String?,
    val correctionOfEntryId: String?,
    val resetReason: String?,
    val notes: String?,
    val schemaVersion: Int,
    val revision: Long,
    val operationId: String,
    val source: String,
    val createdAt: Long,
    val clientUpdatedAt: Long,
    val serverUpdatedAtSeconds: Long?,
    val serverUpdatedAtNanos: Int?,
    val deletedAt: Long?,
    val syncState: String,
    val healthCode: String?
)

@Entity(
    tableName = "drive_expenses",
    primaryKeys = ["ownerUid", "expenseId"],
    indices = [
        Index(value = ["ownerUid", "vehicleId", "deletedAt", "occurredAt", "expenseId"]),
        Index(value = ["ownerUid", "vehicleId", "category", "occurredAt"]),
        Index(value = ["ownerUid", "vehicleId", "currencyCode", "currencyExponent", "occurredAt"]),
        Index(value = ["ownerUid", "syncState", "clientUpdatedAt"]),
        Index(value = ["ownerUid", "operationId"], unique = true),
        Index(value = ["ownerUid", "duplicateFingerprint"])
    ]
)
data class DriveExpenseEntity(
    val ownerUid: String,
    val expenseId: String,
    val vehicleId: String,
    val occurredAt: Long,
    val category: String,
    val transactionKind: String,
    val amountMinor: Long,
    val currencyCode: String,
    val currencyExponent: Int,
    val vendorName: String?,
    val notes: String?,
    val referenceNumber: String?,
    val periodStartEpochDay: Long?,
    val periodEndEpochDay: Long?,
    val dueEpochDay: Long?,
    val odometerEntryId: String?,
    val odometerMetersSnapshot: Long?,
    val splitGroupId: String?,
    val duplicateFingerprint: String?,
    val relatedExpenseId: String?,
    val schemaVersion: Int,
    val revision: Long,
    val operationId: String,
    val source: String,
    val createdAt: Long,
    val clientUpdatedAt: Long,
    val serverUpdatedAtSeconds: Long?,
    val serverUpdatedAtNanos: Int?,
    val deletedAt: Long?,
    val syncState: String,
    val healthCode: String?
)

@Entity(
    tableName = "drive_reminders",
    primaryKeys = ["ownerUid", "reminderId"],
    indices = [
        Index(value = ["ownerUid", "vehicleId", "status", "dueEpochDay"]),
        Index(value = ["ownerUid", "vehicleId", "status", "dueOdometerMeters"]),
        Index(value = ["ownerUid", "linkedServiceRecordId"]),
        Index(value = ["ownerUid", "syncState", "clientUpdatedAt"]),
        Index(value = ["ownerUid", "operationId"], unique = true)
    ]
)
data class DriveReminderEntity(
    val ownerUid: String,
    val reminderId: String,
    val vehicleId: String,
    val title: String,
    val reminderType: String,
    val status: String,
    val dueEpochDay: Long?,
    val dueOdometerMeters: Long?,
    val recurrenceMonths: Int?,
    val recurrenceDistanceMeters: Long?,
    val recurrenceAnchor: String,
    val leadDays: Int?,
    val leadDistanceMeters: Long?,
    val snoozedUntilEpochDay: Long?,
    val linkedServiceRecordId: String?,
    val lastCompletedServiceRecordId: String?,
    val lastCompletedAt: Long?,
    val lastCompletedOdometerMeters: Long?,
    val notes: String?,
    val schemaVersion: Int,
    val revision: Long,
    val operationId: String,
    val source: String,
    val createdAt: Long,
    val clientUpdatedAt: Long,
    val serverUpdatedAtSeconds: Long?,
    val serverUpdatedAtNanos: Int?,
    val deletedAt: Long?,
    val syncState: String,
    val healthCode: String?
)

@Entity(
    tableName = "drive_ledger_operations",
    primaryKeys = ["ownerUid", "operationId"],
    indices = [
        Index(
            value = ["ownerUid", "entityType", "recordId", "targetRevision"],
            unique = true
        ),
        Index(value = ["ownerUid", "state", "nextAttemptAt", "createdAt"]),
        Index(value = ["ownerUid", "vehicleId", "createdAt"]),
        Index(value = ["ownerUid", "logicalBatchId"])
    ]
)
data class DriveLedgerOperationEntity(
    val ownerUid: String,
    val operationId: String,
    val logicalBatchId: String,
    val entityType: String,
    val recordId: String,
    val vehicleId: String,
    val kind: String,
    val targetRevision: Long,
    val state: String,
    val attemptCount: Int,
    val nextAttemptAt: Long,
    val claimedAt: Long?,
    val claimedBy: String?,
    val safeErrorCode: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "drive_ledger_sync_metadata",
    primaryKeys = ["ownerUid", "collectionType"]
)
data class DriveLedgerSyncMetadataEntity(
    val ownerUid: String,
    val collectionType: String,
    val initialHydrationCompleted: Boolean,
    val cursorSeconds: Long?,
    val cursorNanos: Int?,
    val cursorDocumentId: String?,
    val lastSuccessfulPullAt: Long?,
    val updatedAt: Long
)

@Entity(
    tableName = "drive_ledger_sync_receipts",
    primaryKeys = ["ownerUid", "receiptId"],
    indices = [
        Index(value = ["ownerUid", "operationId"], unique = true),
        Index(value = ["ownerUid", "entityType", "recordId", "createdAt"]),
        Index(value = ["ownerUid", "status", "createdAt"]),
        Index(value = ["ownerUid", "vehicleId", "createdAt"])
    ]
)
data class DriveLedgerSyncReceiptEntity(
    val ownerUid: String,
    val receiptId: String,
    val operationId: String,
    val logicalBatchId: String?,
    val entityType: String,
    val recordId: String,
    val vehicleId: String,
    val kind: String,
    val status: String,
    val provenance: String,
    val revision: Long?,
    val winningOperationId: String?,
    val attemptCount: Int,
    val createdAt: Long,
    val finishedAt: Long?,
    val safeErrorCode: String?
)

@Entity(
    tableName = "drive_ledger_conflicts",
    primaryKeys = ["ownerUid", "conflictId"],
    indices = [
        Index(value = ["ownerUid", "entityType", "recordId", "resolvedAt"]),
        Index(value = ["ownerUid", "vehicleId", "createdAt"]),
        Index(value = ["ownerUid", "localOperationId"]),
        Index(value = ["ownerUid", "remoteOperationId"])
    ]
)
data class DriveLedgerConflictEntity(
    val ownerUid: String,
    val conflictId: String,
    val entityType: String,
    val recordId: String,
    val vehicleId: String,
    val localOperationId: String,
    val remoteOperationId: String,
    val localRevision: Long,
    val remoteRevision: Long,
    val localSnapshotJson: String,
    val remoteSnapshotJson: String,
    val winnerOperationId: String,
    val reason: String,
    val createdAt: Long,
    val resolvedAt: Long?
)
