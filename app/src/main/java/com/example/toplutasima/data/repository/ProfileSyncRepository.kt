package com.example.toplutasima.data.repository

import android.content.Context
import android.util.Log
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.dao.ProfileDao
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.network.firestore.FirestorePersonService
import com.example.toplutasima.network.firestore.FirestorePersonService.PersonShareState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileSyncRepository private constructor(
    private val profileDao: () -> ProfileDao,
    private val requireUserId: () -> String,
    private val currentUserId: () -> String?,
    private val fetchSharedProfiles: suspend () -> List<ProfileEntity>,
    private val fetchShareStates: suspend () -> List<PersonShareState>
) {
    constructor(context: Context) : this(
        profileDao = {
            AppDatabase.getDatabase(context.applicationContext).profileDao()
        },
        requireUserId = CurrentUserProvider::requireUserId,
        currentUserId = CurrentUserProvider::currentUserIdOrNull,
        fetchSharedProfiles = FirestorePersonService::fetchSharedWithTransit,
        fetchShareStates = FirestorePersonService::fetchShareStates
    )

    internal constructor(
        profileDao: ProfileDao,
        requireUserId: () -> String,
        currentUserId: () -> String?,
        fetchSharedProfiles: suspend () -> List<ProfileEntity>,
        fetchShareStates: suspend () -> List<PersonShareState>
    ) : this(
        profileDao = { profileDao },
        requireUserId = requireUserId,
        currentUserId = currentUserId,
        fetchSharedProfiles = fetchSharedProfiles,
        fetchShareStates = fetchShareStates
    )

    suspend fun refreshSharedProfiles(): List<ProfileEntity> = withContext(Dispatchers.IO) {
        val userId = requireUserId()
        val dao = profileDao()
        val localProfiles = dao.getSharedWithTransitProfiles(userId)

        return@withContext try {
            val remoteSharedProfiles = fetchSharedProfiles()
                .map { it.copy(userId = userId) }
            ensureCurrentUser(userId)
            if (remoteSharedProfiles.isNotEmpty()) {
                dao.upsertAll(remoteSharedProfiles)
            }

            reconcileRemoteShareState(dao, remoteSharedProfiles, userId)
            dao.getSharedWithTransitProfiles(userId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Shared profiles could not be refreshed from Firestore", e)
            localProfiles
        }
    }

    private suspend fun reconcileRemoteShareState(
        dao: ProfileDao,
        remoteSharedProfiles: List<ProfileEntity>,
        userId: String
    ) {
        val remoteStates = fetchShareStates().associateBy { it.id }
        ensureCurrentUser(userId)
        val remoteSharedIds = remoteSharedProfiles.mapTo(mutableSetOf()) { it.id }

        dao.getSharedWithTransitProfiles(userId)
            .filter { local ->
                val remoteState = remoteStates[local.id]
                local.id !in remoteSharedIds &&
                    (remoteState == null || !remoteState.sharedWithTransit || remoteState.archived)
            }
            .forEach { local ->
                val remoteState = remoteStates[local.id]
                dao.upsert(
                    local.copy(
                        archived = remoteState?.archived ?: local.archived,
                        sharedWithTransit = false,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
    }

    private fun ensureCurrentUser(expectedUserId: String) {
        if (currentUserId() != expectedUserId) {
            throw CancellationException("Authenticated user changed during profile sync")
        }
    }

    private companion object {
        const val TAG = "ProfileSyncRepository"
    }
}
