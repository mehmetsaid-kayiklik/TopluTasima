package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "drive_sync_operations",
    primaryKeys = ["userId", "entityType", "recordId"],
    indices = [
        Index(value = ["userId", "operationId"], unique = true),
        Index(value = ["userId", "retryEligible", "nextAttemptAt", "createdAt"])
    ]
)
data class DriveSyncOperationEntity(
    val operationId: String,
    val userId: String,
    val entityType: String,
    val recordId: String,
    val operationType: String,
    val createdAt: Long,
    val updatedAt: Long,
    val attemptCount: Int = 0,
    val lastErrorCode: String? = null,
    val retryEligible: Boolean = true,
    val nextAttemptAt: Long? = null
)
