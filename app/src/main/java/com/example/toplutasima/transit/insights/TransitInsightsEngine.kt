package com.example.toplutasima.transit.insights

import com.example.toplutasima.transit.summary.TransitSummaryEngine
import com.example.toplutasima.transit.summary.TransitSummaryRequest
import com.example.toplutasima.transit.summary.TransitSummaryResult
import java.util.Locale
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Deterministic, local insight generation. It intentionally owns no Flow collector and no storage;
 * callers use mapLatest/collectLatest and pass only the selected and immediately previous period.
 */
class TransitInsightsEngine(
    private val summaryEngine: TransitSummaryEngine,
    private val confidenceEvaluator: TransitInsightConfidenceEvaluator = TransitInsightConfidenceEvaluator(),
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default
) {

    suspend fun calculate(request: TransitInsightsRequest): TransitInsightsResult =
        withContext(calculationDispatcher) {
            currentCoroutineContext().ensureActive()
            val selectedRecords = request.selectedRecords.filterNot {
                it.isTransitInsightDeleted(request.tombstonedRecordIds)
            }
            val previousRecords = request.previousRecords.filterNot {
                it.isTransitInsightDeleted(request.tombstonedRecordIds)
            }
            val excludedCount = request.selectedRecords.size - selectedRecords.size

            val selectedSummary = resolveSummary(selectedRecords, request.selectedSummary)
            currentCoroutineContext().ensureActive()
            val previousSummary = if (request.previousPeriodLabel != null) {
                resolveSummary(previousRecords, request.previousSummary)
            } else {
                null
            }
            currentCoroutineContext().ensureActive()

            if (selectedRecords.size < MINIMUM_GLOBAL_SAMPLE) {
                val confidence = confidenceEvaluator.evaluate(
                    records = selectedRecords,
                    dataQuality = selectedSummary.selectedDataQuality,
                    provenanceByRecordId = request.provenanceByRecordId,
                    minimumSampleSize = MINIMUM_GLOBAL_SAMPLE
                )
                return@withContext TransitInsightsResult(
                    insights = listOf(
                        TransitInsight(
                            type = TransitInsightType.INSUFFICIENT_DATA,
                            semanticKey = TransitInsightType.INSUFFICIENT_DATA.name,
                            value = "${selectedRecords.size}",
                            periodLabel = request.selectedPeriodLabel,
                            confidence = confidence,
                            explanation = TransitInsightExplanation(
                                recordCount = selectedRecords.size,
                                periodLabel = request.selectedPeriodLabel,
                                calculation = "${selectedRecords.size} < $MINIMUM_GLOBAL_SAMPLE records"
                            ),
                            priority = 1_000
                        )
                    ),
                    selectedSummary = selectedSummary,
                    previousSummary = previousSummary,
                    consideredRecordCount = selectedRecords.size,
                    excludedDeletedRecordCount = excludedCount
                )
            }

            val selectedFacts = TransitInsightRecordFacts.from(selectedRecords)
            val previousFacts = TransitInsightRecordFacts.from(previousRecords)
            currentCoroutineContext().ensureActive()

            val candidates = buildList {
                addFrequentInsights(request, selectedSummary, selectedFacts, this)
                addTimeAndReliabilityInsights(request, selectedSummary, selectedFacts, this)
                addRouteInsights(request, selectedSummary, selectedFacts, this)
                if (previousSummary != null && request.previousPeriodLabel != null) {
                    addComparisonInsights(
                        request = request,
                        selectedSummary = selectedSummary,
                        previousSummary = previousSummary,
                        selectedFacts = selectedFacts,
                        previousFacts = previousFacts,
                        output = this
                    )
                }
            }
            currentCoroutineContext().ensureActive()

            val baseConfidence = confidence(
                records = selectedRecords,
                summary = selectedSummary,
                request = request
            )
            val withQualityNotice = if (
                baseConfidence.level == TransitInsightConfidence.LOW ||
                selectedSummary.selectedDataQuality.statisticsWereFiltered
            ) {
                candidates + qualityNotice(request, selectedSummary, selectedRecords, baseConfidence)
            } else {
                candidates
            }

            val ranked = withQualityNotice
                .distinctBy { it.semanticKey }
                .sortedWith(
                    compareByDescending<TransitInsight> { it.priority }
                        .thenByDescending { it.confidence.score }
                        .thenBy { it.semanticKey }
                )
                .take(request.maxInsights)

            TransitInsightsResult(
                insights = ranked,
                selectedSummary = selectedSummary,
                previousSummary = previousSummary,
                consideredRecordCount = selectedRecords.size,
                excludedDeletedRecordCount = excludedCount
            )
        }

    private suspend fun resolveSummary(
        records: List<Map<String, Any>>,
        supplied: TransitSummaryResult?
    ): TransitSummaryResult {
        if (supplied != null && supplied.selectedDataQuality.scannedRecordCount == records.size) {
            return supplied
        }
        return summaryEngine.calculate(
            TransitSummaryRequest(
                selectedRecords = records,
                boundedTrendRecords = records
            )
        )
    }

    private fun addFrequentInsights(
        request: TransitInsightsRequest,
        result: TransitSummaryResult,
        facts: TransitInsightRecordFacts,
        output: MutableList<TransitInsight>
    ) {
        val summary = result.summary
        addTopValue(
            type = TransitInsightType.MOST_USED_LINE,
            subject = summary.freqLine,
            matchingRecords = facts.lineRecords[summary.freqLine].orEmpty(),
            request = request,
            result = result,
            priority = 100,
            output = output
        )
        addTopValue(
            type = TransitInsightType.MOST_USED_ORIGIN,
            subject = summary.freqFrom,
            matchingRecords = facts.records.filter { it.insightString("binisDuragi") == summary.freqFrom },
            request = request,
            result = result,
            priority = 80,
            output = output
        )
        addTopValue(
            type = TransitInsightType.MOST_USED_DESTINATION,
            subject = summary.freqTo,
            matchingRecords = facts.records.filter { it.insightString("inisDuragi") == summary.freqTo },
            request = request,
            result = result,
            priority = 78,
            output = output
        )

        facts.transferCounts.maxByOrNull { it.value }
            ?.takeIf { it.value >= MINIMUM_REPEAT_COUNT }
            ?.let { (stop, count) ->
                output += insight(
                    type = TransitInsightType.MOST_USED_TRANSFER,
                    subject = stop,
                    value = count.toString(),
                    request = request,
                    result = result,
                    records = facts.records,
                    recordCount = count,
                    calculation = "count(transfer=$stop) = $count",
                    priority = 72
                )
            }

        if (summary.recordMostTripsDay != "-" && summary.recordMostTripsCount > 0) {
            output += insight(
                type = TransitInsightType.BUSIEST_TRAVEL_DAY,
                subject = summary.recordMostTripsDay,
                value = summary.recordMostTripsCount.toString(),
                request = request,
                result = result,
                records = facts.records,
                recordCount = summary.recordMostTripsCount,
                calculation = "max(count(date)) = ${summary.recordMostTripsCount}",
                priority = 74
            )
        }
    }

    private fun addTimeAndReliabilityInsights(
        request: TransitInsightsRequest,
        result: TransitSummaryResult,
        facts: TransitInsightRecordFacts,
        output: MutableList<TransitInsight>
    ) {
        val summary = result.summary
        summary.timeSlotStats.maxByOrNull { it.trips }
            ?.takeIf { it.trips >= MINIMUM_REPEAT_COUNT }
            ?.let { slot ->
                output += insight(
                    type = TransitInsightType.BUSIEST_TIME_SLOT,
                    subject = slot.key,
                    value = slot.trips.toString(),
                    request = request,
                    result = result,
                    records = facts.records,
                    recordCount = slot.trips,
                    calculation = "max(count(timeSlot)) = ${slot.trips}",
                    priority = 84
                )
            }

        facts.averageActualDuration?.let { average ->
            output += insight(
                type = TransitInsightType.AVERAGE_TRIP_DURATION,
                value = formatDecimal(average),
                request = request,
                result = result,
                records = facts.actualDurationRecords,
                recordCount = facts.actualDurationRecords.size,
                calculation = "sum(actualDuration) / ${facts.actualDurationRecords.size} = ${formatDecimal(average)} min",
                priority = 88
            )
        }
        facts.averageWaitingMinutes?.let { average ->
            output += insight(
                type = TransitInsightType.AVERAGE_WAITING_TIME,
                value = formatDecimal(average),
                request = request,
                result = result,
                records = facts.waitingRecords,
                recordCount = facts.waitingRecords.size,
                calculation = "sum(actualDeparture - plannedDeparture) / ${facts.waitingRecords.size} = ${formatDecimal(average)} min",
                priority = 86
            )
        }
        output += insight(
            type = TransitInsightType.AVERAGE_DELAY,
            value = formatDecimal(summary.avgDelay),
            request = request,
            result = result,
            records = facts.records,
            recordCount = summary.totalTrips,
            calculation = "totalDelay / ${summary.totalTrips} = ${formatDecimal(summary.avgDelay)} min",
            priority = 90
        )

        summary.lineReliability
            .filter { it.trips >= MINIMUM_REPEAT_COUNT }
            .sortedWith(
                compareByDescending<com.example.toplutasima.model.LineReliabilityStats> { it.avgDelay }
                    .thenByDescending { it.trips }
                    .thenBy { it.line }
            )
            .firstOrNull()
            ?.let { delayed ->
                output += insight(
                    type = TransitInsightType.MOST_DELAYED_LINE,
                    subject = delayed.line,
                    value = formatDecimal(delayed.avgDelay),
                    request = request,
                    result = result,
                    records = facts.lineRecords[delayed.line].orEmpty(),
                    recordCount = delayed.trips,
                    calculation = "delay(${delayed.line}) / ${delayed.trips} = ${formatDecimal(delayed.avgDelay)} min",
                    priority = 96
                )
            }

        summary.lineReliability
            .filter { it.trips >= MINIMUM_REPEAT_COUNT }
            .sortedWith(
                compareByDescending<com.example.toplutasima.model.LineReliabilityStats> { it.punctualityRate }
                    .thenBy { it.avgDelay }
                    .thenByDescending { it.trips }
                    .thenBy { it.line }
            )
            .firstOrNull()
            ?.let { reliable ->
                output += insight(
                    type = TransitInsightType.MOST_RELIABLE_LINE,
                    subject = reliable.line,
                    value = reliable.punctualityRate.toString(),
                    request = request,
                    result = result,
                    records = facts.lineRecords[reliable.line].orEmpty(),
                    recordCount = reliable.trips,
                    calculation = "onTime(${reliable.line}) / ${reliable.trips} = ${reliable.punctualityRate}%",
                    priority = 94
                )
            }

        val weekday = summary.weekdayWeekendStats.weekday.trips
        val weekend = summary.weekdayWeekendStats.weekend.trips
        if (weekday + weekend > 0 && weekday != weekend) {
            output += insight(
                type = TransitInsightType.WEEKDAY_WEEKEND_DIFFERENCE,
                subject = if (weekday > weekend) "weekday" else "weekend",
                value = abs(weekday - weekend).toString(),
                request = request,
                result = result,
                records = facts.records,
                recordCount = weekday + weekend,
                calculation = "weekday($weekday) - weekend($weekend) = ${weekday - weekend}",
                priority = 62
            )
        }

        val morning = summary.timeSlotStats.firstOrNull { it.key == "morning" }?.trips ?: 0
        val evening = summary.timeSlotStats.firstOrNull { it.key == "evening" }?.trips ?: 0
        if (morning + evening > 0 && morning != evening) {
            output += insight(
                type = TransitInsightType.MORNING_EVENING_DIFFERENCE,
                subject = if (morning > evening) "morning" else "evening",
                value = abs(morning - evening).toString(),
                request = request,
                result = result,
                records = facts.records,
                recordCount = morning + evening,
                calculation = "morning($morning) - evening($evening) = ${morning - evening}",
                priority = 64
            )
        }
    }

    private fun addRouteInsights(
        request: TransitInsightsRequest,
        result: TransitSummaryResult,
        facts: TransitInsightRecordFacts,
        output: MutableList<TransitInsight>
    ) {
        facts.routes.entries
            .filter { (_, route) ->
                route.usableDurationCount >= MINIMUM_REPEAT_COUNT &&
                    route.longerThanPlannedRatio >= FREQUENTLY_LONGER_RATIO
            }
            .maxWithOrNull(
                compareBy<Map.Entry<TransitInsightRecordFacts.RouteKey, TransitInsightRecordFacts.RouteFacts>> {
                    it.value.longerThanPlannedRatio
                }.thenBy { it.value.usableDurationCount }
            )
            ?.let { (route, routeFacts) ->
                output += insight(
                    type = TransitInsightType.ROUTE_OFTEN_LONGER_THAN_PLANNED,
                    subject = route.display,
                    value = (routeFacts.longerThanPlannedRatio * 100.0).roundToInt().toString(),
                    request = request,
                    result = result,
                    records = routeFacts.records,
                    recordCount = routeFacts.usableDurationCount,
                    calculation = "actual > planned: ${routeFacts.longerThanPlannedCount}/${routeFacts.usableDurationCount}",
                    priority = 98
                )
            }

        facts.routes.entries
            .mapNotNull { (route, routeFacts) ->
                val comparable = routeFacts.lineDurations
                    .filterValues { it.size >= MINIMUM_REPEAT_COUNT }
                    .mapValues { (_, durations) -> durations.average() }
                if (comparable.size < 2) return@mapNotNull null
                val fastest = comparable.minBy { it.value }
                val slowest = comparable.maxBy { it.value }
                RouteComparison(
                    route = route,
                    routeFacts = routeFacts,
                    fastestLine = fastest.key,
                    fastestAverage = fastest.value,
                    slowestLine = slowest.key,
                    slowestAverage = slowest.value,
                    difference = slowest.value - fastest.value
                )
            }
            .maxByOrNull { it.difference }
            ?.takeIf { it.difference >= MINIMUM_ROUTE_COMPARISON_DIFFERENCE }
            ?.let { comparison ->
                output += insight(
                    type = TransitInsightType.ROUTE_LINE_COMPARISON,
                    subject = comparison.route.display,
                    value = formatDecimal(comparison.difference),
                    request = request,
                    result = result,
                    records = comparison.routeFacts.records,
                    recordCount = comparison.routeFacts.usableDurationCount,
                    calculation = "avg(${comparison.slowestLine}) - avg(${comparison.fastestLine}) = ${formatDecimal(comparison.difference)} min",
                    priority = 92,
                    evidence = listOf(
                        "${comparison.fastestLine}=${formatDecimal(comparison.fastestAverage)} min",
                        "${comparison.slowestLine}=${formatDecimal(comparison.slowestAverage)} min"
                    )
                )
            }
    }

    private fun addComparisonInsights(
        request: TransitInsightsRequest,
        selectedSummary: TransitSummaryResult,
        previousSummary: TransitSummaryResult,
        selectedFacts: TransitInsightRecordFacts,
        previousFacts: TransitInsightRecordFacts,
        output: MutableList<TransitInsight>
    ) {
        val comparisonLabel = request.previousPeriodLabel ?: return
        val current = selectedSummary.summary
        val previous = previousSummary.summary
        if (previous.totalTrips >= MINIMUM_GLOBAL_SAMPLE) {
            output += comparisonInsight(
                type = TransitInsightType.USAGE_CHANGE,
                currentValue = current.totalTrips.toDouble(),
                previousValue = previous.totalTrips.toDouble(),
                request = request,
                result = selectedSummary,
                records = selectedFacts.records,
                previousRecordCount = previousFacts.records.size,
                calculationName = "tripCount",
                priority = 110
            )
            output += comparisonInsight(
                type = TransitInsightType.DELAY_CHANGE,
                currentValue = current.avgDelay,
                previousValue = previous.avgDelay,
                request = request,
                result = selectedSummary,
                records = selectedFacts.records,
                previousRecordCount = previousFacts.records.size,
                calculationName = "averageDelay",
                priority = 108
            )
            if (previous.totalDistanceKm > 0.0) {
                output += comparisonInsight(
                    type = TransitInsightType.DISTANCE_CHANGE,
                    currentValue = current.totalDistanceKm,
                    previousValue = previous.totalDistanceKm,
                    request = request,
                    result = selectedSummary,
                    records = selectedFacts.records,
                    previousRecordCount = previousFacts.records.size,
                    calculationName = "totalDistance",
                    priority = 104
                )
            }
        }

        val lineTrend = current.topLines.keys
            .asSequence()
            .mapNotNull { line ->
                val currentCount = selectedFacts.lineRecords[line]?.size ?: 0
                val previousCount = previousFacts.lineRecords[line]?.size ?: 0
                if (currentCount < MINIMUM_REPEAT_COUNT || previousCount < MINIMUM_REPEAT_COUNT) null
                else Triple(line, currentCount, previousCount)
            }
            .maxByOrNull { abs(it.second - it.third) }
        lineTrend?.takeIf { it.second != it.third }?.let { (line, currentCount, previousCount) ->
            output += insight(
                type = TransitInsightType.LINE_OR_ROUTE_TREND,
                subject = line,
                value = signedPercentChange(currentCount.toDouble(), previousCount.toDouble()),
                request = request,
                result = selectedSummary,
                records = selectedFacts.lineRecords[line].orEmpty(),
                recordCount = currentCount,
                calculation = "count($line): $previousCount → $currentCount",
                comparisonPeriodLabel = comparisonLabel,
                comparisonRecordCount = previousCount,
                priority = 76
            )
        }
    }

    private fun comparisonInsight(
        type: TransitInsightType,
        currentValue: Double,
        previousValue: Double,
        request: TransitInsightsRequest,
        result: TransitSummaryResult,
        records: List<Map<String, Any>>,
        previousRecordCount: Int,
        calculationName: String,
        priority: Int
    ): TransitInsight = insight(
        type = type,
        value = signedPercentChange(currentValue, previousValue),
        request = request,
        result = result,
        records = records,
        recordCount = records.size,
        calculation = "$calculationName: ${formatDecimal(previousValue)} → ${formatDecimal(currentValue)}",
        comparisonPeriodLabel = request.previousPeriodLabel,
        comparisonRecordCount = previousRecordCount,
        priority = priority
    )

    private fun addTopValue(
        type: TransitInsightType,
        subject: String,
        matchingRecords: List<Map<String, Any>>,
        request: TransitInsightsRequest,
        result: TransitSummaryResult,
        priority: Int,
        output: MutableList<TransitInsight>
    ) {
        if (subject.isBlank() || subject == "-" || matchingRecords.isEmpty()) return
        output += insight(
            type = type,
            subject = subject,
            value = matchingRecords.size.toString(),
            request = request,
            result = result,
            records = matchingRecords,
            recordCount = matchingRecords.size,
            calculation = "count($subject) = ${matchingRecords.size}",
            priority = priority
        )
    }

    private fun insight(
        type: TransitInsightType,
        value: String,
        request: TransitInsightsRequest,
        result: TransitSummaryResult,
        records: List<Map<String, Any>>,
        recordCount: Int,
        calculation: String,
        priority: Int,
        subject: String? = null,
        comparisonPeriodLabel: String? = null,
        comparisonRecordCount: Int? = null,
        evidence: List<String> = emptyList()
    ): TransitInsight = TransitInsight(
        type = type,
        semanticKey = listOf(type.name, subject.orEmpty()).joinToString(":").lowercase(Locale.ROOT),
        subject = subject,
        value = value,
        periodLabel = request.selectedPeriodLabel,
        confidence = confidence(
            records = records,
            summary = result,
            request = request,
            comparisonRecordCount = comparisonRecordCount
        ),
        explanation = TransitInsightExplanation(
            recordCount = recordCount,
            periodLabel = request.selectedPeriodLabel,
            comparisonPeriodLabel = comparisonPeriodLabel,
            calculation = calculation,
            evidence = evidence
        ),
        priority = priority
    )

    private fun confidence(
        records: List<Map<String, Any>>,
        summary: TransitSummaryResult,
        request: TransitInsightsRequest,
        comparisonRecordCount: Int? = null
    ): TransitInsightConfidenceAssessment = confidenceEvaluator.evaluate(
        records = records,
        dataQuality = summary.selectedDataQuality,
        provenanceByRecordId = request.provenanceByRecordId,
        comparisonRecordCount = comparisonRecordCount
    )

    private fun qualityNotice(
        request: TransitInsightsRequest,
        result: TransitSummaryResult,
        records: List<Map<String, Any>>,
        baseConfidence: TransitInsightConfidenceAssessment
    ): TransitInsight = TransitInsight(
        type = TransitInsightType.DATA_QUALITY_NOTICE,
        semanticKey = TransitInsightType.DATA_QUALITY_NOTICE.name.lowercase(Locale.ROOT),
        value = baseConfidence.score.toString(),
        periodLabel = request.selectedPeriodLabel,
        confidence = baseConfidence,
        explanation = TransitInsightExplanation(
            recordCount = records.size,
            periodLabel = request.selectedPeriodLabel,
            calculation = "confidence = 100 - ${baseConfidence.factors.sumOf { it.penaltyPoints }}",
            evidence = baseConfidence.factors.map { it.detail }
        ),
        priority = 120
    )

    private fun signedPercentChange(current: Double, previous: Double): String {
        if (previous == 0.0) return if (current == 0.0) "0%" else "+100%"
        val percent = ((current - previous) / abs(previous) * 100.0).roundToInt()
        return if (percent > 0) "+$percent%" else "$percent%"
    }

    private fun formatDecimal(value: Double): String =
        String.format(Locale.US, "%.1f", value)

    private data class RouteComparison(
        val route: TransitInsightRecordFacts.RouteKey,
        val routeFacts: TransitInsightRecordFacts.RouteFacts,
        val fastestLine: String,
        val fastestAverage: Double,
        val slowestLine: String,
        val slowestAverage: Double,
        val difference: Double
    )

    private companion object {
        const val MINIMUM_GLOBAL_SAMPLE = 2
        const val MINIMUM_REPEAT_COUNT = 2
        const val FREQUENTLY_LONGER_RATIO = 0.6
        const val MINIMUM_ROUTE_COMPARISON_DIFFERENCE = 2.0
    }
}
