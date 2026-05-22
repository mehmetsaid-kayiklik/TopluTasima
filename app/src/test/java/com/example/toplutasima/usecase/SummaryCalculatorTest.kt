package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
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
        assertEquals(2, summary.topLines["S5"])
        assertEquals(1, summary.topLines["X26"])
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
        assertEquals(1, summary.types[VehicleType.BUS.key])
        assertEquals(1, summary.types[VehicleType.SBAHN.key])
        assertEquals(0, summary.days["Pazartesi"])
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
