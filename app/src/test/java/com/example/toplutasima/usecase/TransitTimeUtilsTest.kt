package com.example.toplutasima.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitTimeUtilsTest {

    @Test
    fun `computeYolSuresi returns valid same-day duration`() {
        assertEquals("30", TransitTimeUtils.computeYolSuresi("07:00", "07:30"))
    }

    @Test
    fun `computeYolSuresi handles midnight crossing`() {
        assertEquals("20", TransitTimeUtils.computeYolSuresi("23:50", "00:10"))
    }

    @Test
    fun `computeYolSuresi normalizes negative same-day difference across midnight`() {
        assertEquals("1425", TransitTimeUtils.computeYolSuresi("10:00", "09:45"))
    }

    @Test
    fun `formatTime formats lower boundary`() {
        assertEquals("00:00", TransitTimeUtils.formatTime("0000"))
    }

    @Test
    fun `formatTime formats upper boundary`() {
        assertEquals("23:59", TransitTimeUtils.formatTime("2359"))
    }

    @Test
    fun `formatTime pads single digit hour`() {
        assertEquals("09:00", TransitTimeUtils.formatTime("900"))
    }

    @Test
    fun `formatTime pads single digit minute`() {
        assertEquals("00:05", TransitTimeUtils.formatTime("5"))
    }

    @Test
    fun `toDigits removes separator from lower boundary`() {
        assertEquals("0000", TransitTimeUtils.toDigits("00:00"))
    }

    @Test
    fun `toDigits removes separator from upper boundary`() {
        assertEquals("2359", TransitTimeUtils.toDigits("23:59"))
    }

    @Test
    fun `toDigits preserves single digit hour input shape`() {
        assertEquals("705", TransitTimeUtils.toDigits("7:05"))
    }

    @Test
    fun `stripSeconds removes seconds from time`() {
        assertEquals("07:17", TransitTimeUtils.stripSeconds("07:17:00"))
    }

    @Test
    fun `stripSeconds preserves time without seconds`() {
        assertEquals("07:17", TransitTimeUtils.stripSeconds("07:17"))
    }

    @Test
    fun `stripSeconds preserves blank string`() {
        assertEquals("", TransitTimeUtils.stripSeconds(""))
    }
}
