package com.example.toplutasima.usecase

import com.example.toplutasima.model.SummaryData

data class MonthlyReportCard(
    val totalTrips: Int,
    val avgDelay: Double,
    val topLine: String,
    val busiestDay: String,
    val busiestDayTrips: Int,
    val totalDistanceKm: Double
)

data class WeeklyReportCard(
    val weekNumber: Int,
    val startDay: Int,
    val endDay: Int,
    val trips: Int,
    val activeDays: Int,
    val avgDelay: Double,
    val busiestDay: Int,
    val busiestDayTrips: Int,
    val totalDistance: Double
)

data class TravelReportCards(
    val monthly: MonthlyReportCard,
    val weeks: List<WeeklyReportCard>
)

object ReportCardUtils {
    fun build(summary: SummaryData, heatmap: HeatmapData?): TravelReportCards =
        TravelReportCards(
            monthly = MonthlyReportCard(
                totalTrips = summary.totalTrips,
                avgDelay = summary.avgDelay,
                topLine = summary.freqLine,
                busiestDay = summary.recordMostTripsDay,
                busiestDayTrips = summary.recordMostTripsCount,
                totalDistanceKm = summary.totalDistanceKm
            ),
            weeks = heatmap?.let { buildWeeks(it) }.orEmpty()
        )

    private fun buildWeeks(heatmap: HeatmapData): List<WeeklyReportCard> {
        return (1..heatmap.totalDays step 7)
            .mapIndexed { index, startDay ->
                val endDay = minOf(startDay + 6, heatmap.totalDays)
                val days = startDay..endDay
                val trips = days.sumOf { day -> heatmap.dailyCounts[day] ?: 0 }
                val activeDays = days.count { day -> (heatmap.dailyCounts[day] ?: 0) > 0 }
                val delayTripCount = days.sumOf { day ->
                    if (heatmap.dailyAvgDelay.containsKey(day)) heatmap.dailyCounts[day] ?: 0 else 0
                }
                val delaySum = days.sumOf { day ->
                    (heatmap.dailyAvgDelay[day] ?: 0) * (heatmap.dailyCounts[day] ?: 0)
                }
                val totalDistance = days.sumOf { day -> heatmap.dailyDistanceKm[day] ?: 0.0 }
                val busiestDay = days.maxByOrNull { day -> heatmap.dailyCounts[day] ?: 0 } ?: startDay

                WeeklyReportCard(
                    weekNumber = index + 1,
                    startDay = startDay,
                    endDay = endDay,
                    trips = trips,
                    activeDays = activeDays,
                    avgDelay = if (delayTripCount > 0) delaySum.toDouble() / delayTripCount else 0.0,
                    busiestDay = busiestDay,
                    busiestDayTrips = heatmap.dailyCounts[busiestDay] ?: 0,
                    totalDistance = Math.round(totalDistance * 100) / 100.0
                )
            }
    }
}
