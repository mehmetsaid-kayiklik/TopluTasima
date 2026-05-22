package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.RecordRowUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportUseCaseTest {

    @Test
    fun `generateCsv exports rows with escaped fields`() {
        val csv = ExportUseCase.generateCsv(
            dayGroups = listOf(
                DayGroup(
                    date = "22.05.2026",
                    dayName = "Cuma",
                    trips = listOf(row(note = "needs; \"quote\""))
                )
            ),
            lang = AppLanguage.TR
        )

        assertTrue(csv.startsWith("\uFEFF"))
        assertTrue(csv.contains("Tarih;Gün;Tür"))
        assertTrue(csv.contains("22.05.2026;Cuma;S-Bahn;S5"))
        assertTrue(csv.contains("\"needs; \"\"quote\"\"\""))
    }

    @Test
    fun `generateCsv returns only header for empty groups`() {
        val csv = ExportUseCase.generateCsv(emptyList(), AppLanguage.TR)

        assertEquals(1, csv.trimEnd().lines().size)
        assertTrue(csv.contains("Tarih;Gün;Tür"))
    }

    private fun row(note: String): RecordRowUiModel = RecordRowUiModel(
        id = "s5-1",
        date = "22.05.2026",
        day = "Cuma",
        type = VehicleType.SBAHN.key,
        typeDisplay = "🚆 S-Bahn",
        line = "S5",
        direction = "Friedrichsdorf",
        boardingStop = "Frankfurt Hauptbahnhof",
        plannedDep = "18:22",
        actualDep = "18:30",
        delay = "8",
        alightingStop = "Bad Homburg",
        plannedArr = "18:44",
        actualArr = "19:00",
        dayType = "Hafta İçi",
        weather = "Yağmurlu",
        seated = SeatingStatus.YES.key,
        plannedDuration = "22",
        actualDuration = "30",
        note = note,
        ticketControl = TicketStatus.HAPPENED.key,
        distance = "18.40 km",
        orsDistance = "18.40 km",
        rmvDistance = "17.90 km",
        rmvDistanceStatus = TransitRecordCalculations.RMV_DISTANCE_READY,
        stopCount = "7",
        originalRecord = emptyMap()
    )
}
