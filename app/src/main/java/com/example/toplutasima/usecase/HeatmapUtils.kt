package com.example.toplutasima.usecase

import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.TicketStatus
import java.time.LocalDate
import java.time.YearMonth

enum class HeatmapMetric { TRIPS, AVG_DELAY, TICKET_CONTROL, SEATED }

/**
 * Isı haritası ve streak hesaplama modülü.
 * Seçili ayın günlük sefer yoğunluğunu ve ardışık gün streak'lerini hesaplar.
 */
data class HeatmapData(
    val year: Int,
    val month: Int,
    val dailyCounts: Map<Int, Int>,   // gün numarası → sefer sayısı
    val currentStreak: Int,
    val longestStreak: Int,
    val activeDays: Int,
    val totalDays: Int,
    val maxDailyCount: Int,
    val dailyAvgDelay: Map<Int, Int> = emptyMap(),
    val dailyTicketCounts: Map<Int, Int> = emptyMap(),
    val dailySeatedCounts: Map<Int, Int> = emptyMap(),
    val maxDailyAvgDelay: Int = 0,
    val maxDailyTicketCount: Int = 0,
    val maxDailySeatedCount: Int = 0
)

object HeatmapUtils {

    /**
     * Seçili ay için heatmap verisi oluşturur.
     * @param trips tüm trip'ler (Map<String, Any> formatında)
     * @param year hedef yıl
     * @param month hedef ay (1-12)
     */
    fun buildHeatmapData(trips: List<Map<String, Any>>, year: Int, month: Int): HeatmapData {
        val dailyCounts = mutableMapOf<Int, Int>()
        val dailyDelaySum = mutableMapOf<Int, Int>()
        val dailyDelayCount = mutableMapOf<Int, Int>()
        val dailyTicketCounts = mutableMapOf<Int, Int>()
        val dailySeatedCounts = mutableMapOf<Int, Int>()
        val ym = YearMonth.of(year, month)
        val totalDays = ym.lengthOfMonth()

        for (trip in trips) {
            val tarih = trip["tarih"]?.toString() ?: continue
            val parts = tarih.split(".")
            if (parts.size < 3) continue
            val d = parts[0].toIntOrNull() ?: continue
            val m = parts[1].toIntOrNull() ?: continue
            val y = parts[2].toIntOrNull() ?: continue
            if (y == year && m == month) {
                dailyCounts[d] = (dailyCounts[d] ?: 0) + 1
                val delay = trip["gecikme"]?.toString()?.toIntOrNull() ?: 0
                dailyDelaySum[d] = (dailyDelaySum[d] ?: 0) + delay
                dailyDelayCount[d] = (dailyDelayCount[d] ?: 0) + 1

                val ticketControl = trip["biletKontrolü"]?.toString()
                    ?: trip["biletKontrolü"]?.toString()
                    ?: ""
                if (ticketControl == TicketStatus.HAPPENED.key) {
                    dailyTicketCounts[d] = (dailyTicketCounts[d] ?: 0) + 1
                }

                val seated = trip["oturabildimMi"]?.toString().orEmpty()
                if (seated == SeatingStatus.YES.key) {
                    dailySeatedCounts[d] = (dailySeatedCounts[d] ?: 0) + 1
                }
            }
        }

        val activeDays = dailyCounts.count { it.value > 0 }
        val maxDailyCount = dailyCounts.values.maxOrNull() ?: 0
        val dailyAvgDelay = dailyDelaySum.mapValues { (day, sum) ->
            val count = dailyDelayCount[day] ?: 1
            Math.round(sum.toDouble() / count).toInt()
        }

        // Streak hesaplaması: tüm tarihleri sıralayarak yapılır
        val (currentStreak, longestStreak) = computeStreaks(trips)

        return HeatmapData(
            year = year,
            month = month,
            dailyCounts = dailyCounts,
            currentStreak = currentStreak,
            longestStreak = longestStreak,
            activeDays = activeDays,
            totalDays = totalDays,
            maxDailyCount = maxDailyCount,
            dailyAvgDelay = dailyAvgDelay,
            dailyTicketCounts = dailyTicketCounts,
            dailySeatedCounts = dailySeatedCounts,
            maxDailyAvgDelay = dailyAvgDelay.values.maxOrNull() ?: 0,
            maxDailyTicketCount = dailyTicketCounts.values.maxOrNull() ?: 0,
            maxDailySeatedCount = dailySeatedCounts.values.maxOrNull() ?: 0
        )
    }

    /**
     * Tüm trip'lerden current ve longest streak hesaplar.
     * @return Pair(currentStreak, longestStreak)
     */
    fun computeStreaks(trips: List<Map<String, Any>>): Pair<Int, Int> {
        // Benzersiz tarihler
        val dates = mutableSetOf<LocalDate>()
        for (trip in trips) {
            val tarih = trip["tarih"]?.toString() ?: continue
            val parts = tarih.split(".")
            if (parts.size < 3) continue
            try {
                val d = parts[0].toInt()
                val m = parts[1].toInt()
                val y = parts[2].toInt()
                dates.add(LocalDate.of(y, m, d))
            } catch (_: Exception) { }
        }

        if (dates.isEmpty()) return Pair(0, 0)

        val sorted = dates.sorted()
        var longestStreak = 1
        var currentRun = 1

        for (i in 1 until sorted.size) {
            if (sorted[i] == sorted[i - 1].plusDays(1)) {
                currentRun++
            } else {
                if (currentRun > longestStreak) longestStreak = currentRun
                currentRun = 1
            }
        }
        if (currentRun > longestStreak) longestStreak = currentRun

        // Current streak: bugünden geriye kaç ardışık gün aktif
        val today = LocalDate.now()
        var currentStreak = 0
        var checkDate = today
        while (checkDate in dates) {
            currentStreak++
            checkDate = checkDate.minusDays(1)
        }
        // Eğer bugün kayıt yoksa dünden geriye kontrol et
        if (currentStreak == 0) {
            checkDate = today.minusDays(1)
            while (checkDate in dates) {
                currentStreak++
                checkDate = checkDate.minusDays(1)
            }
        }

        return Pair(currentStreak, longestStreak)
    }

    /**
     * Renk yoğunluk seviyesi (0-4).
     * 0 = hiç trip yok, 1 = az, 2 = orta, 3 = yüksek, 4 = çok yüksek
     */
    fun intensityLevel(count: Int, maxCount: Int): Int {
        if (count == 0 || maxCount == 0) return 0
        val ratio = count.toFloat() / maxCount
        return when {
            ratio <= 0.25f -> 1
            ratio <= 0.5f -> 2
            ratio <= 0.75f -> 3
            else -> 4
        }
    }
}
