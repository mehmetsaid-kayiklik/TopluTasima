package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.VehicleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordRowMapperTest {

    @Test
    fun `fromFirestoreRecord maps firestore record to row model`() {
        val record = fullRecord()

        val row = RecordRowMapper.fromFirestoreRecord(
            rec = record,
            profileId = "profile-1",
            profileName = "Mehmet",
            seatmateNote = "S-Bahn regular"
        )

        assertEquals("doc-1", row.id)
        assertEquals("22.05.2026", row.date)
        assertEquals("Cuma", row.day)
        assertEquals(VehicleType.SBAHN.key, row.type)
        assertEquals("🚆 S-Bahn", row.typeDisplay)
        assertEquals("S5", row.line)
        assertEquals("Friedrichsdorf", row.direction)
        assertEquals("Frankfurt Hauptbahnhof", row.boardingStop)
        assertEquals("18:22", row.plannedDep)
        assertEquals("18:30", row.actualDep)
        assertEquals("8", row.delay)
        assertEquals("Bad Homburg", row.alightingStop)
        assertEquals("18:44", row.plannedArr)
        assertEquals("19:00", row.actualArr)
        assertEquals("Hafta İçi", row.dayType)
        assertEquals("Yağmurlu", row.weather)
        assertEquals(SeatingStatus.YES.key, row.seated)
        assertEquals("22", row.plannedDuration)
        assertEquals("30", row.actualDuration)
        assertEquals("Yoğun ama sorunsuz", row.note)
        assertEquals(TicketStatus.HAPPENED.key, row.ticketControl)
        assertEquals("18.40 km", row.distance)
        assertEquals("18.40 km", row.orsDistance)
        assertEquals("17.90 km", row.rmvDistance)
        assertEquals(TransitRecordCalculations.RMV_DISTANCE_READY, row.rmvDistanceStatus)
        assertEquals("7", row.stopCount)
        assertEquals("profile-1", row.profileId)
        assertEquals("Mehmet", row.profileName)
        assertEquals("S-Bahn regular", row.seatmateNote)
        assertEquals(record, row.originalRecord)
    }

    @Test
    fun `fromFirestoreRecord uses fallback values for missing fields`() {
        val row = RecordRowMapper.fromFirestoreRecord(mapOf("id" to "local-1"))

        assertEquals("local-1", row.id)
        assertEquals("", row.date)
        assertEquals("", row.day)
        assertEquals("", row.type)
        assertEquals("🚌 ", row.typeDisplay)
        assertEquals("", row.line)
        assertEquals("", row.boardingStop)
        assertEquals("", row.orsDistance)
        assertEquals("", row.rmvDistance)
        assertEquals("", row.profileId)
        assertEquals("", row.profileName)
        assertTrue(row.originalRecord.containsKey("id"))
    }

    @Test
    fun `fromFirestoreRecord integrates vehicle icon for vehicle type`() {
        val row = RecordRowMapper.fromFirestoreRecord(
            mapOf(
                "id" to "u4-1",
                "tur" to VehicleType.UBAHN.key
            )
        )

        assertEquals("🚇 U-Bahn", row.typeDisplay)
    }

    private fun fullRecord(): Map<String, Any> = mapOf(
        "firestoreDocId" to "doc-1",
        "id" to "local-1",
        "tarih" to "22.05.2026",
        "gun" to "Cuma",
        "tur" to VehicleType.SBAHN.key,
        "hat" to "S5",
        "yon" to "Friedrichsdorf",
        "binisDuragi" to "Frankfurt Hauptbahnhof",
        "planlananBinis" to "18:22",
        "gercekBinis" to "18:30",
        "gecikme" to 8,
        "inisDuragi" to "Bad Homburg",
        "planlananInis" to "18:44",
        "gercekInis" to "19:00",
        "gununTipi" to "Hafta İçi",
        "havaDurumu" to "Yağmurlu",
        "oturabildimMi" to SeatingStatus.YES.key,
        "planlananYolSuresi" to 22,
        "gercekYolSuresi" to 30,
        "not" to "Yoğun ama sorunsuz",
        "biletKontrolü" to TicketStatus.HAPPENED.key,
        "mesafe" to "18.40 km",
        TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT to "18.40 km",
        TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT to "17.90 km",
        TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS to TransitRecordCalculations.RMV_DISTANCE_READY,
        "durakSayisi" to 7
    )
}
