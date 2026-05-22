package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class HeatmapUtilsTest {

    @Test
    fun `buildHeatmapData aggregates daily counts and metrics`() {
        val data = HeatmapUtils.buildHeatmapData(
            trips = listOf(
                trip("22.05.2026", delay = 4, ticket = TicketStatus.HAPPENED.key, seated = SeatingStatus.YES.key),
                trip("22.05.2026", delay = 8, ticket = TicketStatus.DID_NOT.key, seated = SeatingStatus.NO.key),
                trip("23.05.2026", delay = 0, ticket = TicketStatus.HAPPENED.key, seated = SeatingStatus.YES.key),
                trip("01.06.2026", delay = 12, ticket = TicketStatus.HAPPENED.key, seated = SeatingStatus.YES.key)
            ),
            year = 2026,
            month = 5
        )

        assertEquals(2026, data.year)
        assertEquals(5, data.month)
        assertEquals(31, data.totalDays)
        assertEquals(2, data.activeDays)
        assertEquals(2, data.maxDailyCount)
        assertEquals(2, data.dailyCounts[22])
        assertEquals(1, data.dailyCounts[23])
        assertEquals(6, data.dailyAvgDelay[22])
        assertEquals(1, data.dailyTicketCounts[22])
        assertEquals(1, data.dailyTicketCounts[23])
        assertEquals(1, data.dailySeatedCounts[22])
        assertEquals(1, data.dailySeatedCounts[23])
    }

    @Test
    fun `buildHeatmapData ignores invalid dates and other months`() {
        val data = HeatmapUtils.buildHeatmapData(
            trips = listOf(
                trip("bad-date"),
                trip("01.06.2026")
            ),
            year = 2026,
            month = 5
        )

        assertEquals(emptyMap<Int, Int>(), data.dailyCounts)
        assertEquals(0, data.activeDays)
        assertEquals(0, data.maxDailyCount)
        assertEquals(31, data.totalDays)
    }

    @Test
    fun `computeStreaks returns zero for empty trips`() {
        assertEquals(0 to 0, HeatmapUtils.computeStreaks(emptyList()))
    }

    @Test
    fun `computeStreaks calculates current and longest streak`() {
        val today = LocalDate.now()
        val trips = listOf(
            trip(today.minusDays(4).toRecordDate()),
            trip(today.minusDays(2).toRecordDate()),
            trip(today.minusDays(1).toRecordDate()),
            trip(today.toRecordDate())
        )

        assertEquals(3 to 3, HeatmapUtils.computeStreaks(trips))
    }

    @Test
    fun `intensityLevel returns expected buckets`() {
        assertEquals(0, HeatmapUtils.intensityLevel(0, 10))
        assertEquals(1, HeatmapUtils.intensityLevel(2, 10))
        assertEquals(2, HeatmapUtils.intensityLevel(5, 10))
        assertEquals(3, HeatmapUtils.intensityLevel(7, 10))
        assertEquals(4, HeatmapUtils.intensityLevel(10, 10))
    }

    private fun trip(
        date: String,
        delay: Int = 0,
        ticket: String = TicketStatus.DID_NOT.key,
        seated: String = SeatingStatus.NO.key
    ): Map<String, Any> = mapOf(
        "tarih" to date,
        "gecikme" to delay,
        "biletKontrolü" to ticket,
        "oturabildimMi" to seated
    )

    private fun LocalDate.toRecordDate(): String =
        String.format(Locale.US, "%02d.%02d.%04d", dayOfMonth, monthValue, year)
}
