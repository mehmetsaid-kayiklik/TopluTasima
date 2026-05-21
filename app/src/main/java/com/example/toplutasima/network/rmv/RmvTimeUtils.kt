package com.example.toplutasima.network.rmv

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object RmvTimeUtils {
    fun formatTimeDigits(digits: String): String {
        val padded = digits.padStart(4, '0')
        return "${padded.substring(0, 2)}:${padded.substring(2, 4)}"
    }

    fun normalizeRmvClock(raw: String): String {
        val value = raw.trim()
        if (value.isBlank()) return ""
        val colonTime = Regex("\\d{1,2}:\\d{2}").find(value)?.value
        if (colonTime != null) {
            val parts = colonTime.split(":")
            return "${parts[0].padStart(2, '0')}:${parts[1]}"
        }
        val digits = value.filter { it.isDigit() }
        if (digits.length >= 4) return "${digits.take(2)}:${digits.drop(2).take(2)}"
        return value.take(5)
    }

    fun convertToApiDate(uiDate: String): String =
        LocalDate.parse(uiDate.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            .format(DateTimeFormatter.ISO_DATE)

    fun diffMinutesFlexible(dep: String, arr: String): Int {
        val d = parseFlexibleTime(dep) ?: return 0
        val a = parseFlexibleTime(arr) ?: return 0
        var diff = Duration.between(d, a).toMinutes().toInt()
        if (diff < 0) diff += 24 * 60
        return diff
    }

    private fun parseFlexibleTime(time: String): LocalTime? {
        val trimmed = time.trim()
        return try {
            when {
                trimmed.length >= 8 -> LocalTime.parse(trimmed.substring(0, 8))
                trimmed.length >= 5 -> LocalTime.parse(trimmed.substring(0, 5))
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
