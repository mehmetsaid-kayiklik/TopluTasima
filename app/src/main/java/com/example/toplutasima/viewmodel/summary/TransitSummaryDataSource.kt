package com.example.toplutasima.viewmodel.summary

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.model.SummaryData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Additive summary-facing API that keeps the legacy snapshot path available for rollback. */
internal interface TransitSummaryDataSource {
    suspend fun syncFromFirestore(fullSync: Boolean = false)
    suspend fun getLegacySummaryStats(sheetName: String): Pair<SummaryData, List<String>>
    fun getLegacyAllTrips(): Flow<List<TripEntity>>
    suspend fun getTripsForMonthSnapshot(yearMonth: String): List<TripEntity>
    fun observeTripsForMonth(yearMonth: String): Flow<List<TripEntity>>
    fun observeTripsForMonthRange(startYearMonth: String, endYearMonth: String): Flow<List<TripEntity>>
    fun observeAllTrips(): Flow<List<TripEntity>>
    fun observeMonthSummaries(): Flow<List<MonthSummary>>
}

internal class LocalTransitSummaryDataSource(
    private val repository: LocalTripRepository
) : TransitSummaryDataSource {
    override suspend fun syncFromFirestore(fullSync: Boolean) {
        repository.syncFromFirestore(fullSync)
    }

    override suspend fun getLegacySummaryStats(sheetName: String): Pair<SummaryData, List<String>> =
        repository.getSummaryStats(sheetName)

    override fun getLegacyAllTrips(): Flow<List<TripEntity>> = repository.getAllTrips()

    override suspend fun getTripsForMonthSnapshot(yearMonth: String): List<TripEntity> =
        repository.getTripsForMonth(yearMonth).first()

    override fun observeTripsForMonth(yearMonth: String): Flow<List<TripEntity>> =
        repository.observeTripsForMonth(yearMonth)

    override fun observeTripsForMonthRange(
        startYearMonth: String,
        endYearMonth: String
    ): Flow<List<TripEntity>> = repository.observeTripsForMonthRange(startYearMonth, endYearMonth)

    override fun observeAllTrips(): Flow<List<TripEntity>> = repository.observeAllTrips()

    override fun observeMonthSummaries(): Flow<List<MonthSummary>> =
        repository.observeMonthSummaries()
}
