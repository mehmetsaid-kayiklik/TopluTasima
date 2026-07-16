package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "trip_profile_links",
    primaryKeys = ["userId", "id"],
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["userId", "id"],
            childColumns = ["userId", "profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId", "tripStableKey"]),
        Index(value = ["userId", "profileId"])
    ]
)
data class TripProfileLinkEntity(
    val id: String,
    val tripStableKey: String,
    val profileId: String,
    val seatmateNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val userId: String = ""
)
