package com.example.toplutasima.usecase

import com.example.toplutasima.network.rmv.SegmentDistanceResult
import org.junit.Assert.assertEquals
import org.junit.Test

class TransitRecordCalculationsTest {

    @Test
    fun `computeYearMonth returns year and month for valid date`() {
        assertEquals("2026-05", TransitRecordCalculations.computeYearMonth("22.05.2026"))
    }

    @Test
    fun `computeYearMonth handles january boundary`() {
        assertEquals("2026-01", TransitRecordCalculations.computeYearMonth("01.01.2026"))
    }

    @Test
    fun `computeYearMonth handles december boundary`() {
        assertEquals("2026-12", TransitRecordCalculations.computeYearMonth("31.12.2026"))
    }

    @Test
    fun `computeYearMonth returns blank for invalid format`() {
        assertEquals("", TransitRecordCalculations.computeYearMonth("2026-05-22"))
    }

    @Test
    fun `computeSortDate returns sortable date value`() {
        assertEquals("2026-05-09", TransitRecordCalculations.computeSortDate("09.05.2026"))
    }

    @Test
    fun `computeGununTipi returns weekday for weekday date`() {
        assertEquals("Hafta İçi", TransitRecordCalculations.computeGununTipi("22.05.2026"))
    }

    @Test
    fun `computeGununTipi returns weekend for weekend date`() {
        assertEquals("Hafta Sonu", TransitRecordCalculations.computeGununTipi("24.05.2026"))
    }

    @Test
    fun `computeGununTipi treats public holiday weekday as weekday`() {
        assertEquals("Hafta İçi", TransitRecordCalculations.computeGununTipi("01.05.2026"))
    }

    @Test
    fun `computeGun returns turkish day name`() {
        assertEquals("Cumartesi", TransitRecordCalculations.computeGun("23.05.2026"))
    }

    @Test
    fun `computeGecikme returns positive delay`() {
        assertEquals(5, TransitRecordCalculations.computeGecikme("07:00", "07:05"))
    }

    @Test
    fun `computeGecikme returns zero delay`() {
        assertEquals(0, TransitRecordCalculations.computeGecikme("07:00", "07:00"))
    }

    @Test
    fun `computeGecikme returns negative delay for early departure`() {
        assertEquals(-10, TransitRecordCalculations.computeGecikme("08:00", "07:50"))
    }

    @Test
    fun `computeGecikme handles midnight crossing delay`() {
        assertEquals(3, TransitRecordCalculations.computeGecikme("23:58", "00:01"))
    }

    @Test
    fun `haversineKm returns known equator distance`() {
        val distanceKm = TransitRecordCalculations.haversineKm(0.0 to 0.0, 0.0 to 1.0)

        assertEquals(111.19, distanceKm, 0.01)
    }

    @Test
    fun `haversineMeters returns zero for same coordinate`() {
        val distanceMeters = TransitRecordCalculations.haversineMeters(
            50.107145,
            8.663789,
            50.107145,
            8.663789
        )

        assertEquals(0.0, distanceMeters, 0.0)
    }

    @Test
    fun `orsDistanceKm reads primary ors distance field`() {
        val row = mapOf<String, Any>(
            TransitRecordCalculations.FIELD_ORS_DISTANCE_KM to 14.25,
            "mesafe" to "8.00 km"
        )

        assertEquals(14.25, TransitRecordCalculations.orsDistanceKm(row) ?: 0.0, 0.0)
    }

    @Test
    fun `rmvDistanceKm reads rmv distance field`() {
        val row = mapOf<String, Any>(
            TransitRecordCalculations.FIELD_RMV_DISTANCE_KM to "17,90 km"
        )

        assertEquals(17.90, TransitRecordCalculations.rmvDistanceKm(row) ?: 0.0, 0.0)
    }

    @Test
    fun `calculatedDistanceFields writes ready rmv fields from poly distance`() {
        val fields = TransitRecordCalculations.calculatedDistanceFields(
            SegmentDistanceResult(apiDistanceKm = 8.5, polyDistanceKm = 0.70)
        )

        assertEquals(8.5, fields[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM] as Double, 0.0)
        assertEquals("8.50 km", fields[TransitRecordCalculations.FIELD_ORS_DISTANCE_TEXT])
        assertEquals(0.70, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM] as Double, 0.0)
        assertEquals("0.70 km", fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT])
        assertEquals(700, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS])
        assertEquals(TransitRecordCalculations.RMV_DISTANCE_READY, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS])
    }

    @Test
    fun `calculatedDistanceFields writes failed rmv fields when poly distance is missing`() {
        val fields = TransitRecordCalculations.calculatedDistanceFields(
            SegmentDistanceResult(apiDistanceKm = 8.5, polyDistanceKm = null)
        )

        assertEquals(8.5, fields[TransitRecordCalculations.FIELD_ORS_DISTANCE_KM] as Double, 0.0)
        assertEquals(0.0, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM] as Double, 0.0)
        assertEquals("", fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT])
        assertEquals(0, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS])
        assertEquals(TransitRecordCalculations.RMV_DISTANCE_FAILED, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS])
    }

    @Test
    fun `poly unavailable fields clear rmv values and write terminal status`() {
        val fields = TransitRecordCalculations.polyUnavailableDistanceFields()

        assertEquals(
            setOf(
                TransitRecordCalculations.FIELD_RMV_DISTANCE_KM,
                TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS,
                TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT,
                TransitRecordCalculations.FIELD_RMV_API_VERSION,
                TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS
            ),
            fields.keys
        )
        assertEquals(null, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_KM])
        assertEquals(null, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_METERS])
        assertEquals(null, fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_TEXT])
        assertEquals(null, fields[TransitRecordCalculations.FIELD_RMV_API_VERSION])
        assertEquals(
            TransitRecordCalculations.RMV_DISTANCE_POLY_UNAVAILABLE,
            fields[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS]
        )
    }
}
