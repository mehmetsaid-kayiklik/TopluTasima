package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "drive_sync_receipts",
    primaryKeys = ["userId", "receiptId"],
    indices = [
        Index(value = ["userId", "startedAt"]),
        Index(value = ["userId", "status", "startedAt"])
    ]
)
data class DriveSyncReceiptEntity(
    val userId: String,
    val receiptId: String,
    val kind: String,
    val entityType: String? = null,
    val recordId: String? = null,
    val operationType: String? = null,
    val status: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val attemptCount: Int = 0,
    val errorCode: String? = null
)
