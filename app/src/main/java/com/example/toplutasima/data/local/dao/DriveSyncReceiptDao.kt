package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveSyncReceiptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveSyncReceiptDao {
    @Upsert
    suspend fun upsert(receipt: DriveSyncReceiptEntity)

    @Query(
        "SELECT * FROM drive_sync_receipts WHERE userId = :userId " +
            "ORDER BY startedAt DESC, receiptId DESC LIMIT :limit"
    )
    fun observeRecent(userId: String, limit: Int): Flow<List<DriveSyncReceiptEntity>>

    @Query(
        "SELECT * FROM drive_sync_receipts " +
            "WHERE userId = :userId AND receiptId = :receiptId LIMIT 1"
    )
    suspend fun get(userId: String, receiptId: String): DriveSyncReceiptEntity?

    @Query(
        "DELETE FROM drive_sync_receipts WHERE userId = :userId AND receiptId NOT IN " +
            "(SELECT receiptId FROM drive_sync_receipts WHERE userId = :userId " +
            "ORDER BY startedAt DESC, receiptId DESC LIMIT :keepCount)"
    )
    suspend fun prune(userId: String, keepCount: Int): Int

    @Query("DELETE FROM drive_sync_receipts WHERE userId = :userId")
    suspend fun deleteForUser(userId: String): Int

    @Query("DELETE FROM drive_sync_receipts WHERE userId != :userId")
    suspend fun deleteAllExceptUser(userId: String): Int

    @Query("DELETE FROM drive_sync_receipts")
    suspend fun deleteAll(): Int
}
