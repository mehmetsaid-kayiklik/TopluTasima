package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.toplutasima.data.local.entity.ProfileEntity

@Dao
interface ProfileDao {
    @Insert
    suspend fun insert(profile: ProfileEntity)

    @Update
    suspend fun update(profile: ProfileEntity)

    @Transaction
    suspend fun upsert(profile: ProfileEntity) {
        require(profile.userId.isNotBlank()) { "Profile userId must not be blank" }
        if (getProfileById(profile.userId, profile.id) == null) {
            insert(profile)
        } else {
            update(profile)
        }
    }

    @Transaction
    suspend fun upsertAll(profiles: List<ProfileEntity>) {
        profiles.forEach { profile ->
            require(profile.userId.isNotBlank()) { "Profile userId must not be blank" }
            if (getProfileById(profile.userId, profile.id) == null) {
                insert(profile)
            } else {
                update(profile)
            }
        }
    }

    @Query("SELECT * FROM profiles WHERE userId = :userId ORDER BY displayName ASC")
    suspend fun getAllProfiles(userId: String): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE userId = :userId AND archived = 0 AND sharedWithTransit = 1 ORDER BY displayName ASC")
    suspend fun getSharedWithTransitProfiles(userId: String): List<ProfileEntity>

    @Query("SELECT * FROM profiles WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getProfileById(userId: String, id: String): ProfileEntity?

    @Query("DELETE FROM profiles WHERE userId = :userId AND id = :id")
    suspend fun deleteProfile(userId: String, id: String)

}
