package com.example.toplutasima.usecase

import java.time.Duration
import java.time.LocalTime

object TransitTimeUtils {
    fun computeYolSuresi(binis: String?, inis: String?): String =
        TransitRecordCalculations.computeYolSuresi(binis, inis)

    fun computeDuration(dep: String, arr: String): Int {
        return try {
            val d = LocalTime.parse(dep.take(5))
            val a = LocalTime.parse(arr.take(5))
            var diff = Duration.between(d, a).toMinutes().toInt()
            if (diff < 0) diff += 24 * 60
            diff
        } catch (_: Exception) {
            0
        }
    }

    fun formatTime(t: String): String {
        if (t.isBlank()) return ""
        val padded = t.padStart(4, '0')
        return "${padded.substring(0, 2)}:${padded.substring(2, 4)}"
    }

    fun toDigits(t: String): String = t.replace(":", "").take(4)

    fun stripSeconds(time: String): String {
        val trimmed = time.trim()
        if (trimmed.isBlank()) return trimmed
        val parts = trimmed.split(":")
        return if (parts.size >= 3) "${parts[0]}:${parts[1]}" else trimmed
    }
}
