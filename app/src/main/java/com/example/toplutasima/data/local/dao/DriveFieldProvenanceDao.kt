package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveFieldProvenanceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveFieldProvenanceDao {
    @Upsert
    suspend fun upsertAll(provenance: List<DriveFieldProvenanceEntity>)

    @Query(
        "SELECT * FROM drive_field_provenance " +
            "WHERE userId = :userId AND entityType = :entityType AND recordId = :recordId " +
            "ORDER BY fieldName ASC"
    )
    suspend fun getForRecord(
        userId: String,
        entityType: String,
        recordId: String
    ): List<DriveFieldProvenanceEntity>

    @Query(
        "SELECT * FROM drive_field_provenance " +
            "WHERE userId = :userId AND entityType = :entityType AND recordId = :recordId " +
            "ORDER BY fieldName ASC"
    )
    fun observeForRecord(
        userId: String,
        entityType: String,
        recordId: String
    ): Flow<List<DriveFieldProvenanceEntity>>

    @Query("SELECT * FROM drive_field_provenance WHERE userId = :userId")
    fun observeForUser(userId: String): Flow<List<DriveFieldProvenanceEntity>>

    @Query(
        "DELETE FROM drive_field_provenance " +
            "WHERE userId = :userId AND entityType = :entityType AND recordId = :recordId"
    )
    suspend fun deleteForRecord(userId: String, entityType: String, recordId: String): Int

    @Query("DELETE FROM drive_field_provenance WHERE userId = :userId")
    suspend fun deleteForUser(userId: String): Int

    @Query("DELETE FROM drive_field_provenance WHERE userId != :userId")
    suspend fun deleteAllExceptUser(userId: String): Int

    @Query("DELETE FROM drive_field_provenance")
    suspend fun deleteAll(): Int
}
