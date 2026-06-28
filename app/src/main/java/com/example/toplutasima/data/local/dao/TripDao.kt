package com.example.toplutasima.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.toplutasima.data.local.entity.TripEntity

data class MonthSummaryTuple(
    val yearMonth: String,
    val count: Int
)

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(trips: List<TripEntity>)

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTripById(id: String): TripEntity?

    @Query("SELECT * FROM trips WHERE firestoreDocId = :firestoreDocId LIMIT 1")
    suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity?

    @Query("SELECT * FROM trips WHERE yearMonth = :yearMonth")
    suspend fun getTripsForMonth(yearMonth: String): List<TripEntity>

    @Query("SELECT * FROM trips ORDER BY sortDate DESC")
    suspend fun getAllTrips(): List<TripEntity>

    @Query("""
        UPDATE trips
        SET rmvMesafeKm = NULL,
            rmvMesafeMetre = NULL,
            rmvMesafeText = NULL,
            rmvMesafeDurumu = NULL,
            rmvMesafeGuncellemeTarihi = NULL,
            rmvApiVersion = NULL
    """)
    suspend fun resetAllRmvMesafeBackfillState(): Int

    @Query("SELECT * FROM trips WHERE rmvMesafeDurumu = 'hazir_fallback'")
    suspend fun getTripsWithRmvFallbackDistance(): List<TripEntity>

    @Query("""
        UPDATE trips
        SET rmvMesafeKm = NULL,
            rmvMesafeMetre = NULL,
            rmvMesafeText = NULL,
            rmvApiVersion = NULL,
            rmvMesafeDurumu = 'poly_yok'
        WHERE rmvMesafeDurumu = 'hazir_fallback'
    """)
    suspend fun cleanupRmvFallbackDistances(): Int

    @Query("""
        SELECT * FROM trips
        WHERE rmvMesafeDurumu IS NULL
        OR rmvMesafeDurumu = ''
        OR rmvMesafeDurumu = 'bekliyor'
        OR rmvMesafeDurumu = 'hata_rate_limit_429'
        OR rmvMesafeDurumu = 'hata_timeout'
        OR rmvMesafeDurumu = 'hata_sonuc_yok'
        ORDER BY sortDate DESC
    """)
    suspend fun getTripsNeedingMesafeBackfill(): List<TripEntity>

    @Query("""
        SELECT * FROM trips
        WHERE hat LIKE :query
        OR yon LIKE :query
        OR binisDuragi LIKE :query
        OR inisDuragi LIKE :query
        OR tur LIKE :query
        OR `not` LIKE :query
        ORDER BY sortDate DESC
    """)
    suspend fun searchTrips(query: String): List<TripEntity>

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteTrip(id: String)

    @Query("DELETE FROM trips WHERE firestoreDocId = :firestoreDocId")
    suspend fun deleteTripByFirestoreDocId(firestoreDocId: String)

    @Query("DELETE FROM trips WHERE id IN (:ids)")
    suspend fun deleteTripsByIds(ids: List<String>)

    @Query("SELECT COUNT(*) FROM trips")
    suspend fun getTripCount(): Int

    @Query("SELECT * FROM trips WHERE sortDate >= :sortDate ORDER BY sortDate ASC")
    suspend fun getTripsAfter(sortDate: String): List<TripEntity>

    @Query("SELECT yearMonth, COUNT(*) as count FROM trips WHERE yearMonth IS NOT NULL AND yearMonth != '' GROUP BY yearMonth ORDER BY yearMonth ASC")
    suspend fun getMonthSummaries(): List<MonthSummaryTuple>
}
