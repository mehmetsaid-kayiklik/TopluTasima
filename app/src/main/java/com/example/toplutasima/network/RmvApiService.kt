package com.example.toplutasima.network

import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.LocationOption
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.TransitAlert
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.network.rmv.RmvDistanceService
import com.example.toplutasima.network.rmv.RmvJourneyService
import com.example.toplutasima.network.rmv.RmvSegmentDetailsService
import com.example.toplutasima.network.rmv.RmvStopService
import com.example.toplutasima.network.rmv.RmvTimeUtils

object RmvApiService {
    private val distanceService by lazy { RmvDistanceService() }
    private val stopService by lazy { RmvStopService() }
    private val journeyService by lazy { RmvJourneyService() }
    private val segmentDetailsService by lazy { RmvSegmentDetailsService(distanceService) }

    data class JourneySegment(
        val stopCount: Int,
        val coords: List<Pair<Double, Double>>,
        val stopNames: List<String> = emptyList(),
        val stopTimes: List<String> = emptyList(),
        // Tüm hat listesi içinde kullanıcının biniş/iniş indeksleri
        val fromIdx: Int = 0,
        val toIdx: Int = -1,
        // Tüm hat durak koordinatları (rail routing retry için)
        val allStopCoords: List<Pair<Double, Double>> = emptyList()
    )

    fun formatTimeDigits(digits: String): String =
        RmvTimeUtils.formatTimeDigits(digits)

    fun normalizeLineForDisplay(line: String): String =
        RmvFeatureParsers.normalizeLineToken(line)

    fun convertToApiDate(uiDate: String): String =
        RmvTimeUtils.convertToApiDate(uiDate)

    // ── 1. Location search — Retrofit ────────────────────────────────────────

    suspend fun searchStopOptions(input: String, max: Int = 3): List<StopOption> =
        stopService.searchStopOptions(input, max)

    // ── 1b. Nearby stops — Retrofit ──────────────────────────────────────────

    data class NearbyStop(val id: String, val name: String, val distanceMeters: Int)

    suspend fun searchNearbyStops(lat: Double, lon: Double, radiusMeters: Int = 500, max: Int = 8): List<NearbyStop> =
        stopService.searchNearbyStops(lat, lon, radiusMeters, max)

    suspend fun searchLocationOptions(input: String, max: Int = 8): List<LocationOption> =
        stopService.searchLocationOptions(input, max)

    suspend fun fetchTransitAlerts(line: String, date: String = ""): List<TransitAlert> =
        journeyService.fetchTransitAlerts(line, date)

    suspend fun matchJourneyTrack(
        points: List<Pair<Double, Double>>,
        date: String,
        time: String
    ): List<JourneyMatchCandidate> =
        journeyService.matchJourneyTrack(points, date, time)

    // ── 2. Departure board — Retrofit ─────────────────────────────────────────

    suspend fun fetchDepartureBoard(
        stopId: String,
        destId: String,
        date: String,
        time: String
    ): List<Departure> =
        journeyService.fetchDepartureBoard(stopId, destId, date, time)

    // ── 3. Journey stops — OkHttp (suspend) ──────────────────────────────────

    suspend fun fetchJourneyStops(
        ref: String,
        fromStop: String,
        toStop: String,
        boardingDepTime: String = "",
        fromId: String = "",
        toId: String = ""
    ): JourneySegment =
        segmentDetailsService.fetchJourneyStops(ref, fromStop, toStop, boardingDepTime, fromId, toId)

    // ── 4. ORS distance — OkHttp (suspend) ───────────────────────────────────

    suspend fun calculateDistanceORS(coords: List<Pair<Double, Double>>): Double =
        distanceService.calculateDistanceORS(coords)

    // ── 5. Rail distance — OkHttp (suspend) ──────────────────────────────────

    suspend fun calculateDistanceRail(
        coords: List<Pair<Double, Double>>,
        allStopCoords: List<Pair<Double, Double>> = emptyList(),
        fromIdx: Int = -1,
        toIdx: Int = -1
    ): Double =
        distanceService.calculateDistanceRail(coords, allStopCoords, fromIdx, toIdx)

    // ── 6. Trip — OkHttp (suspend) ────────────────────────────────────────────

    suspend fun fetchTripBasic(
        originId: String, destId: String, date: String, time: String, preferredLine: String = ""
    ): TripResult =
        journeyService.fetchTripBasic(originId, destId, date, time, preferredLine)

    // ── 7. Segment details (suspend wrapper) ─────────────────────────────────

    data class SegmentDetails(
        val distanceKm: Double,
        val stopCount: Int,
        val stopNames: List<String>,
        val stopTimes: List<String> = emptyList(),
        val fromIdx: Int = 0,
        val toIdx: Int = -1,
        val toStopLat: Double = Double.NaN,
        val toStopLng: Double = Double.NaN
    )

    suspend fun fetchSegmentDetails(seg: Segment): SegmentDetails =
        segmentDetailsService.fetchSegmentDetails(seg)

}
