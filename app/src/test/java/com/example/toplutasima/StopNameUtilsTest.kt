package com.example.toplutasima

import com.example.toplutasima.network.StopNameUtils
import org.junit.Test

/**
 * Unit tests for StopNameUtils — shared fuzzy stop name matching.
 */
class StopNameUtilsTest {

    // ── normalize ──

    @Test
    fun `normalize replaces Hbf with Hauptbahnhof`() {
        assertEquals("Frankfurt Hauptbahnhof", StopNameUtils.normalize("Frankfurt Hbf"))
    }

    @Test
    fun `normalize replaces Bf with Bahnhof`() {
        assertEquals("Darmstadt Bahnhof", StopNameUtils.normalize("Darmstadt Bf"))
    }

    @Test
    fun `normalize removes tief suffix`() {
        val result = StopNameUtils.normalize("Frankfurt Hbf tief")
        assertEquals("Frankfurt Hauptbahnhof", result)
    }

    @Test
    fun `normalize removes parentheses and dots`() {
        assertEquals("Frankfurt Main Hauptbahnhof", StopNameUtils.normalize("Frankfurt (Main) Hbf."))
    }

    @Test
    fun `normalize collapses multiple spaces`() {
        assertEquals("A B C", StopNameUtils.normalize("A   B    C"))
    }

    // ── fuzzyMatch ──

    @Test
    fun `fuzzyMatch exact name`() {
        assertTrue(StopNameUtils.fuzzyMatch("Darmstadt Hbf", "Darmstadt Hbf"))
    }

    @Test
    fun `fuzzyMatch Hbf vs Hauptbahnhof`() {
        assertTrue(StopNameUtils.fuzzyMatch("Darmstadt Hbf", "Darmstadt Hauptbahnhof"))
    }

    @Test
    fun `fuzzyMatch with tief suffix`() {
        assertTrue(StopNameUtils.fuzzyMatch("Frankfurt (Main) Hbf tief", "Frankfurt (Main) Hauptbahnhof"))
    }

    @Test
    fun `fuzzyMatch contains check`() {
        assertTrue(StopNameUtils.fuzzyMatch("Luisenplatz", "Darmstadt Luisenplatz"))
    }

    @Test
    fun `fuzzyMatch word-based matching`() {
        assertTrue(StopNameUtils.fuzzyMatch("Frankfurt Hbf", "Frankfurt am Main Hauptbahnhof"))
    }

    @Test
    fun `fuzzyMatch completely different names returns false`() {
        assertFalse(StopNameUtils.fuzzyMatch("Berlin Hbf", "München Pasing"))
    }

    @Test
    fun `fuzzyMatch empty strings`() {
        assertTrue(StopNameUtils.fuzzyMatch("", ""))
    }
}
