package com.example.toplutasima.usecase

import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.ui.AppLanguage
import com.example.toplutasima.ui.S
import java.util.Locale

/**
 * Aylık karşılaştırma delta hesaplama modülü.
 * İki SummaryData arasındaki farkları hesaplar ve UI-ready delta listesi üretir.
 */
data class MonthDelta(
    val label: String,
    val currentValue: String,
    val previousValue: String,
    val deltaText: String,       // "+12", "-3"
    val deltaPercent: Double,    // 0.15, -0.08
    val isPositive: Boolean,     // semantik: artış iyi mi?
    val isNeutral: Boolean = false
)

object MonthComparisonUtils {

    fun computeDeltas(current: SummaryData, previous: SummaryData, lang: AppLanguage): List<MonthDelta> {
        val deltas = mutableListOf<MonthDelta>()

        // 1. Trip count (more trips = neutral)
        deltas.add(buildDelta(
            label = S.totalTrips(lang),
            current = current.totalTrips.toDouble(),
            previous = previous.totalTrips.toDouble(),
            format = { it.toInt().toString() },
            neutralIsGood = true
        ))

        // 2. Total delay (less delay = good → decrease is positive)
        deltas.add(buildDelta(
            label = S.totalDelay(lang),
            current = current.totalDelay.toDouble(),
            previous = previous.totalDelay.toDouble(),
            format = { "${it.toInt()} ${S.minutes(lang)}" },
            neutralIsGood = false  // decrease is good
        ))

        // 3. Average delay
        deltas.add(buildDelta(
            label = S.avgDelay(lang),
            current = current.avgDelay,
            previous = previous.avgDelay,
            format = { String.format(Locale.US, "%.1f ${S.minutes(lang)}", it) },
            neutralIsGood = false
        ))

        // 4. Max delay
        deltas.add(buildDelta(
            label = "⚠️ Max ${S.colDelay(lang)}",
            current = current.maxDelay.toDouble(),
            previous = previous.maxDelay.toDouble(),
            format = { "${it.toInt()} ${S.minutes(lang)}" },
            neutralIsGood = false
        ))

        // 5. Total distance (neutral)
        deltas.add(buildDelta(
            label = S.totalDistance(lang),
            current = current.totalDistanceKm,
            previous = previous.totalDistanceKm,
            format = { String.format(Locale.US, "%.1f km", it) },
            neutralIsGood = true
        ))

        // 6. Seated ratio
        val currentSeatedRate = if (current.totalTrips > 0) current.seatedCount.toDouble() / current.totalTrips * 100 else 0.0
        val previousSeatedRate = if (previous.totalTrips > 0) previous.seatedCount.toDouble() / previous.totalTrips * 100 else 0.0
        deltas.add(buildDelta(
            label = "💺 ${S.colSeated(lang)} %",
            current = currentSeatedRate,
            previous = previousSeatedRate,
            format = { String.format(Locale.US, "%.0f%%", it) },
            neutralIsGood = true // more seated = good
        ))

        // 7. Planned vs actual duration
        deltas.add(buildDelta(
            label = S.totalPlanned(lang),
            current = current.totalPlannedMin.toDouble(),
            previous = previous.totalPlannedMin.toDouble(),
            format = { "${it.toInt()} ${S.minutes(lang)}" },
            neutralIsGood = true
        ))

        return deltas
    }

    /** Önceki ay key'ini bulur: "Mart 2025" → sheetNames listesinde bir önceki */
    fun previousMonthKey(currentSheet: String, sheetNames: List<String>): String? {
        if (currentSheet == "Tümü") return null
        val idx = sheetNames.indexOf(currentSheet)
        // idx 0 = "Tümü", idx 1 = ilk ay → önceki ay yok
        return if (idx > 1) sheetNames[idx - 1] else null
    }

    private fun buildDelta(
        label: String,
        current: Double,
        previous: Double,
        format: (Double) -> String,
        neutralIsGood: Boolean
    ): MonthDelta {
        val diff = current - previous
        val percent = if (previous != 0.0) diff / previous else 0.0

        val deltaText = when {
            diff > 0 -> "+${formatDelta(diff)}"
            diff < 0 -> formatDelta(diff)
            else -> "0"
        }

        // Semantik: "artış iyi mi?" bağlamına göre
        val isPositive = if (neutralIsGood) {
            diff >= 0  // artış iyi veya nötr
        } else {
            diff <= 0  // azalma iyi (gecikme gibi)
        }

        return MonthDelta(
            label = label,
            currentValue = format(current),
            previousValue = format(previous),
            deltaText = deltaText,
            deltaPercent = percent,
            isPositive = isPositive,
            isNeutral = diff == 0.0
        )
    }

    private fun formatDelta(value: Double): String {
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }
    }
}
