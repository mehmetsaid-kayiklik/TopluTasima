package com.example.toplutasima.network.firestore

import android.util.Log
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.example.toplutasima.usecase.TransitTimeUtils
import com.google.firebase.firestore.DocumentSnapshot
import kotlinx.coroutines.tasks.await

class FirestoreMigrationService {
    private fun collection() = FirestoreHelper.tripsCollection()

    suspend fun migrateStripSeconds(): Int {
        val timeFields = listOf("planlananBinis", "gercekBinis", "planlananInis", "gercekInis")
        val snapshot = collection().get().await()
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
        val snapshot = collection().get().await()
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

    suspend fun migrateDerivedFields(): Pair<Int, Int> {
        val snapshot = collection().get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val updates = linkedMapOf<String, Any>()

            val tarih = data["tarih"]?.toString().orEmpty()
            if (tarih.isNotBlank()) {
                putIfChanged(updates, data, "gun", TransitRecordCalculations.computeGun(tarih))
                putIfChanged(updates, data, "gununTipi", TransitRecordCalculations.computeGununTipi(tarih))
                putIfChanged(updates, data, "sortDate", TransitRecordCalculations.computeSortDate(tarih))
                putIfChanged(updates, data, "yearMonth", TransitRecordCalculations.computeYearMonth(tarih))
            }

            val planlananBinis = data["planlananBinis"]?.toString()
            val planlananInis = data["planlananInis"]?.toString()
            val gercekBinis = data["gercekBinis"]?.toString()
            val gercekInis = data["gercekInis"]?.toString()

            putIfChanged(
                updates,
                data,
                "gecikme",
                TransitRecordCalculations.computeGecikme(planlananBinis, gercekBinis)
            )
            putIfChanged(
                updates,
                data,
                "planlananYolSuresi",
                TransitRecordCalculations.computeYolSuresi(planlananBinis, planlananInis)
            )
            putIfChanged(
                updates,
                data,
                "gercekYolSuresi",
                TransitRecordCalculations.computeYolSuresi(gercekBinis, gercekInis)
            )

            if (data.containsKey("mesafe")) {
                val distanceKm = TransitRecordCalculations.parseDistanceKm(data["mesafe"]) ?: 0.0
                TransitRecordCalculations.calculatedDistanceFields(
                    distanceKm,
                    resetRmvDistance = true
                ).forEach { (key, value) ->
                    putIfChanged(updates, data, key, value)
                }
            }

            if (updates.isNotEmpty()) {
                updates["updatedAt"] = System.currentTimeMillis()
                FirestoreHelper.safeFirestore {
                    doc.reference.update(updates).await()
                }.onSuccess {
                    updated++
                }.onFailure { e ->
                    Log.e(TAG, "migrateDerivedFields failed for doc: ${doc.id}", e)
                }
            }
        }

        return Pair(updated, total)
    }

    suspend fun migrateYearMonth(): Pair<Int, Int> {
        var updated = 0
        var total = 0
        var lastVisible: DocumentSnapshot? = null
        val limit = 500L

        while (true) {
            var query = collection().limit(limit)
            if (lastVisible != null) {
                query = query.startAfter(lastVisible!!)
            }
            val snapshot = query.get().await()
            if (snapshot.isEmpty) break

            total += snapshot.documents.size
            val batch = FirestoreHelper.batch()
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
        val snapshot = collection().get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            val existing = doc.getString("sortDate")
            if (!existing.isNullOrBlank()) continue

            val tarih = doc.getString("tarih") ?: continue
            val sd = TransitRecordCalculations.computeSortDate(tarih)
            if (sd.isBlank()) continue

            FirestoreHelper.safeFirestore {
                doc.reference.update(
                    mapOf(
                        "sortDate" to sd,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }.onSuccess {
                updated++
            }.onFailure { e ->
                Log.e(TAG, "migrateSortDate failed for doc: ${doc.id}", e)
            }
        }
        return Pair(updated, total)
    }

    suspend fun migrateDistanceFields(): Pair<Int, Int> {
        val snapshot = collection().get().await()
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
                FirestoreHelper.safeFirestore {
                    doc.reference.update(updates).await()
                }.onSuccess {
                    updated++
                }.onFailure { e ->
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
            var query = collection().limit(limit)
            if (lastVisible != null) {
                query = query.startAfter(lastVisible!!)
            }
            val snapshot = query.get().await()
            if (snapshot.isEmpty) break

            total += snapshot.documents.size
            val batch = FirestoreHelper.batch()
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

    /**
     * Scans all trip records and fixes early-departure delays that were
     * incorrectly stored as 0. Only updates records where:
     *   - gecikme == 0
     *   - gercekBinis < planlananBinis  (actual boarding is earlier)
     *
     * Sets gecikme to a negative value (actual − planned) in minutes.
     */
    suspend fun migrateEarlyDepartures(): Pair<Int, Int> {
        val snapshot = collection().get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue

            // Only touch records where gecikme is exactly 0
            val gecikme = data["gecikme"]?.toString()?.toIntOrNull()
            if (gecikme == null || gecikme != 0) continue

            val planlananBinis = data["planlananBinis"]?.toString()
            val gercekBinis = data["gercekBinis"]?.toString()
            if (planlananBinis.isNullOrBlank() || gercekBinis.isNullOrBlank()) continue

            val plannedMin = parseTimeToMinutes(planlananBinis) ?: continue
            val actualMin = parseTimeToMinutes(gercekBinis) ?: continue

            // Only fix if actual boarding is strictly before planned
            if (actualMin >= plannedMin) continue

            val diff = actualMin - plannedMin // negative value (early)

            FirestoreHelper.safeFirestore {
                doc.reference.update(
                    mapOf(
                        "gecikme" to diff,
                        "updatedAt" to System.currentTimeMillis()
                    )
                ).await()
            }.onSuccess {
                updated++
            }.onFailure { e ->
                Log.e(TAG, "migrateEarlyDepartures failed for doc: ${doc.id}", e)
            }
        }
        return Pair(updated, total)
    }

    /** Parses "HH:MM" or "HH:MM:SS" to total minutes, or null on failure. */
    private fun parseTimeToMinutes(time: String): Int? {
        val parts = time.trim().split(":")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    private fun putIfChanged(
        updates: MutableMap<String, Any>,
        data: Map<String, Any>,
        key: String,
        value: Any
    ) {
        if (data[key]?.toString() != value.toString()) {
            updates[key] = value
        }
    }

    private companion object {
        private const val TAG = "FirestoreMigrationService"
        private const val FIELD_JOURNEY_REF = "journeyRef"
        private const val FIELD_FROM_STOP_ID = "fromStopId"
        private const val FIELD_TO_STOP_ID = "toStopId"
        private const val RMV_DISTANCE_READY = "hazir"
    }
}
