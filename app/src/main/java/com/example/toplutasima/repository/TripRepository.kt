package com.example.toplutasima.repository

import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.network.RmvApiService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class TripRepository {

    // ── RMV API calls (always from RMV) ──

    suspend fun searchStops(input: String, max: Int = 3): List<StopOption> =
        RmvApiService.searchStopOptions(input, max)

    suspend fun fetchDepartures(stopId: String, destId: String, date: String, time: String): List<Departure> =
        RmvApiService.fetchDepartureBoard(stopId, destId, date, time)

    suspend fun fetchTripBasic(originId: String, destId: String, date: String, time: String, preferredLine: String = ""): TripResult =
        RmvApiService.fetchTripBasic(originId, destId, date, time, preferredLine)

    suspend fun fetchSegmentDetails(seg: Segment): RmvApiService.SegmentDetails =
        RmvApiService.fetchSegmentDetails(seg)

    suspend fun searchNearbyStops(lat: Double, lon: Double, radiusMeters: Int = 500): List<RmvApiService.NearbyStop> =
        RmvApiService.searchNearbyStops(lat, lon, radiusMeters)

    // ── Firebase data calls ──

    suspend fun fetchSummary(sheetName: String = "Tümü"): Pair<SummaryData, List<String>> =
        withContext(Dispatchers.IO) { FirestoreService.fetchSummary(sheetName) }

    suspend fun saveSegment(
        id: String, date: String, seg: Segment,
        havaDurumu: String, oturabildim: Boolean, biletKontrolu: Boolean, note: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val gun = FirestoreService.computeGun(date)
            val gununTipi = FirestoreService.computeGununTipi(date)
            val yearMonth = FirestoreService.computeYearMonth(date) // "YYYY-MM" - sunucu taraflı ay sorgusu için

            val data = LinkedHashMap<String, Any?>()
            data["tarih"] = date
            data["gun"] = gun
            data["tur"] = seg.typeTr
            data["hat"] = seg.line
            data["yon"] = seg.direction
            data["binisDuragi"] = seg.fromStop
            data["planlananBinis"] = FirestoreService.stripSeconds(seg.dep)
            data["gercekBinis"] = ""
            data["gecikme"] = 0
            data["inisDuragi"] = seg.toStop
            data["planlananInis"] = FirestoreService.stripSeconds(seg.arr)
            data["gercekInis"] = ""
            data["gununTipi"] = gununTipi
            data["havaDurumu"] = havaDurumu
            data["oturabildimMi"] = SeatingStatus.fromBoolean(oturabildim).key
            data["planlananYolSuresi"] = FirestoreService.computeYolSuresi(seg.dep, seg.arr)
            data["gercekYolSuresi"] = ""
            data["not"] = note
            data["biletKontrolü"] = TicketStatus.fromBoolean(biletKontrolu).key
            data["mesafe"] = if (seg.distanceKm > 0) String.format(Locale.US, "%.2f km", seg.distanceKm) else ""
            data["durakSayisi"] = if (seg.stopCount > 0) seg.stopCount.toString() else ""
            data["id"] = id
            data["yearMonth"] = yearMonth // "YYYY-MM" - ay bazlı sunucu sorgusu için

            FirestoreService.saveTrip(data)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun updateActual(id: String, actualDep: String?, actualArr: String?): Boolean =
        withContext(Dispatchers.IO) { FirestoreService.updateActual(id, actualDep, actualArr) }

    suspend fun updateStops(
        id: String,
        binisDuragi: String?, binisTime: String?,
        inisDuragi: String?, inisTime: String?,
        mesafe: String? = null, durakSayisi: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        FirestoreService.updateStops(id, binisDuragi, binisTime, inisDuragi, inisTime, mesafe, durakSayisi)
    }

    suspend fun updateExistingRecord(id: String, fields: Map<String, Any>): Boolean =
        withContext(Dispatchers.IO) { FirestoreService.updateExistingRecord(id, fields) }
}
