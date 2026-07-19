package com.example.toplutasima.transit.insights


/** Insight-only facts not already exposed by SummaryData, calculated once per bounded month. */
internal data class TransitInsightRecordFacts(
    val records: List<Map<String, Any>>,
    val actualDurationRecords: List<Map<String, Any>>,
    val averageActualDuration: Double?,
    val waitingRecords: List<Map<String, Any>>,
    val averageWaitingMinutes: Double?,
    val transferCounts: Map<String, Int>,
    val routes: Map<RouteKey, RouteFacts>,
    val lineRecords: Map<String, List<Map<String, Any>>>
) {
    data class RouteKey(val from: String, val to: String) {
        val display: String get() = "$from → $to"
    }

    data class RouteFacts(
        val records: List<Map<String, Any>>,
        val lineDurations: Map<String, List<Double>>,
        val longerThanPlannedCount: Int
    ) {
        val usableDurationCount: Int = lineDurations.values.sumOf { it.size }
        val longerThanPlannedRatio: Double =
            if (usableDurationCount == 0) 0.0 else longerThanPlannedCount.toDouble() / usableDurationCount
    }

    companion object {
        fun from(records: List<Map<String, Any>>): TransitInsightRecordFacts {
            val actualDurationRecords = records.filter { it.positiveDouble(FIELD_ACTUAL_DURATION) != null }
            val durations = actualDurationRecords.mapNotNull { it.positiveDouble(FIELD_ACTUAL_DURATION) }

            val waits = records.mapNotNull { record ->
                val planned = record.minutes(FIELD_PLANNED_DEPARTURE) ?: return@mapNotNull null
                val actual = record.minutes(FIELD_ACTUAL_DEPARTURE) ?: return@mapNotNull null
                val difference = forwardDifference(planned, actual)
                if (difference in 0..180) record to difference else null
            }

            val routes = records
                .filter { it.string(FIELD_FROM).isNotBlank() && it.string(FIELD_TO).isNotBlank() }
                .groupBy { RouteKey(it.string(FIELD_FROM), it.string(FIELD_TO)) }
                .mapValues { (_, routeRecords) ->
                    val durationByLine = routeRecords
                        .filter { it.string(FIELD_LINE).isNotBlank() }
                        .groupBy { it.string(FIELD_LINE) }
                        .mapValues { (_, lineRows) ->
                            lineRows.mapNotNull { it.positiveDouble(FIELD_ACTUAL_DURATION) }
                        }
                        .filterValues { it.isNotEmpty() }
                    val longer = routeRecords.count { row ->
                        val actual = row.positiveDouble(FIELD_ACTUAL_DURATION) ?: return@count false
                        val planned = row.positiveDouble(FIELD_PLANNED_DURATION) ?: return@count false
                        actual > planned + LONGER_THAN_PLANNED_MARGIN_MINUTES
                    }
                    RouteFacts(
                        records = routeRecords,
                        lineDurations = durationByLine,
                        longerThanPlannedCount = longer
                    )
                }

            return TransitInsightRecordFacts(
                records = records,
                actualDurationRecords = actualDurationRecords,
                averageActualDuration = durations.averageOrNull(),
                waitingRecords = waits.map { it.first },
                averageWaitingMinutes = waits.map { it.second.toDouble() }.averageOrNull(),
                transferCounts = transferCounts(records),
                routes = routes,
                lineRecords = records
                    .filter { it.string(FIELD_LINE).isNotBlank() }
                    .groupBy { it.string(FIELD_LINE) }
            )
        }

        private fun transferCounts(records: List<Map<String, Any>>): Map<String, Int> {
            val counts = mutableMapOf<String, Int>()
            records.groupBy { it.string(FIELD_DATE) }.values.forEach { dayRows ->
                val ordered = dayRows.sortedWith(
                    compareBy<Map<String, Any>>(
                        { it.departureSortMinutes() ?: Int.MAX_VALUE },
                        { it.string(FIELD_ID) }
                    )
                )
                ordered.zipWithNext().forEach transfer@{ (first, second) ->
                    val transferStop = first.string(FIELD_TO)
                    if (transferStop.isBlank() || !transferStop.equals(second.string(FIELD_FROM), ignoreCase = true)) {
                        return@transfer
                    }
                    val arrival = first.minutes(FIELD_ACTUAL_ARRIVAL)
                        ?: first.minutes(FIELD_PLANNED_ARRIVAL)
                        ?: return@transfer
                    val departure = second.minutes(FIELD_ACTUAL_DEPARTURE)
                        ?: second.minutes(FIELD_PLANNED_DEPARTURE)
                        ?: return@transfer
                    val gap = forwardDifference(arrival, departure)
                    if (gap in MIN_TRANSFER_MINUTES..MAX_TRANSFER_MINUTES) {
                        counts[transferStop] = (counts[transferStop] ?: 0) + 1
                    }
                }
            }
            return counts
        }

        private fun Map<String, Any>.departureSortMinutes(): Int? =
            minutes(FIELD_ACTUAL_DEPARTURE) ?: minutes(FIELD_PLANNED_DEPARTURE)

        private fun Map<String, Any>.minutes(key: String): Int? {
            val parts = string(key).split(":")
            if (parts.size < 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }

        private fun forwardDifference(from: Int, to: Int): Int {
            val raw = to - from
            return if (raw < -MIDNIGHT_THRESHOLD_MINUTES) raw + MINUTES_PER_DAY else raw
        }

        private fun Map<String, Any>.positiveDouble(key: String): Double? =
            this[key]?.toString()?.toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

        internal fun Map<String, Any>.string(key: String): String = this[key]?.toString()?.trim().orEmpty()

        private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

        const val FIELD_ID = "id"
        const val FIELD_DATE = "tarih"
        const val FIELD_LINE = "hat"
        const val FIELD_FROM = "binisDuragi"
        const val FIELD_TO = "inisDuragi"
        const val FIELD_PLANNED_DEPARTURE = "planlananBinis"
        const val FIELD_ACTUAL_DEPARTURE = "gercekBinis"
        const val FIELD_PLANNED_ARRIVAL = "planlananInis"
        const val FIELD_ACTUAL_ARRIVAL = "gercekInis"
        const val FIELD_PLANNED_DURATION = "planlananYolSuresi"
        const val FIELD_ACTUAL_DURATION = "gercekYolSuresi"

        private const val MIN_TRANSFER_MINUTES = 2
        private const val MAX_TRANSFER_MINUTES = 120
        private const val MIDNIGHT_THRESHOLD_MINUTES = 12 * 60
        private const val MINUTES_PER_DAY = 24 * 60
        private const val LONGER_THAN_PLANNED_MARGIN_MINUTES = 2.0
    }
}

internal fun Map<String, Any>.insightString(key: String): String = this[key]?.toString()?.trim().orEmpty()

internal fun Map<String, Any>.insightRecordId(): String =
    insightString("id").ifBlank { insightString("firestoreDocId") }

internal fun Map<String, Any>.isTransitInsightDeleted(tombstoneIds: Set<String>): Boolean {
    val localId = insightString("id")
    val cloudId = insightString("firestoreDocId")
    val explicitDeleted = listOf("deleted", "isDeleted", "_deleted").any { key ->
        this[key]?.toString()?.equals("true", ignoreCase = true) == true
    }
    return explicitDeleted || localId in tombstoneIds || cloudId in tombstoneIds
}
