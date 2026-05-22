package com.example.toplutasima.usecase

import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.ui.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthComparisonUtilsTest {

    @Test
    fun `previousMonthKey returns previous month when available`() {
        val previous = MonthComparisonUtils.previousMonthKey(
            currentSheet = "Mayıs 2026",
            sheetNames = listOf("Tümü", "Nisan 2026", "Mayıs 2026")
        )

        assertEquals("Nisan 2026", previous)
    }

    @Test
    fun `previousMonthKey returns null for all sheet`() {
        val previous = MonthComparisonUtils.previousMonthKey(
            currentSheet = "Tümü",
            sheetNames = listOf("Tümü", "Nisan 2026", "Mayıs 2026")
        )

        assertNull(previous)
    }

    @Test
    fun `previousMonthKey returns null for first month`() {
        val previous = MonthComparisonUtils.previousMonthKey(
            currentSheet = "Nisan 2026",
            sheetNames = listOf("Tümü", "Nisan 2026", "Mayıs 2026")
        )

        assertNull(previous)
    }

    @Test
    fun `computeDeltas calculates increases and delay improvements`() {
        val deltas = MonthComparisonUtils.computeDeltas(
            current = summary(totalTrips = 12, totalDelay = 8, avgDelay = 1.5, maxDelay = 6),
            previous = summary(totalTrips = 10, totalDelay = 12, avgDelay = 2.0, maxDelay = 9),
            lang = AppLanguage.TR
        )

        val tripDelta = deltas[0]
        assertEquals("12", tripDelta.currentValue)
        assertEquals("10", tripDelta.previousValue)
        assertEquals("+2", tripDelta.deltaText)
        assertTrue(tripDelta.isPositive)
        assertFalse(tripDelta.isNeutral)

        val delayDelta = deltas[1]
        assertEquals("8 Dakika", delayDelta.currentValue)
        assertEquals("12 Dakika", delayDelta.previousValue)
        assertEquals("-4", delayDelta.deltaText)
        assertTrue(delayDelta.isPositive)
    }

    private fun summary(
        totalTrips: Int = 0,
        seatedCount: Int = 0,
        totalDelay: Int = 0,
        avgDelay: Double = 0.0,
        maxDelay: Int = 0,
        totalDistanceKm: Double = 0.0,
        totalPlannedMin: Int = 0
    ): SummaryData = SummaryData(
        totalTrips = totalTrips,
        seatedCount = seatedCount,
        ticketControlCount = 0,
        types = emptyMap(),
        freqLine = "-",
        freqFrom = "-",
        freqTo = "-",
        days = emptyMap(),
        totalPlannedMin = totalPlannedMin,
        totalActualMin = 0,
        maxDelay = maxDelay,
        totalDelay = totalDelay,
        avgDelay = avgDelay,
        punctualityRates = emptyMap(),
        recordLongestDay = "-",
        recordLongestDayMin = 0,
        recordMostTripsDay = "-",
        recordMostTripsCount = 0,
        recordMostDelayedLine = "-",
        recordMostDelayedLineMin = 0,
        totalDistanceKm = totalDistanceKm
    )
}
