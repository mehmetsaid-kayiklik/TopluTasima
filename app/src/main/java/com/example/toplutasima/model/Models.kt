package com.example.toplutasima.model

import androidx.compose.ui.graphics.vector.ImageVector

data class StopOption(val id: String, val name: String)

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
    val journeyRef: String = ""
)

data class TripResult(
    val segments: List<Segment>,
    val overallDep: String,
    val overallArr: String,
    val durationMin: Int
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
    val topLines: Map<String, Int> = emptyMap()
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
