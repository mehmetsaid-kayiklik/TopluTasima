package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DrivePhotoOperationEntity
import com.example.toplutasima.data.local.entity.DrivePhotoSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DrivePhotoSyncReceiptEntity
import com.example.toplutasima.data.local.entity.DriveVehiclePhotoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DriveVehiclePhotoDao {
    @Query(
        "SELECT * FROM drive_vehicle_photos WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId " +
            "ORDER BY sortOrder, createdAt, photoId"
    )
    fun observeForVehicle(ownerUid: String, vehicleId: String): Flow<List<DriveVehiclePhotoEntity>>

    @Query("SELECT * FROM drive_vehicle_photos WHERE ownerUid = :ownerUid ORDER BY vehicleId, sortOrder, photoId")
    fun observeAll(ownerUid: String): Flow<List<DriveVehiclePhotoEntity>>

    @Query("SELECT * FROM drive_vehicle_photos WHERE ownerUid = :ownerUid ORDER BY vehicleId, sortOrder, photoId")
    suspend fun getAll(ownerUid: String): List<DriveVehiclePhotoEntity>

    @Query("SELECT * FROM drive_vehicle_photos WHERE ownerUid = :ownerUid AND photoId = :photoId LIMIT 1")
    suspend fun get(ownerUid: String, photoId: String): DriveVehiclePhotoEntity?

    @Query(
        "SELECT * FROM drive_vehicle_photos WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId " +
            "AND deletedAt IS NULL ORDER BY sortOrder, createdAt, photoId"
    )
    suspend fun getActiveForVehicle(ownerUid: String, vehicleId: String): List<DriveVehiclePhotoEntity>

    @Query(
        "SELECT * FROM drive_vehicle_photos WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId " +
            "AND deletedAt IS NULL AND photoId != :excludedPhotoId " +
            "ORDER BY sortOrder, createdAt, photoId LIMIT 1"
    )
    suspend fun deterministicReplacement(
        ownerUid: String,
        vehicleId: String,
        excludedPhotoId: String
    ): DriveVehiclePhotoEntity?

    @Upsert
    suspend fun upsert(entity: DriveVehiclePhotoEntity)

    @Upsert
    suspend fun upsertAll(entities: List<DriveVehiclePhotoEntity>)

    @Query(
        "UPDATE drive_vehicle_photos SET isPrimary = 0, updatedAt = :updatedAt " +
            "WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId AND photoId != :photoId"
    )
    suspend fun clearOtherPrimary(ownerUid: String, vehicleId: String, photoId: String, updatedAt: Long)

    @Query(
        "UPDATE drive_vehicle_photos SET isPrimary = CASE WHEN photoId = :photoId THEN 1 ELSE 0 END, " +
            "updatedAt = :updatedAt WHERE ownerUid = :ownerUid AND vehicleId = :vehicleId"
    )
    suspend fun projectPrimary(ownerUid: String, vehicleId: String, photoId: String?, updatedAt: Long)

    @Query(
        "UPDATE drive_vehicle_photos SET localPreparedPath = :path, healthCode = :healthCode " +
            "WHERE ownerUid = :ownerUid AND photoId = :photoId"
    )
    suspend fun updateLocalCopy(ownerUid: String, photoId: String, path: String?, healthCode: String?)

    @Query("DELETE FROM drive_vehicle_photos WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_vehicle_photos WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_vehicle_photos")
    suspend fun deleteAll(): Int
}

@Dao
interface DrivePhotoOperationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DrivePhotoOperationEntity): Long

    @Upsert
    suspend fun upsert(entity: DrivePhotoOperationEntity)

    @Query("SELECT * FROM drive_photo_operations WHERE ownerUid = :ownerUid AND operationId = :operationId LIMIT 1")
    suspend fun get(ownerUid: String, operationId: String): DrivePhotoOperationEntity?

    @Query(
        "SELECT * FROM drive_photo_operations WHERE ownerUid = :ownerUid " +
            "AND state IN ('PENDING','RETRY') AND nextAttemptAt <= :now ORDER BY createdAt, operationId LIMIT :limit"
    )
    suspend fun pending(ownerUid: String, now: Long, limit: Int): List<DrivePhotoOperationEntity>

    @Query(
        "SELECT * FROM drive_photo_operations WHERE ownerUid = :ownerUid AND photoId = :photoId " +
            "ORDER BY createdAt DESC, operationId DESC LIMIT 1"
    )
    suspend fun latestForPhoto(ownerUid: String, photoId: String): DrivePhotoOperationEntity?

    @Query(
        "UPDATE drive_photo_operations SET state = 'RUNNING', claimedAt = :now, updatedAt = :now, " +
            "attemptCount = attemptCount + 1 WHERE ownerUid = :ownerUid AND operationId = :operationId " +
            "AND ((state IN ('PENDING','RETRY') AND nextAttemptAt <= :now) " +
            "OR (state = 'RUNNING' AND claimedAt IS NOT NULL AND claimedAt <= :staleBefore))"
    )
    suspend fun claim(ownerUid: String, operationId: String, now: Long, staleBefore: Long): Int

    @Query(
        "UPDATE drive_photo_operations SET type = :type, targetRevision = :revision, state = 'PENDING', " +
            "updatedAt = :now, nextAttemptAt = 0, claimedAt = NULL, lastErrorCode = NULL " +
            "WHERE ownerUid = :ownerUid AND operationId = :operationId"
    )
    suspend fun convert(ownerUid: String, operationId: String, type: String, revision: Long, now: Long)

    @Query(
        "UPDATE drive_photo_operations SET state = :state, updatedAt = :now, nextAttemptAt = :nextAttemptAt, " +
            "claimedAt = NULL, lastErrorCode = :errorCode WHERE ownerUid = :ownerUid AND operationId = :operationId"
    )
    suspend fun finish(
        ownerUid: String,
        operationId: String,
        state: String,
        now: Long,
        nextAttemptAt: Long,
        errorCode: String?
    )

    @Query("DELETE FROM drive_photo_operations WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_photo_operations WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_photo_operations")
    suspend fun deleteAll(): Int
}

@Dao
interface DrivePhotoSyncMetadataDao {
    @Query("SELECT * FROM drive_photo_sync_metadata WHERE ownerUid = :ownerUid LIMIT 1")
    suspend fun get(ownerUid: String): DrivePhotoSyncMetadataEntity?

    @Upsert
    suspend fun upsert(entity: DrivePhotoSyncMetadataEntity)

    @Query("DELETE FROM drive_photo_sync_metadata WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_photo_sync_metadata WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_photo_sync_metadata")
    suspend fun deleteAll(): Int
}

@Dao
interface DrivePhotoSyncReceiptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: DrivePhotoSyncReceiptEntity): Long

    @Query("SELECT * FROM drive_photo_sync_receipts WHERE ownerUid = :ownerUid AND operationId = :operationId LIMIT 1")
    suspend fun getForOperation(ownerUid: String, operationId: String): DrivePhotoSyncReceiptEntity?

    @Query("DELETE FROM drive_photo_sync_receipts WHERE ownerUid = :ownerUid")
    suspend fun deleteAllForUser(ownerUid: String): Int

    @Query("DELETE FROM drive_photo_sync_receipts WHERE ownerUid != :ownerUid")
    suspend fun deleteAllExceptUser(ownerUid: String): Int

    @Query("DELETE FROM drive_photo_sync_receipts")
    suspend fun deleteAll(): Int
}
