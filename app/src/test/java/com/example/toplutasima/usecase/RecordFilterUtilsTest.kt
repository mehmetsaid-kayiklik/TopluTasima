package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.viewmodel.records.DayGroup
import com.example.toplutasima.viewmodel.records.RecordRowUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordFilterUtilsTest {

    @Test
    fun `filterDayGroups filters by line name`() {
        val filtered = RecordFilterUtils.filterDayGroups(
            dayGroups = sampleGroups(),
            filter = RecordFilterState(searchQuery = "x26")
        )

        assertEquals(1, RecordFilterUtils.countFilteredRecords(filtered))
        assertEquals("X26", filtered.single().trips.single().line)
    }

    @Test
    fun `filterDayGroups filters by date range`() {
        val filtered = RecordFilterUtils.filterDayGroups(
            dayGroups = sampleGroups(),
            filter = RecordFilterState(
                startDate = "22.05.2026",
                endDate = "23.05.2026"
            )
        )

        assertEquals(1, RecordFilterUtils.countFilteredRecords(filtered))
        assertEquals("22.05.2026", filtered.single().date)
    }

    @Test
    fun `filterDayGroups applies multiple filters together`() {
        val filtered = RecordFilterUtils.filterDayGroups(
            dayGroups = sampleGroups(),
            filter = RecordFilterState(
                searchQuery = "s5",
                vehicleType = VehicleType.SBAHN.key,
                minDelay = 5,
                maxDelay = 10,
                stopName = "bad homburg",
                startDate = "22.05.2026",
                endDate = "22.05.2026"
            )
        )

        assertEquals(1, RecordFilterUtils.countFilteredRecords(filtered))
        val trip = filtered.single().trips.single()
        assertEquals("S5", trip.line)
        assertEquals("Bad Homburg", trip.alightingStop)
    }

    private fun sampleGroups(): List<DayGroup> = listOf(
        DayGroup(
            date = "21.05.2026",
            dayName = "Perşembe",
            trips = listOf(
                row(
                    id = "x26-1",
                    date = "21.05.2026",
                    day = "Perşembe",
                    type = VehicleType.BUS.key,
                    line = "X26",
                    boardingStop = "Frankfurt Flughafen Terminal 1",
                    alightingStop = "Frankfurt Südbahnhof",
                    delay = "3"
                )
            )
        ),
        DayGroup(
            date = "22.05.2026",
            dayName = "Cuma",
            trips = listOf(
                row(
                    id = "s5-1",
                    date = "22.05.2026",
                    day = "Cuma",
                    type = VehicleType.SBAHN.key,
                    line = "S5",
                    boardingStop = "Frankfurt Hauptbahnhof",
                    alightingStop = "Bad Homburg",
                    delay = "8"
                )
            )
        ),
        DayGroup(
            date = "24.05.2026",
            dayName = "Pazar",
            trips = listOf(
                row(
                    id = "u4-1",
                    date = "24.05.2026",
                    day = "Pazar",
                    type = VehicleType.UBAHN.key,
                    line = "U4",
                    boardingStop = "Bockenheimer Warte",
                    alightingStop = "Konstablerwache",
                    delay = "0"
                )
            )
        )
    )

    private fun row(
        id: String,
        date: String,
        day: String,
        type: String,
        line: String,
        boardingStop: String,
        alightingStop: String,
        delay: String
    ): RecordRowUiModel = RecordRowUiModel(
        id = id,
        date = date,
        day = day,
        type = type,
        typeDisplay = type,
        line = line,
        direction = "Friedrichsdorf",
        boardingStop = boardingStop,
        plannedDep = "07:10",
        actualDep = "07:18",
        delay = delay,
        alightingStop = alightingStop,
        plannedArr = "07:32",
        actualArr = "07:40",
        dayType = "Hafta İçi",
        weather = "Sonne",
        seated = SeatingStatus.YES.key,
        plannedDuration = "22",
        actualDuration = "30",
        note = "",
        ticketControl = TicketStatus.DID_NOT.key,
        distance = "18.40 km",
        orsDistance = "18.40 km",
        rmvDistance = "17.90 km",
        rmvDistanceStatus = TransitRecordCalculations.RMV_DISTANCE_READY,
        stopCount = "7",
        originalRecord = emptyMap()
    )
}
