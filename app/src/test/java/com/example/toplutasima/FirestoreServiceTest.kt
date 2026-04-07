package com.example.toplutasima

import com.example.toplutasima.network.FirestoreService
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FirestoreService helper functions.
 * These test pure functions that don't require Firebase connectivity.
 */
class FirestoreServiceTest {

    // ── computeGecikme ──

    @Test
    fun `computeGecikme normal delay`() {
        assertEquals(5, FirestoreService.computeGecikme("07:00", "07:05"))
    }

    @Test
    fun `computeGecikme no delay`() {
        assertEquals(0, FirestoreService.computeGecikme("07:00", "07:00"))
    }

    @Test
    fun `computeGecikme midnight crossing`() {
        // Planned 23:50, actual 00:05 → 15 min delay (not 0 or negative)
        assertEquals(15, FirestoreService.computeGecikme("23:50", "00:05"))
    }

    @Test
    fun `computeGecikme midnight crossing small`() {
        // Planned 23:58, actual 00:01 → 3 min delay
        assertEquals(3, FirestoreService.computeGecikme("23:58", "00:01"))
    }

    @Test
    fun `computeGecikme caps at 120 min`() {
        // A diff > 120 is treated as data error and returns 0
        assertEquals(0, FirestoreService.computeGecikme("08:00", "12:00"))
    }

    @Test
    fun `computeGecikme null or blank inputs`() {
        assertEquals(0, FirestoreService.computeGecikme(null, "07:05"))
        assertEquals(0, FirestoreService.computeGecikme("07:00", null))
        assertEquals(0, FirestoreService.computeGecikme("", "07:05"))
        assertEquals(0, FirestoreService.computeGecikme("07:00", ""))
    }

    // ── computeYolSuresi ──

    @Test
    fun `computeYolSuresi normal duration`() {
        // Implementation returns plain integer string (no "dk" suffix)
        assertEquals("30", FirestoreService.computeYolSuresi("07:00", "07:30"))
    }

    @Test
    fun `computeYolSuresi midnight crossing`() {
        assertEquals("20", FirestoreService.computeYolSuresi("23:50", "00:10"))
    }

    @Test
    fun `computeYolSuresi blank inputs`() {
        assertEquals("", FirestoreService.computeYolSuresi("", "07:30"))
        assertEquals("", FirestoreService.computeYolSuresi("07:00", ""))
    }

    // ── stripSeconds ──

    @Test
    fun `stripSeconds removes seconds`() {
        assertEquals("07:17", FirestoreService.stripSeconds("07:17:00"))
    }

    @Test
    fun `stripSeconds preserves HH-mm format`() {
        assertEquals("07:17", FirestoreService.stripSeconds("07:17"))
    }

    @Test
    fun `stripSeconds handles blank`() {
        assertEquals("", FirestoreService.stripSeconds(""))
    }

    // ── computeGun ──

    @Test
    fun `computeGun returns correct day name`() {
        // 27.03.2026 is a Friday
        assertEquals("Cuma", FirestoreService.computeGun("27.03.2026"))
    }

    @Test
    fun `computeGun handles invalid date`() {
        assertEquals("", FirestoreService.computeGun("invalid"))
    }

    // ── computeGununTipi ──

    @Test
    fun `computeGununTipi weekday returns Hafta ici`() {
        // 27.03.2026 is a Friday (weekday)
        // Implementation stores "Hafta İçi" / "Hafta Sonu" (capital İ/S) in Firestore;
        // tests match implementation to avoid breaking existing data.
        assertEquals("Hafta İçi", FirestoreService.computeGununTipi("27.03.2026"))
    }

    @Test
    fun `computeGununTipi weekend returns Hafta sonu`() {
        // 28.03.2026 is a Saturday
        assertEquals("Hafta Sonu", FirestoreService.computeGununTipi("28.03.2026"))
    }
}
