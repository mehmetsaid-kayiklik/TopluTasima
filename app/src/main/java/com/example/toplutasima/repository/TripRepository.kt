package com.example.toplutasima.repository

import android.content.Context
import com.example.toplutasima.data.OfflineQueueStore
import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.LocationOption
import com.example.toplutasima.model.ReachabilityResult
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.model.TransitAlert
import com.example.toplutasima.network.FirestoreService
import com.example.toplutasima.network.RmvApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class TripRepository(private val appContext: Context? = null) {

    suspend fun searchStops(input: String, max: Int = 3): List<StopOption> =
        RmvApiService.searchStopOptions(input, max)

    suspend fun searchLocations(input: String, max: Int = 8): List<LocationOption> =
        RmvApiService.searchLocationOptions(input, max)

    suspend fun fetchDepartures(stopId: String, destId: String, date: String, time: String): List<Departure> =
        RmvApiService.fetchDepartureBoard(stopId, destId, date, time)

    suspend fun fetchTripBasic(originId: String, destId: String, date: String, time: String, preferredLine: String = ""): TripResult =
        RmvApiService.fetchTripBasic(originId, destId, date, time, preferredLine)

    suspend fun fetchSegmentDetails(seg: Segment): RmvApiService.SegmentDetails =
        RmvApiService.fetchSegmentDetails(seg)

    suspend fun searchNearbyStops(lat: Double, lon: Double, radiusMeters: Int = 500): List<RmvApiService.NearbyStop> =
        RmvApiService.searchNearbyStops(lat, lon, radiusMeters)

    suspend fun fetchTransitAlerts(line: String, date: String = ""): List<TransitAlert> =
        RmvApiService.fetchTransitAlerts(line, date)

    suspend fun matchJourneyTrack(points: List<Pair<Double, Double>>, date: String, time: String): List<JourneyMatchCandidate> =
        RmvApiService.matchJourneyTrack(points, date, time)

    suspend fun fetchReachability(lat: Double, lon: Double, minutes: Int): ReachabilityResult =
        RmvApiService.fetchReachability(lat, lon, minutes)

    suspend fun fetchSummary(sheetName: String = "Tümü"): Pair<SummaryData, List<String>> =
        withContext(Dispatchers.IO) { FirestoreService.fetchSummary(sheetName) }

    suspend fun saveSegment(
        id: String,
        date: String,
        seg: Segment,
        havaDurumu: String,
        oturabildim: Boolean,
        biletKontrolu: Boolean,
        note: String
    ): Boolean = withContext(Dispatchers.IO) {
        val data = buildSegmentData(id, date, seg, havaDurumu, oturabildim, biletKontrolu, note)
        try {
            FirestoreService.saveTrip(data)
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
            try {
                FirestoreService.updateActual(id, actualDep, actualArr)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                appContext?.let {
                    OfflineQueueStore.enqueueUpdateActual(it, id, actualDep, actualArr)
                    return@withContext true
                }
                false
            }
        }

    suspend fun clearActual(id: String, clearDep: Boolean, clearArr: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            try {
                FirestoreService.clearActual(id, clearDep, clearArr)
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
        withContext(Dispatchers.IO) { FirestoreService.fetchRecord(id) }

    suspend fun updateStops(
        id: String,
        binisDuragi: String?,
        binisTime: String?,
        inisDuragi: String?,
        inisTime: String?,
        mesafe: String? = null,
        durakSayisi: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        FirestoreService.updateStops(id, binisDuragi, binisTime, inisDuragi, inisTime, mesafe, durakSayisi)
    }

    suspend fun updateExistingRecord(id: String, fields: Map<String, Any>): Boolean =
        withContext(Dispatchers.IO) {
            try {
                FirestoreService.updateExistingRecord(id, fields)
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

    private fun buildSegmentData(
        id: String,
        date: String,
        seg: Segment,
        havaDurumu: String,
        oturabildim: Boolean,
        biletKontrolu: Boolean,
        note: String
    ): LinkedHashMap<String, Any?> {
        val data = LinkedHashMap<String, Any?>()
        data["tarih"] = date
        data["gun"] = FirestoreService.computeGun(date)
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
        data["gununTipi"] = FirestoreService.computeGununTipi(date)
        data["havaDurumu"] = havaDurumu
        data["oturabildimMi"] = SeatingStatus.fromBoolean(oturabildim).key
        data["planlananYolSuresi"] = FirestoreService.computeYolSuresi(seg.dep, seg.arr)
        data["gercekYolSuresi"] = ""
        data["not"] = note
        data["biletKontrolü"] = TicketStatus.fromBoolean(biletKontrolu).key
        data["mesafe"] = if (seg.distanceKm > 0) String.format(Locale.US, "%.2f km", seg.distanceKm) else ""
        data["durakSayisi"] = if (seg.stopCount > 0) seg.stopCount.toString() else ""
        data["id"] = id
        data["yearMonth"] = FirestoreService.computeYearMonth(date)
        return data
    }
}
