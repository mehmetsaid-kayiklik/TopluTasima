package com.example.toplutasima.usecase

import com.example.toplutasima.model.DayTypeStats
import com.example.toplutasima.model.DelayBucketStats
import com.example.toplutasima.model.LineReliabilityStats
import com.example.toplutasima.model.MonthlyTrendData
import com.example.toplutasima.model.RoutePairStats
import com.example.toplutasima.model.SeatingStatus
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.model.TicketStatus
import com.example.toplutasima.model.TimeSlotStats
import com.example.toplutasima.model.WeekdayWeekendStats
import java.util.Locale

data class LineDelayBucket(
    val key: String,
    val count: Int
)

data class LineTimeDelayStats(
    val key: String,
    val trips: Int,
    val avgDelay: Double
)

data class LineDelayedDayStats(
    val label: String,
    val trips: Int,
    val totalDelay: Int,
    val avgDelay: Double
)

data class LineDetailStats(
    val line: String,
    val trips: Int,
    val punctualityRate: Int,
    val avgDelay: Double,
    val maxDelay: Int,
    val delayBuckets: List<LineDelayBucket>,
    val timeDelayStats: List<LineTimeDelayStats>,
    val delayedDays: List<LineDelayedDayStats>
)

object SummaryCalculator {
    fun computeSummary(
        allDocs: List<Map<String, Any>>,
        sheetName: String = ALL_SHEET
    ): Pair<SummaryData, List<String>> {
        val monthYearSet = mutableSetOf<Pair<Int, Int>>()
        for (row in allDocs) {
            val tarih = row["tarih"]?.toString() ?: continue
            val parts = tarih.split(".")
            if (parts.size >= 3) {
                val monthNum = parts[1].toIntOrNull() ?: continue
                val year = parts[2].toIntOrNull() ?: continue
                monthYearSet.add(year to monthNum)
            }
        }

        val sortedMonths = monthYearSet
            .sortedWith(compareBy({ it.first }, { it.second }))
            .map { (year, month) ->
                val monthName = MONTH_NAMES[String.format(Locale.US, "%02d", month)] ?: month.toString()
                "$monthName $year"
            }

        val rows = if (sheetName == ALL_SHEET) {
            allDocs
        } else {
            val filterParts = sheetName.split(" ")
            val filterMonthNum = if (filterParts.size >= 2) MONTH_NUMBERS[filterParts[0]] else null
            val filterYear = if (filterParts.size >= 2) filterParts[1] else null
            if (filterMonthNum != null && filterYear != null) {
                allDocs.filter { row ->
                    val tarih = row["tarih"]?.toString() ?: return@filter false
                    val parts = tarih.split(".")
                    parts.size >= 3 && parts[1] == filterMonthNum && parts[2] == filterYear
                }
            } else {
                allDocs
            }
        }

        var totalTrips = 0
        var seatedCount = 0
        var ticketControlCount = 0
        val types = mutableMapOf(
            "Otob\u00fcs" to 0,
            "U-Bahn" to 0,
            "S-Bahn" to 0,
            "Re/Rb" to 0,
            "Fernzug" to 0,
            "Stra\u00dfenbahn" to 0
        )
        val lines = mutableMapOf<String, Int>()
        val fromStops = mutableMapOf<String, Int>()
        val toStops = mutableMapOf<String, Int>()
        val days = mutableMapOf(
            "Pazartesi" to 0,
            "Sal\u0131" to 0,
            "\u00c7ar\u015famba" to 0,
            "Per\u015fembe" to 0,
            "Cuma" to 0,
            "Cumartesi" to 0,
            "Pazar" to 0
        )
        var totalPlanned = 0.0
        var totalActual = 0.0
        var maxDelay = 0.0
        var totalDelay = 0.0
        var delayCount = 0
        val weatherCounts = mutableMapOf<String, Int>()
        var totalDistanceKm = 0.0
        var totalOrsDistanceKm = 0.0
        var totalRmvDistanceKm = 0.0
        val typeOnTime = mutableMapOf<String, Pair<Int, Int>>()
        val dailyTrips = mutableMapOf<String, Int>()
        val dailyDuration = mutableMapOf<String, Double>()
        val lineMaxDelays = mutableMapOf<String, Double>()
        val lineTotalDelays = mutableMapOf<String, Double>()
        val timeSlotTotals = mutableMapOf<String, Triple<Int, Double, Int>>()
        val delayBuckets = linkedMapOf("early" to 0, "zero" to 0, "low" to 0, "medium" to 0, "high" to 0)
        val lineReliabilityAgg = mutableMapOf<String, LineAgg>()
        val routeAgg = mutableMapOf<Pair<String, String>, RouteAgg>()
        val weekdayAgg = DayTypeAgg()
        val weekendAgg = DayTypeAgg()
        var shortestTrip: Pair<String, Int>? = null
        var longestTrip: Pair<String, Int>? = null
        var longestDistanceTrip: Pair<String, Double>? = null

        for (row in rows) {
            val tarih = row["tarih"]?.toString() ?: continue
            if (tarih.isBlank() || tarih.lowercase() == "tarih") continue

            totalTrips++

            val oturabildim = row["oturabildimMi"]?.toString() ?: ""
            if (oturabildim == SeatingStatus.YES.key) seatedCount++

            val biletKontrolu = row["biletKontrol\u00fc"]?.toString() ?: ""
            if (biletKontrolu == TicketStatus.HAPPENED.key) ticketControlCount++

            val type = row["tur"]?.toString() ?: ""
            val line = row["hat"]?.toString() ?: ""
            val from = row["binisDuragi"]?.toString() ?: ""
            val to = row["inisDuragi"]?.toString() ?: ""
            val day = row["gun"]?.toString() ?: ""
            val plannedDep = row["planlananBinis"]?.toString() ?: ""

            if (types.containsKey(type)) types[type] = types[type]!! + 1
            if (line.isNotBlank()) lines[line] = (lines[line] ?: 0) + 1
            if (from.isNotBlank()) fromStops[from] = (fromStops[from] ?: 0) + 1
            if (to.isNotBlank()) toStops[to] = (toStops[to] ?: 0) + 1
            if (day.isNotBlank() && days.containsKey(day)) days[day] = days[day]!! + 1

            val weather = row["havaDurumu"]?.toString() ?: ""
            if (weather.isNotBlank()) weatherCounts[weather] = (weatherCounts[weather] ?: 0) + 1

            val distanceKm = TransitRecordCalculations.orsDistanceKm(row)
            val rmvKm = TransitRecordCalculations.rmvDistanceKm(row)
            if (distanceKm != null) {
                totalDistanceKm += distanceKm
                totalOrsDistanceKm += distanceKm
            }
            if (rmvKm != null &&
                row[TransitRecordCalculations.FIELD_RMV_DISTANCE_STATUS]?.toString() ==
                TransitRecordCalculations.RMV_DISTANCE_READY
            ) {
                totalRmvDistanceKm += rmvKm
            }

            val plannedMin = row["planlananYolSuresi"]?.toString()?.toDoubleOrNull()
            val actualMin = row["gercekYolSuresi"]?.toString()?.toDoubleOrNull()
            val bestDuration = actualMin ?: plannedMin
            val gecikme = row["gecikme"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0

            val dayTypeAgg = if (isWeekendTrip(row)) weekendAgg else weekdayAgg
            dayTypeAgg.trips++
            dayTypeAgg.delay += gecikme.toDouble()
            if (distanceKm != null) {
                dayTypeAgg.distance += distanceKm
                dayTypeAgg.distanceCount++
            }

            if (plannedMin != null) totalPlanned += plannedMin
            if (actualMin != null) {
                totalActual += actualMin
                dailyDuration[tarih] = (dailyDuration[tarih] ?: 0.0) + actualMin
            }

            val routeDisplay = if (from.isNotBlank() && to.isNotBlank()) {
                routeTitle(line, from, to)
            } else {
                line.ifBlank { "-" }
            }
            if (bestDuration != null && bestDuration > 0) {
                val durationInt = Math.round(bestDuration).toInt()
                if (shortestTrip == null || durationInt < shortestTrip!!.second) {
                    shortestTrip = routeDisplay to durationInt
                }
                if (longestTrip == null || durationInt > longestTrip!!.second) {
                    longestTrip = routeDisplay to durationInt
                }
            }
            if (distanceKm != null && distanceKm > 0.0) {
                if (longestDistanceTrip == null || distanceKm > longestDistanceTrip!!.second) {
                    longestDistanceTrip = routeDisplay to distanceKm
                }
            }

            dailyTrips[tarih] = (dailyTrips[tarih] ?: 0) + 1

            val onTime = gecikme <= 3
            when {
                gecikme < 0 -> delayBuckets["early"] = (delayBuckets["early"] ?: 0) + 1
                gecikme == 0 -> delayBuckets["zero"] = (delayBuckets["zero"] ?: 0) + 1
                gecikme <= 5 -> delayBuckets["low"] = (delayBuckets["low"] ?: 0) + 1
                gecikme <= 10 -> delayBuckets["medium"] = (delayBuckets["medium"] ?: 0) + 1
                else -> delayBuckets["high"] = (delayBuckets["high"] ?: 0) + 1
            }

            val prevType = typeOnTime[type] ?: Pair(0, 0)
            typeOnTime[type] = Pair(prevType.first + 1, prevType.second + if (onTime) 1 else 0)

            val slotKey = timeSlotKey(plannedDep)
            if (slotKey != "unknown") {
                val prev = timeSlotTotals[slotKey] ?: Triple(0, 0.0, 0)
                timeSlotTotals[slotKey] = Triple(
                    prev.first + 1,
                    prev.second + gecikme.toDouble(),
                    prev.third + if (onTime) 1 else 0
                )
            }

            if (line.isNotBlank()) {
                val agg = lineReliabilityAgg.getOrPut(line) { LineAgg() }
                agg.trips++
                agg.delay += maxOf(0, gecikme).toDouble()
                if (onTime) agg.onTime++
                if (gecikme > agg.maxDelay) agg.maxDelay = gecikme
            }

            if (from.isNotBlank() && to.isNotBlank()) {
                val agg = routeAgg.getOrPut(from to to) { RouteAgg() }
                agg.trips++
                agg.delay += maxOf(0, gecikme).toDouble()
                if (bestDuration != null && bestDuration > 0) {
                    val durationInt = Math.round(bestDuration).toInt()
                    agg.durationSum += bestDuration
                    agg.durationCount++
                    if (durationInt < agg.fastest) agg.fastest = durationInt
                    if (durationInt > agg.slowest) agg.slowest = durationInt
                }
            }

            if (gecikme > 0) {
                totalDelay += gecikme.toDouble()
                delayCount++
                if (gecikme.toDouble() > maxDelay) maxDelay = gecikme.toDouble()

                if (line.isNotBlank()) {
                    if (!lineMaxDelays.containsKey(line) || gecikme.toDouble() > lineMaxDelays[line]!!) {
                        lineMaxDelays[line] = gecikme.toDouble()
                    }
                    lineTotalDelays[line] = (lineTotalDelays[line] ?: 0.0) + gecikme.toDouble()
                }
            }
        }

        val recordLongestDay = dailyDuration.maxByOrNull { it.value }
        val recordMostTripsDay = dailyTrips.maxByOrNull { it.value }
        val recordMostDelayedLine = lineMaxDelays.maxByOrNull { it.value }
        val recordTotalDelayLine = lineTotalDelays.maxByOrNull { it.value }

        val punctualityRates = mutableMapOf<String, Int>()
        for ((t, stats) in typeOnTime) {
            punctualityRates[t] =
                if (stats.first > 0) Math.round(stats.second.toDouble() / stats.first * 100).toInt()
                else 0
        }
        for (t in types.keys) {
            if (!punctualityRates.containsKey(t)) punctualityRates[t] = 0
        }

        val avgDelay = if (totalTrips > 0) totalDelay / totalTrips else 0.0

        val topLines: Map<String, Int> = lines.entries
            .sortedByDescending { it.value }
            .take(7)
            .associate { it.key to it.value }
            .let { LinkedHashMap(it) }

        val timeSlotStats = listOf("morning", "noon", "evening", "night").mapNotNull { key ->
            val stats = timeSlotTotals[key] ?: return@mapNotNull null
            TimeSlotStats(
                key = key,
                trips = stats.first,
                avgDelay = if (stats.first > 0) stats.second / stats.first else 0.0,
                punctualityRate =
                    if (stats.first > 0) Math.round(stats.third.toDouble() / stats.first * 100).toInt()
                    else 0
            )
        }

        val lineReliability = lineReliabilityAgg.entries
            .sortedWith(compareByDescending<Map.Entry<String, LineAgg>> { it.value.trips }.thenBy { it.key })
            .take(7)
            .map { (line, stats) ->
                LineReliabilityStats(
                    line = line,
                    trips = stats.trips,
                    avgDelay = if (stats.trips > 0) stats.delay / stats.trips else 0.0,
                    punctualityRate =
                        if (stats.trips > 0) Math.round(stats.onTime.toDouble() / stats.trips * 100).toInt()
                        else 0,
                    maxDelay = stats.maxDelay
                )
            }

        val routePairs = routeAgg.entries
            .sortedWith(
                compareByDescending<Map.Entry<Pair<String, String>, RouteAgg>> { it.value.trips }
                    .thenBy { it.key.first }
            )
            .take(7)
            .map { (route, stats) ->
                RoutePairStats(
                    fromStop = route.first,
                    toStop = route.second,
                    trips = stats.trips,
                    avgDurationMin =
                        if (stats.durationCount > 0) Math.round(stats.durationSum / stats.durationCount).toInt()
                        else 0,
                    avgDelay = if (stats.trips > 0) stats.delay / stats.trips else 0.0,
                    fastestMin = if (stats.fastest == Int.MAX_VALUE) 0 else stats.fastest,
                    slowestMin = stats.slowest
                )
            }

        val delayDistribution = delayBuckets.map { (key, count) ->
            DelayBucketStats(
                key = key,
                count = count,
                earlyCount = if (key == "early") count else 0
            )
        }

        val summaryData = SummaryData(
            totalTrips = totalTrips,
            seatedCount = seatedCount,
            ticketControlCount = ticketControlCount,
            types = types,
            freqLine = getTop(lines),
            freqFrom = getTop(fromStops),
            freqTo = getTop(toStops),
            days = days,
            totalPlannedMin = Math.round(totalPlanned).toInt(),
            totalActualMin = Math.round(totalActual).toInt(),
            maxDelay = Math.round(maxDelay).toInt(),
            totalDelay = Math.round(totalDelay).toInt(),
            avgDelay = avgDelay,
            punctualityRates = punctualityRates,
            recordLongestDay = recordLongestDay?.key ?: "-",
            recordLongestDayMin = Math.round(recordLongestDay?.value ?: 0.0).toInt(),
            recordMostTripsDay = recordMostTripsDay?.key ?: "-",
            recordMostTripsCount = recordMostTripsDay?.value ?: 0,
            recordMostDelayedLine = recordMostDelayedLine?.key ?: "-",
            recordMostDelayedLineMin = Math.round(recordMostDelayedLine?.value ?: 0.0).toInt(),
            recordTotalDelayLine = recordTotalDelayLine?.key ?: "-",
            recordTotalDelayLineMin = Math.round(recordTotalDelayLine?.value ?: 0.0).toInt(),
            weatherCounts = weatherCounts,
            totalDistanceKm = Math.round(totalDistanceKm * 100) / 100.0,
            totalOrsDistanceKm = Math.round(totalOrsDistanceKm * 100) / 100.0,
            totalRmvDistanceKm = Math.round(totalRmvDistanceKm * 100) / 100.0,
            topLines = topLines,
            timeSlotStats = timeSlotStats,
            lineReliability = lineReliability,
            routePairs = routePairs,
            delayDistribution = delayDistribution,
            recordShortestTrip = shortestTrip?.first ?: "-",
            recordShortestTripMin = shortestTrip?.second ?: 0,
            recordLongestTrip = longestTrip?.first ?: "-",
            recordLongestTripMin = longestTrip?.second ?: 0,
            recordLongestDistanceTrip = longestDistanceTrip?.first ?: "-",
            recordLongestDistanceKm = Math.round((longestDistanceTrip?.second ?: 0.0) * 100) / 100.0,
            weekdayWeekendStats = WeekdayWeekendStats(
                weekday = weekdayAgg.toStats(),
                weekend = weekendAgg.toStats()
            ),
            monthlyTrend = computeMonthlyTrend(allDocs)
        )

        return Pair(summaryData, sortedMonths)
    }

    fun computeMonthlyTrend(allDocs: List<Map<String, Any>>): List<MonthlyTrendData> {
        val monthYearSet = mutableSetOf<Pair<Int, Int>>()
        for (row in allDocs) {
            val tarih = row["tarih"]?.toString() ?: continue
            val parts = tarih.split(".")
            if (parts.size >= 3) {
                val monthNum = parts[1].toIntOrNull() ?: continue
                val year = parts[2].toIntOrNull() ?: continue
                monthYearSet.add(year to monthNum)
            }
        }

        val maxYearMonth = monthYearSet.maxWithOrNull(compareBy({ it.first }, { it.second }))
        val endYearMonth = if (maxYearMonth != null) {
            java.time.YearMonth.of(maxYearMonth.first, maxYearMonth.second)
        } else {
            java.time.YearMonth.now()
        }

        val targetMonths = (0..5).map { endYearMonth.minusMonths(it.toLong()) }

        val SHORT_MONTH_NAMES = mapOf(
            1 to "Oca", 2 to "Şub", 3 to "Mar", 4 to "Nis", 5 to "May", 6 to "Haz",
            7 to "Tem", 8 to "Ağu", 9 to "Eyl", 10 to "Eki", 11 to "Kas", 12 to "Ara"
        )

        return targetMonths.map { ym ->
            val monthStr = String.format(Locale.US, "%02d", ym.monthValue)
            val yearStr = ym.year.toString()

            val filteredRows = allDocs.filter { row ->
                val tarih = row["tarih"]?.toString() ?: return@filter false
                val parts = tarih.split(".")
                parts.size >= 3 && parts[1] == monthStr && parts[2] == yearStr
            }

            var tripCount = 0
            var totalDistance = 0.0

            for (row in filteredRows) {
                val tarih = row["tarih"]?.toString() ?: continue
                if (tarih.isBlank() || tarih.lowercase() == "tarih") continue

                tripCount++
                val distanceKm = TransitRecordCalculations.orsDistanceKm(row)
                if (distanceKm != null) {
                    totalDistance += distanceKm
                }
            }

            MonthlyTrendData(
                monthName = SHORT_MONTH_NAMES[ym.monthValue] ?: ym.month.name.take(3),
                trips = tripCount,
                distanceKm = Math.round(totalDistance * 100) / 100.0
            )
        }
    }

    fun computeLineDetail(
        allDocs: List<Map<String, Any>>,
        sheetName: String,
        lineName: String
    ): LineDetailStats? {
        val rows = rowsForSheet(allDocs, sheetName)
            .filter { row -> row["hat"]?.toString() == lineName }
        if (rows.isEmpty()) return null

        val delayBuckets = linkedMapOf("early" to 0, "zero" to 0, "low" to 0, "medium" to 0, "high" to 0)
        val slotAgg = mutableMapOf<String, DelayAgg>()
        val dayAgg = mutableMapOf<String, DelayAgg>()
        var delayTotal = 0.0
        var maxDelay = 0
        var onTime = 0

        for (row in rows) {
            val delay = row["gecikme"]?.toString()?.toDoubleOrNull()?.toInt() ?: 0
            delayTotal += delay.toDouble()
            if (delay > maxDelay) maxDelay = delay
            if (delay <= 3) onTime++

            when {
                delay < 0 -> delayBuckets["early"] = (delayBuckets["early"] ?: 0) + 1
                delay == 0 -> delayBuckets["zero"] = (delayBuckets["zero"] ?: 0) + 1
                delay <= 5 -> delayBuckets["low"] = (delayBuckets["low"] ?: 0) + 1
                delay <= 10 -> delayBuckets["medium"] = (delayBuckets["medium"] ?: 0) + 1
                else -> delayBuckets["high"] = (delayBuckets["high"] ?: 0) + 1
            }

            lineDetailSlotKey(row["planlananBinis"]?.toString().orEmpty())?.let { key ->
                val agg = slotAgg.getOrPut(key) { DelayAgg() }
                agg.trips++
                agg.delay += delay.toDouble()
            }

            val date = row["tarih"]?.toString().orEmpty()
            if (date.isNotBlank()) {
                val day = row["gun"]?.toString().orEmpty()
                val label = if (day.isNotBlank()) "$date - $day" else date
                val agg = dayAgg.getOrPut(label) { DelayAgg() }
                agg.trips++
                agg.delay += delay.toDouble()
            }
        }

        val timeDelayStats = listOf("morning", "noon", "evening").map { key ->
            val agg = slotAgg[key]
            LineTimeDelayStats(
                key = key,
                trips = agg?.trips ?: 0,
                avgDelay = if (agg != null && agg.trips > 0) agg.delay / agg.trips else 0.0
            )
        }

        val delayedDays = dayAgg.entries
            .filter { it.value.delay > 0.0 }
            .sortedWith(
                compareByDescending<Map.Entry<String, DelayAgg>> { it.value.delay }
                    .thenByDescending { it.value.trips }
                    .thenBy { it.key }
            )
            .take(3)
            .map { (label, agg) ->
                LineDelayedDayStats(
                    label = label,
                    trips = agg.trips,
                    totalDelay = Math.round(agg.delay).toInt(),
                    avgDelay = if (agg.trips > 0) agg.delay / agg.trips else 0.0
                )
            }

        return LineDetailStats(
            line = lineName,
            trips = rows.size,
            punctualityRate = Math.round(onTime.toDouble() / rows.size * 100).toInt(),
            avgDelay = delayTotal / rows.size,
            maxDelay = maxDelay,
            delayBuckets = delayBuckets.map { (key, count) -> LineDelayBucket(key, count) },
            timeDelayStats = timeDelayStats,
            delayedDays = delayedDays
        )
    }

    private data class DelayAgg(
        var trips: Int = 0,
        var delay: Double = 0.0
    )

    private data class DayTypeAgg(
        var trips: Int = 0,
        var delay: Double = 0.0,
        var distance: Double = 0.0,
        var distanceCount: Int = 0
    ) {
        fun toStats(): DayTypeStats =
            DayTypeStats(
                trips = trips,
                avgDelay = if (trips > 0) delay / trips else 0.0,
                avgDistanceKm =
                    if (distanceCount > 0) Math.round(distance / distanceCount * 100) / 100.0
                    else 0.0
            )
    }

    private fun rowsForSheet(allDocs: List<Map<String, Any>>, sheetName: String): List<Map<String, Any>> {
        if (sheetName == ALL_SHEET) return allDocs

        val filterParts = sheetName.split(" ")
        val filterMonthNum = if (filterParts.size >= 2) MONTH_NUMBERS[filterParts[0]] else null
        val filterYear = if (filterParts.size >= 2) filterParts[1] else null
        if (filterMonthNum == null || filterYear == null) return allDocs

        return allDocs.filter { row ->
            val tarih = row["tarih"]?.toString() ?: return@filter false
            val parts = tarih.split(".")
            parts.size >= 3 && parts[1] == filterMonthNum && parts[2] == filterYear
        }
    }

    private fun isWeekendTrip(row: Map<String, Any>): Boolean {
        val day = row["gun"]?.toString().orEmpty()
        if (day == "Cumartesi" || day == "Pazar") return true
        if (day in WEEKDAY_NAMES) return false

        val dayType = row["gununTipi"]?.toString().orEmpty()
        if (dayType.contains("Sonu", ignoreCase = true)) return true
        if (dayType.contains("İçi", ignoreCase = true) || dayType.contains("Ici", ignoreCase = true)) return false

        return TransitRecordCalculations.computeGununTipi(row["tarih"]?.toString().orEmpty()) == "Hafta Sonu"
    }

    private fun lineDetailSlotKey(time: String): String? {
        val hour = (timeToMinutes(time) ?: return null) / 60
        return when (hour) {
            in 5..10 -> "morning"
            in 11..15 -> "noon"
            in 16..20 -> "evening"
            else -> null
        }
    }

    private data class LineAgg(
        var trips: Int = 0,
        var delay: Double = 0.0,
        var onTime: Int = 0,
        var maxDelay: Int = 0
    )

    private data class RouteAgg(
        var trips: Int = 0,
        var durationSum: Double = 0.0,
        var durationCount: Int = 0,
        var delay: Double = 0.0,
        var fastest: Int = Int.MAX_VALUE,
        var slowest: Int = 0
    )

    private fun timeToMinutes(time: String): Int? {
        val parts = time.trim().split(":")
        if (parts.size < 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    private fun timeSlotKey(time: String): String {
        val hour = (timeToMinutes(time) ?: return "unknown") / 60
        return when (hour) {
            in 5..10 -> "morning"
            in 11..15 -> "noon"
            in 16..20 -> "evening"
            else -> "night"
        }
    }

    private fun routeTitle(line: String, from: String, to: String): String =
        listOf(line.ifBlank { "-" }, "$from \u2192 $to").joinToString(" \u2022 ")

    private fun getTop(map: Map<String, Int>): String =
        map.maxByOrNull { it.value }?.key ?: "-"

    private const val ALL_SHEET = "T\u00fcm\u00fc"

    private val MONTH_NAMES = mapOf(
        "01" to "Ocak",
        "02" to "\u015eubat",
        "03" to "Mart",
        "04" to "Nisan",
        "05" to "May\u0131s",
        "06" to "Haziran",
        "07" to "Temmuz",
        "08" to "A\u011fustos",
        "09" to "Eyl\u00fcl",
        "10" to "Ekim",
        "11" to "Kas\u0131m",
        "12" to "Aral\u0131k"
    )

    private val MONTH_NUMBERS = MONTH_NAMES.entries.associate { (key, value) -> value to key }
    private val WEEKDAY_NAMES = setOf("Pazartesi", "Sal\u0131", "\u00c7ar\u015famba", "Per\u015fembe", "Cuma")
}
