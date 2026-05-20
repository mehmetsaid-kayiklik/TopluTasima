package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val nameKind: String, // "NICKNAME", "FIRST_NAME", "UNKNOWN"
    val memoryNote: String? = null,
    val birthHint: String? = null,
    val infoSource: String, // "ASKED", "OBSERVED", "UNKNOWN"
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false
)
