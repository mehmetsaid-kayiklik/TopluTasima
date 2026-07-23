package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveExpenseEntity
import com.example.toplutasima.data.local.entity.DriveLedgerConflictEntity
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveOdometerEntryEntity
import com.example.toplutasima.data.local.entity.DriveReminderEntity
import kotlinx.coroutines.flow.Flow

data class DriveExpenseSummaryRow(
    val category: String,
    val currencyCode: String,
    val currencyExponent: Int,
    val signedAmountMinor: Long,
    val recordCount: Int
)

@Dao
interface DriveOdometerEntryDao {
    @Upsert
    suspend fun upsert(entity: DriveOdometerEntryEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DriveOdometerEntryEntity>)

    @Query("SELECT * FROM drive_odometer_entries WHERE ownerUid = :ownerUid AND odometerEntryId = :entryId LIMIT 1")
    suspend fun get(ownerUid: String, entryId: String): DriveOdometerEntryEntity?

    @Query("SELECT * FROM drive_odometer_entries WHERE ownerUid = :ownerUid AND odometerEntryId = :entryId AND deletedAt IS NULL LIMIT 1")
    suspend fun getActive(ownerUid: String, entryId: String): DriveOdometerEntryEntity?

    @Query(
        """
        SELECT * FROM drive_odometer_entries
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
        ORDER BY CASE WHEN observedAt IS NULL THEN 1 ELSE 0 END,
                 observedAt DESC, clientUpdatedAt DESC, odometerEntryId DESC
        """
    )
    fun observeHistory(ownerUid: String, vehicleId: String): Flow<List<DriveOdometerEntryEntity>>

    @Query("SELECT * FROM drive_odometer_entries WHERE ownerUid = :ownerUid ORDER BY vehicleId, observedAt, odometerEntryId")
    fun observeAll(ownerUid: String): Flow<List<DriveOdometerEntryEntity>>

    @Query("SELECT * FROM drive_odometer_entries WHERE ownerUid = :ownerUid")
    suspend fun getAll(ownerUid: String): List<DriveOdometerEntryEntity>

    @Query(
        """
        WITH active_series AS (
            SELECT odometerSeriesId
            FROM drive_odometer_entries
            WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
              AND deletedAt IS NULL AND observedAt IS NOT NULL
            ORDER BY observedAt DESC, clientUpdatedAt DESC, odometerEntryId DESC
            LIMIT 1
        )
        SELECT * FROM drive_odometer_entries
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
          AND deletedAt IS NULL AND observedAt IS NOT NULL
          AND odometerSeriesId = (SELECT odometerSeriesId FROM active_series)
          AND quality IN ('CONFIRMED', 'ESTIMATED')
        ORDER BY CASE quality WHEN 'CONFIRMED' THEN 0 ELSE 1 END,
                 observedAt DESC,
                 COALESCE(serverUpdatedAtSeconds, -1) DESC,
                 COALESCE(serverUpdatedAtNanos, -1) DESC,
                 operationId DESC,
                 odometerEntryId DESC
        LIMIT 1
        """
    )
    fun observeCurrent(ownerUid: String, vehicleId: String): Flow<DriveOdometerEntryEntity?>

    @Query(
        """
        SELECT * FROM drive_odometer_entries
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
          AND deletedAt IS NULL AND observedAt IS NOT NULL
        ORDER BY observedAt DESC, clientUpdatedAt DESC, odometerEntryId DESC
        LIMIT 1
        """
    )
    suspend fun getLatestDated(ownerUid: String, vehicleId: String): DriveOdometerEntryEntity?

    @Query(
        """
        SELECT * FROM drive_odometer_entries
        WHERE ownerUid = :ownerUid AND sourceRecordType = :sourceType
          AND sourceRecordId = :sourceId AND readingRole = :readingRole
        LIMIT 1
        """
    )
    suspend fun getBySource(
        ownerUid: String,
        sourceType: String,
        sourceId: String,
        readingRole: String
    ): DriveOdometerEntryEntity?

    @Query("DELETE FROM drive_odometer_entries WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    @Query("DELETE FROM drive_odometer_entries WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String)

    @Query("DELETE FROM drive_odometer_entries")
    suspend fun deleteAll()
}

@Dao
interface DriveExpenseDao {
    @Upsert
    suspend fun upsert(entity: DriveExpenseEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DriveExpenseEntity>)

    @Query("SELECT * FROM drive_expenses WHERE ownerUid = :ownerUid AND expenseId = :expenseId LIMIT 1")
    suspend fun get(ownerUid: String, expenseId: String): DriveExpenseEntity?

    @Query(
        """
        SELECT * FROM drive_expenses
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
          AND occurredAt >= :fromInclusive AND occurredAt < :toExclusive
        ORDER BY occurredAt DESC, expenseId DESC
        """
    )
    fun observeByVehicleAndDate(
        ownerUid: String,
        vehicleId: String,
        fromInclusive: Long,
        toExclusive: Long
    ): Flow<List<DriveExpenseEntity>>

    @Query("SELECT * FROM drive_expenses WHERE ownerUid = :ownerUid ORDER BY vehicleId, occurredAt, expenseId")
    fun observeAll(ownerUid: String): Flow<List<DriveExpenseEntity>>

    @Query("SELECT * FROM drive_expenses WHERE ownerUid = :ownerUid")
    suspend fun getAll(ownerUid: String): List<DriveExpenseEntity>

    @Query(
        """
        SELECT category, currencyCode, currencyExponent,
               SUM(CASE WHEN transactionKind IN ('REFUND', 'CREDIT')
                        THEN -amountMinor ELSE amountMinor END) AS signedAmountMinor,
               COUNT(*) AS recordCount
        FROM drive_expenses
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
          AND deletedAt IS NULL
          AND occurredAt >= :fromInclusive AND occurredAt < :toExclusive
        GROUP BY category, currencyCode, currencyExponent
        ORDER BY currencyCode, currencyExponent, category
        """
    )
    fun observeSummary(
        ownerUid: String,
        vehicleId: String,
        fromInclusive: Long,
        toExclusive: Long
    ): Flow<List<DriveExpenseSummaryRow>>

    @Query(
        """
        SELECT * FROM drive_expenses
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
          AND duplicateFingerprint = :fingerprint AND deletedAt IS NULL
          AND expenseId != :excludeExpenseId
        ORDER BY occurredAt DESC
        """
    )
    suspend fun duplicateCandidates(
        ownerUid: String,
        vehicleId: String,
        fingerprint: String,
        excludeExpenseId: String
    ): List<DriveExpenseEntity>

    @Query("DELETE FROM drive_expenses WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    @Query("DELETE FROM drive_expenses WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String)

    @Query("DELETE FROM drive_expenses")
    suspend fun deleteAll()
}

@Dao
interface DriveReminderDao {
    @Upsert
    suspend fun upsert(entity: DriveReminderEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DriveReminderEntity>)

    @Query("SELECT * FROM drive_reminders WHERE ownerUid = :ownerUid AND reminderId = :reminderId LIMIT 1")
    suspend fun get(ownerUid: String, reminderId: String): DriveReminderEntity?

    @Query(
        """
        SELECT * FROM drive_reminders
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId AND deletedAt IS NULL
        ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'SNOOZED' THEN 1 ELSE 2 END,
                 COALESCE(dueEpochDay, 9223372036854775807), reminderId
        """
    )
    fun observeForVehicle(ownerUid: String, vehicleId: String): Flow<List<DriveReminderEntity>>

    @Query("SELECT * FROM drive_reminders WHERE ownerUid = :ownerUid ORDER BY vehicleId, reminderId")
    fun observeAll(ownerUid: String): Flow<List<DriveReminderEntity>>

    @Query("SELECT * FROM drive_reminders WHERE ownerUid = :ownerUid")
    suspend fun getAll(ownerUid: String): List<DriveReminderEntity>

    @Query(
        """
        SELECT * FROM drive_reminders
        WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId
          AND deletedAt IS NULL
          AND (status = 'ACTIVE' OR
               (status = 'SNOOZED' AND snoozedUntilEpochDay <= :todayEpochDay))
          AND (
              (dueEpochDay IS NOT NULL AND dueEpochDay <= :todayEpochDay)
              OR (dueOdometerMeters IS NOT NULL AND :currentOdometerMeters IS NOT NULL
                  AND dueOdometerMeters <= :currentOdometerMeters)
          )
        ORDER BY COALESCE(dueEpochDay, 9223372036854775807), reminderId
        """
    )
    fun observeDue(
        ownerUid: String,
        vehicleId: String,
        todayEpochDay: Long,
        currentOdometerMeters: Long?
    ): Flow<List<DriveReminderEntity>>

    @Query("DELETE FROM drive_reminders WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    @Query("DELETE FROM drive_reminders WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String)

    @Query("DELETE FROM drive_reminders")
    suspend fun deleteAll()
}

@Dao
interface DriveLedgerOperationDao {
    @Upsert
    suspend fun upsert(entity: DriveLedgerOperationEntity)

    @Query("SELECT * FROM drive_ledger_operations WHERE ownerUid = :ownerUid AND operationId = :operationId LIMIT 1")
    suspend fun get(ownerUid: String, operationId: String): DriveLedgerOperationEntity?

    @Query(
        """
        SELECT COALESCE(MAX(targetRevision), 0) FROM drive_ledger_operations
        WHERE ownerUid = :ownerUid AND entityType = :entityType AND recordId = :recordId
        """
    )
    suspend fun highestTargetRevision(ownerUid: String, entityType: String, recordId: String): Long

    @Query(
        """
        SELECT * FROM drive_ledger_operations
        WHERE ownerUid = :ownerUid AND (
          (state IN ('PENDING', 'RETRY') AND nextAttemptAt <= :now)
          OR (state = 'RUNNING' AND claimedAt IS NOT NULL AND claimedAt <= :now - 900000)
        )
        ORDER BY createdAt, operationId LIMIT :limit
        """
    )
    suspend fun pending(ownerUid: String, now: Long, limit: Int): List<DriveLedgerOperationEntity>

    @Query(
        """
        UPDATE drive_ledger_operations
        SET state = 'RUNNING', claimedAt = :claimedAt, claimedBy = :claimedBy, updatedAt = :claimedAt
        WHERE ownerUid = :ownerUid AND operationId = :operationId
          AND (
            (state IN ('PENDING', 'RETRY') AND (claimedAt IS NULL OR claimedAt < :staleBefore))
            OR (state = 'RUNNING' AND claimedAt IS NOT NULL AND claimedAt < :staleBefore)
          )
        """
    )
    suspend fun claim(
        ownerUid: String,
        operationId: String,
        claimedAt: Long,
        claimedBy: String,
        staleBefore: Long
    ): Int

    @Query(
        """
        UPDATE drive_ledger_operations
        SET state = :state, attemptCount = :attemptCount, nextAttemptAt = :nextAttemptAt,
            claimedAt = NULL, claimedBy = NULL, safeErrorCode = :safeErrorCode, updatedAt = :updatedAt
        WHERE ownerUid = :ownerUid AND operationId = :operationId AND claimedBy = :claimedBy
        """
    )
    suspend fun finishClaim(
        ownerUid: String,
        operationId: String,
        claimedBy: String,
        state: String,
        attemptCount: Int,
        nextAttemptAt: Long,
        safeErrorCode: String?,
        updatedAt: Long
    ): Int

    @Query(
        """
        UPDATE drive_ledger_operations
        SET state = 'CONFLICT', claimedAt = NULL, claimedBy = NULL,
            safeErrorCode = :safeErrorCode, updatedAt = :updatedAt
        WHERE ownerUid = :ownerUid AND operationId = :operationId
        """
    )
    suspend fun markConflict(ownerUid: String, operationId: String, safeErrorCode: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM drive_ledger_operations WHERE ownerUid = :ownerUid AND state IN ('PENDING','RUNNING','RETRY')")
    fun observePendingCount(ownerUid: String): Flow<Int>

    @Query("SELECT * FROM drive_ledger_operations WHERE ownerUid = :ownerUid ORDER BY createdAt, operationId")
    fun observeAll(ownerUid: String): Flow<List<DriveLedgerOperationEntity>>

    @Query("DELETE FROM drive_ledger_operations WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_operations WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_operations")
    suspend fun deleteAll()
}

@Dao
interface DriveLedgerSyncMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DriveLedgerSyncMetadataEntity)

    @Query("SELECT * FROM drive_ledger_sync_metadata WHERE ownerUid = :ownerUid AND collectionType = :collectionType LIMIT 1")
    suspend fun get(ownerUid: String, collectionType: String): DriveLedgerSyncMetadataEntity?

    @Query("DELETE FROM drive_ledger_sync_metadata WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_sync_metadata WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_sync_metadata")
    suspend fun deleteAll()
}

@Dao
interface DriveLedgerSyncReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DriveLedgerSyncReceiptEntity)

    @Query("SELECT * FROM drive_ledger_sync_receipts WHERE ownerUid = :ownerUid ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(ownerUid: String, limit: Int): Flow<List<DriveLedgerSyncReceiptEntity>>

    @Query("SELECT * FROM drive_ledger_sync_receipts WHERE ownerUid = :ownerUid ORDER BY createdAt DESC")
    fun observeAll(ownerUid: String): Flow<List<DriveLedgerSyncReceiptEntity>>

    @Query("SELECT * FROM drive_ledger_sync_receipts WHERE ownerUid = :ownerUid AND operationId = :operationId LIMIT 1")
    suspend fun getByOperation(ownerUid: String, operationId: String): DriveLedgerSyncReceiptEntity?

    @Query("DELETE FROM drive_ledger_sync_receipts WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_sync_receipts WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_sync_receipts")
    suspend fun deleteAll()
}

@Dao
interface DriveLedgerConflictDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DriveLedgerConflictEntity)

    @Query("SELECT * FROM drive_ledger_conflicts WHERE ownerUid = :ownerUid AND resolvedAt IS NULL ORDER BY createdAt DESC")
    fun observeUnresolved(ownerUid: String): Flow<List<DriveLedgerConflictEntity>>

    @Query("SELECT * FROM drive_ledger_conflicts WHERE ownerUid = :ownerUid AND entityType = :entityType AND recordId = :recordId AND resolvedAt IS NULL ORDER BY createdAt DESC")
    suspend fun unresolvedForRecord(ownerUid: String, entityType: String, recordId: String): List<DriveLedgerConflictEntity>

    @Query("UPDATE drive_ledger_conflicts SET resolvedAt = :resolvedAt WHERE ownerUid = :ownerUid AND conflictId = :conflictId AND resolvedAt IS NULL")
    suspend fun resolve(ownerUid: String, conflictId: String, resolvedAt: Long): Int

    @Query("DELETE FROM drive_ledger_conflicts WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_conflicts WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String)

    @Query("DELETE FROM drive_ledger_conflicts")
    suspend fun deleteAll()
}
