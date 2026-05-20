package com.example.toplutasima.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trip_profile_links",
    foreignKeys = [
        ForeignKey(
            entity = ProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tripStableKey"]),
        Index(value = ["profileId"])
    ]
)
data class TripProfileLinkEntity(
    @PrimaryKey
    val id: String,
    val tripStableKey: String,
    val profileId: String,
    val seatmateNote: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
