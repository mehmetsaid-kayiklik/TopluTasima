package com.example.toplutasima.network.firestore

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.BulkUpdateRow
import com.example.toplutasima.network.rmv.SegmentDistanceResult
import com.example.toplutasima.usecase.TransitRecordCalculations
import kotlinx.coroutines.tasks.await

class FirestoreTripRemoteDataSource {
    private fun collection() = FirestoreHelper.tripsCollection()

    suspend fun saveTrip(data: Map<String, Any?>): String {
        val enriched = data.toMutableMap()
        val firestoreDocId = enriched["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
        val tarih = enriched["tarih"]?.toString()
        if (!tarih.isNullOrBlank()) {
            if (!enriched.containsKey("yearMonth")) {
                enriched["yearMonth"] = TransitRecordCalculations.computeYearMonth(tarih)
            }
            if (!enriched.containsKey("sortDate")) {
                enriched["sortDate"] = TransitRecordCalculations.computeSortDate(tarih)
            }
        }
        enriched["updatedAt"] = System.currentTimeMillis()
        enrichNewDistanceFields(enriched)
        val ordered = buildOrderedMap(enriched)
        return if (firestoreDocId != null) {
            collection().document(firestoreDocId).set(ordered).await()
            firestoreDocId
        } else {
            val doc = collection().add(ordered).await()
            doc.id
        }
    }

    suspend fun updateActual(tripId: String, actualDep: String?, actualArr: String?): Boolean {
        val snapshot = collection()
            .whereEqualTo("id", tripId)
            .get().await()
        Log.d(TAG, "updateActual query for id=$tripId → found ${snapshot.size()} docs")
        if (snapshot.isEmpty) {
            Log.w(TAG, "updateActual: no document found with id=$tripId — write skipped")
            return false
        }
        val docRef = snapshot.documents[0].reference
        val existingData = snapshot.documents[0].data ?: return false
        val updates = mutableMapOf<String, Any>()
        if (!actualDep.isNullOrBlank()) updates["gercekBinis"] = actualDep
        if (!actualArr.isNullOrBlank()) updates["gercekInis"] = actualArr

        if (!actualDep.isNullOrBlank()) {
            val planlananBinis = existingData["planlananBinis"]?.toString()
            updates["gecikme"] = TransitRecordCalculations.computeGecikme(planlananBinis, actualDep)
        }

        val finalGercekBinis = actualDep ?: existingData["gercekBinis"]?.toString()
        val finalGercekInis = actualArr ?: existingData["gercekInis"]?.toString()
        if (!finalGercekBinis.isNullOrBlank() && !finalGercekInis.isNullOrBlank()) {
            updates["gercekYolSuresi"] =
                TransitRecordCalculations.computeYolSuresi(finalGercekBinis, finalGercekInis)
        }

        if (updates.isNotEmpty()) {
            updates["updatedAt"] = System.currentTimeMillis()
            docRef.update(updates).await()
        }
        return true
    }

    suspend fun clearActual(tripId: String, clearDep: Boolean, clearArr: Boolean): Boolean {
        val snapshot = collection()
            .whereEqualTo("id", tripId)
            .get().await()
        if (snapshot.isEmpty) return false
        val docRef = snapshot.documents[0].reference
        val existingData = snapshot.documents[0].data ?: return false
        val updates = mutableMapOf<String, Any>()
        if (clearDep) {
            updates["gercekBinis"] = ""
            updates["gecikme"] = 0
        }
        if (clearArr) updates["gercekInis"] = ""

        val finalGercekBinis = if (clearDep) "" else existingData["gercekBinis"]?.toString()
        val finalGercekInis = if (clearArr) "" else existingData["gercekInis"]?.toString()
        updates["gercekYolSuresi"] =
            if (!finalGercekBinis.isNullOrBlank() && !finalGercekInis.isNullOrBlank()) {
                TransitRecordCalculations.computeYolSuresi(finalGercekBinis, finalGercekInis)
            } else {
                ""
            }

        if (updates.isNotEmpty()) {
            updates["updatedAt"] = System.currentTimeMillis()
            docRef.update(updates).await()
        }
        return true
    }

    suspend fun fetchRecord(tripId: String): Map<String, Any>? {
        if (tripId.isBlank()) return null
        return FirestoreHelper.safeFirestore {
            val snapshot = collection()
                .whereEqualTo("id", tripId)
                .limit(1)
                .get().await()
            if (snapshot.isEmpty) null else snapshot.documents[0].data
        }.getOrElse { e ->
            Log.e(TAG, "fetchRecord failed for tripId: $tripId", e)
            throw e
        }
    }

    suspend fun fetchTrips(): List<Map<String, Any>> {
        val snapshot = collection().get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data + ("firestoreDocId" to doc.id)
        }.sortedByDescending { doc ->
            doc["sortDate"]?.toString().takeIf { !it.isNullOrBlank() }
                ?: TransitRecordCalculations.computeSortDate(doc["tarih"]?.toString() ?: "")
        }
    }

    suspend fun fetchTripsAfter(sortDate: String): List<Map<String, Any>> {
        val snapshot = collection()
            .whereGreaterThanOrEqualTo("sortDate", sortDate)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data + ("firestoreDocId" to doc.id)
        }
    }

    suspend fun fetchTripsUpdatedAfter(updatedAt: Long): List<Map<String, Any>> {
        val snapshot = collection()
            .whereGreaterThan("updatedAt", updatedAt)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data + ("firestoreDocId" to doc.id)
        }
    }

    suspend fun fetchTripsFiltered(
        month: String = "T\u00fcm\u00fc",
        type: String = "T\u00fcm\u00fc"
    ): List<Map<String, Any>> {
        val snapshot = collection().get().await()

        val filterMonthNum: String?
        val filterYear: String?
        if (month != ALL_FILTER) {
            val filterParts = month.split(" ")
            filterMonthNum = if (filterParts.size >= 2) {
                MONTH_NUMBERS[filterParts[0]]
            } else {
                MONTH_NUMBERS[month]
            }
            filterYear = if (filterParts.size >= 2) filterParts[1] else null
        } else {
            filterMonthNum = null
            filterYear = null
        }

        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val tarih = data["tarih"]?.toString() ?: ""
            if (tarih.isBlank() || tarih.lowercase() == "tarih") return@mapNotNull null

            if (filterMonthNum != null) {
                val parts = tarih.split(".")
                if (parts.size < 3 || parts[1] != filterMonthNum) return@mapNotNull null
                if (filterYear != null && parts[2] != filterYear) return@mapNotNull null
            }

            if (type != ALL_FILTER) {
                val tur = data["tur"]?.toString() ?: ""
                if (tur != type) return@mapNotNull null
            }

            data + ("firestoreDocId" to doc.id)
        }.sortedByDescending { doc ->
            doc["sortDate"]?.toString().takeIf { !it.isNullOrBlank() }
                ?: TransitRecordCalculations.computeSortDate(doc["tarih"]?.toString() ?: "")
        }
    }

    suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean {
        logD("FirestoreUpdate", "docId='$docId' isEmpty=${docId.isBlank()}")
        return FirestoreHelper.safeFirestore {
            val docRef = collection().document(docId)
            val cleanFields = fields.filterValues { it != null }.mapValues { it.value!! }
            val existing = docRef.get().await().data
            val updates = cleanFields.toMutableMap()
            enrichUpdatedDistanceFields(updates)

            val newTarih = updates["tarih"]?.toString()
            if (!newTarih.isNullOrBlank()) {
                updates["sortDate"] = TransitRecordCalculations.computeSortDate(newTarih)
                updates["yearMonth"] = TransitRecordCalculations.computeYearMonth(newTarih)
            }

            val finalPlanlananBinis =
                updates["planlananBinis"]?.toString() ?: existing?.get("planlananBinis")?.toString()
            val finalGercekBinis =
                updates["gercekBinis"]?.toString() ?: existing?.get("gercekBinis")?.toString()
            val finalPlanlananInis =
                updates["planlananInis"]?.toString() ?: existing?.get("planlananInis")?.toString()
            val finalGercekInis =
                updates["gercekInis"]?.toString() ?: existing?.get("gercekInis")?.toString()

            if (updates.containsKey("planlananBinis") || updates.containsKey("planlananInis")) {
                updates["planlananYolSuresi"] =
                    TransitRecordCalculations.computeYolSuresi(finalPlanlananBinis, finalPlanlananInis)
            }
            if (updates.containsKey("gercekBinis") || updates.containsKey("gercekInis")) {
                updates["gercekYolSuresi"] =
                    TransitRecordCalculations.computeYolSuresi(finalGercekBinis, finalGercekInis)
            }
            if (updates.containsKey("gercekBinis") || updates.containsKey("planlananBinis")) {
                updates["gecikme"] =
                    TransitRecordCalculations.computeGecikme(finalPlanlananBinis, finalGercekBinis)
            }

            updates["updatedAt"] = System.currentTimeMillis()
            docRef.update(updates).await()
            true
        }.getOrElse { e ->
            Log.e(TAG, "updateTrip failed for docId: $docId", e)
            throw e
        }
    }

    suspend fun updateStops(
        tripId: String,
        binisDuragi: String?,
        binisTime: String?,
        inisDuragi: String?,
        inisTime: String?,
        mesafe: String? = null,
        durakSayisi: String? = null,
        distanceResult: SegmentDistanceResult? = null
    ): Boolean {
        val snapshot = collection()
            .whereEqualTo("id", tripId)
            .get()
            .await()
        if (snapshot.isEmpty) return false

        val docRef = snapshot.documents[0].reference
        val existingData = snapshot.documents[0].data ?: return false
        val updates = mutableMapOf<String, Any>()

        if (!binisDuragi.isNullOrBlank()) {
            updates["binisDuragi"] = binisDuragi
            updates[TransitRecordCalculations.FIELD_FROM_STOP_ID] = ""
        }
        if (!binisTime.isNullOrBlank()) updates["planlananBinis"] = binisTime
        if (!inisDuragi.isNullOrBlank()) {
            updates["inisDuragi"] = inisDuragi
            updates[TransitRecordCalculations.FIELD_TO_STOP_ID] = ""
        }
        if (!inisTime.isNullOrBlank()) updates["planlananInis"] = inisTime
        if (mesafe != null) {
            updates["mesafe"] = mesafe
            if (distanceResult != null) {
                updates.putAll(TransitRecordCalculations.calculatedDistanceFields(distanceResult))
            } else {
                val distanceKm = TransitRecordCalculations.parseDistanceKm(mesafe) ?: 0.0
                updates.putAll(
                    TransitRecordCalculations.calculatedDistanceFields(
                        distanceKm,
                        resetRmvDistance = true
                    )
                )
            }
        }
        if (durakSayisi != null) updates["durakSayisi"] = durakSayisi

        if (binisTime != null || inisTime != null) {
            val finalBinis = binisTime ?: existingData["planlananBinis"]?.toString()
            val finalInis = inisTime ?: existingData["planlananInis"]?.toString()
            val yolSuresi = TransitRecordCalculations.computeYolSuresi(finalBinis, finalInis)
            if (yolSuresi.isNotBlank()) updates["planlananYolSuresi"] = yolSuresi
        }

        if (updates.isNotEmpty()) {
            updates["updatedAt"] = System.currentTimeMillis()
            docRef.update(updates).await()
        }
        return true
    }

    suspend fun deleteTrip(docId: String): Boolean {
        return FirestoreHelper.safeFirestore {
            collection().document(docId).delete().await()
            true
        }.getOrElse { e ->
            Log.e(TAG, "deleteTrip failed for docId: $docId", e)
            throw e
        }
    }

    suspend fun bulkUpdate(
        tripId: String,
        mesafe: String,
        durakSayisi: Int,
        distanceResult: SegmentDistanceResult? = null
    ): Boolean {
        val snapshot = collection()
            .whereEqualTo("id", tripId)
            .get()
            .await()
        if (snapshot.isEmpty) return false

        val docRef = snapshot.documents[0].reference
        docRef.update(
            buildMap {
                if (distanceResult != null) {
                    putAll(TransitRecordCalculations.calculatedDistanceFields(distanceResult))
                } else {
                    putAll(
                        TransitRecordCalculations.calculatedDistanceFields(
                            TransitRecordCalculations.parseDistanceKm(mesafe) ?: 0.0,
                            resetRmvDistance = true
                        )
                    )
                }
                put("mesafe", mesafe)
                put("durakSayisi", durakSayisi)
                put("updatedAt", System.currentTimeMillis())
            }
        ).await()
        return true
    }

    suspend fun updateExistingRecord(tripId: String, fields: Map<String, Any>): Boolean {
        return FirestoreHelper.safeFirestore {
            val snapshot = collection()
                .whereEqualTo("id", tripId)
                .get().await()
            if (snapshot.isEmpty) {
                false
            } else {
                val updates = fields.toMutableMap()
                enrichUpdatedDistanceFields(updates)
                updates["updatedAt"] = System.currentTimeMillis()
                snapshot.documents[0].reference.update(updates).await()
                true
            }
        }.getOrElse { e ->
            Log.e(TAG, "updateExistingRecord failed for tripId: $tripId", e)
            throw e
        }
    }

    suspend fun saveTripMap(data: Map<String, Any?>): Boolean {
        return FirestoreHelper.safeFirestore {
            val enriched = data.toMutableMap()
            val tarih = enriched["tarih"]?.toString()
            if (!tarih.isNullOrBlank()) {
                if (!enriched.containsKey("yearMonth")) {
                    enriched["yearMonth"] = TransitRecordCalculations.computeYearMonth(tarih)
                }
                if (!enriched.containsKey("sortDate")) {
                    enriched["sortDate"] = TransitRecordCalculations.computeSortDate(tarih)
                }
            }
            enriched["updatedAt"] = System.currentTimeMillis()
            enrichNewDistanceFields(enriched)
            collection().add(buildOrderedMap(enriched)).await()
            true
        }.getOrElse { e ->
            Log.e(TAG, "saveTripMap failed", e)
            throw e
        }
    }

    suspend fun fetchRowsForBulkUpdate(): List<BulkUpdateRow> {
        val snapshot = collection().get().await()
        val results = mutableListOf<BulkUpdateRow>()
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val mesafe = data["mesafe"]?.toString() ?: ""
            val durakSayisi = data["durakSayisi"]?.toString() ?: ""

            if (mesafe.isBlank() || durakSayisi.isBlank() || durakSayisi == "0") {
                results.add(
                    BulkUpdateRow(
                        rowIndex = 0,
                        sheetName = "",
                        hat = data["hat"]?.toString() ?: "",
                        tur = data["tur"]?.toString() ?: "",
                        yon = data["yon"]?.toString() ?: "",
                        binisDuragi = data["binisDuragi"]?.toString() ?: "",
                        inisDuragi = data["inisDuragi"]?.toString() ?: "",
                        planlananBinis = data["planlananBinis"]?.toString() ?: "",
                        tarih = data["tarih"]?.toString() ?: "",
                        firestoreDocId = doc.id
                    )
                )
            }
        }
        return results
    }

    suspend fun fetchAllRowsForStopNameUpdate(): List<BulkUpdateRow> {
        val snapshot = collection().get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            BulkUpdateRow(
                rowIndex = 0,
                sheetName = "",
                hat = data["hat"]?.toString() ?: "",
                tur = data["tur"]?.toString() ?: "",
                yon = data["yon"]?.toString() ?: "",
                binisDuragi = data["binisDuragi"]?.toString() ?: "",
                inisDuragi = data["inisDuragi"]?.toString() ?: "",
                planlananBinis = data["planlananBinis"]?.toString() ?: "",
                tarih = data["tarih"]?.toString() ?: "",
                firestoreDocId = doc.id
            )
        }
    }

    private fun buildOrderedMap(data: Map<String, Any?>): LinkedHashMap<String, Any?> {
        val ordered = LinkedHashMap<String, Any?>()
        for (field in FIELD_ORDER) {
            if (data.containsKey(field)) {
                ordered[field] = data[field]
            }
        }
        for ((key, value) in data) {
            if (!ordered.containsKey(key)) {
                ordered[key] = value
            }
        }
        return ordered
    }

    private fun enrichNewDistanceFields(data: MutableMap<String, Any?>) {
        val legacyDistanceKm = TransitRecordCalculations.parseDistanceKm(data["mesafe"])
        if (!data.containsKey(TransitRecordCalculations.FIELD_ORS_DISTANCE_KM)) {
            data[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM] = legacyDistanceKm ?: 0.0
        }
        if (!data.containsKey(TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT)) {
            data[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT] =
                legacyDistanceKm?.let { TransitRecordCalculations.formatDistanceKm(it) }.orEmpty()
        }
        for ((key, value) in TransitRecordCalculations.rmvPendingDistanceFields()) {
            if (!data.containsKey(key)) data[key] = value
        }
    }

    private fun enrichUpdatedDistanceFields(updates: MutableMap<String, Any>) {
        if (!updates.containsKey("mesafe")) return
        if (updates.containsKey(TransitRecordCalculations.FIELD_ORS_DISTANCE_KM) &&
            updates.containsKey(TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT)
        ) {
            return
        }
        val legacyDistanceKm = TransitRecordCalculations.parseDistanceKm(updates["mesafe"]) ?: 0.0
        updates.putAll(
            TransitRecordCalculations.calculatedDistanceFields(
                legacyDistanceKm,
                resetRmvDistance = true
            )
        )
    }

    private fun logD(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    private companion object {
        private const val TAG = "FirestoreTripRemoteDataSource"
        private const val ALL_FILTER = "T\u00fcm\u00fc"
        private const val FIELD_JOURNEY_REF = "journeyRef"
        private const val FIELD_FROM_STOP_ID = "fromStopId"
        private const val FIELD_TO_STOP_ID = "toStopId"

        private val MONTH_NUMBERS = mapOf(
            "Ocak" to "01",
            "\u015eubat" to "02",
            "Mart" to "03",
            "Nisan" to "04",
            "May\u0131s" to "05",
            "Haziran" to "06",
            "Temmuz" to "07",
            "A\u011fustos" to "08",
            "Eyl\u00fcl" to "09",
            "Ekim" to "10",
            "Kas\u0131m" to "11",
            "Aral\u0131k" to "12"
        )

        private val FIELD_ORDER = listOf(
            "tarih", "gun", "tur", "hat", "yon", "binisDuragi",
            "planlananBinis", "gercekBinis", "gecikme", "inisDuragi",
            "planlananInis", "gercekInis", "gununTipi", "havaDurumu",
            "oturabildimMi", "planlananYolSuresi", "gercekYolSuresi",
            "not", "biletKontrol\u00fc", "seatmateUuid", "mesafe",
            TransitRecordCalculations.FIELD_ORS_DISTANCE_KM,
            TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT,
            TransitRecordCalculations.FIELD_RMV_DISTANCE_KM,
            TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS,
            TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT,
            TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS,
            TransitRecordCalculations.FIELD_RMV_DISTANCE_UPDATED_AT,
            TransitRecordCalculations.FIELD_RMV_API_VERSION,
            FIELD_JOURNEY_REF, FIELD_FROM_STOP_ID, FIELD_TO_STOP_ID,
            "durakSayisi", "id",
            "yearMonth", "sortDate", "updatedAt"
        )
    }
}
