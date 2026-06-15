package com.example.toplutasima.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey
    val id: String,
    val displayName: String,
    val memoryNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val sharedWithTransit: Boolean = false  // Bellek uygulamasından paylaşılmış mı?
)
