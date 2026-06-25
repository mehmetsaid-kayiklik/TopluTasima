package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.Segment
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import com.example.toplutasima.repository.TripRecordMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRecordMapperTest {

    @Test
    fun `buildSegmentData maps segment list to firestore maps`() {
        val segments = listOf(
            x26Segment(),
            s5Segment()
        )

        val maps = segments.mapIndexed { index, segment ->
            TripRecordMapper.buildSegmentData(
                id = "trip-$index",
                date = "22.05.2026",
                seg = segment,
                havaDurumu = "Sonne",
                oturabildim = true,
                biletKontrolu = false,
                note = "RMV sample"
            )
        }

        assertEquals(2, maps.size)
        assertEquals("X26", maps[0]["hat"])
        assertEquals("S5", maps[1]["hat"])
        assertEquals("trip-0", maps[0]["id"])
        assertEquals("trip-1", maps[1]["id"])
    }

    @Test
    fun `buildSegmentData maps segment to firestore fields`() {
        val data = TripRecordMapper.buildSegmentData(
            id = "trip-1",
            date = "22.05.2026",
            seg = s5Segment(),
            havaDurumu = "Yağmurlu",
            oturabildim = true,
            biletKontrolu = true,
            note = "Yoğun ama sorunsuz",
            seatmateUuid = "seatmate-1"
        )

        assertEquals("22.05.2026", data["tarih"])
        assertEquals("Cuma", data["gun"])
        assertEquals(VehicleType.SBAHN.key, data["tur"])
        assertEquals("S5", data["hat"])
        assertEquals("Friedrichsdorf", data["yon"])
        assertEquals("Frankfurt Hauptbahnhof", data["binisDuragi"])
        assertEquals("18:22", data["planlananBinis"])
        assertEquals("Bad Homburg", data["inisDuragi"])
        assertEquals("18:44", data["planlananInis"])
        assertEquals("Hafta İçi", data["gununTipi"])
        assertEquals("Yağmurlu", data["havaDurumu"])
        assertEquals(SeatingStatus.YES.key, data["oturabildimMi"])
        assertEquals("22", data["planlananYolSuresi"])
        assertEquals(TicketStatus.HAPPENED.key, data["biletKontrolü"])
        assertEquals("Yoğun ama sorunsuz", data["not"])
        assertEquals("seatmate-1", data["seatmateUuid"])
        assertEquals("18.40 km", data["mesafe"])
        assertEquals(18.40, data[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM] as Double, 0.0)
        assertEquals("18.40 km", data[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT])
        assertEquals(17.90, data[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM] as Double, 0.0)
        assertEquals("17.90 km", data[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT])
        assertEquals(TransitRecordCalculations.RMV_DISTANCE_READY, data[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS])
        assertEquals("journey-s5", data[TransitRecordCalculations.FIELD_JOURNEY_REF])
        assertEquals("3000010", data[TransitRecordCalculations.FIELD_FROM_STOP_ID])
        assertEquals("3001234", data[TransitRecordCalculations.FIELD_TO_STOP_ID])
        assertEquals("7", data["durakSayisi"])
        assertEquals("trip-1", data["id"])
        assertEquals("2026-05", data["yearMonth"])
        assertEquals("2026-05-22", data["sortDate"])
    }

    @Test
    fun `buildSegmentData handles empty segment list`() {
        val maps = emptyList<Segment>().mapIndexed { index, segment ->
            TripRecordMapper.buildSegmentData(
                id = "trip-$index",
                date = "22.05.2026",
                seg = segment,
                havaDurumu = "",
                oturabildim = false,
                biletKontrolu = false,
                note = ""
            )
        }

        assertTrue(maps.isEmpty())
    }

    @Test
    fun `buildSegmentData uses fallback values for segment with missing fields`() {
        val data = TripRecordMapper.buildSegmentData(
            id = "missing-1",
            date = "22.05.2026",
            seg = Segment(
                typeTr = "",
                line = "",
                direction = "",
                fromStop = "",
                toStop = "",
                dep = "",
                arr = ""
            ),
            havaDurumu = "",
            oturabildim = false,
            biletKontrolu = false,
            note = ""
        )

        assertEquals("", data["tur"])
        assertEquals("", data["hat"])
        assertEquals("", data["binisDuragi"])
        assertEquals("", data["planlananBinis"])
        assertEquals("", data["inisDuragi"])
        assertEquals("", data["planlananInis"])
        assertEquals("", data["planlananYolSuresi"])
        assertEquals(SeatingStatus.NO.key, data["oturabildimMi"])
        assertEquals(TicketStatus.DID_NOT.key, data["biletKontrolü"])
        assertEquals("", data["mesafe"])
        assertEquals(0.0, data[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM] as Double, 0.0)
        assertEquals("", data[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT])
        assertEquals("", data["durakSayisi"])
    }

    private fun s5Segment(): Segment = Segment(
        typeTr = VehicleType.SBAHN.key,
        line = "S5",
        direction = "Friedrichsdorf",
        fromStop = "Frankfurt Hauptbahnhof",
        toStop = "Bad Homburg",
        dep = "18:22:00",
        arr = "18:44:00",
        distanceKm = 18.40,
        polyDistanceKm = 17.90,
        stopCount = 7,
        stopNames = listOf("Frankfurt Hauptbahnhof", "Frankfurt West", "Bad Homburg"),
        stopTimes = listOf("18:22", "18:31", "18:44"),
        journeyRef = "journey-s5",
        fromStopId = "3000010",
        toStopId = "3001234"
    )

    private fun x26Segment(): Segment = Segment(
        typeTr = VehicleType.BUS.key,
        line = "X26",
        direction = "Wiesbaden Hauptbahnhof",
        fromStop = "Frankfurt Flughafen Terminal 1",
        toStop = "Frankfurt Südbahnhof",
        dep = "07:10:00",
        arr = "07:45:00",
        distanceKm = 14.25,
        polyDistanceKm = 13.80,
        stopCount = 5,
        journeyRef = "journey-x26",
        fromStopId = "airport-1",
        toStopId = "south-1"
    )
}
