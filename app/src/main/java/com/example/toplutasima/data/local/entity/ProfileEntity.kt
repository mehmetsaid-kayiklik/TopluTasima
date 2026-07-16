package com.example.toplutasima.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "profiles",
    primaryKeys = ["userId", "id"]
)
data class ProfileEntity(
    val id: String,
    val displayName: String,
    val memoryNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sharedWithTransit: Boolean = false,  // Bellek uygulamasından paylaşılmış mı?
    val userId: String = ""
)
