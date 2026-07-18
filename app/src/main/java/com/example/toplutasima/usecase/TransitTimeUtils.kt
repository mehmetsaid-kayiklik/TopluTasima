package com.example.toplutasima.usecase

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object TransitTimeUtils {
    private val flexibleTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

    fun computeYolSuresi(binis: String?, inis: String?): String =
        TransitRecordCalculations.computeYolSuresi(binis, inis)

    fun computeDuration(dep: String, arr: String): Int {
        val departureMinutes = parseMinutesOrNull(dep) ?: return 0
        val arrivalMinutes = parseMinutesOrNull(arr) ?: return 0
        var diff = arrivalMinutes - departureMinutes
        if (diff < 0) diff += 24 * 60
        return diff
    }

    fun formatTime(t: String): String {
        val trimmed = t.trim()
        if (trimmed.isBlank()) return ""
        parseMinutesOrNull(trimmed)?.let { minutes ->
            return "%02d:%02d".format(minutes / 60, minutes % 60)
        }
        val padded = trimmed.filter(Char::isDigit).take(4).padStart(4, '0')
        return "${padded.substring(0, 2)}:${padded.substring(2, 4)}"
    }

    fun toDigits(t: String): String = t.replace(":", "").take(4)

    fun stripSeconds(time: String): String {
        val trimmed = time.trim()
        if (trimmed.isBlank()) return trimmed
        val parts = trimmed.split(":")
        return if (parts.size >= 3) "${parts[0]}:${parts[1]}" else trimmed
    }

    /** HH:mm ve H:mm transit saatlerini doğrulayarak gün içindeki dakikaya çevirir. */
    fun parseMinutesOrNull(time: String?): Int? {
        if (time.isNullOrBlank()) return null
        return try {
            LocalTime.parse(stripSeconds(time), flexibleTimeFormatter).toSecondOfDay() / 60
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
