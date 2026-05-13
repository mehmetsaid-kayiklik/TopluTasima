package com.example.toplutasima.model

import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable
enum class LocationOptionKind { STOP, ADDRESS, POI }

@Serializable
data class StopOption(
    val id: String,
    val name: String,
    val kind: LocationOptionKind = LocationOptionKind.STOP,
    val lat: Double? = null,
    val lon: Double? = null,
    val resolvedStopId: String = "",
    val resolvedStopName: String = ""
) {
    val routingId: String get() = resolvedStopId.ifBlank { id }
    val routingName: String get() = resolvedStopName.ifBlank { name }
}

data class LocationOption(
    val id: String,
    val name: String,
    val kind: LocationOptionKind,
    val lat: Double? = null,
    val lon: Double? = null,
    val resolvedStopId: String = "",
    val resolvedStopName: String = ""
) {
    fun toStopOption(): StopOption = StopOption(
        id = id,
        name = name,
        kind = kind,
        lat = lat,
        lon = lon,
        resolvedStopId = resolvedStopId,
        resolvedStopName = resolvedStopName
    )
}

data class TransitAlert(
    val id: String,
    val title: String,
    val detail: String = "",
    val line: String = "",
    val severity: String = "info"
)

data class JourneyMatchCandidate(
    val id: String,
    val line: String,
    val direction: String,
    val fromStop: String,
    val toStop: String,
    val confidence: Int,
    val requestId: String = ""
)

data class ReachabilityPoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val minutes: Int
)

data class ReachabilityResult(
    val originLat: Double,
    val originLon: Double,
    val minutes: Int,
    val points: List<ReachabilityPoint>,
    val supported: Boolean,
    val message: String = ""
)

data class Departure(
    val line: String,
    val direction: String,
    val time: String,
    val track: String,
    val typeTr: String,
    val journeyDetailRef: String = ""
)

data class Segment(
    val typeTr: String,
    val line: String,
    val direction: String,
    val fromStop: String,
    val toStop: String,
    val dep: String,
    val arr: String,
    val distanceKm: Double = 0.0,
    val stopCount: Int = 0,
    val stopNames: List<String> = emptyList(),
    val stopTimes: List<String> = emptyList(),
    val journeyRef: String = "",
    // stopNames içinde biniş/iniş pozisyonları (tüm hat listesinde)
    val stopFromIdx: Int = 0,
    val stopToIdx: Int = -1,
    // İniş durağının koordinatları — proximity alert için (yoksa NaN)
    val toStopLat: Double = Double.NaN,
    val toStopLng: Double = Double.NaN
)

data class TripResult(
    val segments: List<Segment>,
    val overallDep: String,
    val overallArr: String,
    val durationMin: Int
)

data class TimeSlotStats(
    val key: String,
    val trips: Int,
    val avgDelay: Double,
    val punctualityRate: Int
)

data class LineReliabilityStats(
    val line: String,
    val trips: Int,
    val avgDelay: Double,
    val punctualityRate: Int,
    val maxDelay: Int
)

data class RoutePairStats(
    val fromStop: String,
    val toStop: String,
    val trips: Int,
    val avgDurationMin: Int,
    val avgDelay: Double,
    val fastestMin: Int,
    val slowestMin: Int
)

data class DelayBucketStats(
    val key: String,
    val count: Int
)

data class SummaryData(
    val totalTrips: Int,
    val seatedCount: Int,
    val ticketControlCount: Int,
    val types: Map<String, Int>,
    val freqLine: String,
    val freqFrom: String,
    val freqTo: String,
    val days: Map<String, Int>,
    val totalPlannedMin: Int,
    val totalActualMin: Int,
    val maxDelay: Int,
    val totalDelay: Int,
    val avgDelay: Double,
    val punctualityRates: Map<String, Int>,
    val recordLongestDay: String,
    val recordLongestDayMin: Int,
    val recordMostTripsDay: String,
    val recordMostTripsCount: Int,
    val recordMostDelayedLine: String,
    val recordMostDelayedLineMin: Int,
    val recordTotalDelayLine: String = "-",
    val recordTotalDelayLineMin: Int = 0,
    val weatherCounts: Map<String, Int> = emptyMap(),
    val totalDistanceKm: Double = 0.0,
    val topLines: Map<String, Int> = emptyMap(),
    val timeSlotStats: List<TimeSlotStats> = emptyList(),
    val lineReliability: List<LineReliabilityStats> = emptyList(),
    val routePairs: List<RoutePairStats> = emptyList(),
    val delayDistribution: List<DelayBucketStats> = emptyList(),
    val recordShortestTrip: String = "-",
    val recordShortestTripMin: Int = 0,
    val recordLongestTrip: String = "-",
    val recordLongestTripMin: Int = 0,
    val recordLongestDistanceTrip: String = "-",
    val recordLongestDistanceKm: Double = 0.0
)

data class NavItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

data class BulkUpdateRow(
    val rowIndex: Int = 0,
    val sheetName: String = "",
    val hat: String,
    val tur: String,
    val yon: String,
    val binisDuragi: String,
    val inisDuragi: String,
    val planlananBinis: String,
    val tarih: String,
    val firestoreDocId: String = ""
)
