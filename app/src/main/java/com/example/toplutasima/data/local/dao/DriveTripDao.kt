package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.example.toplutasima.data.local.entity.DriveTripEntity
import kotlinx.coroutines.flow.Flow

data class DriveTripSummary(
    val totalDistanceKm: Double,
    val tripCount: Int,
    val lastUsedAt: Long?
)

@Dao
interface DriveTripDao {
    @Upsert
    suspend fun upsert(trip: DriveTripEntity)

    @Upsert
    suspend fun upsertAll(trips: List<DriveTripEntity>)

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND vehicleId = :vehicleId AND deletedAt IS NULL " +
            "ORDER BY startedAt DESC, id DESC"
    )
    fun observeActiveTripsForVehicle(
        userId: String,
        vehicleId: String
    ): Flow<List<DriveTripEntity>>

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY startedAt DESC, id DESC"
    )
    fun observeActiveTrips(userId: String): Flow<List<DriveTripEntity>>

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND vehicleId = :vehicleId " +
            "ORDER BY startedAt DESC, id DESC"
    )
    suspend fun getTripsForVehicle(
        userId: String,
        vehicleId: String
    ): List<DriveTripEntity>

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND id = :id AND deletedAt IS NULL LIMIT 1"
    )
    fun observeActiveTrip(userId: String, id: String): Flow<DriveTripEntity?>

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND id = :id LIMIT 1"
    )
    suspend fun getTrip(userId: String, id: String): DriveTripEntity?

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND id = :id AND deletedAt IS NULL LIMIT 1"
    )
    suspend fun getActiveTrip(userId: String, id: String): DriveTripEntity?

    @Query(
        """
        SELECT
            COALESCE(SUM(distanceKm), 0.0) AS totalDistanceKm,
            COUNT(*) AS tripCount,
            MAX(startedAt) AS lastUsedAt
        FROM drive_trips
        WHERE userId = :userId AND vehicleId = :vehicleId AND deletedAt IS NULL
        """
    )
    fun observeSummary(userId: String, vehicleId: String): Flow<DriveTripSummary>

    @Query(
        "SELECT id FROM drive_trips " +
            "WHERE userId = :userId AND vehicleId = :vehicleId AND deletedAt IS NULL " +
            "ORDER BY id ASC"
    )
    suspend fun getActiveTripIdsForVehicle(userId: String, vehicleId: String): List<String>

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND deletedAt IS NOT NULL " +
            "ORDER BY deletedAt ASC, id ASC"
    )
    suspend fun getTombstones(userId: String): List<DriveTripEntity>

    @Query(
        "SELECT * FROM drive_trips " +
            "WHERE userId = :userId AND syncState IN (:syncStates) " +
            "ORDER BY updatedAt ASC, id ASC"
    )
    suspend fun getPendingSyncTrips(
        userId: String,
        syncStates: List<String>
    ): List<DriveTripEntity>

    @Query("DELETE FROM drive_trips WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String): Int

    @Query("DELETE FROM drive_trips WHERE userId != :userId")
    suspend fun deleteAllExceptUser(userId: String): Int

    @Query("DELETE FROM drive_trips")
    suspend fun deleteAll(): Int

    @Query(
        """
        UPDATE drive_trips
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
        UPDATE drive_trips
        SET deletedAt = COALESCE(deletedAt, :deletedAt),
            updatedAt = CASE WHEN updatedAt < :updatedAt THEN :updatedAt ELSE updatedAt END,
            syncState = :syncState
        WHERE userId = :userId AND vehicleId = :vehicleId AND deletedAt IS NULL
        """
    )
    suspend fun markDeletedForVehicle(
        userId: String,
        vehicleId: String,
        deletedAt: Long,
        updatedAt: Long,
        syncState: String
    ): Int

    @Query(
        """
        UPDATE drive_trips
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
}
