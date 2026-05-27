package com.example.toplutasima.data.repository

import android.content.Context
import android.util.Log
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.dao.ProfileDao
import com.example.toplutasima.data.local.entity.ProfileEntity
import com.example.toplutasima.network.firestore.FirestorePersonService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileSyncRepository(
    context: Context
) {
    private val appContext = context.applicationContext

    suspend fun refreshSharedProfiles(): List<ProfileEntity> = withContext(Dispatchers.IO) {
        val dao = AppDatabase.getDatabase(appContext).profileDao()
        val localProfiles = dao.getSharedWithTransitProfiles()

        return@withContext try {
            val remoteSharedProfiles = FirestorePersonService.fetchSharedWithTransit()
            if (remoteSharedProfiles.isNotEmpty()) {
                dao.upsertAll(remoteSharedProfiles)
            }

            reconcileRemoteShareState(dao, remoteSharedProfiles)
            dao.getSharedWithTransitProfiles()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Shared profiles could not be refreshed from Firestore", e)
            localProfiles
        }
    }

    private suspend fun reconcileRemoteShareState(
        dao: ProfileDao,
        remoteSharedProfiles: List<ProfileEntity>
    ) {
        val remoteStates = FirestorePersonService.fetchShareStates().associateBy { it.id }
        val remoteSharedIds = remoteSharedProfiles.mapTo(mutableSetOf()) { it.id }

        dao.getSharedWithTransitProfiles()
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

    private companion object {
        const val TAG = "ProfileSyncRepository"
    }
}