package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** UID-scoped pull cursor. A completed initial hydration is tracked independently of cursors. */
@Entity(tableName = "drive_sync_metadata")
data class DriveSyncMetadataEntity(
    @PrimaryKey val userId: String,
    val initialHydrationCompleted: Boolean = false,
    val vehicleCursorSeconds: Long? = null,
    val vehicleCursorNanos: Int? = null,
    val vehicleCursorDocumentId: String? = null,
    val tripCursorSeconds: Long? = null,
    val tripCursorNanos: Int? = null,
    val tripCursorDocumentId: String? = null,
    val lastSuccessfulPullAt: Long? = null,
    val updatedAt: Long
)
