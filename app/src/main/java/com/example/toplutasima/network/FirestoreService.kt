package com.example.toplutasima.network

import android.util.Log
import com.example.toplutasima.BuildConfig
import com.example.toplutasima.model.BulkUpdateRow
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.TicketStatus
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
        "not", "biletKontrolü", "mesafe", "durakSayisi", "id",
        "yearMonth", "sortDate"
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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
        } catch (_: Exception) {
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

    // ── Save a new trip document ──
    suspend fun saveTrip(data: Map<String, Any?>): String {
        val enriched = data.toMutableMap()
        val tarih = enriched["tarih"]?.toString()
        if (!tarih.isNullOrBlank()) {
            if (!enriched.containsKey("yearMonth")) enriched["yearMonth"] = computeYearMonth(tarih)
            if (!enriched.containsKey("sortDate"))  enriched["sortDate"]  = computeSortDate(tarih)
        }
        val ordered = buildOrderedMap(enriched)
        val doc = db.collection(COLLECTION).add(ordered).await()
        return doc.id
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

        if (updates.isNotEmpty()) docRef.update(updates).await()
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
            logW("FirestoreService", "fetchRecord failed: ${e.message}")
            null
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

    // ── Fetch summary for all months (Level 1 Navigation) ──
    // Optimizasyon: yearMonth alanı varsa gruplamak için sadece o alana bakılır.
    // Eski kayıtlar için tarih alanından ayrıştırma yapılır.
    suspend fun fetchMonthSummaries(): List<MonthSummary> {
        val snapshot = db.collection(COLLECTION).get().await()

        // (Year, MonthNum) -> Count
        val monthCounts = mutableMapOf<Pair<Int, Int>, Int>()
        for (doc in snapshot.documents) {
            // Önce yeni yearMonth alanını dene ("YYYY-MM")
            val yearMonth = doc.getString("yearMonth")
            if (!yearMonth.isNullOrBlank()) {
                val ymParts = yearMonth.split("-")
                if (ymParts.size >= 2) {
                    val year = ymParts[0].toIntOrNull() ?: continue
                    val monthNum = ymParts[1].toIntOrNull() ?: continue
                    monthCounts[year to monthNum] = (monthCounts[year to monthNum] ?: 0) + 1
                    continue
                }
            }
            // Eski kayıtlar için DD.MM.YYYY'den ayrıştır
            val tarih = doc.getString("tarih") ?: continue
            val parts = tarih.split(".")
            if (parts.size >= 3) {
                val monthNum = parts[1].toIntOrNull() ?: continue
                val year = parts[2].toIntOrNull() ?: continue
                monthCounts[year to monthNum] = (monthCounts[year to monthNum] ?: 0) + 1
            }
        }

        return monthCounts.entries
            .map { (key, count) ->
                val (year, monthNum) = key
                val monthStr = String.format(Locale.US, "%02d", monthNum)
                val monthName = MONTH_NAMES[monthStr] ?: monthStr
                MonthSummary(
                    monthName = monthName,
                    year = year.toString(),
                    count = count,
                    sortKey = "${year}${monthStr}"
                )
            }
            .sortedBy { it.sortKey }
    }

    // ── Fetch trips for a specific month (Level 2 Navigation) ──
    // Optimizasyon: yearMonth alanı ("YYYY-MM") bulunan kayıtlar için sunucu taraflı
    // WHERE sorgusu kullanılır — tüm koleksiyonu çekmek yerine sadece ilgili ay indirilir.
    // Eski kayıtlar (yearMonth alanı olmayan) için fallback olarak client-side filtreleme yapılır.
    suspend fun fetchTripsForMonth(monthName: String, year: String): List<Map<String, Any>> {
        val monthNum = MONTH_NUMBERS[monthName] ?: return emptyList()
        val monthStr = monthNum.padStart(2, '0')
        val yearMonth = "$year-$monthStr"

        // 1. Sunucu taraflı sorgu (yeni kayıtlar)
        val serverSnapshot = db.collection(COLLECTION)
            .whereEqualTo("yearMonth", yearMonth)
            .get().await()
        val serverDocs = serverSnapshot.documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            data + ("firestoreDocId" to doc.id)
        }

        // 2. Eski kayıtlar için fallback
        // Firestore'da "alan mevcut değil" sorgusu desteklenmiyor.
        // Bu yüzden migrateYearMonth() çalıştırılana kadar tam koleksiyon okunur;
        // sadece yearMonth alanı olmayan (eski) dökümanlar client-side filtrelenir.
        val filterMonth = monthNum.toIntOrNull() ?: return serverDocs
        val filterYear = year.toIntOrNull() ?: return serverDocs

        val legacySnapshot = db.collection(COLLECTION).get().await()
        val fallbackDocs = legacySnapshot.documents.mapNotNull { doc ->
            // yearMonth alanı zaten varsa sunucu sorgusunda yakalandı, atla
            if (!doc.getString("yearMonth").isNullOrBlank()) return@mapNotNull null
            val tarih = doc.getString("tarih") ?: return@mapNotNull null
            val parts = tarih.split(".")
            if (parts.size >= 3) {
                val docMonth = parts[1].toIntOrNull()
                val docYear = parts[2].toIntOrNull()
                if (docMonth == filterMonth && docYear == filterYear) {
                    val data = doc.data ?: return@mapNotNull null
                    data + ("firestoreDocId" to doc.id)
                } else null
            } else null
        }

        return serverDocs + fallbackDocs
    }

    // ── Compute summary from trips, optionally filtered by "MonthName Year" ──
    suspend fun fetchSummary(sheetName: String = "Tümü"): Pair<SummaryData, List<String>> {
        val snapshot = db.collection(COLLECTION).get().await()
        val allDocs = snapshot.documents.mapNotNull { it.data }

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
        val typeOnTime = mutableMapOf<String, Pair<Int, Int>>() // total, onTime
        val dailyTrips = mutableMapOf<String, Int>()
        val dailyDuration = mutableMapOf<String, Double>()
        val lineMaxDelays = mutableMapOf<String, Double>()
        val lineTotalDelays = mutableMapOf<String, Double>()

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

            if (types.containsKey(type)) types[type] = types[type]!! + 1
            if (line.isNotBlank()) lines[line] = (lines[line] ?: 0) + 1
            if (from.isNotBlank()) fromStops[from] = (fromStops[from] ?: 0) + 1
            if (to.isNotBlank()) toStops[to] = (toStops[to] ?: 0) + 1
            if (day.isNotBlank() && days.containsKey(day)) days[day] = days[day]!! + 1

            val weather = row["havaDurumu"]?.toString() ?: ""
            if (weather.isNotBlank()) weatherCounts[weather] = (weatherCounts[weather] ?: 0) + 1

            val mesafe = row["mesafe"]?.toString() ?: ""
            if (mesafe.isNotBlank()) {
                val numPart = mesafe.replace(Regex("[^0-9.,]"), "").replace(',', '.').toDoubleOrNull()
                if (numPart != null) totalDistanceKm += numPart
            }

            val plannedMin = row["planlananYolSuresi"]?.toString()?.toDoubleOrNull()
            val actualMin = row["gercekYolSuresi"]?.toString()?.toDoubleOrNull()

            if (plannedMin != null) totalPlanned += plannedMin
            if (actualMin != null) {
                totalActual += actualMin
                dailyDuration[tarih] = (dailyDuration[tarih] ?: 0.0) + actualMin
            }

            dailyTrips[tarih] = (dailyTrips[tarih] ?: 0) + 1

            // Delay: use stored gecikme field (gercekBinis - planlananBinis in minutes)
            val gecikme = row["gecikme"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            if (gecikme > 0) {
                totalDelay += gecikme.toDouble()
                delayCount++
                if (gecikme.toDouble() > maxDelay) maxDelay = gecikme.toDouble()

                val prev = typeOnTime[type] ?: Pair(0, 0)
                typeOnTime[type] = Pair(prev.first + 1, prev.second + if (gecikme <= 3) 1 else 0)

                if (line.isNotBlank()) {
                    if (!lineMaxDelays.containsKey(line) || gecikme.toDouble() > lineMaxDelays[line]!!) {
                        lineMaxDelays[line] = gecikme.toDouble()
                    }
                    lineTotalDelays[line] = (lineTotalDelays[line] ?: 0.0) + gecikme.toDouble()
                }
            } else {
                // gecikme == 0 means on-time
                val prev = typeOnTime[type] ?: Pair(0, 0)
                typeOnTime[type] = Pair(prev.first + 1, prev.second + 1)
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
            topLines = topLines
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
        if (!binisDuragi.isNullOrBlank()) updates["binisDuragi"] = binisDuragi
        if (!binisTime.isNullOrBlank()) updates["planlananBinis"] = binisTime
        if (!inisDuragi.isNullOrBlank()) updates["inisDuragi"] = inisDuragi
        if (!inisTime.isNullOrBlank()) updates["planlananInis"] = inisTime
        if (mesafe != null) updates["mesafe"] = mesafe
        if (durakSayisi != null) updates["durakSayisi"] = durakSayisi

        // Recompute planlananYolSuresi if times change
        if (binisTime != null || inisTime != null) {
            val finalBinis = binisTime ?: existingData["planlananBinis"]?.toString()
            val finalInis = inisTime ?: existingData["planlananInis"]?.toString()
            val yolSuresi = computeYolSuresi(finalBinis, finalInis)
            if (yolSuresi.isNotBlank()) updates["planlananYolSuresi"] = yolSuresi
        }

        if (updates.isNotEmpty()) docRef.update(updates).await()
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
            mapOf(
                "mesafe" to mesafe,
                "durakSayisi" to durakSayisi
            )
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
            val ordered = buildOrderedMap(enriched)
            db.collection(COLLECTION).add(ordered).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
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

            docRef.update(updates).await()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
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
            false
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
            snapshot.documents[0].reference.update(fields).await()
            true
        } catch (e: Exception) {
            false
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
    // Bu fonksiyon Settings ekranındaki butonla tetiklenir, yalnızca bir kez çalıştırılması yeterlidir.
    // Çalıştırıldıktan sonra fetchTripsForMonth tam koleksiyon okumak yerine
    // sunucu taraflı WHERE sorgusuyla yalnızca o aya ait dökümanları getirir.
    suspend fun migrateYearMonth(): Pair<Int, Int> { // (güncellenen, toplam)
        val snapshot = db.collection(COLLECTION).get().await()
        val total = snapshot.documents.size
        var updated = 0

        for (doc in snapshot.documents) {
            // Zaten yearMonth varsa geç
            val existing = doc.getString("yearMonth")
            if (!existing.isNullOrBlank()) continue

            val tarih = doc.getString("tarih") ?: continue
            val ym = computeYearMonth(tarih)
            if (ym.isBlank()) continue

            try {
                doc.reference.update("yearMonth", ym).await()
                updated++
            } catch (_: Exception) { /* tek döküman başarısız olursa devam et */ }
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
                doc.reference.update("sortDate", sd).await()
                updated++
            } catch (_: Exception) { /* tek döküman başarısız olursa devam et */ }
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
            logW("FirestoreFav", "saveFavorite failed: ${e.message}")
        }
    }

    suspend fun deleteFavorite(favId: String) {
        try {
            db.collection(FAV_COLLECTION).document(favId).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW("FirestoreFav", "deleteFavorite failed: ${e.message}")
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
                } catch (_: Exception) { com.example.toplutasima.model.UsageType.BOTH }
                com.example.toplutasima.model.FavoriteStop(
                    id = id, stopId = stopId, stopName = stopName,
                    label = label, usageType = usageType
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW("FirestoreFav", "fetchAllFavorites failed: ${e.message}")
            emptyList()
        }
    }
}
