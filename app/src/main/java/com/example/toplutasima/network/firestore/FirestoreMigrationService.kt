package com.example.toplutasima.network.firestore

import android.util.Log
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreMigrationService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val collectionName: String = "trips"
) {
    suspend fun migrateStripSeconds(): Int {
        val timeFields = listOf("planlananBinis", "gercekBinis", "planlananInis", "gercekInis")
        val snapshot = db.collection(collectionName).get().await()
        var updatedCount = 0

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val updates = mutableMapOf<String, Any>()

            for (field in timeFields) {
                val value = data[field]?.toString() ?: continue
                if (value.isBlank()) continue
                val stripped = TransitTimeUtils.stripSeconds(value)
                if (stripped != value) {
                    updates[field] = stripped
                }
            }

            if (updates.isNotEmpty()) {
                updates["updatedAt"] = System.currentTimeMillis()
                doc.reference.update(updates).await()
                updatedCount++
            }
        }

        return updatedCount
    }

    suspend fun migrateYolSuresi(): Pair<Int, Int> {
        val snapshot = db.collection(collectionName).get().await()
        var updatedCount = 0
        val totalCount = snapshot.documents.size

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val updates = mutableMapOf<String, Any>()

            val planlananBinis = data["planlananBinis"]?.toString()
            val planlananInis = data["planlananInis"]?.toString()
            val gercekBinis = data["gercekBinis"]?.toString()
            val gercekInis = data["gercekInis"]?.toString()
            val mevcutPlanlanan = data["planlananYolSuresi"]?.toString()
            val mevcutGercek = data["gercekYolSuresi"]?.toString()

            if (!planlananBinis.isNullOrBlank() && !planlananInis.isNullOrBlank()) {
                val pDuration = TransitRecordCalculations.computeYolSuresi(planlananBinis, planlananInis)
                if (pDuration.isNotBlank() && pDuration != mevcutPlanlanan) updates["planlananYolSuresi"] = pDuration
            }
            if (!gercekBinis.isNullOrBlank() && !gercekInis.isNullOrBlank()) {
                val gDuration = TransitRecordCalculations.computeYolSuresi(gercekBinis, gercekInis)
                if (gDuration.isNotBlank() && gDuration != mevcutGercek) updates["gercekYolSuresi"] = gDuration
            }

            if (updates.isNotEmpty()) {
                updates["updatedAt"] = System.currentTimeMillis()
                doc.reference.update(updates).await()
                updatedCount++
            }
        }

        return Pair(updatedCount, totalCount)
    }

    suspend fun migrateYearMonth(): Pair<Int, Int> {
        var updated = 0
        var total = 0
        var lastVisible: DocumentSnapshot? = null
        val limit = 500L

        while (true) {
            var query = db.collection(collectionName).limit(limit)
            if (lastVisible != null) {
                query = query.startAfter(lastVisible!!)
            }
            val snapshot = query.get().await()
            if (snapshot.isEmpty) break

            total += snapshot.documents.size
            val batch = db.batch()
            var batchCount = 0

            for (doc in snapshot.documents) {
                val existing = doc.getString("yearMonth")
                if (!existing.isNullOrBlank()) continue

                val tarih = doc.getString("tarih") ?: continue
                val ym = TransitRecordCalculations.computeYearMonth(tarih)
                if (ym.isBlank()) continue

                batch.update(
                    doc.reference,
                    mapOf(
                        "yearMonth" to ym,
                        "updatedAt" to System.currentTimeMillis()
                    )
                )
                batchCount++
            }

            if (batchCount > 0) {
                batch.commit().await()
                updated += batchCount
            }

            lastVisible = snapshot.documents[snapshot.size() - 1]
            if (snapshot.size() < limit) break
        }
        return Pair(updated, total)
    }

    suspend fun migrateSortDate(): Pair<Int, Int> {
        val snapshot = db.collection(collectionName).get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            val existing = doc.getString("sortDate")
            if (!existing.isNullOrBlank()) continue

            val tarih = doc.getString("tarih") ?: continue
            val sd = TransitRecordCalculations.computeSortDate(tarih)
            if (sd.isBlank()) continue

            try {
                doc.reference.update(
                    mapOf(
                        "sortDate" to sd,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
                updated++
            } catch (e: Exception) {
                Log.e(TAG, "migrateSortDate failed for doc: ${doc.id}", e)
            }
        }
        return Pair(updated, total)
    }

    suspend fun migrateDistanceFields(): Pair<Int, Int> {
        val snapshot = db.collection(collectionName).get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val updates = linkedMapOf<String, Any>()

            val existingOrsKm = TransitRecordCalculations.parseDistanceKm(
                data[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM]
            )
            val orsKm = existingOrsKm
                ?: TransitRecordCalculations.parseDistanceKm(data["mesafe"])
                ?: 0.0
            val orsText = TransitRecordCalculations.formatDistanceKm(orsKm)

            if (!data.containsKey(TransitRecordCalculations.FIELD_ORS_DISTANCE_KM) ||
                (existingOrsKm == null && orsKm > 0.0)
            ) {
                updates[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM] = orsKm
            }
            if (!data.containsKey(TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT) ||
                (data[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT]?.toString().isNullOrBlank() &&
                    orsText.isNotBlank())
            ) {
                updates[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT] = orsText
            }

            val rmvKm = TransitRecordCalculations.parseDistanceKm(
                data[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM]
            )
            val rmvText = data[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT]?.toString().orEmpty()
            if (!data.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_KM)) {
                updates[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM] = 0.0
            }
            if (!data.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS)) {
                updates[TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS] = 0
            }
            if (!data.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT)) {
                updates[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT] = ""
            }
            if (!data.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS) ||
                data[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS]?.toString().isNullOrBlank()
            ) {
                updates[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS] =
                    if (rmvKm != null || rmvText.isNotBlank()) RMV_DISTANCE_READY
                    else TransitRecordCalculations.RMV_DISTANCE_PENDING
            }
            if (!data.containsKey(TransitRecordCalculations.FIELD_RMV_DISTANCE_UPDATED_AT)) {
                updates[TransitRecordCalculations.FIELD_RMV_DISTANCE_UPDATED_AT] = ""
            }
            if (!data.containsKey(TransitRecordCalculations.FIELD_RMV_API_VERSION)) {
                updates[TransitRecordCalculations.FIELD_RMV_API_VERSION] = ""
            }
            if (!data.containsKey(FIELD_JOURNEY_REF)) updates[FIELD_JOURNEY_REF] = ""
            if (!data.containsKey(FIELD_FROM_STOP_ID)) updates[FIELD_FROM_STOP_ID] = ""
            if (!data.containsKey(FIELD_TO_STOP_ID)) updates[FIELD_TO_STOP_ID] = ""

            if (updates.isNotEmpty()) {
                updates["updatedAt"] = System.currentTimeMillis()
                try {
                    doc.reference.update(updates).await()
                    updated++
                } catch (e: Exception) {
                    Log.e(TAG, "migrateDistanceFields failed for doc: ${doc.id}", e)
                }
            }
        }
        return Pair(updated, total)
    }

    suspend fun migrateSeatmateUuid(): Pair<Int, Int> {
        var updated = 0
        var total = 0
        var lastVisible: DocumentSnapshot? = null
        val limit = 500L

        while (true) {
            var query = db.collection(collectionName).limit(limit)
            if (lastVisible != null) {
                query = query.startAfter(lastVisible!!)
            }
            val snapshot = query.get().await()
            if (snapshot.isEmpty) break

            total += snapshot.documents.size
            val batch = db.batch()
            var batchCount = 0

            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                if (!data.containsKey("seatmateUuid")) {
                    batch.update(
                        doc.reference,
                        mapOf(
                            "seatmateUuid" to "",
                            "updatedAt" to System.currentTimeMillis()
                        )
                    )
                    batchCount++
                }
            }

            if (batchCount > 0) {
                batch.commit().await()
                updated += batchCount
            }

            lastVisible = snapshot.documents[snapshot.size() - 1]
            if (snapshot.size() < limit) break
        }
        return Pair(total, updated)
    }

    private companion object {
        private const val TAG = "FirestoreMigrationService"
        private const val FIELD_JOURNEY_REF = "journeyRef"
        private const val FIELD_FROM_STOP_ID = "fromStopId"
        private const val FIELD_TO_STOP_ID = "toStopId"
        private const val RMV_DISTANCE_READY = "hazir"
    }
}
