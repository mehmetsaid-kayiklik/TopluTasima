package com.example.toplutasima

import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.usecase.HeatmapData
import com.example.toplutasima.usecase.ReportCardUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCardUtilsTest {
    @Test
    fun `build creates monthly report from summary`() {
        val cards = ReportCardUtils.build(
            summary = summaryData(),
            heatmap = null
        )

        assertEquals(42, cards.monthly.totalTrips)
        assertEquals("S8", cards.monthly.topLine)
        assertEquals("12.05.2026", cards.monthly.busiestDay)
        assertEquals(0, cards.weeks.size)
    }

    @Test
    fun `build exposes all week ranges from heatmap`() {
        val cards = ReportCardUtils.build(
            summary = summaryData(),
            heatmap = HeatmapData(
                year = 2026,
                month = 5,
                dailyCounts = mapOf(1 to 2, 2 to 1, 8 to 4, 9 to 3, 10 to 1),
                currentStreak = 0,
                longestStreak = 3,
                activeDays = 5,
                totalDays = 31,
                maxDailyCount = 4,
                dailyAvgDelay = mapOf(8 to 6, 9 to 3, 10 to 0),
                dailyDistanceKm = mapOf(1 to 6.25, 2 to 3.75, 8 to 12.345, 9 to 8.0)
            )
        )

        assertEquals(5, cards.weeks.size)
        val firstWeek = cards.weeks[0]
        assertEquals(1, firstWeek.weekNumber)
        assertEquals(1, firstWeek.startDay)
        assertEquals(7, firstWeek.endDay)
        assertEquals(3, firstWeek.trips)
        assertEquals(10.0, firstWeek.totalDistance, 0.0)

        val weekly = cards.weeks[1]
        assertEquals(2, weekly.weekNumber)
        assertEquals(8, weekly.startDay)
        assertEquals(14, weekly.endDay)
        assertEquals(8, weekly.trips)
        assertEquals(3, weekly.activeDays)
        assertEquals(8, weekly.busiestDay)
        assertEquals(4, weekly.busiestDayTrips)
        assertEquals(4.125, weekly.avgDelay, 0.001)
        assertEquals(20.35, weekly.totalDistance, 0.0)
    }

    private fun summaryData(): SummaryData = SummaryData(
        totalTrips = 42,
        seatedCount = 20,
        ticketControlCount = 3,
        types = emptyMap(),
        freqLine = "S8",
        freqFrom = "A",
        freqTo = "B",
        days = emptyMap(),
        totalPlannedMin = 1200,
        totalActualMin = 1230,
        maxDelay = 18,
        totalDelay = 90,
        avgDelay = 2.1,
        punctualityRates = emptyMap(),
        recordLongestDay = "12.05.2026",
        recordLongestDayMin = 180,
        recordMostTripsDay = "12.05.2026",
        recordMostTripsCount = 7,
        recordMostDelayedLine = "S8",
        recordMostDelayedLineMin = 18,
        totalDistanceKm = 81.5
    )
}
