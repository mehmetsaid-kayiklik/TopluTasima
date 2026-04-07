package com.example.toplutasima.network

/**
 * Shared utility functions for stop name matching.
 * Used by RmvApiService (journey stop matching) and RmvLogViewModel (direct/transfer detection).
 */
object StopNameUtils {

    /**
     * Normalize a stop name for fuzzy comparison.
     * Handles Hbf/Hauptbahnhof, Bf/Bahnhof, platform suffixes (tief),
     * city abbreviations, parentheses, and extra whitespace.
     */
    fun normalize(name: String): String = name.trim()
        .replace(Regex("\\bHbf\\b", RegexOption.IGNORE_CASE), "Hauptbahnhof")
        .replace(Regex("\\bBf\\b", RegexOption.IGNORE_CASE), "Bahnhof")
        .replace(Regex("\\btief\\b", RegexOption.IGNORE_CASE), "")
        .replace(Regex("[().]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()

    /**
     * Fuzzy match two stop names.
     * Returns true if one contains the other (after normalization),
     * or if all significant words (length > 1) from [query] appear in [candidate].
     */
    fun fuzzyMatch(query: String, candidate: String): Boolean {
        val nq = normalize(query)
        val nc = normalize(candidate)
        if (nq.contains(nc, true) || nc.contains(nq, true)) return true
        val qWords = nq.split(" ").filter { it.length > 1 }.map { it.lowercase() }
        if (qWords.isEmpty()) return false
        val cJoined = nc.lowercase()
        return qWords.all { cJoined.contains(it) }
    }
}
