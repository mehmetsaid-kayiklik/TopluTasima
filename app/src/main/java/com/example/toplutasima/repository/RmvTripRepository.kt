package com.example.toplutasima.repository

import com.example.toplutasima.model.Departure
import com.example.toplutasima.model.JourneyMatchCandidate
import com.example.toplutasima.model.LocationOption
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.StopOption
import com.example.toplutasima.model.TransitAlert
import com.example.toplutasima.model.TripResult
import com.example.toplutasima.network.RmvApiService

class RmvTripRepository {
    suspend fun searchStops(input: String, max: Int = 3): List<StopOption> =
        RmvApiService.searchStopOptions(input, max)

    suspend fun searchLocations(input: String, max: Int = 8): List<LocationOption> =
        RmvApiService.searchLocationOptions(input, max)

    suspend fun fetchDepartures(stopId: String, destId: String, date: String, time: String): List<Departure> =
        RmvApiService.fetchDepartureBoard(stopId, destId, date, time)

    suspend fun fetchTripBasic(
        originId: String,
        destId: String,
        date: String,
        time: String,
        preferredLine: String = ""
    ): TripResult =
        RmvApiService.fetchTripBasic(originId, destId, date, time, preferredLine)

    suspend fun fetchSegmentDetails(seg: Segment): RmvApiService.SegmentDetails =
        RmvApiService.fetchSegmentDetails(seg)

    suspend fun searchNearbyStops(
        lat: Double,
        lon: Double,
        radiusMeters: Int = 500
    ): List<RmvApiService.NearbyStop> =
        RmvApiService.searchNearbyStops(lat, lon, radiusMeters)

    suspend fun fetchTransitAlerts(departure: Departure): List<TransitAlert> =
        RmvApiService.fetchTransitAlerts(departure)

    suspend fun matchJourneyTrack(
        points: List<Pair<Double, Double>>,
        date: String,
        time: String
    ): List<JourneyMatchCandidate> =
        RmvApiService.matchJourneyTrack(points, date, time)
}
