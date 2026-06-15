package com.example.toplutasima

import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity

object TestProfileFixtures {
    fun profile(
        id: String = "profile-1",
        displayName: String = "Mehmet",
        memoryNote: String? = null,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L,
        archived: Boolean = false
    ): ProfileEntity =
        ProfileEntity(
            id = id,
            displayName = displayName,
            memoryNote = memoryNote,
            createdAt = createdAt,
            updatedAt = updatedAt,
            archived = archived
        )

    fun tripProfileLink(
        id: String = "link-1",
        tripStableKey: String = "trip-key-123",
        profileId: String = "profile-1",
        seatmateNote: String? = null,
        createdAt: Long = 1000L,
        updatedAt: Long = 1000L
    ): TripProfileLinkEntity =
        TripProfileLinkEntity(
            id = id,
            tripStableKey = tripStableKey,
            profileId = profileId,
            seatmateNote = seatmateNote,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
}
