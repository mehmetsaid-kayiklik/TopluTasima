package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveAssignmentOperationEntity
import com.example.toplutasima.data.local.entity.DriveAssignmentSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveAssignmentSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveVehicleAssignmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveVehicleAssignmentDao {
    @Upsert
    suspend fun upsert(assignment: DriveVehicleAssignmentEntity)

    @Upsert
    suspend fun upsertAll(assignments: List<DriveVehicleAssignmentEntity>)

    @Query(
        "SELECT * FROM drive_vehicle_assignments " +
            "WHERE ownerUid = :ownerUid ORDER BY vehicleId ASC"
    )
    fun observeAll(ownerUid: String): Flow<List<DriveVehicleAssignmentEntity>>

    @Query(
        "SELECT * FROM drive_vehicle_assignments " +
            "WHERE ownerUid = :ownerUid ORDER BY vehicleId ASC"
    )
    suspend fun getAll(ownerUid: String): List<DriveVehicleAssignmentEntity>

    @Query(
        "SELECT * FROM drive_vehicle_assignments " +
            "WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId LIMIT 1"
    )
    fun observe(ownerUid: String, vehicleId: String): Flow<DriveVehicleAssignmentEntity?>

    @Query(
        "SELECT * FROM drive_vehicle_assignments " +
            "WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId LIMIT 1"
    )
    suspend fun get(ownerUid: String, vehicleId: String): DriveVehicleAssignmentEntity?

    @Query(
        "SELECT * FROM drive_vehicle_assignments " +
            "WHERE ownerUid = :ownerUid AND personId = :personId AND deletedAt IS NULL " +
            "ORDER BY vehicleId ASC"
    )
    fun observeActiveForPerson(
        ownerUid: String,
        personId: String
    ): Flow<List<DriveVehicleAssignmentEntity>>

    @Query(
        "UPDATE drive_vehicle_assignments SET healthCode = :healthCode " +
            "WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId"
    )
    suspend fun setHealth(ownerUid: String, vehicleId: String, healthCode: String?): Int

    @Query("DELETE FROM drive_vehicle_assignments WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_vehicle_assignments WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_vehicle_assignments")
    suspend fun deleteAll(): Int
}

@Dao
interface DriveAssignmentOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(operation: DriveAssignmentOperationEntity): Long

    @Upsert
    suspend fun upsert(operation: DriveAssignmentOperationEntity)

    @Query(
        "SELECT * FROM drive_assignment_operations " +
            "WHERE ownerUid = :ownerUid AND operationId = :operationId LIMIT 1"
    )
    suspend fun get(ownerUid: String, operationId: String): DriveAssignmentOperationEntity?

    @Query(
        "SELECT MAX(targetRevision) FROM drive_assignment_operations " +
            "WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId"
    )
    suspend fun highestTargetRevision(ownerUid: String, vehicleId: String): Long?

    @Query(
        "SELECT * FROM drive_assignment_operations " +
            "WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId " +
            "ORDER BY targetRevision DESC, createdAt DESC LIMIT 1"
    )
    suspend fun latestForVehicle(
        ownerUid: String,
        vehicleId: String
    ): DriveAssignmentOperationEntity?

    @Query(
        "SELECT * FROM drive_assignment_operations " +
            "WHERE ownerUid = :ownerUid AND state IN ('PENDING', 'RETRY') " +
            "AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now) " +
            "ORDER BY createdAt ASC, operationId ASC LIMIT :limit"
    )
    suspend fun pending(ownerUid: String, now: Long, limit: Int): List<DriveAssignmentOperationEntity>

    @Query(
        "UPDATE drive_assignment_operations SET state = :state, attemptCount = attemptCount + 1, " +
            "nextAttemptAt = :nextAttemptAt, lastErrorCode = :errorCode, updatedAt = :updatedAt " +
            "WHERE ownerUid = :ownerUid AND operationId = :operationId"
    )
    suspend fun recordAttempt(
        ownerUid: String,
        operationId: String,
        state: String,
        nextAttemptAt: Long?,
        errorCode: String?,
        updatedAt: Long
    ): Int

    @Query(
        "DELETE FROM drive_assignment_operations " +
            "WHERE ownerUid = :ownerUid AND operationId = :operationId"
    )
    suspend fun delete(ownerUid: String, operationId: String): Int

    @Query("DELETE FROM drive_assignment_operations WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_assignment_operations WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_assignment_operations")
    suspend fun deleteAll(): Int
}

@Dao
interface DriveAssignmentSyncMetadataDao {
    @Upsert
    suspend fun upsert(metadata: DriveAssignmentSyncMetadataEntity)

    @Query("SELECT * FROM drive_assignment_sync_metadata WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): DriveAssignmentSyncMetadataEntity?

    @Query("DELETE FROM drive_assignment_sync_metadata WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_assignment_sync_metadata WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_assignment_sync_metadata")
    suspend fun deleteAll(): Int
}

@Dao
interface DriveAssignmentSyncReceiptDao {
    @Upsert
    suspend fun upsert(receipt: DriveAssignmentSyncReceiptEntity)

    @Query(
        "SELECT * FROM drive_assignment_sync_receipts WHERE ownerUid = :ownerUid " +
            "ORDER BY startedAt DESC, receiptId DESC LIMIT :limit"
    )
    fun observeRecent(ownerUid: String, limit: Int): Flow<List<DriveAssignmentSyncReceiptEntity>>

    @Query(
        "SELECT * FROM drive_assignment_sync_receipts " +
            "WHERE ownerUid = :ownerUid AND receiptId = :receiptId LIMIT 1"
    )
    suspend fun get(ownerUid: String, receiptId: String): DriveAssignmentSyncReceiptEntity?

    @Query("DELETE FROM drive_assignment_sync_receipts WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_assignment_sync_receipts WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_assignment_sync_receipts")
    suspend fun deleteAll(): Int
}
