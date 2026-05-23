package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.MonthlyTrendData
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryCalculatorTest {

    @Test
    fun `computeSummary returns zero values for empty records`() {
        val (summary, months) = SummaryCalculator.computeSummary(emptyList())

        assertEquals(0, summary.totalTrips)
        assertEquals(0, summary.seatedCount)
        assertEquals(0, summary.ticketControlCount)
        assertEquals(0, summary.totalPlannedMin)
        assertEquals(0, summary.totalActualMin)
        assertEquals(0, summary.totalDelay)
        assertEquals(0.0, summary.avgDelay, 0.0)
        assertEquals(0.0, summary.totalDistanceKm, 0.0)
        assertEquals("-", summary.freqLine)
        assertTrue(months.isEmpty())
    }

    @Test
    fun `computeSummary aggregates single X26 record`() {
        val record = rmvRecord(
            tarih = "22.05.2026",
            tur = VehicleType.BUS.key,
            hat = "X26",
            binisDuragi = "Frankfurt Flughafen Terminal 1",
            inisDuragi = "Frankfurt Südbahnhof",
            gun = "Cuma",
            planlananBinis = "07:10",
            planlananYolSuresi = 35,
            gercekYolSuresi = 38,
            gecikme = 3,
            orsDistanceKm = 14.25,
            rmvDistanceKm = 14.10,
            oturabildimMi = SeatingStatus.YES.key,
            biletKontrolu = TicketStatus.HAPPENED.key
        )

        val (summary, months) = SummaryCalculator.computeSummary(listOf(record))

        assertEquals(listOf("Mayıs 2026"), months)
        assertEquals(1, summary.totalTrips)
        assertEquals(1, summary.seatedCount)
        assertEquals(1, summary.ticketControlCount)
        assertEquals(1, summary.types[VehicleType.BUS.key])
        assertEquals("X26", summary.freqLine)
        assertEquals("Frankfurt Flughafen Terminal 1", summary.freqFrom)
        assertEquals("Frankfurt Südbahnhof", summary.freqTo)
        assertEquals(1, summary.days["Cuma"])
        assertEquals(35, summary.totalPlannedMin)
        assertEquals(38, summary.totalActualMin)
        assertEquals(3, summary.maxDelay)
        assertEquals(3, summary.totalDelay)
        assertEquals(3.0, summary.avgDelay, 0.0)
        assertEquals(14.25, summary.totalDistanceKm, 0.0)
        assertEquals(14.25, summary.totalOrsDistanceKm, 0.0)
        assertEquals(14.10, summary.totalRmvDistanceKm, 0.0)
    }

    @Test
    fun `computeSummary aggregates multiple RMV records`() {
        val records = listOf(
            rmvRecord(
                tarih = "22.05.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "Frankfurt Flughafen Terminal 1",
                inisDuragi = "Frankfurt Südbahnhof",
                gun = "Cuma",
                planlananBinis = "07:10",
                planlananYolSuresi = 35,
                gercekYolSuresi = 38,
                gecikme = 3,
                orsDistanceKm = 14.25,
                rmvDistanceKm = 14.10
            ),
            rmvRecord(
                tarih = "23.05.2026",
                tur = VehicleType.SBAHN.key,
                hat = "S5",
                binisDuragi = "Frankfurt Hauptbahnhof",
                inisDuragi = "Bad Homburg",
                gun = "Cumartesi",
                planlananBinis = "18:22",
                planlananYolSuresi = 22,
                gercekYolSuresi = 30,
                gecikme = 8,
                orsDistanceKm = 18.40,
                rmvDistanceKm = 17.90
            ),
            rmvRecord(
                tarih = "01.06.2026",
                tur = VehicleType.SBAHN.key,
                hat = "S5",
                binisDuragi = "Bad Homburg",
                inisDuragi = "Friedrichsdorf",
                gun = "Pazartesi",
                planlananBinis = "08:04",
                planlananYolSuresi = 18,
                gercekYolSuresi = 18,
                gecikme = 0,
                orsDistanceKm = 7.50,
                rmvDistanceKm = 7.20
            )
        )

        val (summary, months) = SummaryCalculator.computeSummary(records)

        assertEquals(listOf("Mayıs 2026", "Haziran 2026"), months)
        assertEquals(3, summary.totalTrips)
        assertEquals(1, summary.types[VehicleType.BUS.key])
        assertEquals(2, summary.types[VehicleType.SBAHN.key])
        assertEquals(75, summary.totalPlannedMin)
        assertEquals(86, summary.totalActualMin)
        assertEquals(11, summary.totalDelay)
        assertEquals(8, summary.maxDelay)
        assertEquals(11.0 / 3.0, summary.avgDelay, 0.0001)
        assertEquals(40.15, summary.totalDistanceKm, 0.0)
        assertEquals(40.15, summary.totalOrsDistanceKm, 0.0)
        assertEquals(39.20, summary.totalRmvDistanceKm, 0.0)
        assertEquals(2, summary.weekdayWeekendStats.weekday.trips)
        assertEquals(1, summary.weekdayWeekendStats.weekend.trips)
        assertEquals(1.5, summary.weekdayWeekendStats.weekday.avgDelay, 0.0)
        assertEquals(8.0, summary.weekdayWeekendStats.weekend.avgDelay, 0.0)
        assertEquals(10.88, summary.weekdayWeekendStats.weekday.avgDistanceKm, 0.0)
        assertEquals(18.40, summary.weekdayWeekendStats.weekend.avgDistanceKm, 0.0)
        assertEquals("weekday", summary.weekdayWeekendStats.busiestType)
        assertEquals(2, summary.topLines["S5"])
        assertEquals(1, summary.topLines["X26"])
        
        assertEquals(2, summary.timeSlotStats.size)
        assertEquals("morning", summary.timeSlotStats[0].key)
        assertEquals(2, summary.timeSlotStats[0].trips)
        assertEquals("evening", summary.timeSlotStats[1].key)
        assertEquals(1, summary.timeSlotStats[1].trips)
    }

    @Test
    fun `computeSummary applies month filter`() {
        val records = listOf(
            rmvRecord(
                tarih = "22.05.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "Frankfurt Flughafen Terminal 1",
                inisDuragi = "Frankfurt Südbahnhof",
                gun = "Cuma",
                planlananBinis = "07:10",
                planlananYolSuresi = 35,
                gercekYolSuresi = 38,
                gecikme = 3,
                orsDistanceKm = 14.25,
                rmvDistanceKm = 14.10
            ),
            rmvRecord(
                tarih = "23.05.2026",
                tur = VehicleType.SBAHN.key,
                hat = "S5",
                binisDuragi = "Frankfurt Hauptbahnhof",
                inisDuragi = "Bad Homburg",
                gun = "Cumartesi",
                planlananBinis = "18:22",
                planlananYolSuresi = 22,
                gercekYolSuresi = 30,
                gecikme = 8,
                orsDistanceKm = 18.40,
                rmvDistanceKm = 17.90
            ),
            rmvRecord(
                tarih = "01.06.2026",
                tur = VehicleType.SBAHN.key,
                hat = "S5",
                binisDuragi = "Bad Homburg",
                inisDuragi = "Friedrichsdorf",
                gun = "Pazartesi",
                planlananBinis = "08:04",
                planlananYolSuresi = 18,
                gercekYolSuresi = 18,
                gecikme = 0,
                orsDistanceKm = 7.50,
                rmvDistanceKm = 7.20
            )
        )

        val (summary, months) = SummaryCalculator.computeSummary(records, sheetName = "Mayıs 2026")

        assertEquals(listOf("Mayıs 2026", "Haziran 2026"), months)
        assertEquals(2, summary.totalTrips)
        assertEquals(57, summary.totalPlannedMin)
        assertEquals(68, summary.totalActualMin)
        assertEquals(11, summary.totalDelay)
        assertEquals(8, summary.maxDelay)
        assertEquals(32.65, summary.totalDistanceKm, 0.0)
        assertEquals(32.00, summary.totalRmvDistanceKm, 0.0)
        assertEquals(1, summary.weekdayWeekendStats.weekday.trips)
        assertEquals(1, summary.weekdayWeekendStats.weekend.trips)
        assertEquals(3.0, summary.weekdayWeekendStats.weekday.avgDelay, 0.0)
        assertEquals(8.0, summary.weekdayWeekendStats.weekend.avgDelay, 0.0)
        assertEquals(14.25, summary.weekdayWeekendStats.weekday.avgDistanceKm, 0.0)
        assertEquals(18.40, summary.weekdayWeekendStats.weekend.avgDistanceKm, 0.0)
        assertEquals("equal", summary.weekdayWeekendStats.busiestType)
        assertEquals(1, summary.types[VehicleType.BUS.key])
        assertEquals(1, summary.types[VehicleType.SBAHN.key])
        assertEquals(0, summary.days["Pazartesi"])
    }

    @Test
    fun `computeLineDetail aggregates selected line details for sheet`() {
        val records = listOf(
            rmvRecord(
                tarih = "22.05.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "A",
                inisDuragi = "B",
                gun = "Cuma",
                planlananBinis = "07:10",
                planlananYolSuresi = 10,
                gercekYolSuresi = 10,
                gecikme = 0,
                orsDistanceKm = 1.0,
                rmvDistanceKm = 1.0
            ),
            rmvRecord(
                tarih = "22.05.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "A",
                inisDuragi = "B",
                gun = "Cuma",
                planlananBinis = "08:10",
                planlananYolSuresi = 10,
                gercekYolSuresi = 14,
                gecikme = 4,
                orsDistanceKm = 1.0,
                rmvDistanceKm = 1.0
            ),
            rmvRecord(
                tarih = "23.05.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "A",
                inisDuragi = "B",
                gun = "Cumartesi",
                planlananBinis = "12:10",
                planlananYolSuresi = 10,
                gercekYolSuresi = 18,
                gecikme = 8,
                orsDistanceKm = 1.0,
                rmvDistanceKm = 1.0
            ),
            rmvRecord(
                tarih = "24.05.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "A",
                inisDuragi = "B",
                gun = "Pazar",
                planlananBinis = "18:10",
                planlananYolSuresi = 10,
                gercekYolSuresi = 22,
                gecikme = 12,
                orsDistanceKm = 1.0,
                rmvDistanceKm = 1.0
            ),
            rmvRecord(
                tarih = "24.05.2026",
                tur = VehicleType.BUS.key,
                hat = "85",
                binisDuragi = "A",
                inisDuragi = "B",
                gun = "Pazar",
                planlananBinis = "18:10",
                planlananYolSuresi = 10,
                gercekYolSuresi = 30,
                gecikme = 20,
                orsDistanceKm = 1.0,
                rmvDistanceKm = 1.0
            ),
            rmvRecord(
                tarih = "01.06.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "A",
                inisDuragi = "B",
                gun = "Pazartesi",
                planlananBinis = "07:10",
                planlananYolSuresi = 10,
                gercekYolSuresi = 40,
                gecikme = 30,
                orsDistanceKm = 1.0,
                rmvDistanceKm = 1.0
            )
        )

        val detail = SummaryCalculator.computeLineDetail(records, "Mayıs 2026", "X26")!!

        assertEquals("X26", detail.line)
        assertEquals(4, detail.trips)
        assertEquals(25, detail.punctualityRate)
        assertEquals(6.0, detail.avgDelay, 0.0)
        assertEquals(12, detail.maxDelay)
        assertEquals(listOf(0, 1, 1, 1, 1), detail.delayBuckets.map { it.count })
        assertEquals(2.0, detail.timeDelayStats.first { it.key == "morning" }.avgDelay, 0.0)
        assertEquals(8.0, detail.timeDelayStats.first { it.key == "noon" }.avgDelay, 0.0)
        assertEquals(12.0, detail.timeDelayStats.first { it.key == "evening" }.avgDelay, 0.0)
        assertEquals(listOf(12, 8, 4), detail.delayedDays.map { it.totalDelay })
    }

    @Test
    fun `computeMonthlyTrend returns trend stats for last 6 months ordered newest to oldest`() {
        val records = listOf(
            rmvRecord(
                tarih = "22.05.2026",
                tur = VehicleType.BUS.key,
                hat = "X26",
                binisDuragi = "A",
                inisDuragi = "B",
                gun = "Cuma",
                planlananBinis = "07:10",
                planlananYolSuresi = 35,
                gercekYolSuresi = 38,
                gecikme = 3,
                orsDistanceKm = 10.0,
                rmvDistanceKm = 10.0
            ),
            rmvRecord(
                tarih = "23.05.2026",
                tur = VehicleType.SBAHN.key,
                hat = "S5",
                binisDuragi = "C",
                inisDuragi = "D",
                gun = "Cumartesi",
                planlananBinis = "18:22",
                planlananYolSuresi = 22,
                gercekYolSuresi = 30,
                gecikme = 8,
                orsDistanceKm = 15.0,
                rmvDistanceKm = 15.0
            ),
            rmvRecord(
                tarih = "10.04.2026",
                tur = VehicleType.SBAHN.key,
                hat = "S5",
                binisDuragi = "E",
                inisDuragi = "F",
                gun = "Çarşamba",
                planlananBinis = "08:04",
                planlananYolSuresi = 18,
                gercekYolSuresi = 18,
                gecikme = 0,
                orsDistanceKm = 5.0,
                rmvDistanceKm = 5.0
            ),
            rmvRecord(
                tarih = "15.12.2025",
                tur = VehicleType.SBAHN.key,
                hat = "S5",
                binisDuragi = "E",
                inisDuragi = "F",
                gun = "Pazartesi",
                planlananBinis = "08:04",
                planlananYolSuresi = 18,
                gercekYolSuresi = 18,
                gecikme = 0,
                orsDistanceKm = 7.5,
                rmvDistanceKm = 7.5
            )
        )

        val (summary, _) = SummaryCalculator.computeSummary(records)
        val trend = summary.monthlyTrend

        assertEquals(6, trend.size)

        assertEquals("May", trend[0].monthName)
        assertEquals(2, trend[0].trips)
        assertEquals(25.0, trend[0].distanceKm, 0.0)

        assertEquals("Nis", trend[1].monthName)
        assertEquals(1, trend[1].trips)
        assertEquals(5.0, trend[1].distanceKm, 0.0)

        assertEquals("Mar", trend[2].monthName)
        assertEquals(0, trend[2].trips)
        assertEquals(0.0, trend[2].distanceKm, 0.0)

        assertEquals("Şub", trend[3].monthName)
        assertEquals(0, trend[3].trips)
        assertEquals(0.0, trend[3].distanceKm, 0.0)

        assertEquals("Oca", trend[4].monthName)
        assertEquals(0, trend[4].trips)
        assertEquals(0.0, trend[4].distanceKm, 0.0)

        assertEquals("Ara", trend[5].monthName)
        assertEquals(1, trend[5].trips)
        assertEquals(7.5, trend[5].distanceKm, 0.0)
    }

    private fun rmvRecord(
        tarih: String,
        tur: String,
        hat: String,
        binisDuragi: String,
        inisDuragi: String,
        gun: String,
        planlananBinis: String,
        planlananYolSuresi: Int,
        gercekYolSuresi: Int,
        gecikme: Int,
        orsDistanceKm: Double,
        rmvDistanceKm: Double,
        oturabildimMi: String = SeatingStatus.NO.key,
        biletKontrolu: String = TicketStatus.DID_NOT.key
    ): Map<String, Any> = mapOf(
        "tarih" to tarih,
        "tur" to tur,
        "hat" to hat,
        "binisDuragi" to binisDuragi,
        "inisDuragi" to inisDuragi,
        "gun" to gun,
        "planlananBinis" to planlananBinis,
        "planlananYolSuresi" to planlananYolSuresi,
        "gercekYolSuresi" to gercekYolSuresi,
        "gecikme" to gecikme,
        TransitRecordCalculations.FIELD_ORS_DISTANCE_KM to orsDistanceKm,
        TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT to TransitRecordCalculations.formatDistanceKm(orsDistanceKm),
        TransitRecordCalculations.FIELD_RMV_DISTANCE_KM to rmvDistanceKm,
        TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT to TransitRecordCalculations.formatDistanceKm(rmvDistanceKm),
        "oturabildimMi" to oturabildimMi,
        "biletKontrolü" to biletKontrolu
    )
}
