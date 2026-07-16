package com.example.toplutasima.repository

import android.content.Context
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.TripProfileLinkEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

class TripProfileLinkRepository(private val appContext: Context? = null) {
    private fun getDatabase() = appContext?.let { AppDatabase.getDatabase(it) }
    private fun getTripDao() = getDatabase()?.tripDao()
    private fun getLinkDao() = getDatabase()?.tripProfileLinkDao()

    suspend fun saveInitialLink(
        tripStableKey: String,
        profileId: String?,
        seatmateNote: String?
    ) = withContext(Dispatchers.IO) {
        if (profileId.isNullOrBlank()) return@withContext
        val userId = CurrentUserProvider.requireUserId()
        getLinkDao()?.upsert(
            TripProfileLinkEntity(
                id = UUID.randomUUID().toString(),
                tripStableKey = tripStableKey,
                profileId = profileId,
                seatmateNote = seatmateNote,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                userId = userId
            )
        )
    }

    suspend fun updateStableKey(
        localTripStableKey: String,
        firestoreDocId: String
    ) = withContext(Dispatchers.IO) {
        if (firestoreDocId.isBlank()) return@withContext
        getLinkDao()?.updateStableKey(
            CurrentUserProvider.requireUserId(),
            localTripStableKey,
            firestoreDocId,
            System.currentTimeMillis()
        )
    }

    suspend fun updateTripProfileLink(
        tripStableKey: String,
        profileId: String?,
        seatmateNote: String?
    ) = withContext(Dispatchers.IO) {
        val userId = CurrentUserProvider.requireUserId()
        val linkDao = getLinkDao() ?: return@withContext
        val localTrip = getTripDao()?.getTripById(userId, tripStableKey)
        val firestoreDocId = localTrip?.firestoreDocId ?: ""

        if (profileId.isNullOrBlank()) {
            linkDao.deleteLinksForTrip(userId, tripStableKey, firestoreDocId)
            return@withContext
        }

        val existingLinks = if (firestoreDocId.isNotBlank()) {
            linkDao.getLinksForTrip(userId, firestoreDocId)
        } else {
            linkDao.getLinksForTrip(userId, tripStableKey)
        }

        if (existingLinks.isNotEmpty()) {
            val existing = existingLinks.first()
            linkDao.upsert(
                existing.copy(
                    profileId = profileId,
                    seatmateNote = seatmateNote,
                    updatedAt = System.currentTimeMillis(),
                    userId = userId
                )
            )
        } else {
            linkDao.upsert(
                TripProfileLinkEntity(
                    id = UUID.randomUUID().toString(),
                    tripStableKey = firestoreDocId.ifBlank { tripStableKey },
                    profileId = profileId,
                    seatmateNote = seatmateNote,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    userId = userId
                )
            )
        }
    }
}
