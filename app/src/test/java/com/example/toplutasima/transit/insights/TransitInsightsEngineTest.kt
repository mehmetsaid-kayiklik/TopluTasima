package com.example.toplutasima.transit.insights

import com.example.toplutasima.transit.provenance.TransitFieldFreshness
import com.example.toplutasima.transit.provenance.TransitFieldProvenance
import com.example.toplutasima.transit.provenance.TransitFieldSource
import com.example.toplutasima.transit.summary.TransitSummaryEngine
import com.example.toplutasima.transit.summary.TransitSummaryHealthAssessment
import com.example.toplutasima.transit.summary.TransitSummaryHealthAssessor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class TransitInsightsEngineTest {

    @Test
    fun `sufficient healthy records produce high confidence insight`() = runBlocking {
        val records = records(count = 12, month = 6)
        val result = engine().calculate(
            request(
                selected = records,
                provenance = knownProvenance(records)
            )
        )

        val line = result.insights.single { it.type == TransitInsightType.MOST_USED_LINE }
        assertEquals("X26", line.subject)
        assertEquals("12", line.value)
        assertEquals(TransitInsightConfidence.HIGH, line.confidence.level)
        assertEquals(12, line.explanation.recordCount)
        assertTrue(line.explanation.calculation.contains("count"))
    }

    @Test
    fun `small unhealthy unknown sample has low confidence`() = runBlocking {
        val records = records(count = 3, month = 6)
        val assessor = TransitSummaryHealthAssessor { input ->
            TransitSummaryHealthAssessment(
                recordsForStatistics = input,
                warningIssueCount = input.size
            )
        }
        val result = engine(TransitSummaryEngine(healthAssessor = assessor)).calculate(
            request(selected = records)
        )

        val line = result.insights.single { it.type == TransitInsightType.MOST_USED_LINE }
        assertEquals(TransitInsightConfidence.LOW, line.confidence.level)
        assertTrue(line.confidence.factors.any { it.type == TransitInsightConfidenceFactorType.SAMPLE_SIZE })
        assertTrue(line.confidence.factors.any { it.type == TransitInsightConfidenceFactorType.DATA_HEALTH })
        assertTrue(line.confidence.factors.any { it.type == TransitInsightConfidenceFactorType.UNKNOWN_PROVENANCE })
        assertTrue(result.insights.any { it.type == TransitInsightType.DATA_QUALITY_NOTICE })
    }

    @Test
    fun `missing actual times produce medium confidence with an explicit factor`() = runBlocking {
        val records = records(count = 4, month = 6).map { row ->
            row - "gercekBinis" - "gercekInis"
        }
        val result = engine().calculate(
            request(selected = records, provenance = knownProvenance(records))
        )

        val line = insight(result, TransitInsightType.MOST_USED_LINE)
        assertEquals(TransitInsightConfidence.MEDIUM, line.confidence.level)
        assertTrue(line.confidence.factors.any {
            it.type == TransitInsightConfidenceFactorType.MISSING_ACTUAL_TIMES
        })
    }

    @Test
    fun `one record returns explicit insufficient data result`() = runBlocking {
        val result = engine().calculate(request(selected = records(count = 1, month = 6)))

        assertEquals(1, result.insights.size)
        assertEquals(TransitInsightType.INSUFFICIENT_DATA, result.insights.single().type)
        assertEquals(TransitInsightConfidence.INSUFFICIENT_DATA, result.insights.single().confidence.level)
    }

    @Test
    fun `only previous month is used for comparison insights`() = runBlocking {
        val current = records(count = 10, month = 6, delay = 8, distance = 12.0)
        val previous = records(count = 5, month = 5, delay = 4, distance = 10.0)
        val result = engine().calculate(
            request(
                selected = current,
                previous = previous,
                provenance = knownProvenance(current + previous)
            )
        )

        val usage = result.insights.single { it.type == TransitInsightType.USAGE_CHANGE }
        assertEquals("+100%", usage.value)
        assertEquals("May 2026", usage.explanation.comparisonPeriodLabel)
        assertTrue(result.insights.any { it.type == TransitInsightType.DELAY_CHANGE })
        assertTrue(result.insights.any { it.type == TransitInsightType.DISTANCE_CHANGE })
    }

    @Test
    fun `health issues reduce otherwise equal confidence`() = runBlocking {
        val records = records(count = 12, month = 6)
        val provenance = knownProvenance(records)
        val healthy = engine().calculate(request(selected = records, provenance = provenance))
        val assessor = TransitSummaryHealthAssessor { input ->
            TransitSummaryHealthAssessment(recordsForStatistics = input, warningIssueCount = 6)
        }
        val unhealthy = engine(TransitSummaryEngine(healthAssessor = assessor)).calculate(
            request(selected = records, provenance = provenance)
        )

        assertTrue(
            insight(healthy, TransitInsightType.AVERAGE_DELAY).confidence.score >
                insight(unhealthy, TransitInsightType.AVERAGE_DELAY).confidence.score
        )
    }

    @Test
    fun `unknown provenance reduces confidence without excluding records`() = runBlocking {
        val records = records(count = 12, month = 6)
        val known = engine().calculate(
            request(selected = records, provenance = knownProvenance(records))
        )
        val unknown = engine().calculate(request(selected = records))

        assertEquals(12, unknown.consideredRecordCount)
        assertEquals(12, unknown.selectedSummary.summary.totalTrips)
        assertTrue(
            insight(known, TransitInsightType.AVERAGE_DELAY).confidence.score >
                insight(unknown, TransitInsightType.AVERAGE_DELAY).confidence.score
        )
    }

    @Test
    fun `deleted and tombstoned records never enter insight summary`() = runBlocking {
        val records = records(count = 12, month = 6).mapIndexed { index, row ->
            if (index == 11) row + ("deleted" to true) else row
        }
        val tombstones = setOf("trip-6-0", "trip-6-1", "trip-6-2")
        val result = engine().calculate(
            request(
                selected = records,
                provenance = knownProvenance(records),
                tombstones = tombstones
            )
        )

        assertEquals(8, result.consideredRecordCount)
        assertEquals(4, result.excludedDeletedRecordCount)
        assertEquals(8, result.selectedSummary.summary.totalTrips)
        assertFalse(result.insights.any { it.explanation.recordCount > 8 })
    }

    @Test
    fun `semantic insight cards are deduplicated`() = runBlocking {
        val records = records(count = 20, month = 6)
        val result = engine().calculate(
            request(
                selected = records,
                provenance = knownProvenance(records),
                maxInsights = 50
            )
        )

        assertEquals(result.insights.size, result.insights.map { it.semanticKey }.distinct().size)
        assertEquals(result.insights.size, result.insights.map { it.type }.distinct().size)
    }

    @Test
    fun `non-finite distance cannot produce NaN insight output`() = runBlocking {
        val records = records(count = 8, month = 6).map { row ->
            row + ("orsMesafeKm" to Double.NaN)
        }
        val result = engine().calculate(
            request(selected = records, provenance = knownProvenance(records))
        )

        assertTrue(result.selectedSummary.summary.totalDistanceKm.isFinite())
        assertFalse(result.insights.any { it.value.contains("NaN", ignoreCase = true) })
    }

    @Test
    fun `default locale does not change deterministic insight values`() = runBlocking {
        val records = records(count = 8, month = 6, distance = 12.5)
        val request = request(selected = records, provenance = knownProvenance(records))
        val baseline = engine().calculate(request)
        val original = Locale.getDefault()
        val localized = try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            engine().calculate(request)
        } finally {
            Locale.setDefault(original)
        }

        assertEquals(
            baseline.insights.map { it.semanticKey to it.value },
            localized.insights.map { it.semanticKey to it.value }
        )
    }

    @Test
    fun `same route line comparison and longer than planned are explainable`() = runBlocking {
        val x26 = records(count = 6, month = 6, line = "X26", plannedDuration = 20, actualDuration = 35)
        val s8 = records(count = 4, month = 6, line = "S8", plannedDuration = 20, actualDuration = 22)
            .mapIndexed { index, row -> row + ("id" to "s8-$index") }
        val all = x26 + s8
        val result = engine().calculate(
            request(selected = all, provenance = knownProvenance(all), maxInsights = 50)
        )

        val longer = insight(result, TransitInsightType.ROUTE_OFTEN_LONGER_THAN_PLANNED)
        val comparison = insight(result, TransitInsightType.ROUTE_LINE_COMPARISON)
        assertEquals("A → B", longer.subject)
        assertTrue(longer.explanation.calculation.contains("actual > planned"))
        assertTrue(comparison.explanation.evidence.any { it.contains("X26") })
        assertTrue(comparison.explanation.evidence.any { it.contains("S8") })
    }

    @Test
    fun `cancellation from summary calculation propagates unchanged`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancellationSeen = CompletableDeferred<Boolean>()
        val summaryEngine = TransitSummaryEngine(
            healthAssessor = TransitSummaryHealthAssessor {
                started.complete(Unit)
                awaitCancellation()
            }
        )
        val job = launch {
            try {
                engine(summaryEngine).calculate(request(selected = records(count = 8, month = 6)))
                cancellationSeen.complete(false)
            } catch (cancellation: CancellationException) {
                cancellationSeen.complete(true)
                throw cancellation
            }
        }

        started.await()
        job.cancelAndJoin()

        assertTrue(cancellationSeen.await())
        assertTrue(job.isCancelled)
    }

    @Test(timeout = 20_000)
    fun `large bounded month completes without loading unrelated history`() = runBlocking {
        val records = records(count = 10_000, month = 6)
        val result = engine().calculate(
            request(selected = records, provenance = knownProvenance(records), maxInsights = 20)
        )

        assertEquals(10_000, result.consideredRecordCount)
        assertEquals(10_000, result.selectedSummary.summary.totalTrips)
        assertTrue(result.insights.isNotEmpty())
    }

    private fun engine(summaryEngine: TransitSummaryEngine = TransitSummaryEngine()) =
        TransitInsightsEngine(summaryEngine = summaryEngine)

    private fun request(
        selected: List<Map<String, Any>>,
        previous: List<Map<String, Any>> = emptyList(),
        provenance: Map<String, Map<String, TransitFieldProvenance>> = emptyMap(),
        tombstones: Set<String> = emptySet(),
        maxInsights: Int = 20
    ) = TransitInsightsRequest(
        selectedPeriodLabel = "June 2026",
        selectedRecords = selected,
        previousPeriodLabel = previous.takeIf { it.isNotEmpty() }?.let { "May 2026" },
        previousRecords = previous,
        tombstonedRecordIds = tombstones,
        provenanceByRecordId = provenance,
        maxInsights = maxInsights
    )

    private fun knownProvenance(
        records: List<Map<String, Any>>
    ): Map<String, Map<String, TransitFieldProvenance>> = records.associate { row ->
        val id = row["id"].toString()
        id to mapOf(
            "hat" to TransitFieldProvenance(
                fieldId = "hat",
                source = TransitFieldSource.PLANNED_RMV,
                freshness = TransitFieldFreshness.FRESH
            )
        )
    }

    private fun records(
        count: Int,
        month: Int,
        line: String = "X26",
        delay: Int = 3,
        distance: Double = 10.0,
        plannedDuration: Int = 30,
        actualDuration: Int = 34
    ): List<Map<String, Any>> = List(count) { index ->
        val day = index % 28 + 1
        val departureHour = 6 + index % 12
        mapOf(
            "id" to "trip-$month-$index",
            "firestoreDocId" to "cloud-$month-$index",
            "tarih" to "%02d.%02d.2026".format(day, month),
            "gun" to DAYS[index % DAYS.size],
            "tur" to "Otobüs",
            "hat" to line,
            "binisDuragi" to "A",
            "inisDuragi" to "B",
            "planlananBinis" to "%02d:00".format(departureHour),
            "gercekBinis" to "%02d:03".format(departureHour),
            "planlananInis" to "%02d:30".format(departureHour),
            "gercekInis" to "%02d:34".format(departureHour),
            "planlananYolSuresi" to plannedDuration,
            "gercekYolSuresi" to actualDuration,
            "gecikme" to delay,
            "orsMesafeKm" to distance
        )
    }

    private fun insight(result: TransitInsightsResult, type: TransitInsightType): TransitInsight =
        result.insights.firstOrNull { it.type == type }
            ?: throw AssertionError("Missing insight $type: ${result.insights.map { it.type }}")

    private companion object {
        val DAYS = listOf(
            "Pazartesi",
            "Salı",
            "Çarşamba",
            "Perşembe",
            "Cuma",
            "Cumartesi",
            "Pazar"
        )
    }
}
