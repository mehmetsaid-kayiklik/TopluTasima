package com.example.toplutasima.repository

import android.content.Context
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.repository.toEntity
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.model.Segment
import com.example.toplutasima.network.rmv.SegmentDistanceResult
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.transit.TransitFeatureFlags
import com.example.toplutasima.transit.sync.TransitSyncStatusStore
import com.example.toplutasima.usecase.TransitRecordCalculations
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransitRecordRepository(
    private val appContext: Context? = null,
    private val profileLinkRepository: TripProfileLinkRepository = TripProfileLinkRepository(appContext),
    private val recordMapper: TripRecordMapper = TripRecordMapper,
    private val tripRemoteDataSource: FirestoreTripRemoteDataSource = FirestoreTripRemoteDataSource()
) {
    private fun getTripDao() = appContext?.let { AppDatabase.getDatabase(it).tripDao() }
    private fun getSyncStatusStore() = if (TransitFeatureFlags.SYNC_RECEIPTS) {
        appContext?.let { TransitSyncStatusStore.get(it) }
    } else {
        null
    }

    suspend fun saveSegment(
        id: String,
        date: String,
        seg: Segment,
        havaDurumu: String,
        oturabildim: Boolean,
        biletKontrolu: Boolean,
        note: String,
        seatmateUuid: String = "",
        profileId: String? = null,
        seatmateNote: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = CurrentUserProvider.requireUserId()
        val syncStatusStore = getSyncStatusStore()
        if (isDeletionTombstoned(syncStatusStore, userId, id)) return@withContext false
        syncStatusStore?.markLocalSaving(userId, id)
        val data = recordMapper.buildSegmentData(
            id = id,
            date = date,
            seg = seg,
            havaDurumu = havaDurumu,
            oturabildim = oturabildim,
            biletKontrolu = biletKontrolu,
            note = note,
            seatmateUuid = seatmateUuid
        )
        val tripDao = getTripDao()
        val existingFirestoreDocId = data["firestoreDocId"]
            ?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: tripDao?.getTripById(userId, id)?.firestoreDocId?.takeIf { it.isNotBlank() }
        val firestoreDocId = existingFirestoreDocId ?: tripRemoteDataSource.newTripDocumentId()
        data["firestoreDocId"] = firestoreDocId
        // Persist the client-generated ID before Firestore so a timeout can only retry this ID.
        tripDao?.upsertAll(listOf(data.toEntity(userId)))
        syncStatusStore?.markLocalSafe(userId, id)

        profileLinkRepository.saveInitialLink(id, profileId, seatmateNote)
        profileLinkRepository.updateStableKey(id, firestoreDocId)

        try {
            syncStatusStore?.markSyncing(userId, id)
            tripRemoteDataSource.saveTrip(data)
            if (isDeletionTombstoned(syncStatusStore, userId, id, firestoreDocId)) {
                tripDao?.deleteTrip(userId, id)
                appContext?.let {
                    OfflineQueueStore.enqueueDeleteTrip(it, id, firestoreDocId, userId)
                }
                return@withContext false
            }
            syncStatusStore?.markSynced(userId, id)
            true
        } catch (e: CancellationException) {
            syncStatusStore?.markLocalSafe(userId, id)
            throw e
        } catch (_: Exception) {
            appContext?.let {
                OfflineQueueStore.enqueueSaveTrip(it, data, userId)
                return@withContext true
            }
            false
        }
    }

    suspend fun updateActual(id: String, actualDep: String?, actualArr: String?): Boolean =
        withContext(Dispatchers.IO) {
            val userId = CurrentUserProvider.requireUserId()
            val syncStatusStore = getSyncStatusStore()
            if (isDeletionTombstoned(syncStatusStore, userId, id)) return@withContext false
            syncStatusStore?.markLocalSaving(userId, id)
            val tripDao = getTripDao()
            // Firestore'a gönderilecek ID: önce Room'dan doğru kaydı bul,
            // varsa Firestore doc içindeki "id" alanını kullan (doc'u "id" field'ı ile arıyoruz)
            var firestoreQueryId = id
            if (tripDao != null) {
                var existingEntity = tripDao.getTripById(userId, id)
                if (existingEntity == null) {
                    existingEntity = tripDao.getTripByFirestoreDocId(userId, id)
                }
                // Room kaydındaki "id" alanı Firestore'daki "id" field'ı ile eşleşmeli
                existingEntity?.id?.takeIf { it.isNotBlank() }?.let { firestoreQueryId = it }

                val existing = existingEntity?.toMap()?.toMutableMap()
                if (existing != null) {
                    if (!actualDep.isNullOrBlank()) existing["gercekBinis"] = actualDep
                    if (!actualArr.isNullOrBlank()) existing["gercekInis"] = actualArr
                    if (!actualDep.isNullOrBlank()) {
                        val planlananBinis = existing["planlananBinis"]?.toString()
                        existing["gecikme"] = TransitRecordCalculations.computeGecikme(planlananBinis, actualDep)
                    }
                    val finalGercekBinis = actualDep ?: existing["gercekBinis"]?.toString()
                    val finalGercekInis = actualArr ?: existing["gercekInis"]?.toString()
                    if (!finalGercekBinis.isNullOrBlank() && !finalGercekInis.isNullOrBlank()) {
                        existing["gercekYolSuresi"] =
                            TransitRecordCalculations.computeYolSuresi(finalGercekBinis, finalGercekInis)
                    }
                    tripDao.upsertAll(listOf(existing.toEntity(userId)))
                    syncStatusStore?.markLocalSafe(userId, id)
                }
            }
            try {
                syncStatusStore?.markSyncing(userId, id)
                val ok = tripRemoteDataSource.updateActual(firestoreQueryId, actualDep, actualArr)
                if (ok) {
                    syncStatusStore?.markSynced(userId, id)
                } else {
                    syncStatusStore?.markPermanentError(userId, id, "Remote record was not found")
                }
                ok
            } catch (e: CancellationException) {
                syncStatusStore?.markLocalSafe(userId, id)
                throw e
            } catch (_: Exception) {
                appContext?.let {
                    OfflineQueueStore.enqueueUpdateActual(
                        it,
                        firestoreQueryId,
                        actualDep,
                        actualArr,
                        userId
                    )
                    return@withContext true
                }
                false
            }
        }

    suspend fun clearActual(id: String, clearDep: Boolean, clearArr: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val userId = CurrentUserProvider.requireUserId()
            val syncStatusStore = getSyncStatusStore()
            if (isDeletionTombstoned(syncStatusStore, userId, id)) return@withContext false
            syncStatusStore?.markLocalSaving(userId, id)
            val tripDao = getTripDao()
            if (tripDao != null) {
                val existing = tripDao.getTripById(userId, id)?.toMap()?.toMutableMap()
                if (existing != null) {
                    if (clearDep) {
                        existing["gercekBinis"] = ""
                        existing["gecikme"] = 0
                    }
                    if (clearArr) existing["gercekInis"] = ""
                    val finalGercekBinis = if (clearDep) "" else existing["gercekBinis"]?.toString()
                    val finalGercekInis = if (clearArr) "" else existing["gercekInis"]?.toString()
                    existing["gercekYolSuresi"] =
                        if (!finalGercekBinis.isNullOrBlank() && !finalGercekInis.isNullOrBlank()) {
                            TransitRecordCalculations.computeYolSuresi(finalGercekBinis, finalGercekInis)
                        } else {
                            ""
                        }
                    tripDao.upsertAll(listOf(existing.toEntity(userId)))
                    syncStatusStore?.markLocalSafe(userId, id)
                }
            }
            try {
                syncStatusStore?.markSyncing(userId, id)
                val ok = tripRemoteDataSource.clearActual(id, clearDep, clearArr)
                if (ok) {
                    syncStatusStore?.markSynced(userId, id)
                } else {
                    syncStatusStore?.markPermanentError(userId, id, "Remote record was not found")
                }
                ok
            } catch (e: CancellationException) {
                syncStatusStore?.markLocalSafe(userId, id)
                throw e
            } catch (_: Exception) {
                val fields = mutableMapOf<String, Any>()
                if (clearDep) {
                    fields["gercekBinis"] = ""
                    fields["gecikme"] = 0
                }
                if (clearArr) fields["gercekInis"] = ""
                if (clearDep || clearArr) fields["gercekYolSuresi"] = ""
                appContext?.let {
                    OfflineQueueStore.enqueueUpdateRecord(it, id, fields, userId)
                    return@withContext true
                }
                false
            }
        }

    suspend fun fetchRecord(id: String): Map<String, Any>? =
        withContext(Dispatchers.IO) { tripRemoteDataSource.fetchRecord(id) }

    suspend fun updateStops(
        id: String,
        binisDuragi: String?,
        binisTime: String?,
        inisDuragi: String?,
        inisTime: String?,
        mesafe: String? = null,
        durakSayisi: String? = null,
        distanceResult: SegmentDistanceResult? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val userId = CurrentUserProvider.requireUserId()
        val syncStatusStore = getSyncStatusStore()
        if (isDeletionTombstoned(syncStatusStore, userId, id)) return@withContext false
        syncStatusStore?.markLocalSaving(userId, id)
        val tripDao = getTripDao()
        if (tripDao != null) {
            val existing = tripDao.getTripById(userId, id)?.toMap()?.toMutableMap()
            if (existing != null) {
                if (!binisDuragi.isNullOrBlank()) {
                    existing["binisDuragi"] = binisDuragi
                    existing[TransitRecordCalculations.FIELD_FROM_STOP_ID] = ""
                }
                if (!binisTime.isNullOrBlank()) existing["planlananBinis"] = binisTime
                if (!inisDuragi.isNullOrBlank()) {
                    existing["inisDuragi"] = inisDuragi
                    existing[TransitRecordCalculations.FIELD_TO_STOP_ID] = ""
                }
                if (!inisTime.isNullOrBlank()) existing["planlananInis"] = inisTime
                if (mesafe != null) {
                    existing["mesafe"] = mesafe
                    if (distanceResult != null) {
                        existing.putAll(TransitRecordCalculations.calculatedDistanceFields(distanceResult))
                    } else {
                        val distanceKm = TransitRecordCalculations.parseDistanceKm(mesafe) ?: 0.0
                        existing.putAll(
                            TransitRecordCalculations.calculatedDistanceFields(distanceKm, resetRmvDistance = true)
                        )
                    }
                }
                if (durakSayisi != null) existing["durakSayisi"] = durakSayisi

                if (binisTime != null || inisTime != null) {
                    val finalBinis = binisTime ?: existing["planlananBinis"]?.toString()
                    val finalInis = inisTime ?: existing["planlananInis"]?.toString()
                    existing["planlananYolSuresi"] = TransitRecordCalculations.computeYolSuresi(finalBinis, finalInis)
                }
                tripDao.upsertAll(listOf(existing.toEntity(userId)))
                syncStatusStore?.markLocalSafe(userId, id)
            }
        }
        try {
            syncStatusStore?.markSyncing(userId, id)
            val ok = tripRemoteDataSource.updateStops(
                id,
                binisDuragi,
                binisTime,
                inisDuragi,
                inisTime,
                mesafe,
                durakSayisi,
                distanceResult
            )
            if (ok) {
                syncStatusStore?.markSynced(userId, id)
            } else {
                syncStatusStore?.markPermanentError(userId, id, "Remote record was not found")
            }
            ok
        } catch (e: CancellationException) {
            syncStatusStore?.markLocalSafe(userId, id)
            throw e
        } catch (e: Exception) {
            syncStatusStore?.markTemporaryError(userId, id, e.message)
            throw e
        }
    }

    suspend fun updateExistingRecord(id: String, fields: Map<String, Any>): Boolean =
        withContext(Dispatchers.IO) {
            val userId = CurrentUserProvider.requireUserId()
            val syncStatusStore = getSyncStatusStore()
            if (isDeletionTombstoned(syncStatusStore, userId, id)) return@withContext false
            syncStatusStore?.markLocalSaving(userId, id)
            val tripDao = getTripDao()
            if (tripDao != null) {
                val existing = tripDao.getTripById(userId, id)?.toMap()?.toMutableMap()
                if (existing != null) {
                    existing.putAll(fields)
                    tripDao.upsertAll(listOf(existing.toEntity(userId)))
                    syncStatusStore?.markLocalSafe(userId, id)
                }
            }
            try {
                syncStatusStore?.markSyncing(userId, id)
                val ok = tripRemoteDataSource.updateExistingRecord(id, fields)
                if (ok) {
                    syncStatusStore?.markSynced(userId, id)
                } else {
                    syncStatusStore?.markPermanentError(userId, id, "Remote record was not found")
                }
                ok
            } catch (e: CancellationException) {
                syncStatusStore?.markLocalSafe(userId, id)
                throw e
            } catch (_: Exception) {
                appContext?.let {
                    OfflineQueueStore.enqueueUpdateRecord(it, id, fields, userId)
                    return@withContext true
                }
                false
            }
        }

    private fun isDeletionTombstoned(
        store: TransitSyncStatusStore?,
        userId: String,
        recordId: String,
        firestoreDocId: String? = recordId
    ): Boolean = TransitFeatureFlags.SYNC_DELETE_RECEIPTS &&
        store?.isDeletionTombstoned(userId, recordId, firestoreDocId) == true
}
