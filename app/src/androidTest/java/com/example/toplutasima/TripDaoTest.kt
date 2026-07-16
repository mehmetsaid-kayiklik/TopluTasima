package com.example.toplutasima

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.usecase.TransitRecordCalculations
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripDaoTest {
    private val userId = "test-user"
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = TestDatabaseFactory.createInMemoryDatabase()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun backfillQueryOnlyIncludesUnprocessedAndRetryableStatuses() = runBlocking {
        val statuses = listOf(
            null,
            "",
            TransitRecordCalculations.RMV_DISTANCE_PENDING,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_RATE_LIMIT,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_TIMEOUT,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_NO_RESULT,
            TransitRecordCalculations.RMV_DISTANCE_POLY_UNAVAILABLE,
            TransitRecordCalculations.RMV_DISTANCE_FAILED,
            TransitRecordCalculations.RMV_DISTANCE_FAILED_PARSE_EXCEPTION,
            TransitRecordCalculations.RMV_DISTANCE_READY,
            TransitRecordCalculations.RMV_DISTANCE_READY_FALLBACK
        )
        database.tripDao().upsertAll(
            statuses.mapIndexed { index, status ->
                TripEntity(
                    id = "trip-$index",
                    rmvMesafeDurumu = status,
                    sortDate = "2026-06-${index + 1}",
                    userId = userId
                )
            }
        )

        val selectedIds = database.tripDao().getTripsNeedingMesafeBackfill(userId).map { it.id }.toSet()

        assertEquals((0..5).map { "trip-$it" }.toSet(), selectedIds)
    }

    @Test
    fun fallbackCleanupClearsOnlyRmvFieldsAndPreservesOrsDistance() = runBlocking {
        database.tripDao().upsertAll(
            listOf(
                TripEntity(
                    id = "fallback",
                    orsMesafeKm = 8.5,
                    orsMesafeText = "8.50 km",
                    rmvMesafeKm = 8.5,
                    rmvMesafeMetre = 8500,
                    rmvMesafeText = "8.50 km",
                    rmvMesafeDurumu = TransitRecordCalculations.RMV_DISTANCE_READY_FALLBACK,
                    rmvApiVersion = "ors_route_fallback",
                    userId = userId
                ),
                TripEntity(
                    id = "hafas",
                    rmvMesafeKm = 8.1,
                    rmvMesafeMetre = 8100,
                    rmvMesafeText = "8.10 km",
                    rmvMesafeDurumu = TransitRecordCalculations.RMV_DISTANCE_READY,
                    rmvApiVersion = "poly=1",
                    userId = userId
                )
            )
        )

        assertEquals(1, database.tripDao().cleanupRmvFallbackDistances(userId))

        val fallback = database.tripDao().getTripById(userId, "fallback")!!
        assertEquals(8.5, fallback.orsMesafeKm!!, 0.0)
        assertEquals("8.50 km", fallback.orsMesafeText)
        assertNull(fallback.rmvMesafeKm)
        assertNull(fallback.rmvMesafeMetre)
        assertNull(fallback.rmvMesafeText)
        assertNull(fallback.rmvApiVersion)
        assertEquals(TransitRecordCalculations.RMV_DISTANCE_POLY_UNAVAILABLE, fallback.rmvMesafeDurumu)

        val hafas = database.tripDao().getTripById(userId, "hafas")!!
        assertEquals(8.1, hafas.rmvMesafeKm!!, 0.0)
        assertEquals(TransitRecordCalculations.RMV_DISTANCE_READY, hafas.rmvMesafeDurumu)
    }
}
