package com.example.toplutasima.repository

import android.content.Context
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.repository.toEntity
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.model.Segment
import com.example.toplutasima.network.rmv.SegmentDistanceResult
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
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
        tripDao?.upsertAll(listOf(data.toEntity()))

        profileLinkRepository.saveInitialLink(id, profileId, seatmateNote)

        try {
            val firestoreDocId = tripRemoteDataSource.saveTrip(data)
            if (firestoreDocId.isNotBlank()) {
                data["firestoreDocId"] = firestoreDocId
                tripDao?.upsertAll(listOf(data.toEntity()))
                profileLinkRepository.updateStableKey(id, firestoreDocId)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            appContext?.let {
                OfflineQueueStore.enqueueSaveTrip(it, data)
                return@withContext true
            }
            false
        }
    }

    suspend fun updateActual(id: String, actualDep: String?, actualArr: String?): Boolean =
        withContext(Dispatchers.IO) {
            val tripDao = getTripDao()
            // Firestore'a gönderilecek ID: önce Room'dan doğru kaydı bul,
            // varsa Firestore doc içindeki "id" alanını kullan (doc'u "id" field'ı ile arıyoruz)
            var firestoreQueryId = id
            if (tripDao != null) {
                var existingEntity = tripDao.getTripById(id)
                if (existingEntity == null) {
                    existingEntity = tripDao.getTripByFirestoreDocId(id)
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
                    tripDao.upsertAll(listOf(existing.toEntity()))
                }
            }
            try {
                val ok = tripRemoteDataSource.updateActual(firestoreQueryId, actualDep, actualArr)
                ok
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                appContext?.let {
                    OfflineQueueStore.enqueueUpdateActual(it, firestoreQueryId, actualDep, actualArr)
                    return@withContext true
                }
                false
            }
        }

    suspend fun clearActual(id: String, clearDep: Boolean, clearArr: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            val tripDao = getTripDao()
            if (tripDao != null) {
                val existing = tripDao.getTripById(id)?.toMap()?.toMutableMap()
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
                    tripDao.upsertAll(listOf(existing.toEntity()))
                }
            }
            try {
                tripRemoteDataSource.clearActual(id, clearDep, clearArr)
            } catch (e: CancellationException) {
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
                    OfflineQueueStore.enqueueUpdateRecord(it, id, fields)
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
        val tripDao = getTripDao()
        if (tripDao != null) {
            val existing = tripDao.getTripById(id)?.toMap()?.toMutableMap()
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
                tripDao.upsertAll(listOf(existing.toEntity()))
            }
        }
        tripRemoteDataSource.updateStops(
            id,
            binisDuragi,
            binisTime,
            inisDuragi,
            inisTime,
            mesafe,
            durakSayisi,
            distanceResult
        )
    }

    suspend fun updateExistingRecord(id: String, fields: Map<String, Any>): Boolean =
        withContext(Dispatchers.IO) {
            val tripDao = getTripDao()
            if (tripDao != null) {
                val existing = tripDao.getTripById(id)?.toMap()?.toMutableMap()
                if (existing != null) {
                    existing.putAll(fields)
                    tripDao.upsertAll(listOf(existing.toEntity()))
                }
            }
            try {
                tripRemoteDataSource.updateExistingRecord(id, fields)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                appContext?.let {
                    OfflineQueueStore.enqueueUpdateRecord(it, id, fields)
                    return@withContext true
                }
                false
            }
        }
}
