package com.example.toplutasima.usecase

import com.example.toplutasima.network.rmv.SegmentDistanceResult
import java.time.LocalDate
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

object TransitRecordCalculations {
    const val FIELD_ORS_DISTANCE_KM = "orsMesafeKm"
    const val FIELD_ORS_DISTANCE_TEXT = "orsMesafeText"
    const val FIELD_RMV_DISTANCE_KM = "rmvMesafeKm"
    const val FIELD_RMV_DISTANCE_METERS = "rmvMesafeMetre"
    const val FIELD_RMV_DISTANCE_TEXT = "rmvMesafeText"
    const val FIELD_RMV_DISTANCE_STATUS = "rmvMesafeDurumu"
    const val FIELD_RMV_DISTANCE_UPDATED_AT = "rmvMesafeGuncellemeTarihi"
    const val FIELD_RMV_API_VERSION = "rmvApiVersion"
    const val FIELD_JOURNEY_REF = "journeyRef"
    const val FIELD_FROM_STOP_ID = "fromStopId"
    const val FIELD_TO_STOP_ID = "toStopId"

    const val RMV_DISTANCE_PENDING = "bekliyor"
    const val RMV_DISTANCE_READY = "hazir"
    const val RMV_DISTANCE_READY_FALLBACK = "hazir_fallback"
    const val RMV_DISTANCE_POLY_UNAVAILABLE = "poly_yok"
    const val RMV_DISTANCE_FAILED = "hata"
    const val RMV_DISTANCE_FAILED_RATE_LIMIT = "hata_rate_limit_429"
    const val RMV_DISTANCE_FAILED_TIMEOUT = "hata_timeout"
    const val RMV_DISTANCE_FAILED_NO_RESULT = "hata_sonuc_yok"
    const val RMV_DISTANCE_FAILED_PARSE_EXCEPTION = "hata_parse_exception"
    const val RMV_DISTANCE_MISSING_REFERENCE = "referans_eksik"
    const val RMV_DISTANCE_INVALID_REFERENCE = "referans_gecersiz"
    const val RMV_DISTANCE_FALLBACK_FAILED = "hata_fallback_basarisiz"

    private val dayNames = mapOf(
        java.time.DayOfWeek.MONDAY to "Pazartesi",
        java.time.DayOfWeek.TUESDAY to "Sal\u0131",
        java.time.DayOfWeek.WEDNESDAY to "\u00c7ar\u015famba",
        java.time.DayOfWeek.THURSDAY to "Per\u015fembe",
        java.time.DayOfWeek.FRIDAY to "Cuma",
        java.time.DayOfWeek.SATURDAY to "Cumartesi",
        java.time.DayOfWeek.SUNDAY to "Pazar"
    )

    fun computeYearMonth(tarih: String): String {
        val parts = tarih.split(".")
        if (parts.size < 3) return ""
        val year = parts[2].padStart(4, '0')
        val month = parts[1].padStart(2, '0')
        return "$year-$month"
    }

    fun computeSortDate(tarih: String): String {
        val parts = tarih.split(".")
        if (parts.size < 3) return ""
        val day = parts[0].padStart(2, '0')
        val month = parts[1].padStart(2, '0')
        val year = parts[2].padStart(4, '0')
        return "$year-$month-$day"
    }

    fun computeGununTipi(tarih: String): String {
        return try {
            val parts = tarih.split(".")
            if (parts.size < 3) return "Hafta \u0130\u00e7i"
            val date = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            val dow = date.dayOfWeek
            if (dow == java.time.DayOfWeek.SATURDAY || dow == java.time.DayOfWeek.SUNDAY) {
                "Hafta Sonu"
            } else {
                "Hafta \u0130\u00e7i"
            }
        } catch (_: Exception) {
            "Hafta \u0130\u00e7i"
        }
    }

    fun computeGun(tarih: String): String {
        return try {
            val parts = tarih.split(".")
            if (parts.size < 3) return ""
            val date = LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
            dayNames[date.dayOfWeek] ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    fun computeGecikme(planlananBinis: String?, gercekBinis: String?): Int {
        if (planlananBinis.isNullOrBlank() || gercekBinis.isNullOrBlank()) return 0
        return try {
            val diff = toMinutes(gercekBinis) - toMinutes(planlananBinis)
            when {
                diff < 0 -> {
                    val overnightDelay = diff + 24 * 60
                    when {
                        -diff <= 120 -> diff
                        overnightDelay <= 120 -> overnightDelay
                        else -> 0
                    }
                }
                diff > 120 -> 0
                else -> diff
            }
        } catch (_: Exception) {
            0
        }
    }

    fun computeYolSuresi(binis: String?, inis: String?): String {
        if (binis.isNullOrBlank() || inis.isNullOrBlank()) return ""
        return try {
            var diff = toMinutes(inis) - toMinutes(binis)
            if (diff < 0) diff += 24 * 60
            diff.toString()
        } catch (_: Exception) {
            ""
        }
    }

    fun parseDistanceKm(value: Any?): Double? {
        val raw = value?.toString()?.trim().orEmpty()
        if (raw.isBlank()) return null
        val normalized = raw
            .replace(Regex("[^0-9,.-]"), "")
            .replace(',', '.')
        return normalized.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    fun formatDistanceKm(distanceKm: Double): String =
        if (distanceKm > 0.0) String.format(Locale.US, "%.2f km", distanceKm) else ""

    fun orsDistanceKm(row: Map<String, *>): Double? =
        parseDistanceKm(row[FIELD_ORS_DISTANCE_KM]) ?: parseDistanceKm(row["mesafe"])

    fun rmvDistanceKm(row: Map<String, *>): Double? =
        parseDistanceKm(row[FIELD_RMV_DISTANCE_KM])

    fun rmvPendingDistanceFields(): LinkedHashMap<String, Any> = linkedMapOf(
        FIELD_RMV_DISTANCE_KM to 0.0,
        FIELD_RMV_DISTANCE_METERS to 0,
        FIELD_RMV_DISTANCE_TEXT to "",
        FIELD_RMV_DISTANCE_STATUS to RMV_DISTANCE_PENDING,
        FIELD_RMV_DISTANCE_UPDATED_AT to "",
        FIELD_RMV_API_VERSION to ""
    )

    fun polyUnavailableDistanceFields(): LinkedHashMap<String, Any?> = linkedMapOf(
        FIELD_RMV_DISTANCE_KM to null,
        FIELD_RMV_DISTANCE_METERS to null,
        FIELD_RMV_DISTANCE_TEXT to null,
        FIELD_RMV_API_VERSION to null,
        FIELD_RMV_DISTANCE_STATUS to RMV_DISTANCE_POLY_UNAVAILABLE
    )

    fun calculatedDistanceFields(
        distanceKm: Double,
        resetRmvDistance: Boolean = false
    ): LinkedHashMap<String, Any> {
        val fields = linkedMapOf<String, Any>(
            FIELD_ORS_DISTANCE_KM to if (distanceKm > 0.0) distanceKm else 0.0,
            FIELD_ORS_DISTANCE_TEXT to formatDistanceKm(distanceKm)
        )
        if (resetRmvDistance) fields.putAll(rmvPendingDistanceFields())
        return fields
    }

    fun calculatedDistanceFields(
        result: SegmentDistanceResult
    ): LinkedHashMap<String, Any> {
        val apiDistanceKm = result.apiDistanceKm?.takeIf { it > 0.0 } ?: 0.0
        val polyDistanceKm = result.polyDistanceKm?.takeIf { it > 0.0 }
        val fields = linkedMapOf<String, Any>(
            FIELD_ORS_DISTANCE_KM to apiDistanceKm,
            FIELD_ORS_DISTANCE_TEXT to formatDistanceKm(apiDistanceKm)
        )
        if (polyDistanceKm != null) {
            fields[FIELD_RMV_DISTANCE_KM] = polyDistanceKm
            fields[FIELD_RMV_DISTANCE_METERS] = (polyDistanceKm * 1000.0).roundToInt()
            fields[FIELD_RMV_DISTANCE_TEXT] = formatDistanceKm(polyDistanceKm)
            fields[FIELD_RMV_DISTANCE_STATUS] = RMV_DISTANCE_READY
        } else {
            fields[FIELD_RMV_DISTANCE_KM] = 0.0
            fields[FIELD_RMV_DISTANCE_METERS] = 0
            fields[FIELD_RMV_DISTANCE_TEXT] = ""
            fields[FIELD_RMV_DISTANCE_STATUS] = RMV_DISTANCE_FAILED
        }
        fields[FIELD_RMV_DISTANCE_UPDATED_AT] = ""
        fields[FIELD_RMV_API_VERSION] = ""
        return fields
    }

    fun haversineMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val radiusMeters = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return radiusMeters * 2 * asin(sqrt(a))
    }

    fun haversineKm(p1: Pair<Double, Double>, p2: Pair<Double, Double>): Double =
        haversineMeters(p1.first, p1.second, p2.first, p2.second) / 1000.0

    private fun toMinutes(time: String): Int {
        val parts = time.trim().split(":")
        if (parts.size < 2) return 0
        return (parts[0].toIntOrNull() ?: 0) * 60 + (parts[1].toIntOrNull() ?: 0)
    }
}
