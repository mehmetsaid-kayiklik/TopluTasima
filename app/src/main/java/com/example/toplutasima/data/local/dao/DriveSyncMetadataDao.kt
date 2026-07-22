package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveSyncMetadataEntity

@Dao
interface DriveSyncMetadataDao {
    @Upsert
    suspend fun upsert(metadata: DriveSyncMetadataEntity)

    @Query("SELECT * FROM drive_sync_metadata WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: String): DriveSyncMetadataEntity?

    @Query("DELETE FROM drive_sync_metadata WHERE userId = :userId")
    suspend fun deleteForUser(userId: String): Int

    @Query("DELETE FROM drive_sync_metadata WHERE userId != :userId")
    suspend fun deleteAllExceptUser(userId: String): Int

    @Query("DELETE FROM drive_sync_metadata")
    suspend fun deleteAll(): Int
}
