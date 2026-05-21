package com.example.toplutasima.network

import android.util.Log
import com.example.toplutasima.model.BulkUpdateRow
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.network.firestore.FirestoreFavoriteDataSource
import com.example.toplutasima.network.firestore.FirestoreMigrationService
import com.example.toplutasima.network.firestore.FirestoreTripRemoteDataSource
import com.example.toplutasima.usecase.SummaryCalculator
import com.example.toplutasima.usecase.TransitRecordCalculations
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

object FirestoreService {

    data class MonthSummary(
        val monthName: String,
        val year: String,
        val count: Int,
        val sortKey: String // YYYYMM format for easy sorting
    )

    private val db get() = FirebaseFirestore.getInstance()
    private val tripDataSource by lazy { FirestoreTripRemoteDataSource(collectionName = COLLECTION) }
    private val favoriteDataSource by lazy { FirestoreFavoriteDataSource(favoriteCollectionName = FAV_COLLECTION) }
    private val migrationService by lazy { FirestoreMigrationService(collectionName = COLLECTION) }
    private const val COLLECTION = "trips"
    private const val FAV_COLLECTION = "favorite_stops"

    const val FIELD_ORS_DISTANCE_KM = "orsMesafeKm"
    const val FIELD_ORS_DISTANCE_TEXT = "orsMesafeText"
    const val FIELD_RMV_DISTANCE_KM = "rmvMesafeKm"
    const val FIELD_RMV_DISTANCE_METERS = "rmvMesafeMetre"
    const val FIELD_RMV_DISTANCE_TEXT = "rmvMesafeText"
    const val FIELD_RMV_DISTANCE_STATUS = "rmvMesafeDurumu"
    const val FIELD_RMV_DISTANCE_UPDATED_AT = "rmvMesafeGuncellemeTarihi"
    const val FIELD_RMV_API_VERSION = "rmvApiVersion"
    const val FIELD_JOURNEY_REF = "journeyRef"
    const val FIELD_FROM_STOP_ID = "fromStopId"
    const val FIELD_TO_STOP_ID = "toStopId"

    const val RMV_DISTANCE_PENDING = "bekliyor"
    const val RMV_DISTANCE_READY = "hazir"
    const val RMV_DISTANCE_FAILED = "hata"

    // ── Required field order for Firestore documents ──
    private val FIELD_ORDER = listOf(
        "tarih", "gun", "tur", "hat", "yon", "binisDuragi",
        "planlananBinis", "gercekBinis", "gecikme", "inisDuragi",
        "planlananInis", "gercekInis", "gununTipi", "havaDurumu",
        "oturabildimMi", "planlananYolSuresi", "gercekYolSuresi",
        "not", "biletKontrolü", "seatmateUuid", "mesafe",
        FIELD_ORS_DISTANCE_KM, FIELD_ORS_DISTANCE_TEXT,
        FIELD_RMV_DISTANCE_KM, FIELD_RMV_DISTANCE_METERS,
        FIELD_RMV_DISTANCE_TEXT, FIELD_RMV_DISTANCE_STATUS,
        FIELD_RMV_DISTANCE_UPDATED_AT, FIELD_RMV_API_VERSION,
        FIELD_JOURNEY_REF, FIELD_FROM_STOP_ID, FIELD_TO_STOP_ID,
        "durakSayisi", "id",
        "yearMonth", "sortDate", "updatedAt"
    )

    fun computeYearMonth(tarih: String): String =
        TransitRecordCalculations.computeYearMonth(tarih)

    fun computeSortDate(tarih: String): String =
        TransitRecordCalculations.computeSortDate(tarih)

    fun computeGununTipi(tarih: String): String =
        TransitRecordCalculations.computeGununTipi(tarih)

    fun computeGun(tarih: String): String =
        TransitRecordCalculations.computeGun(tarih)

    fun computeGecikme(planlananBinis: String?, gercekBinis: String?): Int =
        TransitRecordCalculations.computeGecikme(planlananBinis, gercekBinis)

    // ── Helper: build ordered map with correct field order ──
    fun buildOrderedMap(data: Map<String, Any?>): LinkedHashMap<String, Any?> {
        val ordered = LinkedHashMap<String, Any?>()
        for (field in FIELD_ORDER) {
            if (data.containsKey(field)) {
                ordered[field] = data[field]
            }
        }
        // Add any extra fields not in the standard order
        for ((key, value) in data) {
            if (!ordered.containsKey(key)) {
                ordered[key] = value
            }
        }
        return ordered
    }

    fun parseDistanceKm(value: Any?): Double? =
        TransitRecordCalculations.parseDistanceKm(value)

    fun formatDistanceKm(distanceKm: Double): String =
        TransitRecordCalculations.formatDistanceKm(distanceKm)

    fun orsDistanceKm(row: Map<String, *>): Double? =
        TransitRecordCalculations.orsDistanceKm(row)

    fun rmvDistanceKm(row: Map<String, *>): Double? =
        TransitRecordCalculations.rmvDistanceKm(row)

    fun rmvPendingDistanceFields(): LinkedHashMap<String, Any> =
        TransitRecordCalculations.rmvPendingDistanceFields()

    fun calculatedDistanceFields(
        distanceKm: Double,
        resetRmvDistance: Boolean = false
    ): LinkedHashMap<String, Any> =
        TransitRecordCalculations.calculatedDistanceFields(distanceKm, resetRmvDistance)

    private fun enrichNewDistanceFields(data: MutableMap<String, Any?>) {
        val legacyDistanceKm = TransitRecordCalculations.parseDistanceKm(data["mesafe"])
        if (!data.containsKey(FIELD_ORS_DISTANCE_KM)) {
            data[FIELD_ORS_DISTANCE_KM] = legacyDistanceKm ?: 0.0
        }
        if (!data.containsKey(FIELD_ORS_DISTANCE_TEXT)) {
            data[FIELD_ORS_DISTANCE_TEXT] = legacyDistanceKm?.let { TransitRecordCalculations.formatDistanceKm(it) }.orEmpty()
        }
        for ((key, value) in TransitRecordCalculations.rmvPendingDistanceFields()) {
            if (!data.containsKey(key)) data[key] = value
        }
    }

    private fun enrichUpdatedDistanceFields(updates: MutableMap<String, Any>) {
        if (!updates.containsKey("mesafe")) return
        if (updates.containsKey(FIELD_ORS_DISTANCE_KM) && updates.containsKey(FIELD_ORS_DISTANCE_TEXT)) return
        val legacyDistanceKm = TransitRecordCalculations.parseDistanceKm(updates["mesafe"]) ?: 0.0
        updates.putAll(TransitRecordCalculations.calculatedDistanceFields(legacyDistanceKm, resetRmvDistance = true))
    }

    // ── Save a new trip document ──
    suspend fun saveTrip(data: Map<String, Any?>): String =
        tripDataSource.saveTrip(data)

    // ── Update actual departure/arrival by trip ID field ──
    // Returns true if the record was found and updated, false if not found.
    suspend fun updateActual(tripId: String, actualDep: String?, actualArr: String?): Boolean =
        tripDataSource.updateActual(tripId, actualDep, actualArr)

    // ── Fetch a single record by trip ID field ──
    /**
     * Tek bir kaydı "id" alanına göre sorgular ve döküman datasını döner.
     * [RmvLogViewModel.refreshActualTimesFromPrefs] tarafından kullanılır:
     * SharedFlow event'i kaçırılmışsa ViewModel açılışında DB'den güncel
     * gercekBinis/gercekInis değerlerini tazeler.
     *
     * @return Döküman data map'i veya bulunamazsa null
     */
    suspend fun clearActual(tripId: String, clearDep: Boolean, clearArr: Boolean): Boolean =
        tripDataSource.clearActual(tripId, clearDep, clearArr)

    suspend fun fetchRecord(tripId: String): Map<String, Any>? =
        tripDataSource.fetchRecord(tripId)

    // ── Fetch all trips, sorted by sortDate descending ──
    // sortDate ("YYYY-MM-DD") alfabetik = kronolojik; orderBy("tarih") string
    // sıralamasının aksine ay/yıl geçişlerinde doğru çalışır.
    // Eski kayıtlar (sortDate eksik) için tarih alanından türetilir.
    suspend fun fetchTrips(): List<Map<String, Any>> =
        tripDataSource.fetchTrips()

    suspend fun fetchTripsAfter(sortDate: String): List<Map<String, Any>> {
        val snapshot = db.collection(COLLECTION)
            .whereGreaterThanOrEqualTo("sortDate", sortDate)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data + ("firestoreDocId" to doc.id)
        }
    }

    suspend fun fetchTripsUpdatedAfter(updatedAt: Long): List<Map<String, Any>> {
        val snapshot = db.collection(COLLECTION)
            .whereGreaterThan("updatedAt", updatedAt)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data + ("firestoreDocId" to doc.id)
        }
    }

    // ── Compute summary from trips, optionally filtered by "MonthName Year" ──
    fun computeSummary(
        allDocs: List<Map<String, Any>>,
        sheetName: String = "T\u00fcm\u00fc"
    ): Pair<SummaryData, List<String>> =
        SummaryCalculator.computeSummary(allDocs, sheetName)

    // ── Update stops by trip ID field ──
    suspend fun updateStops(
        tripId: String,
        binisDuragi: String?, binisTime: String?,
        inisDuragi: String?, inisTime: String?,
        mesafe: String? = null, durakSayisi: String? = null
    ): Boolean {
        val snapshot = db.collection(COLLECTION)
            .whereEqualTo("id", tripId)
            .get().await()
        if (snapshot.isEmpty) return false
        val docRef = snapshot.documents[0].reference
        val existingData = snapshot.documents[0].data ?: return false
        val updates = mutableMapOf<String, Any>()
        if (!binisDuragi.isNullOrBlank()) {
            updates["binisDuragi"] = binisDuragi
            updates[FIELD_FROM_STOP_ID] = ""
        }
        if (!binisTime.isNullOrBlank()) updates["planlananBinis"] = binisTime
        if (!inisDuragi.isNullOrBlank()) {
            updates["inisDuragi"] = inisDuragi
            updates[FIELD_TO_STOP_ID] = ""
        }
        if (!inisTime.isNullOrBlank()) updates["planlananInis"] = inisTime
        if (mesafe != null) {
            updates["mesafe"] = mesafe
            val distanceKm = TransitRecordCalculations.parseDistanceKm(mesafe) ?: 0.0
            updates.putAll(TransitRecordCalculations.calculatedDistanceFields(distanceKm, resetRmvDistance = true))
        }
        if (durakSayisi != null) updates["durakSayisi"] = durakSayisi

        // Recompute planlananYolSuresi if times change
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

    // ── Bulk update mesafe & durakSayisi ──
    suspend fun bulkUpdate(tripId: String, mesafe: String, durakSayisi: Int): Boolean {
        val snapshot = db.collection(COLLECTION)
            .whereEqualTo("id", tripId)
            .get().await()
        if (snapshot.isEmpty) return false
        val docRef = snapshot.documents[0].reference
        docRef.update(
            buildMap {
                putAll(TransitRecordCalculations.calculatedDistanceFields(TransitRecordCalculations.parseDistanceKm(mesafe) ?: 0.0, resetRmvDistance = true))
                putAll(mapOf(
                "mesafe" to mesafe,
                "durakSayisi" to durakSayisi,
                "updatedAt" to System.currentTimeMillis()
                ))
            }
        ).await()
        return true
    }

    // ── Save trip (used by migration: accepts a full row map) ──
    suspend fun saveTripMap(data: Map<String, Any?>): Boolean {
        return try {
            val enriched = data.toMutableMap()
            val tarih = enriched["tarih"]?.toString()
            if (!tarih.isNullOrBlank()) {
                if (!enriched.containsKey("yearMonth")) enriched["yearMonth"] = TransitRecordCalculations.computeYearMonth(tarih)
                if (!enriched.containsKey("sortDate"))  enriched["sortDate"]  = TransitRecordCalculations.computeSortDate(tarih)
            }
            enriched["updatedAt"] = System.currentTimeMillis()
            enrichNewDistanceFields(enriched)
            val ordered = buildOrderedMap(enriched)
            db.collection(COLLECTION).add(ordered).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreService", "saveTripMap failed", e)
            throw e
        }
    }

    // ── Fetch trips with optional "MonthName Year" / type filter ──
    // sortDate ("YYYY-MM-DD") ile client-side sıralama yapılır; eski kayıtlar
    // için tarih alanından otomatik türetilir.
    suspend fun fetchTripsFiltered(
        month: String = "T\u00fcm\u00fc",
        type: String = "T\u00fcm\u00fc"
    ): List<Map<String, Any>> =
        tripDataSource.fetchTripsFiltered(month, type)

    // ── Update trip by Firestore document ID ──
    suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean =
        tripDataSource.updateTrip(docId, fields)

    // ── Delete trip by Firestore document ID ──
    suspend fun deleteTrip(docId: String): Boolean =
        tripDataSource.deleteTrip(docId)

    // ── One-time migration: strip seconds from all time fields ──
    suspend fun migrateStripSeconds(): Int =
        migrationService.migrateStripSeconds()

    // ── Update existing record by trip ID field (for post-save field changes) ──
    suspend fun updateExistingRecord(tripId: String, fields: Map<String, Any>): Boolean =
        tripDataSource.updateExistingRecord(tripId, fields)

    // ── One-time migration: compute Yol Suresi for all trips ──
    suspend fun migrateYolSuresi(): Pair<Int, Int> =
        migrationService.migrateYolSuresi()

    // ── Fetch rows for bulk update ──
    suspend fun fetchRowsForBulkUpdate(): List<BulkUpdateRow> {
        val snapshot = db.collection(COLLECTION).get().await()
        val results = mutableListOf<BulkUpdateRow>()
        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val docId = doc.id
            val mesafe = data["mesafe"]?.toString() ?: ""
            val durakSayisi = data["durakSayisi"]?.toString() ?: ""
            
            if (mesafe.isBlank() || durakSayisi.isBlank() || durakSayisi == "0") {
                val hat = data["hat"]?.toString() ?: ""
                val tur = data["tur"]?.toString() ?: ""
                val yon = data["yon"]?.toString() ?: ""
                val binisDuragi = data["binisDuragi"]?.toString() ?: ""
                val inisDuragi = data["inisDuragi"]?.toString() ?: ""
                val planlananBinis = data["planlananBinis"]?.toString() ?: ""
                val tarih = data["tarih"]?.toString() ?: ""

                results.add(
                    BulkUpdateRow(
                        rowIndex = 0,
                        sheetName = "", // not used for Firebase
                        hat = hat,
                        tur = tur,
                        yon = yon,
                        binisDuragi = binisDuragi,
                        inisDuragi = inisDuragi,
                        planlananBinis = planlananBinis,
                        tarih = tarih,
                        firestoreDocId = docId
                    )
                )
            }
        }
        return results
    }

    // ── Fetch all rows for Stop Name update ──
    suspend fun fetchAllRowsForStopNameUpdate(): List<BulkUpdateRow> {
        val snapshot = db.collection(COLLECTION).get().await()
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

    // ── One-time migration: yearMonth alanını tüm eski kayıtlara ekle ──
    // Sayfalı (paginated) ve toplu (WriteBatch) okuma/yazma kullanılarak OOM riski önlenir.
    suspend fun migrateYearMonth(): Pair<Int, Int> =
        migrationService.migrateYearMonth()

    // ── One-time migration: sortDate alanını tüm eski kayıtlara ekle ──
    // "YYYY-MM-DD" formatında sortDate, orderBy için kronolojik sıralamayı garanti eder.
    // Migration sonrasında fetchTrips/fetchTripsFiltered doğru sırada çalışır.
    suspend fun migrateSortDate(): Pair<Int, Int> =
        migrationService.migrateSortDate()

    suspend fun migrateDistanceFields(): Pair<Int, Int> =
        migrationService.migrateDistanceFields()

    suspend fun migrateSeatmateUuid(): Pair<Int, Int> =
        migrationService.migrateSeatmateUuid()

    // ── Favorite Stops Firebase Backup ──────────────────────────────────────
    suspend fun saveFavorite(fav: com.example.toplutasima.model.FavoriteStop) =
        favoriteDataSource.saveFavorite(fav)

    suspend fun deleteFavorite(favId: String) =
        favoriteDataSource.deleteFavorite(favId)

    suspend fun fetchAllFavorites(): List<com.example.toplutasima.model.FavoriteStop> =
        favoriteDataSource.fetchAllFavorites()
}
