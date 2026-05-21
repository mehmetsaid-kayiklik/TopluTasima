package com.example.toplutasima.repository

import android.content.Context
import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.LocationOption
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.TransitAlert
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.network.RmvApiService

class TripRepository(private val appContext: Context? = null) {
    private val rmvTripRepository = RmvTripRepository()
    private val profileLinkRepository = TripProfileLinkRepository(appContext)
    private val transitRecordRepository = TransitRecordRepository(appContext, profileLinkRepository)

    suspend fun searchStops(input: String, max: Int = 3): List<StopOption> =
        rmvTripRepository.searchStops(input, max)

    suspend fun searchLocations(input: String, max: Int = 8): List<LocationOption> =
        rmvTripRepository.searchLocations(input, max)

    suspend fun fetchDepartures(stopId: String, destId: String, date: String, time: String): List<Departure> =
        rmvTripRepository.fetchDepartures(stopId, destId, date, time)

    suspend fun fetchTripBasic(
        originId: String,
        destId: String,
        date: String,
        time: String,
        preferredLine: String = ""
    ): TripResult =
        rmvTripRepository.fetchTripBasic(originId, destId, date, time, preferredLine)

    suspend fun fetchSegmentDetails(seg: Segment): RmvApiService.SegmentDetails =
        rmvTripRepository.fetchSegmentDetails(seg)

    suspend fun searchNearbyStops(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 500
    ): List<RmvApiService.NearbyStop> =
        rmvTripRepository.searchNearbyStops(lat, lon, radiusMeters)

    suspend fun fetchTransitAlerts(line: String, date: String = ""): List<TransitAlert> =
        rmvTripRepository.fetchTransitAlerts(line, date)

    suspend fun matchJourneyTrack(
        points: List<Pair<Double, Double>>,
        date: String,
        time: String
    ): List<JourneyMatchCandidate> =
        rmvTripRepository.matchJourneyTrack(points, date, time)

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
    ): Boolean =
        transitRecordRepository.saveSegment(
            id,
            date,
            seg,
            havaDurumu,
            oturabildim,
            biletKontrolu,
            note,
            seatmateUuid,
            profileId,
            seatmateNote
        )

    suspend fun updateTripProfileLink(
        tripStableKey: String,
        profileId: String?,
        seatmateNote: String?
    ) =
        profileLinkRepository.updateTripProfileLink(tripStableKey, profileId, seatmateNote)

    suspend fun updateActual(id: String, actualDep: String?, actualArr: String?): Boolean =
        transitRecordRepository.updateActual(id, actualDep, actualArr)

    suspend fun clearActual(id: String, clearDep: Boolean, clearArr: Boolean): Boolean =
        transitRecordRepository.clearActual(id, clearDep, clearArr)

    suspend fun fetchRecord(id: String): Map<String, Any>? =
        transitRecordRepository.fetchRecord(id)

    suspend fun updateStops(
        id: String,
        binisDuragi: String?,
        binisTime: String?,
        inisDuragi: String?,
        inisTime: String?,
        mesafe: String? = null,
        durakSayisi: String? = null
    ): Boolean =
        transitRecordRepository.updateStops(id, binisDuragi, binisTime, inisDuragi, inisTime, mesafe, durakSayisi)

    suspend fun updateExistingRecord(id: String, fields: Map<String, Any>): Boolean =
        transitRecordRepository.updateExistingRecord(id, fields)
}
