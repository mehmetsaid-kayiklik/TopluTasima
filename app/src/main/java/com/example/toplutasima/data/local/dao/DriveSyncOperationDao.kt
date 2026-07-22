package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveSyncOperationDao {
    @Upsert
    suspend fun upsert(operation: DriveSyncOperationEntity)

    @Query(
        "SELECT * FROM drive_sync_operations " +
            "WHERE userId = :userId AND entityType = :entityType AND recordId = :recordId " +
            "LIMIT 1"
    )
    suspend fun getOperation(
        userId: String,
        entityType: String,
        recordId: String
    ): DriveSyncOperationEntity?

    @Query(
        """
        SELECT * FROM drive_sync_operations
        WHERE userId = :userId
            AND retryEligible = 1
            AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY createdAt ASC, updatedAt ASC, recordId ASC
        LIMIT :limit
        """
    )
    suspend fun getRetryEligibleOperations(
        userId: String,
        now: Long,
        limit: Int
    ): List<DriveSyncOperationEntity>

    @Query(
        "SELECT COUNT(*) FROM drive_sync_operations WHERE userId = :userId"
    )
    fun observePendingCount(userId: String): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM drive_sync_operations WHERE userId = :userId"
    )
    suspend fun pendingCount(userId: String): Int

    @Query(
        "DELETE FROM drive_sync_operations " +
            "WHERE userId = :userId AND entityType = :entityType AND recordId = :recordId"
    )
    suspend fun deleteForRecord(userId: String, entityType: String, recordId: String): Int

    @Query("DELETE FROM drive_sync_operations WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String): Int

    @Query("DELETE FROM drive_sync_operations WHERE userId != :userId")
    suspend fun deleteAllExceptUser(userId: String): Int

    @Query("DELETE FROM drive_sync_operations")
    suspend fun deleteAll(): Int

    @Query(
        """
        UPDATE drive_sync_operations
        SET attemptCount = attemptCount + 1,
            updatedAt = :updatedAt,
            lastErrorCode = :lastErrorCode,
            retryEligible = :retryEligible,
            nextAttemptAt = :nextAttemptAt
        WHERE userId = :userId
            AND entityType = :entityType
            AND recordId = :recordId
            AND operationId = :operationId
        """
    )
    suspend fun recordAttemptIfCurrent(
        userId: String,
        entityType: String,
        recordId: String,
        operationId: String,
        updatedAt: Long,
        lastErrorCode: String?,
        retryEligible: Boolean,
        nextAttemptAt: Long?
    ): Int

    @Query(
        """
        DELETE FROM drive_sync_operations
        WHERE userId = :userId
            AND entityType = :entityType
            AND recordId = :recordId
            AND operationId = :operationId
        """
    )
    suspend fun deleteIfCurrent(
        userId: String,
        entityType: String,
        recordId: String,
        operationId: String
    ): Int
}
