package com.example.toplutasima.viewmodel.records

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.model.MonthSummary
import kotlinx.coroutines.flow.Flow

internal interface RecordsTripDataSource {
    suspend fun syncFromFirestore(fullSync: Boolean = false)
    fun getTripsForMonth(yearMonth: String): Flow<List<TripEntity>>
    fun observeTripsForMonth(yearMonth: String): Flow<List<TripEntity>> = getTripsForMonth(yearMonth)
    suspend fun getTripById(id: String): TripEntity?
    suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity?
    fun getAllTrips(): Flow<List<TripEntity>>
    fun observeAllTrips(): Flow<List<TripEntity>> = getAllTrips()
    suspend fun searchTrips(query: String): List<TripEntity>
    suspend fun saveTrip(trip: TripEntity)
    suspend fun deleteTrip(id: String)
    suspend fun retryDelete(id: String): Boolean = false
    suspend fun keepDeleteLocalOnly(id: String): Boolean = false
    suspend fun getMonthSummaries(): List<MonthSummary>
}

internal class LocalRecordsTripDataSource(
    private val repository: LocalTripRepository
) : RecordsTripDataSource {
    override suspend fun syncFromFirestore(fullSync: Boolean) {
        repository.syncFromFirestore(fullSync)
    }

    override fun getTripsForMonth(yearMonth: String): Flow<List<TripEntity>> =
        repository.getTripsForMonth(yearMonth)

    override fun observeTripsForMonth(yearMonth: String): Flow<List<TripEntity>> =
        repository.observeTripsForMonth(yearMonth)

    override suspend fun getTripById(id: String): TripEntity? =
        repository.getTripById(id)

    override suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity? =
        repository.getTripByFirestoreDocId(firestoreDocId)

    override fun getAllTrips(): Flow<List<TripEntity>> =
        repository.getAllTrips()

    override fun observeAllTrips(): Flow<List<TripEntity>> =
        repository.observeAllTrips()

    override suspend fun searchTrips(query: String): List<TripEntity> =
        repository.searchTrips(query)

    override suspend fun saveTrip(trip: TripEntity) {
        repository.saveTrip(trip)
    }

    override suspend fun deleteTrip(id: String) {
        repository.deleteTrip(id)
    }

    override suspend fun retryDelete(id: String): Boolean = repository.retryDelete(id)

    override suspend fun keepDeleteLocalOnly(id: String): Boolean =
        repository.keepDeleteLocalOnly(id)

    override suspend fun getMonthSummaries(): List<MonthSummary> =
        repository.getMonthSummaries()
}
