package com.example.toplutasima.data.repository

import android.content.Context
import com.example.toplutasima.data.local.dao.TripDao
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.model.SummaryData
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class TripRepository(private val context: Context, private val tripDao: TripDao) {

    private companion object {
        const val SYNC_PREFS = "sync_prefs"
        const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        const val KEY_LAST_SYNC_SORT_DATE = "last_sync_sortdate"
    }

    suspend fun syncFromFirestore(fullSync: Boolean = false) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
        val lastSyncTimestamp = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
        val lastSyncSortDate = prefs.getString(KEY_LAST_SYNC_SORT_DATE, null)
        val shouldFullSync = fullSync || lastSyncTimestamp <= 0L
        val now = System.currentTimeMillis()
        
        val tripsMap = if (shouldFullSync) {
            FirestoreService.fetchTrips()
        } else {
            val fallbackSortDate = lastSyncSortDate ?: sortDateFromTimestamp(lastSyncTimestamp)
            val updatedTrips = FirestoreService.fetchTripsUpdatedAfter(lastSyncTimestamp)
            val legacyTrips = FirestoreService.fetchTripsAfter(fallbackSortDate)
            (updatedTrips + legacyTrips).distinctBy { remoteKey(it) }
        }
        
        if (tripsMap.isNotEmpty()) {
            val entities = tripsMap.map { it.toEntity() }
            tripDao.upsertAll(entities)
            
            // Son eklenenlerin en büyük sortDate'ini bul
            val maxSortDate = entities.mapNotNull { it.sortDate }.maxOrNull()
            if (maxSortDate != null) {
                // Eger aynı gün içinde birden fazla ekleme varsa, ayni sortDate olacaktir.
                // Bu sebeple bir sonraki sync'in de bugünü çekebilmesi icin lastSync'i dünden başlatmak isteyebiliriz ama
                // sortDate sadece YYYY-MM-DD. Aynı gün içindeki farklı saatleri ayıramayız.
                // İsteğe göre "maxSortDate"i kullanmak riskli, bu yüzden kullanıcının dediği gibi sadece last_sync yapalım.
                // Ama `sortDate > lastSync` olursa aynı günküler gelmez. `sortDate >= lastSync` olursa hep aynı günü çeker.
                // Firestore'da `whereGreaterThan` dedik, bu yüzden `maxSortDate` i koyarsak ayni gunku diger seyahatleri alamayiz.
                // Cözüm: lastSync olarak (bugün - 1 gün) kaydedelim veya direk şu anki tarihi kaydedelim ama
                // whereGreaterThanOrEqualTo ile yapalım. Neyse kullanıcının dediği gibi yapıyorum:
                prefs.edit().putString(KEY_LAST_SYNC_SORT_DATE, maxSortDate).apply()
            }
        }

        if (shouldFullSync) {
            deleteLocalTripsMissingFromFirestore(tripsMap)
        }

        val maxSortDate = tripsMap.mapNotNull { row ->
            row["sortDate"]?.toString()?.takeIf { it.isNotBlank() }
                ?: FirestoreService.computeSortDate(row["tarih"]?.toString().orEmpty()).takeIf { it.isNotBlank() }
        }.maxOrNull()
        val editor = prefs.edit().putLong(KEY_LAST_SYNC_TIMESTAMP, now)
        if (maxSortDate != null) editor.putString(KEY_LAST_SYNC_SORT_DATE, maxSortDate)
        editor.apply()
    }

    private fun remoteKey(row: Map<String, Any>): String =
        row["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
            ?: row["id"]?.toString()?.takeIf { it.isNotBlank() }
            ?: row.hashCode().toString()

    private fun sortDateFromTimestamp(timestamp: Long): String =
        Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

    private suspend fun deleteLocalTripsMissingFromFirestore(remoteTrips: List<Map<String, Any>>) {
        val remoteDocIds = remoteTrips.mapNotNull {
            it["firestoreDocId"]?.toString()?.takeIf { docId -> docId.isNotBlank() }
        }.toSet()
        val staleLocalIds = tripDao.getAllTrips().mapNotNull { trip ->
            val docId = trip.firestoreDocId
            if (!docId.isNullOrBlank() && docId !in remoteDocIds) trip.id else null
        }
        if (staleLocalIds.isNotEmpty()) {
            tripDao.deleteTripsByIds(staleLocalIds)
        }
    }

    fun getTripsForMonth(yearMonth: String): Flow<List<TripEntity>> = flow {
        emit(tripDao.getTripsForMonth(yearMonth))
    }

    suspend fun getTripById(id: String): TripEntity? {
        return tripDao.getTripById(id)
    }

    suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity? {
        return tripDao.getTripByFirestoreDocId(firestoreDocId)
    }

    fun getAllTrips(): Flow<List<TripEntity>> = flow {
        emit(tripDao.getAllTrips())
    }

    suspend fun searchTrips(query: String): List<TripEntity> = withContext(Dispatchers.IO) {
        tripDao.searchTrips("%$query%")
    }

    suspend fun saveTrip(trip: TripEntity) = withContext(Dispatchers.IO) {
        tripDao.upsertAll(listOf(trip))
        val firestoreDocId = FirestoreService.saveTrip(trip.toMap())
        if (trip.firestoreDocId.isNullOrBlank() && firestoreDocId.isNotBlank()) {
            tripDao.upsertAll(listOf(trip.copy(firestoreDocId = firestoreDocId)))
        }
    }

    suspend fun deleteTrip(id: String) = withContext(Dispatchers.IO) {
        val localTrip = tripDao.getTripByFirestoreDocId(id) ?: tripDao.getTripById(id)
        if (localTrip != null) {
            tripDao.deleteTrip(localTrip.id)
        } else {
            tripDao.deleteTrip(id)
            tripDao.deleteTripByFirestoreDocId(id)
        }
        
        // FirestoreService'ye dokunmamak adına işlemi burada yapıyoruz
        val collection = FirebaseFirestore.getInstance().collection("trips")
        val docId = localTrip?.firestoreDocId?.takeIf { it.isNotBlank() } ?: id
        if (docId.isNotBlank()) {
            collection.document(docId).delete().await()
        }

        val appId = localTrip?.id ?: id
        val snapshot = collection
            .whereEqualTo("id", appId)
            .get()
            .await()
            
        for (doc in snapshot.documents) {
            doc.reference.delete().await()
        }
    }

    suspend fun getMonthSummaries(): List<FirestoreService.MonthSummary> = withContext(Dispatchers.IO) {
        val tuples = tripDao.getMonthSummaries()
        val monthNames = mapOf(
            "01" to "Ocak", "02" to "Şubat", "03" to "Mart", "04" to "Nisan",
            "05" to "Mayıs", "06" to "Haziran", "07" to "Temmuz", "08" to "Ağustos",
            "09" to "Eylül", "10" to "Ekim", "11" to "Kasım", "12" to "Aralık"
        )
        tuples.map { tuple ->
            val parts = tuple.yearMonth.split("-")
            val year = if (parts.isNotEmpty()) parts[0] else ""
            val monthNum = if (parts.size > 1) parts[1] else ""
            val monthName = monthNames[monthNum] ?: monthNum
            FirestoreService.MonthSummary(
                monthName = monthName,
                year = year,
                count = tuple.count,
                sortKey = "${year}${monthNum}"
            )
        }.sortedByDescending { it.sortKey }
    }

    suspend fun getSummaryStats(sheetName: String = "Tümü"): Pair<SummaryData, List<String>> = withContext(Dispatchers.IO) {
        val entities = tripDao.getAllTrips()
        @Suppress("UNCHECKED_CAST")
        val allDocs = entities.map { it.toMap() } as List<Map<String, Any>>
        FirestoreService.computeSummary(allDocs, sheetName)
    }
}
