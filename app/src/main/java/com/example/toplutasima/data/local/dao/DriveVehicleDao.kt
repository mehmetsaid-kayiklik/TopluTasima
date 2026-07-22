package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import kotlinx.coroutines.flow.Flow

data class DriveVehicleWithSummary(
    @Embedded val vehicle: DriveVehicleEntity,
    val totalDistanceKm: Double,
    val tripCount: Int,
    val lastUsedAt: Long?
)

@Dao
interface DriveVehicleDao {
    @Upsert
    suspend fun upsert(vehicle: DriveVehicleEntity)

    @Upsert
    suspend fun upsertAll(vehicles: List<DriveVehicleEntity>)

    @Query(
        "SELECT * FROM drive_vehicles " +
            "WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY displayName COLLATE NOCASE ASC, id ASC"
    )
    fun observeActiveVehicles(userId: String): Flow<List<DriveVehicleEntity>>

    @Query(
        "SELECT * FROM drive_vehicles " +
            "WHERE userId = :userId AND id = :id AND deletedAt IS NULL LIMIT 1"
    )
    fun observeActiveVehicle(userId: String, id: String): Flow<DriveVehicleEntity?>

    @Query(
        "SELECT * FROM drive_vehicles " +
            "WHERE userId = :userId AND id = :id LIMIT 1"
    )
    suspend fun getVehicle(userId: String, id: String): DriveVehicleEntity?

    @Query(
        "SELECT * FROM drive_vehicles " +
            "WHERE userId = :userId AND id = :id AND deletedAt IS NULL LIMIT 1"
    )
    suspend fun getActiveVehicle(userId: String, id: String): DriveVehicleEntity?

    @Query(
        "SELECT * FROM drive_vehicles WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY id ASC"
    )
    suspend fun getActiveVehiclesSnapshot(userId: String): List<DriveVehicleEntity>

    @Query(
        """
        SELECT
            v.*,
            COALESCE(SUM(t.distanceKm), 0.0) AS totalDistanceKm,
            COUNT(t.id) AS tripCount,
            MAX(t.startedAt) AS lastUsedAt
        FROM drive_vehicles AS v
        LEFT JOIN drive_trips AS t
            ON t.userId = v.userId
            AND t.vehicleId = v.id
            AND t.deletedAt IS NULL
        WHERE v.userId = :userId AND v.deletedAt IS NULL
        GROUP BY v.userId, v.id
        ORDER BY v.displayName COLLATE NOCASE ASC, v.id ASC
        """
    )
    fun observeActiveVehiclesWithSummary(userId: String): Flow<List<DriveVehicleWithSummary>>

    @Query(
        """
        SELECT
            v.*,
            COALESCE(SUM(t.distanceKm), 0.0) AS totalDistanceKm,
            COUNT(t.id) AS tripCount,
            MAX(t.startedAt) AS lastUsedAt
        FROM drive_vehicles AS v
        LEFT JOIN drive_trips AS t
            ON t.userId = v.userId
            AND t.vehicleId = v.id
            AND t.deletedAt IS NULL
        WHERE v.userId = :userId AND v.id = :id AND v.deletedAt IS NULL
        GROUP BY v.userId, v.id
        LIMIT 1
        """
    )
    fun observeActiveVehicleWithSummary(
        userId: String,
        id: String
    ): Flow<DriveVehicleWithSummary?>

    @Query(
        "SELECT * FROM drive_vehicles " +
            "WHERE userId = :userId AND deletedAt IS NOT NULL " +
            "ORDER BY deletedAt ASC, id ASC"
    )
    suspend fun getTombstones(userId: String): List<DriveVehicleEntity>

    @Query(
        "SELECT * FROM drive_vehicles " +
            "WHERE userId = :userId AND syncState IN (:syncStates) " +
            "ORDER BY updatedAt ASC, id ASC"
    )
    suspend fun getPendingSyncVehicles(
        userId: String,
        syncStates: List<String>
    ): List<DriveVehicleEntity>

    @Query("DELETE FROM drive_vehicles WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String): Int

    @Query("DELETE FROM drive_vehicles WHERE userId != :userId")
    suspend fun deleteAllExceptUser(userId: String): Int

    @Query("DELETE FROM drive_vehicles")
    suspend fun deleteAll(): Int

    @Query(
        """
        UPDATE drive_vehicles
        SET deletedAt = COALESCE(deletedAt, :deletedAt),
            updatedAt = CASE WHEN updatedAt < :updatedAt THEN :updatedAt ELSE updatedAt END,
            syncState = :syncState
        WHERE userId = :userId AND id = :id
        """
    )
    suspend fun markDeleted(
        userId: String,
        id: String,
        deletedAt: Long,
        updatedAt: Long,
        syncState: String
    ): Int

    @Query(
        """
        UPDATE drive_vehicles
        SET syncState = :syncState
        WHERE userId = :userId AND id = :id AND updatedAt = :expectedUpdatedAt
        """
    )
    suspend fun setSyncStateIfUnchanged(
        userId: String,
        id: String,
        expectedUpdatedAt: Long,
        syncState: String
    ): Int

    /** Canonical assignment projection; this never changes the vehicle snapshot revision/time. */
    @Query(
        "UPDATE drive_vehicles SET assignedPersonId = :personId " +
            "WHERE userId = :userId AND id = :vehicleId"
    )
    suspend fun setAssignmentMirror(
        userId: String,
        vehicleId: String,
        personId: String?
    ): Int
}
