package com.example.toplutasima.viewmodel.records

import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.LocalTripRepository
import com.example.toplutasima.model.MonthSummary
import kotlinx.coroutines.flow.Flow

internal interface RecordsTripDataSource {
    suspend fun syncFromFirestore(fullSync: Boolean = false)
    fun getTripsForMonth(yearMonth: String): Flow<List<TripEntity>>
    suspend fun getTripById(id: String): TripEntity?
    suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity?
    fun getAllTrips(): Flow<List<TripEntity>>
    suspend fun searchTrips(query: String): List<TripEntity>
    suspend fun saveTrip(trip: TripEntity)
    suspend fun deleteTrip(id: String)
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

    override suspend fun getTripById(id: String): TripEntity? =
        repository.getTripById(id)

    override suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity? =
        repository.getTripByFirestoreDocId(firestoreDocId)

    override fun getAllTrips(): Flow<List<TripEntity>> =
        repository.getAllTrips()

    override suspend fun searchTrips(query: String): List<TripEntity> =
        repository.searchTrips(query)

    override suspend fun saveTrip(trip: TripEntity) {
        repository.saveTrip(trip)
    }

    override suspend fun deleteTrip(id: String) {
        repository.deleteTrip(id)
    }

    override suspend fun getMonthSummaries(): List<MonthSummary> =
        repository.getMonthSummaries()
}
