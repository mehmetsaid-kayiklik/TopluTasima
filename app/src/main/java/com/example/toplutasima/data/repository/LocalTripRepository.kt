package com.example.toplutasima.data.repository

import android.content.Context
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.local.dao.TripDao
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.network.firestore.FirestoreHelper
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.sync.TransitSyncStatusStore
import com.example.toplutasima.usecase.SummaryCalculator
import com.example.toplutasima.usecase.TransitRecordCalculations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

internal suspend fun deleteRemoteTripThenLocal(
    deleteRemote: suspend () -> Unit,
    deleteLocal: suspend () -> Unit
) {
    deleteRemote()
    deleteLocal()
}

internal suspend fun deleteLocalTripThenQueue(
    deleteLocal: suspend () -> Unit,
    enqueueDelete: suspend () -> Unit
) {
    deleteLocal()
    enqueueDelete()
}

class LocalTripRepository(
    private val context: Context,
    private val tripDao: TripDao,
    private val tripRemoteDataSource: FirestoreTripRemoteDataSource = FirestoreTripRemoteDataSource(),
    private val deleteReceiptsEnabled: Boolean = TransitFeatureFlags.SYNC_RECEIPTS &&
        TransitFeatureFlags.SYNC_DELETE_RECEIPTS
) {

    private fun deletionStore(): TransitSyncStatusStore? =
        if (deleteReceiptsEnabled) TransitSyncStatusStore.get(context) else null

    private companion object {
        const val SYNC_PREFS = "sync_prefs"
        const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        const val KEY_LAST_SYNC_SORT_DATE = "last_sync_sortdate"
        const val KEY_LAST_FULL_SYNC_TIMESTAMP = "last_full_sync_timestamp"
    }

    suspend fun syncFromFirestore(fullSync: Boolean = false) = withContext(Dispatchers.IO) {
        val userId = CurrentUserProvider.requireUserId()
        val prefs = context.getSharedPreferences(SYNC_PREFS, Context.MODE_PRIVATE)
        val lastSyncTimestamp = prefs.getLong(scopedKey(KEY_LAST_SYNC_TIMESTAMP, userId), 0L)
        val lastSyncSortDate = prefs.getString(scopedKey(KEY_LAST_SYNC_SORT_DATE, userId), null)
        val lastFullSyncTimestamp = prefs.getLong(scopedKey(KEY_LAST_FULL_SYNC_TIMESTAMP, userId), 0L)
        val localTripCount = tripDao.getTripCount(userId)
        val shouldFullSync = fullSync || lastSyncTimestamp <= 0L ||
            lastFullSyncTimestamp <= 0L || localTripCount == 0
        val now = System.currentTimeMillis()
        
        val tripsMap = if (shouldFullSync) {
            tripRemoteDataSource.fetchTrips()
        } else {
            val fallbackSortDate = lastSyncSortDate ?: sortDateFromTimestamp(lastSyncTimestamp)
            val updatedTrips = tripRemoteDataSource.fetchTripsUpdatedAfter(lastSyncTimestamp)
            val legacyTrips = tripRemoteDataSource.fetchTripsAfter(fallbackSortDate)
            (updatedTrips + legacyTrips).distinctBy { remoteKey(it) }
        }
        
        val tripsEligibleForLocalUpsert = filterTombstonedRemoteTrips(userId, tripsMap)
        if (tripsEligibleForLocalUpsert.isNotEmpty()) {
            ensureCurrentUser(userId)
            val entities = tripsEligibleForLocalUpsert.map { it.toEntity(userId) }
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
                prefs.edit().putString(scopedKey(KEY_LAST_SYNC_SORT_DATE, userId), maxSortDate).apply()
            }
        }
        deleteTombstonedLocalTrips(userId)

        if (fullSync) {
            ensureCurrentUser(userId)
            deleteLocalTripsMissingFromFirestore(tripsMap, userId)
        }

        val maxSortDate = tripsMap.mapNotNull { row ->
            row["sortDate"]?.toString()?.takeIf { it.isNotBlank() }
                ?: TransitRecordCalculations.computeSortDate(row["tarih"]?.toString().orEmpty()).takeIf { it.isNotBlank() }
        }.maxOrNull()
        ensureCurrentUser(userId)
        val editor = prefs.edit().putLong(scopedKey(KEY_LAST_SYNC_TIMESTAMP, userId), now)
        if (shouldFullSync) editor.putLong(scopedKey(KEY_LAST_FULL_SYNC_TIMESTAMP, userId), now)
        if (maxSortDate != null) editor.putString(scopedKey(KEY_LAST_SYNC_SORT_DATE, userId), maxSortDate)
        editor.apply()
    }

    private fun filterTombstonedRemoteTrips(
        userId: String,
        trips: List<Map<String, Any>>
    ): List<Map<String, Any>> {
        val store = deletionStore() ?: return trips
        return trips.filterNot { row ->
            store.isDeletionTombstoned(
                userId = userId,
                recordId = row["id"]?.toString(),
                firestoreDocId = row["firestoreDocId"]?.toString()
            )
        }
    }

    private suspend fun deleteTombstonedLocalTrips(userId: String) {
        val store = deletionStore() ?: return
        store.deletionTombstonesForUser(userId).forEach { state ->
            tripDao.deleteTrip(userId, state.recordId)
            state.deleteMetadata?.firestoreDocId
                ?.takeIf { it.isNotBlank() }
                ?.let { tripDao.deleteTripByFirestoreDocId(userId, it) }
        }
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

    private suspend fun deleteLocalTripsMissingFromFirestore(
        remoteTrips: List<Map<String, Any>>,
        userId: String
    ) {
        val remoteDocIds = remoteTrips.mapNotNull {
            it["firestoreDocId"]?.toString()?.takeIf { docId -> docId.isNotBlank() }
        }.toSet()
        val staleLocalIds = tripDao.getAllTrips(userId).mapNotNull { trip ->
            val docId = trip.firestoreDocId
            if (!docId.isNullOrBlank() && docId !in remoteDocIds) trip.id else null
        }
        if (staleLocalIds.isNotEmpty()) {
            tripDao.deleteTripsByIds(userId, staleLocalIds)
        }
    }

    fun getTripsForMonth(yearMonth: String): Flow<List<TripEntity>> = flow {
        val userId = CurrentUserProvider.requireUserId()
        emit(filterTombstonedEntities(userId, tripDao.getTripsForMonth(userId, yearMonth)))
    }

    /**
     * Opt-in live counterpart of [getTripsForMonth]. The legacy API intentionally remains
     * one-shot until its consumers are migrated behind the transit live-data feature gate.
     */
    fun observeTripsForMonth(yearMonth: String): Flow<List<TripEntity>> = flow {
        val userId = CurrentUserProvider.requireUserId()
        val store = deletionStore()
        if (store == null) {
            emitAll(tripDao.observeTripsForMonth(userId, yearMonth))
        } else {
            emitAll(
                combine(tripDao.observeTripsForMonth(userId, yearMonth), store.states) { trips, _ ->
                    filterTombstonedEntities(userId, trips)
                }
            )
        }
    }

    /** Live, bounded counterpart used by the summary trend. */
    fun observeTripsForMonthRange(
        startYearMonth: String,
        endYearMonth: String
    ): Flow<List<TripEntity>> = flow {
        val userId = CurrentUserProvider.requireUserId()
        val store = deletionStore()
        val roomFlow = tripDao.observeTripsForMonthRange(userId, startYearMonth, endYearMonth)
        if (store == null) {
            emitAll(roomFlow)
        } else {
            emitAll(
                combine(roomFlow, store.states) { trips, _ ->
                    filterTombstonedEntities(userId, trips)
                }
            )
        }
    }

    suspend fun getTripById(id: String): TripEntity? {
        return tripDao.getTripById(CurrentUserProvider.requireUserId(), id)
    }

    suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity? {
        return tripDao.getTripByFirestoreDocId(CurrentUserProvider.requireUserId(), firestoreDocId)
    }

    fun getAllTrips(): Flow<List<TripEntity>> = flow {
        val userId = CurrentUserProvider.requireUserId()
        emit(filterTombstonedEntities(userId, tripDao.getAllTrips(userId)))
    }

    /**
     * Opt-in Room invalidation flow. Keeping this separate preserves existing one-shot
     * collection semantics while the live transit path is rolled out incrementally.
     */
    fun observeAllTrips(): Flow<List<TripEntity>> = flow {
        val userId = CurrentUserProvider.requireUserId()
        val store = deletionStore()
        if (store == null) {
            emitAll(tripDao.observeAllTrips(userId))
        } else {
            emitAll(
                combine(tripDao.observeAllTrips(userId), store.states) { trips, _ ->
                    filterTombstonedEntities(userId, trips)
                }
            )
        }
    }

    private fun filterTombstonedEntities(userId: String, trips: List<TripEntity>): List<TripEntity> {
        val store = deletionStore() ?: return trips
        return trips.filterNot { trip ->
            store.isDeletionTombstoned(userId, trip.id, trip.firestoreDocId)
        }
    }

    suspend fun getTripsNeedingMesafeBackfill(): List<TripEntity> = withContext(Dispatchers.IO) {
        tripDao.getTripsNeedingMesafeBackfill(CurrentUserProvider.requireUserId())
    }

    suspend fun resetAllMesafeBackfillState(): Int = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val userId = CurrentUserProvider.requireUserId()
            val trips = tripDao.getAllTrips(userId)
            val docIds = trips.map { trip ->
                trip.firestoreDocId?.takeIf { it.isNotBlank() } ?: trip.id
            }
            tripRemoteDataSource.resetRmvMesafeFields(docIds)
            tripDao.resetAllRmvMesafeBackfillState(userId)
            trips.size
        }
    }

    suspend fun cleanupRmvFallbackDistances(): Int = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val userId = CurrentUserProvider.requireUserId()
            val trips = tripDao.getTripsWithRmvFallbackDistance(userId)
            val docIds = trips.map { trip ->
                trip.firestoreDocId?.takeIf { it.isNotBlank() } ?: trip.id
            }
            tripRemoteDataSource.cleanupRmvFallbackFields(docIds)
            tripDao.cleanupRmvFallbackDistances(userId)
        }
    }

    suspend fun searchTrips(query: String): List<TripEntity> = withContext(Dispatchers.IO) {
        val userId = CurrentUserProvider.requireUserId()
        filterTombstonedEntities(userId, tripDao.searchTrips(userId, "%$query%"))
    }

    private fun getTripProfileLinkDao() = com.example.toplutasima.data.local.AppDatabase.getDatabase(context).tripProfileLinkDao()

    suspend fun saveTrip(trip: TripEntity) = withContext(Dispatchers.IO) {
        val userId = CurrentUserProvider.requireUserId()
        check(trip.userId.isBlank() || trip.userId == userId) {
            "Cannot save a trip owned by a different user"
        }
        check(
            deletionStore()?.isDeletionTombstoned(userId, trip.id, trip.firestoreDocId) != true
        ) {
            "Cannot save a locally deleted transit record"
        }
        val existingFirestoreDocId = trip.firestoreDocId?.takeIf { it.isNotBlank() }
            ?: tripDao.getTripById(userId, trip.id)?.firestoreDocId?.takeIf { it.isNotBlank() }
        val firestoreDocId = existingFirestoreDocId ?: tripRemoteDataSource.newTripDocumentId()
        val tripWithDocumentId = trip.copy(firestoreDocId = firestoreDocId, userId = userId)
        // Persist the client-generated ID before Firestore so retries reuse the same document.
        tripDao.upsertAll(listOf(tripWithDocumentId))
        getTripProfileLinkDao().updateStableKey(userId, trip.id, firestoreDocId, System.currentTimeMillis())
        ensureCurrentUser(userId)
        tripRemoteDataSource.saveTrip(tripWithDocumentId.toMap())
        // A delete may have started while the remote save was in flight. Re-queueing the
        // idempotent delete makes delete win regardless of which network call completed last.
        if (deletionStore()?.isDeletionTombstoned(userId, trip.id, firestoreDocId) == true) {
            tripDao.deleteTrip(userId, trip.id)
            OfflineQueueStore.enqueueDeleteTrip(context, trip.id, firestoreDocId, userId)
        }
    }

    suspend fun updateTripMesafeBackfill(trip: TripEntity, fields: Map<String, Any?>) = withContext(Dispatchers.IO) {
        withContext(NonCancellable) {
            val userId = CurrentUserProvider.requireUserId()
            check(trip.userId == userId) {
                "Cannot update distance fields for a trip owned by a different user"
            }
            val docId = trip.firestoreDocId?.takeIf { it.isNotBlank() } ?: trip.id
            try {
                tripRemoteDataSource.updateTrip(docId, fields)
                tripDao.upsertAll(listOf(trip.withMesafeBackfillFields(fields).copy(userId = userId)))
            } catch (e: Exception) {
                tripDao.upsertAll(
                    listOf(trip.withMesafeBackfillFields(fields.asFailedMesafeBackfillFields()).copy(userId = userId))
                )
                throw e
            }
        }
    }

    private fun TripEntity.withMesafeBackfillFields(fields: Map<String, Any?>): TripEntity = copy(
        orsMesafeKm = fields[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM]?.toString()?.toDoubleOrNull()
            ?: orsMesafeKm,
        orsMesafeText = fields[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT]?.toString()
            ?: orsMesafeText,
        rmvMesafeKm = if (fields.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_KM)) {
            fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM]?.toString()?.toDoubleOrNull()
        } else {
            rmvMesafeKm
        },
        rmvMesafeMetre = if (fields.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS)) {
            fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS]?.toString()?.toDoubleOrNull()?.toInt()
        } else {
            rmvMesafeMetre
        },
        rmvMesafeText = if (fields.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT)) {
            fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT]?.toString()
        } else {
            rmvMesafeText
        },
        rmvMesafeDurumu = fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS]?.toString()
            ?: rmvMesafeDurumu,
        rmvMesafeGuncellemeTarihi = fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_UPDATED_AT]?.toString()
            ?: rmvMesafeGuncellemeTarihi,
        rmvApiVersion = if (fields.containsKey(TransitRecordCalculations.FIELD_RMV_API_VERSION)) {
            fields[TransitRecordCalculations.FIELD_RMV_API_VERSION]?.toString()
        } else {
            rmvApiVersion
        }
    )

    private fun Map<String, Any?>.asFailedMesafeBackfillFields(): Map<String, Any?> =
        toMutableMap().apply {
            this[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM] = 0.0
            this[TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS] = 0
            this[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT] = ""
            this[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS] = TransitRecordCalculations.RMV_DISTANCE_FAILED
        }

    suspend fun deleteTrip(id: String) = withContext(Dispatchers.IO) {
        val userId = CurrentUserProvider.requireUserId()
        val localTrip = tripDao.getTripByFirestoreDocId(userId, id) ?: tripDao.getTripById(userId, id)
        val tripId = localTrip?.id ?: id
        val firestoreDocId = localTrip?.firestoreDocId ?: ""
        val docId = localTrip?.firestoreDocId?.takeIf { it.isNotBlank() } ?: id
        val appId = localTrip?.id ?: id

        val deleteLocal = suspend {
            ensureCurrentUser(userId)
            if (localTrip != null) {
                tripDao.deleteTrip(userId, localTrip.id)
            } else {
                tripDao.deleteTrip(userId, id)
                tripDao.deleteTripByFirestoreDocId(userId, id)
            }

            // Programmatic cleanup for links that do not cascade from the trip table.
            getTripProfileLinkDao().deleteLinksForTrip(userId, tripId, firestoreDocId)
        }

        if (!deleteReceiptsEnabled) {
            deleteRemoteTripThenLocal(
                deleteRemote = {
                    ensureCurrentUser(userId)
                    val collection = FirestoreHelper.tripsCollection()
                    if (docId.isNotBlank()) {
                        check(tripRemoteDataSource.deleteTrip(docId)) {
                            "Firestore trip delete failed for document $docId"
                        }
                    }

                    val snapshot = collection
                        .whereEqualTo("id", appId)
                        .get()
                        .await()

                    for (doc in snapshot.documents) {
                        doc.reference.delete().await()
                    }
                },
                deleteLocal = deleteLocal
            )
            return@withContext
        }

        val store = requireNotNull(deletionStore())
        store.markLocalDeleted(userId, tripId, docId)
        try {
            deleteLocalTripThenQueue(
                deleteLocal = deleteLocal,
                enqueueDelete = {
                    OfflineQueueStore.enqueueDeleteTrip(
                        context = context,
                        recordId = tripId,
                        firestoreDocId = docId,
                        userId = userId
                    )
                }
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            store.markDeleteTemporaryError(userId, tripId, error.message)
            throw error
        }
    }

    suspend fun retryDelete(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!deleteReceiptsEnabled) return@withContext false
        val userId = CurrentUserProvider.requireUserId()
        OfflineQueueStore.retryDelete(context, userId, id)
    }

    suspend fun keepDeleteLocalOnly(id: String): Boolean = withContext(Dispatchers.IO) {
        if (!deleteReceiptsEnabled) return@withContext false
        val userId = CurrentUserProvider.requireUserId()
        OfflineQueueStore.discardPendingDelete(context, userId, id)
    }

    suspend fun getMonthSummaries(): List<MonthSummary> = withContext(Dispatchers.IO) {
        mapMonthSummaries(tripDao.getMonthSummaries(CurrentUserProvider.requireUserId()))
    }

    fun observeMonthSummaries(): Flow<List<MonthSummary>> = flow {
        val userId = CurrentUserProvider.requireUserId()
        emitAll(tripDao.observeMonthSummaries(userId).map(::mapMonthSummaries))
    }

    private fun mapMonthSummaries(
        tuples: List<com.example.toplutasima.data.local.dao.MonthSummaryTuple>
    ): List<MonthSummary> {
        val monthNames = mapOf(
            "01" to "Ocak", "02" to "Şubat", "03" to "Mart", "04" to "Nisan",
            "05" to "Mayıs", "06" to "Haziran", "07" to "Temmuz", "08" to "Ağustos",
            "09" to "Eylül", "10" to "Ekim", "11" to "Kasım", "12" to "Aralık"
        )
        return tuples.map { tuple ->
            val parts = tuple.yearMonth.split("-")
            val year = if (parts.isNotEmpty()) parts[0] else ""
            val monthNum = if (parts.size > 1) parts[1] else ""
            val monthName = monthNames[monthNum] ?: monthNum
            MonthSummary(
                monthName = monthName,
                year = year,
                count = tuple.count,
                sortKey = "${year}${monthNum}"
            )
        }.sortedBy { it.sortKey }
    }

    suspend fun getSummaryStats(sheetName: String = "Tümü"): Pair<SummaryData, List<String>> = withContext(Dispatchers.IO) {
        val entities = tripDao.getAllTrips(CurrentUserProvider.requireUserId())
        @Suppress("UNCHECKED_CAST")
        val allDocs = entities.map { it.toMap() } as List<Map<String, Any>>
        SummaryCalculator.computeSummary(allDocs, sheetName)
    }

    private fun scopedKey(key: String, userId: String): String = "$key:$userId"

    private fun ensureCurrentUser(expectedUserId: String) {
        if (CurrentUserProvider.currentUserIdOrNull() != expectedUserId) {
            throw CancellationException("Authenticated user changed during local/remote sync")
        }
    }
}
