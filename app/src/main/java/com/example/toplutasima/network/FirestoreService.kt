package com.example.toplutasima.network

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.BulkUpdateRow
import com.example.toplutasima.model.DelayBucketStats
import com.example.toplutasima.model.LineReliabilityStats
import com.example.toplutasima.model.RoutePairStats
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.TimeSlotStats
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object FirestoreService {

    // ── Debug-only logging helpers ─────────────────────────────────────────
    private fun logD(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.d(tag, msg) }
    private fun logW(tag: String, msg: String) { if (BuildConfig.DEBUG) Log.w(tag, msg) }

    data class MonthSummary(
        val monthName: String,
        val year: String,
        val count: Int,
        val sortKey: String // YYYYMM format for easy sorting
    )

    private val db get() = FirebaseFirestore.getInstance()
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

    // ── Turkish month-number → name mapping ──
    private val MONTH_NAMES = mapOf(
        "01" to "Ocak", "02" to "Şubat", "03" to "Mart", "04" to "Nisan",
        "05" to "Mayıs", "06" to "Haziran", "07" to "Temmuz", "08" to "Ağustos",
        "09" to "Eylül", "10" to "Ekim", "11" to "Kasım", "12" to "Aralık"
    )

    // Reverse: Turkish month name → month number string
    private val MONTH_NUMBERS = MONTH_NAMES.entries.associate { (k, v) -> v to k }

    // ── Turkish day names ──
    private val DAY_NAMES = mapOf(
        java.time.DayOfWeek.MONDAY to "Pazartesi",
        java.time.DayOfWeek.TUESDAY to "Salı",
        java.time.DayOfWeek.WEDNESDAY to "Çarşamba",
        java.time.DayOfWeek.THURSDAY to "Perşembe",
        java.time.DayOfWeek.FRIDAY to "Cuma",
        java.time.DayOfWeek.SATURDAY to "Cumartesi",
        java.time.DayOfWeek.SUNDAY to "Pazar"
    )

    // ── Required field order for Firestore documents ──
    private val FIELD_ORDER = listOf(
        "tarih", "gun", "tur", "hat", "yon", "binisDuragi",
        "planlananBinis", "gercekBinis", "gecikme", "inisDuragi",
        "planlananInis", "gercekInis", "gununTipi", "havaDurumu",
        "oturabildimMi", "planlananYolSuresi", "gercekYolSuresi",
        "not", "biletKontrolü", "mesafe",
        FIELD_ORS_DISTANCE_KM, FIELD_ORS_DISTANCE_TEXT,
        FIELD_RMV_DISTANCE_KM, FIELD_RMV_DISTANCE_METERS,
        FIELD_RMV_DISTANCE_TEXT, FIELD_RMV_DISTANCE_STATUS,
        FIELD_RMV_DISTANCE_UPDATED_AT, FIELD_RMV_API_VERSION,
        FIELD_JOURNEY_REF, FIELD_FROM_STOP_ID, FIELD_TO_STOP_ID,
        "durakSayisi", "id",
        "yearMonth", "sortDate", "updatedAt"
    )

    // ── Helper: compute yearMonth ("YYYY-MM") from "DD.MM.YYYY" ──
    // Bu alan yeni kayıtlara eklenir ve sunucu taraflı ay filtresi sağlar.
    fun computeYearMonth(tarih: String): String {
        val parts = tarih.split(".")
        if (parts.size < 3) return ""
        val year = parts[2].padStart(4, '0')
        val month = parts[1].padStart(2, '0')
        return "$year-$month"
    }

    // ── Helper: compute sortDate ("YYYY-MM-DD") from "DD.MM.YYYY" ──
    // "YYYY-MM-DD" formatında alfabetik sıralama = kronolojik sıralama.
    // orderBy("tarih") yerine orderBy("sortDate") kullanmayı sağlar.
    fun computeSortDate(tarih: String): String {
        val parts = tarih.split(".")
        if (parts.size < 3) return ""
        val day   = parts[0].padStart(2, '0')
        val month = parts[1].padStart(2, '0')
        val year  = parts[2].padStart(4, '0')
        return "$year-$month-$day"
    }

    // ── Helper: compute gununTipi from "DD.MM.YYYY" ──
    fun computeGununTipi(tarih: String): String {
        return try {
            val parts = tarih.split(".")
            if (parts.size < 3) return "Hafta İçi"
            val date = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            val dow = date.dayOfWeek
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY)
                "Hafta Sonu" else "Hafta İçi"
        } catch (e: Exception) {
            Log.e("FirestoreService", "computeGununTipi ayrıştırma hatası: $tarih", e)
            "Hafta İçi"
        }
    }

    // ── Helper: compute Turkish day name from "DD.MM.YYYY" ──
    fun computeGun(tarih: String): String {
        return try {
            val parts = tarih.split(".")
            if (parts.size < 3) return ""
            val date = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            DAY_NAMES[date.dayOfWeek] ?: ""
        } catch (e: Exception) {
            Log.e("FirestoreService", "computeGun ayrıştırma hatası: $tarih", e)
            ""
        }
    }

    // ── Helper: compute gecikme (delay in minutes) ──
    fun computeGecikme(planlananBinis: String?, gercekBinis: String?): Int {
        if (planlananBinis.isNullOrBlank() || gercekBinis.isNullOrBlank()) return 0
        return try {
            fun toMinutes(time: String): Int {
                val p = time.trim().split(":")
                if (p.size < 2) return 0
                return (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toIntOrNull() ?: 0)
            }
            var diff = toMinutes(gercekBinis) - toMinutes(planlananBinis)
            // Handle midnight crossing (e.g. planned 23:50, actual 00:05 → 15 min delay)
            if (diff < 0) diff += 24 * 60
            // Cap at 120 min to avoid false positives from data errors
            if (diff > 120) 0 else diff
        } catch (e: Exception) {
            Log.e("FirestoreService", "computeGecikme ayrıştırma hatası planlanan: $planlananBinis, gercek: $gercekBinis", e)
            0
        }
    }

    // ── Helper: compute yol suresi (duration in minutes) ──
    fun computeYolSuresi(binis: String?, inis: String?): String {
        if (binis.isNullOrBlank() || inis.isNullOrBlank()) return ""
        return try {
            fun toMinutes(time: String): Int {
                val p = time.trim().split(":")
                if (p.size < 2) return 0
                return (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toIntOrNull() ?: 0)
            }
            var diff = toMinutes(inis) - toMinutes(binis)
            if (diff < 0) diff += 24 * 60
            diff.toString()
        } catch (e: Exception) {
            Log.e("FirestoreService", "computeYolSuresi ayrıştırma hatası binis: $binis, inis: $inis", e)
            ""
        }
    }

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

    fun parseDistanceKm(value: Any?): Double? {
        val raw = value?.toString()?.trim().orEmpty()
        if (raw.isBlank()) return null
        val normalized = raw
            .replace(Regex("[^0-9,.-]"), "")
            .replace(',', '.')
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    fun formatDistanceKm(distanceKm: Double): String =
        if (distanceKm > 0.0) String.format(Locale.US, "%.2f km", distanceKm) else ""

    fun orsDistanceKm(row: Map<String, *>): Double? =
        parseDistanceKm(row[FIELD_ORS_DISTANCE_KM]) ?: parseDistanceKm(row["mesafe"])

    fun rmvDistanceKm(row: Map<String, *>): Double? =
        parseDistanceKm(row[FIELD_RMV_DISTANCE_KM])

    fun rmvPendingDistanceFields(): LinkedHashMap<String, Any> = linkedMapOf(
        FIELD_RMV_DISTANCE_KM to 0.0,
        FIELD_RMV_DISTANCE_METERS to 0,
        FIELD_RMV_DISTANCE_TEXT to "",
        FIELD_RMV_DISTANCE_STATUS to RMV_DISTANCE_PENDING,
        FIELD_RMV_DISTANCE_UPDATED_AT to "",
        FIELD_RMV_API_VERSION to ""
    )

    fun calculatedDistanceFields(
        distanceKm: Double,
        resetRmvDistance: Boolean = false
    ): LinkedHashMap<String, Any> {
        val fields = linkedMapOf<String, Any>(
            FIELD_ORS_DISTANCE_KM to if (distanceKm > 0.0) distanceKm else 0.0,
            FIELD_ORS_DISTANCE_TEXT to formatDistanceKm(distanceKm)
        )
        if (resetRmvDistance) fields.putAll(rmvPendingDistanceFields())
        return fields
    }

    private fun enrichNewDistanceFields(data: MutableMap<String, Any?>) {
        val legacyDistanceKm = parseDistanceKm(data["mesafe"])
        if (!data.containsKey(FIELD_ORS_DISTANCE_KM)) {
            data[FIELD_ORS_DISTANCE_KM] = legacyDistanceKm ?: 0.0
        }
        if (!data.containsKey(FIELD_ORS_DISTANCE_TEXT)) {
            data[FIELD_ORS_DISTANCE_TEXT] = legacyDistanceKm?.let { formatDistanceKm(it) }.orEmpty()
        }
        for ((key, value) in rmvPendingDistanceFields()) {
            if (!data.containsKey(key)) data[key] = value
        }
    }

    private fun enrichUpdatedDistanceFields(updates: MutableMap<String, Any>) {
        if (!updates.containsKey("mesafe")) return
        if (updates.containsKey(FIELD_ORS_DISTANCE_KM) && updates.containsKey(FIELD_ORS_DISTANCE_TEXT)) return
        val legacyDistanceKm = parseDistanceKm(updates["mesafe"]) ?: 0.0
        updates.putAll(calculatedDistanceFields(legacyDistanceKm, resetRmvDistance = true))
    }

    // ── Save a new trip document ──
    suspend fun saveTrip(data: Map<String, Any?>): String {
        val enriched = data.toMutableMap()
        val firestoreDocId = enriched["firestoreDocId"]?.toString()?.takeIf { it.isNotBlank() }
        val tarih = enriched["tarih"]?.toString()
        if (!tarih.isNullOrBlank()) {
            if (!enriched.containsKey("yearMonth")) enriched["yearMonth"] = computeYearMonth(tarih)
            if (!enriched.containsKey("sortDate"))  enriched["sortDate"]  = computeSortDate(tarih)
        }
        enriched["updatedAt"] = System.currentTimeMillis()
        enrichNewDistanceFields(enriched)
        val ordered = buildOrderedMap(enriched)
        return if (firestoreDocId != null) {
            db.collection(COLLECTION).document(firestoreDocId).set(ordered).await()
            firestoreDocId
        } else {
            val doc = db.collection(COLLECTION).add(ordered).await()
            doc.id
        }
    }

    // ── Update actual departure/arrival by trip ID field ──
    // Returns true if the record was found and updated, false if not found.
    suspend fun updateActual(tripId: String, actualDep: String?, actualArr: String?): Boolean {
        val snapshot = db.collection(COLLECTION)
            .whereEqualTo("id", tripId)
            .get().await()
        if (snapshot.isEmpty) return false
        val docRef = snapshot.documents[0].reference
        val existingData = snapshot.documents[0].data ?: return false
        val updates = mutableMapOf<String, Any>()
        if (!actualDep.isNullOrBlank()) updates["gercekBinis"] = actualDep
        if (!actualArr.isNullOrBlank()) updates["gercekInis"] = actualArr

        // Recompute gecikme when gercekBinis is updated
        if (!actualDep.isNullOrBlank()) {
            val planlananBinis = existingData["planlananBinis"]?.toString()
            updates["gecikme"] = computeGecikme(planlananBinis, actualDep)
        }

        val finalGercekBinis = actualDep ?: existingData["gercekBinis"]?.toString()
        val finalGercekInis = actualArr ?: existingData["gercekInis"]?.toString()
        if (!finalGercekBinis.isNullOrBlank() && !finalGercekInis.isNullOrBlank()) {
            updates["gercekYolSuresi"] = computeYolSuresi(finalGercekBinis, finalGercekInis)
        }

        if (updates.isNotEmpty()) {
            updates["updatedAt"] = System.currentTimeMillis()
            docRef.update(updates).await()
        }
        return true
    }

    // ── Fetch a single record by trip ID field ──
    /**
     * Tek bir kaydı "id" alanına göre sorgular ve döküman datasını döner.
     * [RmvLogViewModel.refreshActualTimesFromPrefs] tarafından kullanılır:
     * SharedFlow event'i kaçırılmışsa ViewModel açılışında DB'den güncel
     * gercekBinis/gercekInis değerlerini tazeler.
     *
     * @return Döküman data map'i veya bulunamazsa null
     */
    suspend fun clearActual(tripId: String, clearDep: Boolean, clearArr: Boolean): Boolean {
        val snapshot = db.collection(COLLECTION)
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
        updates["gercekYolSuresi"] = if (!finalGercekBinis.isNullOrBlank() && !finalGercekInis.isNullOrBlank()) {
            computeYolSuresi(finalGercekBinis, finalGercekInis)
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
        return try {
            val snapshot = db.collection(COLLECTION)
                .whereEqualTo("id", tripId)
                .limit(1)
                .get().await()
            if (snapshot.isEmpty) null
            else snapshot.documents[0].data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreService", "fetchRecord failed for tripId: $tripId", e)
            throw e
        }
    }

    // ── Fetch all trips, sorted by sortDate descending ──
    // sortDate ("YYYY-MM-DD") alfabetik = kronolojik; orderBy("tarih") string
    // sıralamasının aksine ay/yıl geçişlerinde doğru çalışır.
    // Eski kayıtlar (sortDate eksik) için tarih alanından türetilir.
    suspend fun fetchTrips(): List<Map<String, Any>> {
        val snapshot = db.collection(COLLECTION).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data + ("firestoreDocId" to doc.id)
        }.sortedByDescending { doc ->
            doc["sortDate"]?.toString().takeIf { !it.isNullOrBlank() }
                ?: computeSortDate(doc["tarih"]?.toString() ?: "")
        }
    }

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
    fun computeSummary(allDocs: List<Map<String, Any>>, sheetName: String = "Tümü"): Pair<SummaryData, List<String>> {

        // Extract month+year pairs for the dropdown
        val monthYearSet = mutableSetOf<Pair<Int, Int>>()
        for (row in allDocs) {
            val tarih = row["tarih"]?.toString() ?: continue
            val parts = tarih.split(".")
            if (parts.size >= 3) {
                val monthNum = parts[1].toIntOrNull() ?: continue
                val year = parts[2].toIntOrNull() ?: continue
                monthYearSet.add(year to monthNum)
            }
        }
        // Sort chronologically: oldest first
        val sortedMonths = monthYearSet
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { (year, month) ->
                val monthName = MONTH_NAMES[String.format(Locale.US, "%02d", month)] ?: month.toString()
                "$monthName $year"
            }

        // Filter rows by "MonthName Year" if a specific month is selected
        val rows = if (sheetName == "Tümü") {
            allDocs
        } else {
            // Parse "MonthName Year" → monthNum + year
            val filterParts = sheetName.split(" ")
            val filterMonthNum = if (filterParts.size >= 2) MONTH_NUMBERS[filterParts[0]] else null
            val filterYear = if (filterParts.size >= 2) filterParts[1] else null
            if (filterMonthNum != null && filterYear != null) {
                allDocs.filter { row ->
                    val tarih = row["tarih"]?.toString() ?: return@filter false
                    val parts = tarih.split(".")
                    parts.size >= 3 && parts[1] == filterMonthNum && parts[2] == filterYear
                }
            } else {
                allDocs
            }
        }

        var totalTrips = 0
        var seatedCount = 0
        var ticketControlCount = 0
        val types = mutableMapOf(
            "Otobüs" to 0, "U-Bahn" to 0, "S-Bahn" to 0,
            "Re/Rb" to 0, "Fernzug" to 0, "Straßenbahn" to 0
        )
        val lines = mutableMapOf<String, Int>()
        val fromStops = mutableMapOf<String, Int>()
        val toStops = mutableMapOf<String, Int>()
        val days = mutableMapOf(
            "Pazartesi" to 0, "Salı" to 0, "Çarşamba" to 0,
            "Perşembe" to 0, "Cuma" to 0, "Cumartesi" to 0, "Pazar" to 0
        )
        var totalPlanned = 0.0
        var totalActual = 0.0
        var maxDelay = 0.0
        var totalDelay = 0.0
        var delayCount = 0
        val weatherCounts = mutableMapOf<String, Int>()
        var totalDistanceKm = 0.0
        var totalOrsDistanceKm = 0.0
        var totalRmvDistanceKm = 0.0
        val typeOnTime = mutableMapOf<String, Pair<Int, Int>>() // total, onTime
        val dailyTrips = mutableMapOf<String, Int>()
        val dailyDuration = mutableMapOf<String, Double>()
        val lineMaxDelays = mutableMapOf<String, Double>()
        val lineTotalDelays = mutableMapOf<String, Double>()
        val timeSlotTotals = mutableMapOf<String, Triple<Int, Double, Int>>() // trips, delay, onTime
        val delayBuckets = linkedMapOf("zero" to 0, "low" to 0, "medium" to 0, "high" to 0)
        data class LineAgg(var trips: Int = 0, var delay: Double = 0.0, var onTime: Int = 0, var maxDelay: Int = 0)
        data class RouteAgg(
            var trips: Int = 0,
            var durationSum: Double = 0.0,
            var durationCount: Int = 0,
            var delay: Double = 0.0,
            var fastest: Int = Int.MAX_VALUE,
            var slowest: Int = 0
        )
        val lineReliabilityAgg = mutableMapOf<String, LineAgg>()
        val routeAgg = mutableMapOf<Pair<String, String>, RouteAgg>()
        var shortestTrip: Pair<String, Int>? = null
        var longestTrip: Pair<String, Int>? = null
        var longestDistanceTrip: Pair<String, Double>? = null

        fun timeToMinutes(time: String): Int? {
            val parts = time.trim().split(":")
            if (parts.size < 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }

        fun timeSlotKey(time: String): String {
            val hour = (timeToMinutes(time) ?: return "unknown") / 60
            return when (hour) {
                in 5..10 -> "morning"
                in 11..15 -> "noon"
                in 16..20 -> "evening"
                else -> "night"
            }
        }

        fun routeTitle(line: String, from: String, to: String): String =
            listOf(line.ifBlank { "-" }, "$from → $to").joinToString(" • ")

        for (row in rows) {
            val tarih = row["tarih"]?.toString() ?: continue
            if (tarih.isBlank() || tarih.lowercase() == "tarih") continue

            totalTrips++

            val oturabildim = row["oturabildimMi"]?.toString() ?: ""
            if (oturabildim == SeatingStatus.YES.key) seatedCount++

            val biletKontrolu = row["biletKontrolü"]?.toString() ?: ""
            if (biletKontrolu == TicketStatus.HAPPENED.key) ticketControlCount++

            val type = row["tur"]?.toString() ?: ""
            val line = row["hat"]?.toString() ?: ""
            val from = row["binisDuragi"]?.toString() ?: ""
            val to = row["inisDuragi"]?.toString() ?: ""
            val day = row["gun"]?.toString() ?: ""
            val plannedDep = row["planlananBinis"]?.toString() ?: ""

            if (types.containsKey(type)) types[type] = types[type]!! + 1
            if (line.isNotBlank()) lines[line] = (lines[line] ?: 0) + 1
            if (from.isNotBlank()) fromStops[from] = (fromStops[from] ?: 0) + 1
            if (to.isNotBlank()) toStops[to] = (toStops[to] ?: 0) + 1
            if (day.isNotBlank() && days.containsKey(day)) days[day] = days[day]!! + 1

            val weather = row["havaDurumu"]?.toString() ?: ""
            if (weather.isNotBlank()) weatherCounts[weather] = (weatherCounts[weather] ?: 0) + 1

            val distanceKm = orsDistanceKm(row)
            val rmvKm = rmvDistanceKm(row)
            if (distanceKm != null) {
                totalDistanceKm += distanceKm
                totalOrsDistanceKm += distanceKm
            }
            if (rmvKm != null) totalRmvDistanceKm += rmvKm

            val plannedMin = row["planlananYolSuresi"]?.toString()?.toDoubleOrNull()
            val actualMin = row["gercekYolSuresi"]?.toString()?.toDoubleOrNull()
            val bestDuration = actualMin ?: plannedMin

            if (plannedMin != null) totalPlanned += plannedMin
            if (actualMin != null) {
                totalActual += actualMin
                dailyDuration[tarih] = (dailyDuration[tarih] ?: 0.0) + actualMin
            }

            val routeDisplay = if (from.isNotBlank() && to.isNotBlank()) routeTitle(line, from, to) else line.ifBlank { "-" }
            if (bestDuration != null && bestDuration > 0) {
                val durationInt = Math.round(bestDuration).toInt()
                if (shortestTrip == null || durationInt < shortestTrip!!.second) shortestTrip = routeDisplay to durationInt
                if (longestTrip == null || durationInt > longestTrip!!.second) longestTrip = routeDisplay to durationInt
            }
            if (distanceKm != null && distanceKm > 0.0) {
                if (longestDistanceTrip == null || distanceKm > longestDistanceTrip!!.second) {
                    longestDistanceTrip = routeDisplay to distanceKm
                }
            }

            dailyTrips[tarih] = (dailyTrips[tarih] ?: 0) + 1

            // Delay: use stored gecikme field (gercekBinis - planlananBinis in minutes)
            val gecikme = row["gecikme"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            val onTime = gecikme <= 3
            when {
                gecikme <= 0 -> delayBuckets["zero"] = (delayBuckets["zero"] ?: 0) + 1
                gecikme <= 5 -> delayBuckets["low"] = (delayBuckets["low"] ?: 0) + 1
                gecikme <= 10 -> delayBuckets["medium"] = (delayBuckets["medium"] ?: 0) + 1
                else -> delayBuckets["high"] = (delayBuckets["high"] ?: 0) + 1
            }

            val prevType = typeOnTime[type] ?: Pair(0, 0)
            typeOnTime[type] = Pair(prevType.first + 1, prevType.second + if (onTime) 1 else 0)

            val slotKey = timeSlotKey(plannedDep)
            if (slotKey != "unknown") {
                val prev = timeSlotTotals[slotKey] ?: Triple(0, 0.0, 0)
                timeSlotTotals[slotKey] = Triple(
                    prev.first + 1,
                    prev.second + gecikme.toDouble(),
                    prev.third + if (onTime) 1 else 0
                )
            }

            if (line.isNotBlank()) {
                val agg = lineReliabilityAgg.getOrPut(line) { LineAgg() }
                agg.trips++
                agg.delay += gecikme.toDouble()
                if (onTime) agg.onTime++
                if (gecikme > agg.maxDelay) agg.maxDelay = gecikme
            }

            if (from.isNotBlank() && to.isNotBlank()) {
                val agg = routeAgg.getOrPut(from to to) { RouteAgg() }
                agg.trips++
                agg.delay += gecikme.toDouble()
                if (bestDuration != null && bestDuration > 0) {
                    val durationInt = Math.round(bestDuration).toInt()
                    agg.durationSum += bestDuration
                    agg.durationCount++
                    if (durationInt < agg.fastest) agg.fastest = durationInt
                    if (durationInt > agg.slowest) agg.slowest = durationInt
                }
            }

            if (gecikme > 0) {
                totalDelay += gecikme.toDouble()
                delayCount++
                if (gecikme.toDouble() > maxDelay) maxDelay = gecikme.toDouble()

                if (line.isNotBlank()) {
                    if (!lineMaxDelays.containsKey(line) || gecikme.toDouble() > lineMaxDelays[line]!!) {
                        lineMaxDelays[line] = gecikme.toDouble()
                    }
                lineTotalDelays[line] = (lineTotalDelays[line] ?: 0.0) + gecikme.toDouble()
                }
            }
        }

        fun getTop(map: Map<String, Int>): String {
            return map.maxByOrNull { it.value }?.key ?: "-"
        }

        val recordLongestDay = dailyDuration.maxByOrNull { it.value }
        val recordMostTripsDay = dailyTrips.maxByOrNull { it.value }
        val recordMostDelayedLine = lineMaxDelays.maxByOrNull { it.value }
        val recordTotalDelayLine = lineTotalDelays.maxByOrNull { it.value }

        val punctualityRates = mutableMapOf<String, Int>()
        for ((t, stats) in typeOnTime) {
            punctualityRates[t] = if (stats.first > 0) Math.round(stats.second.toDouble() / stats.first * 100).toInt() else 0
        }
        // Ensure all types present
        for (t in types.keys) {
            if (!punctualityRates.containsKey(t)) punctualityRates[t] = 0
        }

        val avgDelay = if (totalTrips > 0) totalDelay / totalTrips else 0.0

        // Top 7 most-used lines, sorted by count descending
        val topLines: Map<String, Int> = lines.entries
            .sortedByDescending { it.value }
            .take(7)
            .associate { it.key to it.value }
            .let { LinkedHashMap(it) }

        val timeSlotStats = listOf("morning", "noon", "evening", "night").mapNotNull { key ->
            val stats = timeSlotTotals[key] ?: return@mapNotNull null
            TimeSlotStats(
                key = key,
                trips = stats.first,
                avgDelay = if (stats.first > 0) stats.second / stats.first else 0.0,
                punctualityRate = if (stats.first > 0) Math.round(stats.third.toDouble() / stats.first * 100).toInt() else 0
            )
        }

        val lineReliability = lineReliabilityAgg.entries
            .sortedWith(compareByDescending<Map.Entry<String, LineAgg>> { it.value.trips }.thenBy { it.key })
            .take(7)
            .map { (line, stats) ->
                LineReliabilityStats(
                    line = line,
                    trips = stats.trips,
                    avgDelay = if (stats.trips > 0) stats.delay / stats.trips else 0.0,
                    punctualityRate = if (stats.trips > 0) Math.round(stats.onTime.toDouble() / stats.trips * 100).toInt() else 0,
                    maxDelay = stats.maxDelay
                )
            }

        val routePairs = routeAgg.entries
            .sortedWith(compareByDescending<Map.Entry<Pair<String, String>, RouteAgg>> { it.value.trips }.thenBy { it.key.first })
            .take(7)
            .map { (route, stats) ->
                RoutePairStats(
                    fromStop = route.first,
                    toStop = route.second,
                    trips = stats.trips,
                    avgDurationMin = if (stats.durationCount > 0) Math.round(stats.durationSum / stats.durationCount).toInt() else 0,
                    avgDelay = if (stats.trips > 0) stats.delay / stats.trips else 0.0,
                    fastestMin = if (stats.fastest == Int.MAX_VALUE) 0 else stats.fastest,
                    slowestMin = stats.slowest
                )
            }

        val delayDistribution = delayBuckets.map { (key, count) -> DelayBucketStats(key, count) }

        val summaryData = SummaryData(
            totalTrips = totalTrips,
            seatedCount = seatedCount,
            ticketControlCount = ticketControlCount,
            types = types,
            freqLine = getTop(lines),
            freqFrom = getTop(fromStops),
            freqTo = getTop(toStops),
            days = days,
            totalPlannedMin = Math.round(totalPlanned).toInt(),
            totalActualMin = Math.round(totalActual).toInt(),
            maxDelay = Math.round(maxDelay).toInt(),
            totalDelay = Math.round(totalDelay).toInt(),
            avgDelay = avgDelay,
            punctualityRates = punctualityRates,
            recordLongestDay = recordLongestDay?.key ?: "-",
            recordLongestDayMin = Math.round(recordLongestDay?.value ?: 0.0).toInt(),
            recordMostTripsDay = recordMostTripsDay?.key ?: "-",
            recordMostTripsCount = recordMostTripsDay?.value ?: 0,
            recordMostDelayedLine = recordMostDelayedLine?.key ?: "-",
            recordMostDelayedLineMin = Math.round(recordMostDelayedLine?.value ?: 0.0).toInt(),
            recordTotalDelayLine = recordTotalDelayLine?.key ?: "-",
            recordTotalDelayLineMin = Math.round(recordTotalDelayLine?.value ?: 0.0).toInt(),
            weatherCounts = weatherCounts,
            totalDistanceKm = Math.round(totalDistanceKm * 100) / 100.0,
            totalOrsDistanceKm = Math.round(totalOrsDistanceKm * 100) / 100.0,
            totalRmvDistanceKm = Math.round(totalRmvDistanceKm * 100) / 100.0,
            topLines = topLines,
            timeSlotStats = timeSlotStats,
            lineReliability = lineReliability,
            routePairs = routePairs,
            delayDistribution = delayDistribution,
            recordShortestTrip = shortestTrip?.first ?: "-",
            recordShortestTripMin = shortestTrip?.second ?: 0,
            recordLongestTrip = longestTrip?.first ?: "-",
            recordLongestTripMin = longestTrip?.second ?: 0,
            recordLongestDistanceTrip = longestDistanceTrip?.first ?: "-",
            recordLongestDistanceKm = Math.round((longestDistanceTrip?.second ?: 0.0) * 100) / 100.0
        )

        return Pair(summaryData, sortedMonths)
    }

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
            val distanceKm = parseDistanceKm(mesafe) ?: 0.0
            updates.putAll(calculatedDistanceFields(distanceKm, resetRmvDistance = true))
        }
        if (durakSayisi != null) updates["durakSayisi"] = durakSayisi

        // Recompute planlananYolSuresi if times change
        if (binisTime != null || inisTime != null) {
            val finalBinis = binisTime ?: existingData["planlananBinis"]?.toString()
            val finalInis = inisTime ?: existingData["planlananInis"]?.toString()
            val yolSuresi = computeYolSuresi(finalBinis, finalInis)
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
                putAll(calculatedDistanceFields(parseDistanceKm(mesafe) ?: 0.0, resetRmvDistance = true))
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
                if (!enriched.containsKey("yearMonth")) enriched["yearMonth"] = computeYearMonth(tarih)
                if (!enriched.containsKey("sortDate"))  enriched["sortDate"]  = computeSortDate(tarih)
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
    suspend fun fetchTripsFiltered(month: String = "Tümü", type: String = "Tümü"): List<Map<String, Any>> {
        val snapshot = db.collection(COLLECTION).get().await()

        // Parse "MonthName Year" filter if provided
        val filterMonthNum: String?
        val filterYear: String?
        if (month != "Tümü") {
            val filterParts = month.split(" ")
            filterMonthNum = if (filterParts.size >= 2) MONTH_NUMBERS[filterParts[0]] else MONTH_NUMBERS[month]
            filterYear = if (filterParts.size >= 2) filterParts[1] else null
        } else {
            filterMonthNum = null
            filterYear = null
        }

        return snapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            val tarih = data["tarih"]?.toString() ?: ""
            if (tarih.isBlank() || tarih.lowercase() == "tarih") return@mapNotNull null

            // Month+year filter
            if (filterMonthNum != null) {
                val parts = tarih.split(".")
                if (parts.size < 3 || parts[1] != filterMonthNum) return@mapNotNull null
                if (filterYear != null && parts[2] != filterYear) return@mapNotNull null
            }

            // Type filter
            if (type != "Tümü") {
                val tur = data["tur"]?.toString() ?: ""
                if (tur != type) return@mapNotNull null
            }

            data + ("firestoreDocId" to doc.id)
        }.sortedByDescending { doc ->
            doc["sortDate"]?.toString().takeIf { !it.isNullOrBlank() }
                ?: computeSortDate(doc["tarih"]?.toString() ?: "")
        }
    }

    // ── Update trip by Firestore document ID ──
    suspend fun updateTrip(docId: String, fields: Map<String, Any?>): Boolean {
        logD("FirestoreUpdate", "docId='$docId' isEmpty=${docId.isBlank()}")
        return try {
            val docRef = db.collection(COLLECTION).document(docId)
            val cleanFields = fields.filterValues { it != null }.mapValues { it.value!! }
            val existing = docRef.get().await().data
            val updates = cleanFields.toMutableMap()
            enrichUpdatedDistanceFields(updates)

            // Tarih değişince sortDate ve yearMonth'u da güncelle (Bug 1 + Bug 5)
            val newTarih = updates["tarih"]?.toString()
            if (!newTarih.isNullOrBlank()) {
                updates["sortDate"]  = computeSortDate(newTarih)
                updates["yearMonth"] = computeYearMonth(newTarih)
            }

            val finalPlanlananBinis = updates["planlananBinis"]?.toString() ?: existing?.get("planlananBinis")?.toString()
            val finalGercekBinis = updates["gercekBinis"]?.toString() ?: existing?.get("gercekBinis")?.toString()
            val finalPlanlananInis = updates["planlananInis"]?.toString() ?: existing?.get("planlananInis")?.toString()
            val finalGercekInis = updates["gercekInis"]?.toString() ?: existing?.get("gercekInis")?.toString()

            if (updates.containsKey("planlananBinis") || updates.containsKey("planlananInis")) {
                updates["planlananYolSuresi"] = computeYolSuresi(finalPlanlananBinis, finalPlanlananInis)
            }
            if (updates.containsKey("gercekBinis") || updates.containsKey("gercekInis")) {
                updates["gercekYolSuresi"] = computeYolSuresi(finalGercekBinis, finalGercekInis)
            }
            if (updates.containsKey("gercekBinis") || updates.containsKey("planlananBinis")) {
                updates["gecikme"] = computeGecikme(finalPlanlananBinis, finalGercekBinis)
            }

            updates["updatedAt"] = System.currentTimeMillis()
            docRef.update(updates).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreService", "updateTrip failed for docId: $docId", e)
            throw e
        }
    }

    // ── Delete trip by Firestore document ID ──
    suspend fun deleteTrip(docId: String): Boolean {
        return try {
            db.collection(COLLECTION).document(docId).delete().await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreService", "deleteTrip failed for docId: $docId", e)
            throw e
        }
    }

    // ── Helper: strip seconds from time string "HH:MM:SS" → "HH:MM" ──
    fun stripSeconds(time: String): String {
        val trimmed = time.trim()
        if (trimmed.isBlank()) return trimmed
        val parts = trimmed.split(":")
        return if (parts.size >= 3) "${parts[0]}:${parts[1]}" else trimmed
    }

    // ── One-time migration: strip seconds from all time fields ──
    suspend fun migrateStripSeconds(): Int {
        val timeFields = listOf("planlananBinis", "gercekBinis", "planlananInis", "gercekInis")
        val snapshot = db.collection(COLLECTION).get().await()
        var updatedCount = 0

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val updates = mutableMapOf<String, Any>()

            for (field in timeFields) {
                val value = data[field]?.toString() ?: continue
                if (value.isBlank()) continue
                val stripped = stripSeconds(value)
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

    // ── Update existing record by trip ID field (for post-save field changes) ──
    suspend fun updateExistingRecord(tripId: String, fields: Map<String, Any>): Boolean {
        return try {
            val snapshot = db.collection(COLLECTION)
                .whereEqualTo("id", tripId)
                .get().await()
            if (snapshot.isEmpty) return false
            val updates = fields.toMutableMap()
            enrichUpdatedDistanceFields(updates)
            updates["updatedAt"] = System.currentTimeMillis()
            snapshot.documents[0].reference.update(updates).await()
            true
        } catch (e: Exception) {
            Log.e("FirestoreService", "updateExistingRecord failed for tripId: $tripId", e)
            throw e
        }
    }

    // ── One-time migration: compute Yol Suresi for all trips ──
    suspend fun migrateYolSuresi(): Pair<Int, Int> { // returns (updated, total)
        val snapshot = db.collection(COLLECTION).get().await()
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
                val pDuration = computeYolSuresi(planlananBinis, planlananInis)
                if (pDuration.isNotBlank() && pDuration != mevcutPlanlanan) updates["planlananYolSuresi"] = pDuration
            }
            if (!gercekBinis.isNullOrBlank() && !gercekInis.isNullOrBlank()) {
                val gDuration = computeYolSuresi(gercekBinis, gercekInis)
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
    suspend fun migrateYearMonth(): Pair<Int, Int> { // (güncellenen, toplam)
        var updated = 0
        var total = 0
        var lastVisible: com.google.firebase.firestore.DocumentSnapshot? = null
        val limit = 500L

        while (true) {
            var query = db.collection(COLLECTION).limit(limit)
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
                val ym = computeYearMonth(tarih)
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

    // ── One-time migration: sortDate alanını tüm eski kayıtlara ekle ──
    // "YYYY-MM-DD" formatında sortDate, orderBy için kronolojik sıralamayı garanti eder.
    // Migration sonrasında fetchTrips/fetchTripsFiltered doğru sırada çalışır.
    suspend fun migrateSortDate(): Pair<Int, Int> { // (güncellenen, toplam)
        val snapshot = db.collection(COLLECTION).get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            // Zaten sortDate varsa geç
            val existing = doc.getString("sortDate")
            if (!existing.isNullOrBlank()) continue

            val tarih = doc.getString("tarih") ?: continue
            val sd = computeSortDate(tarih)
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
                Log.e("FirestoreService", "migrateSortDate failed for doc: ${doc.id}", e)
                /* tek döküman başarısız olursa devam et */
            }
        }
        return Pair(updated, total)
    }

    suspend fun migrateDistanceFields(): Pair<Int, Int> {
        val snapshot = db.collection(COLLECTION).get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            val data = doc.data ?: continue
            val updates = linkedMapOf<String, Any>()

            val existingOrsKm = parseDistanceKm(data[FIELD_ORS_DISTANCE_KM])
            val orsKm = existingOrsKm
                ?: parseDistanceKm(data["mesafe"])
                ?: 0.0
            val orsText = formatDistanceKm(orsKm)

            if (!data.containsKey(FIELD_ORS_DISTANCE_KM) || (existingOrsKm == null && orsKm > 0.0)) {
                updates[FIELD_ORS_DISTANCE_KM] = orsKm
            }
            if (!data.containsKey(FIELD_ORS_DISTANCE_TEXT) ||
                (data[FIELD_ORS_DISTANCE_TEXT]?.toString().isNullOrBlank() && orsText.isNotBlank())
            ) {
                updates[FIELD_ORS_DISTANCE_TEXT] = orsText
            }

            val rmvKm = parseDistanceKm(data[FIELD_RMV_DISTANCE_KM])
            val rmvText = data[FIELD_RMV_DISTANCE_TEXT]?.toString().orEmpty()
            if (!data.containsKey(FIELD_RMV_DISTANCE_KM)) updates[FIELD_RMV_DISTANCE_KM] = 0.0
            if (!data.containsKey(FIELD_RMV_DISTANCE_METERS)) updates[FIELD_RMV_DISTANCE_METERS] = 0
            if (!data.containsKey(FIELD_RMV_DISTANCE_TEXT)) updates[FIELD_RMV_DISTANCE_TEXT] = ""
            if (!data.containsKey(FIELD_RMV_DISTANCE_STATUS) ||
                data[FIELD_RMV_DISTANCE_STATUS]?.toString().isNullOrBlank()
            ) {
                updates[FIELD_RMV_DISTANCE_STATUS] =
                    if (rmvKm != null || rmvText.isNotBlank()) RMV_DISTANCE_READY else RMV_DISTANCE_PENDING
            }
            if (!data.containsKey(FIELD_RMV_DISTANCE_UPDATED_AT)) updates[FIELD_RMV_DISTANCE_UPDATED_AT] = ""
            if (!data.containsKey(FIELD_RMV_API_VERSION)) updates[FIELD_RMV_API_VERSION] = ""
            if (!data.containsKey(FIELD_JOURNEY_REF)) updates[FIELD_JOURNEY_REF] = ""
            if (!data.containsKey(FIELD_FROM_STOP_ID)) updates[FIELD_FROM_STOP_ID] = ""
            if (!data.containsKey(FIELD_TO_STOP_ID)) updates[FIELD_TO_STOP_ID] = ""

            if (updates.isNotEmpty()) {
                updates["updatedAt"] = System.currentTimeMillis()
                try {
                    doc.reference.update(updates).await()
                    updated++
                } catch (e: Exception) {
                    Log.e("FirestoreService", "migrateDistanceFields failed for doc: ${doc.id}", e)
                    // Keep migrating remaining documents if a single document fails.
                }
            }
        }
        return Pair(updated, total)
    }

    // ── Favorite Stops Firebase Backup ──────────────────────────────────────
    suspend fun saveFavorite(fav: com.example.toplutasima.model.FavoriteStop) {
        try {
            db.collection(FAV_COLLECTION).document(fav.id).set(
                mapOf(
                    "id" to fav.id,
                    "stopId" to fav.stopId,
                    "stopName" to fav.stopName,
                    "label" to fav.label,
                    "usageType" to fav.usageType.name
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreService", "saveFavorite failed for: ${fav.id}", e)
            throw e
        }
    }

    suspend fun deleteFavorite(favId: String) {
        try {
            db.collection(FAV_COLLECTION).document(favId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreService", "deleteFavorite failed for: $favId", e)
            throw e
        }
    }

    suspend fun fetchAllFavorites(): List<com.example.toplutasima.model.FavoriteStop> {
        return try {
            val snapshot = db.collection(FAV_COLLECTION).get().await()
            snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                val id = data["id"]?.toString() ?: return@mapNotNull null
                val stopId = data["stopId"]?.toString() ?: return@mapNotNull null
                val stopName = data["stopName"]?.toString() ?: return@mapNotNull null
                val label = data["label"]?.toString() ?: stopName
                val usageType = try {
                    com.example.toplutasima.model.UsageType.valueOf(
                        data["usageType"]?.toString() ?: "BOTH"
                    )
                } catch (e: Exception) {
                    Log.e("FirestoreService", "usageType parse failed for doc: ${doc.id}", e)
                    com.example.toplutasima.model.UsageType.BOTH
                }
                com.example.toplutasima.model.FavoriteStop(
                    id = id, stopId = stopId, stopName = stopName,
                    label = label, usageType = usageType
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("FirestoreService", "fetchAllFavorites failed", e)
            throw e
        }
    }
}
