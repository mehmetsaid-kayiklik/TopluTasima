package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.toplutasima.data.local.entity.TripEntity
import kotlinx.coroutines.flow.Flow

data class MonthSummaryTuple(
    val yearMonth: String,
    val count: Int
)

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(trips: List<TripEntity>)

    @Query("SELECT * FROM trips WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getTripById(userId: String, id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE userId = :userId AND firestoreDocId = :firestoreDocId LIMIT 1")
    suspend fun getTripByFirestoreDocId(userId: String, firestoreDocId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE userId = :userId AND yearMonth = :yearMonth")
    suspend fun getTripsForMonth(userId: String, yearMonth: String): List<TripEntity>

    @Query("SELECT * FROM trips WHERE userId = :userId AND yearMonth = :yearMonth")
    fun observeTripsForMonth(userId: String, yearMonth: String): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE userId = :userId ORDER BY sortDate DESC")
    suspend fun getAllTrips(userId: String): List<TripEntity>

    @Query("SELECT * FROM trips WHERE userId = :userId ORDER BY sortDate DESC")
    fun observeAllTrips(userId: String): Flow<List<TripEntity>>

    @Query(
        "SELECT * FROM trips WHERE userId = :userId " +
            "AND yearMonth >= :startYearMonth AND yearMonth <= :endYearMonth " +
            "ORDER BY sortDate DESC"
    )
    fun observeTripsForMonthRange(
        userId: String,
        startYearMonth: String,
        endYearMonth: String
    ): Flow<List<TripEntity>>

    @Query("""
        UPDATE trips
        SET rmvMesafeKm = NULL,
            rmvMesafeMetre = NULL,
            rmvMesafeText = NULL,
            rmvMesafeDurumu = NULL,
            rmvMesafeGuncellemeTarihi = NULL,
            rmvApiVersion = NULL
        WHERE userId = :userId
    """)
    suspend fun resetAllRmvMesafeBackfillState(userId: String): Int

    @Query("SELECT * FROM trips WHERE userId = :userId AND rmvMesafeDurumu = 'hazir_fallback'")
    suspend fun getTripsWithRmvFallbackDistance(userId: String): List<TripEntity>

    @Query("""
        UPDATE trips
        SET rmvMesafeKm = NULL,
            rmvMesafeMetre = NULL,
            rmvMesafeText = NULL,
            rmvApiVersion = NULL,
            rmvMesafeDurumu = 'poly_yok'
        WHERE userId = :userId AND rmvMesafeDurumu = 'hazir_fallback'
    """)
    suspend fun cleanupRmvFallbackDistances(userId: String): Int

    @Query("""
        SELECT * FROM trips
        WHERE userId = :userId AND (
            rmvMesafeDurumu IS NULL
            OR rmvMesafeDurumu = ''
            OR rmvMesafeDurumu = 'bekliyor'
            OR rmvMesafeDurumu = 'hata_rate_limit_429'
            OR rmvMesafeDurumu = 'hata_timeout'
            OR rmvMesafeDurumu = 'hata_sonuc_yok'
        )
        ORDER BY sortDate DESC
    """)
    suspend fun getTripsNeedingMesafeBackfill(userId: String): List<TripEntity>

    @Query("""
        SELECT * FROM trips
        WHERE userId = :userId AND (
            hat LIKE :query
            OR yon LIKE :query
            OR binisDuragi LIKE :query
            OR inisDuragi LIKE :query
            OR tur LIKE :query
            OR `not` LIKE :query
        )
        ORDER BY sortDate DESC
    """)
    suspend fun searchTrips(userId: String, query: String): List<TripEntity>

    @Query("DELETE FROM trips WHERE userId = :userId AND id = :id")
    suspend fun deleteTrip(userId: String, id: String)

    @Query("DELETE FROM trips WHERE userId = :userId AND firestoreDocId = :firestoreDocId")
    suspend fun deleteTripByFirestoreDocId(userId: String, firestoreDocId: String)

    @Query("DELETE FROM trips WHERE userId = :userId AND id IN (:ids)")
    suspend fun deleteTripsByIds(userId: String, ids: List<String>)

    @Query("SELECT COUNT(*) FROM trips WHERE userId = :userId")
    suspend fun getTripCount(userId: String): Int

    @Query("SELECT yearMonth, COUNT(*) as count FROM trips WHERE userId = :userId AND yearMonth IS NOT NULL AND yearMonth != '' GROUP BY yearMonth ORDER BY yearMonth ASC")
    suspend fun getMonthSummaries(userId: String): List<MonthSummaryTuple>

    @Query("SELECT yearMonth, COUNT(*) as count FROM trips WHERE userId = :userId AND yearMonth IS NOT NULL AND yearMonth != '' GROUP BY yearMonth ORDER BY yearMonth ASC")
    fun observeMonthSummaries(userId: String): Flow<List<MonthSummaryTuple>>
}
