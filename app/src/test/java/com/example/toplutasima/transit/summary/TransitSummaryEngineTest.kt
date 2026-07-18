package com.example.toplutasima.transit.summary

import com.example.toplutasima.usecase.SummaryCalculator
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis
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

class TransitSummaryEngineTest {

    @Test
    fun `scoped live result is equivalent to legacy summary for valid records`() = runBlocking {
        val allRecords = listOf(
            record(
                id = "may-1",
                date = "04.05.2026",
                day = "Pazartesi",
                line = "X26",
                from = "Havalimanı",
                to = "Güney Garı",
                plannedDuration = 30,
                actualDuration = 34,
                delay = 4,
                distanceKm = 10.0
            ),
            record(
                id = "may-2",
                date = "05.05.2026",
                day = "Salı",
                line = "X26",
                from = "Havalimanı",
                to = "Merkez",
                plannedDuration = 20,
                actualDuration = 20,
                delay = 0,
                distanceKm = 5.5
            ),
            record(
                id = "june-1",
                date = "03.06.2026",
                day = "Çarşamba",
                line = "S8",
                from = "Merkez",
                to = "Havalimanı",
                plannedDuration = 25,
                actualDuration = 31,
                delay = 6,
                distanceKm = 12.0
            )
        )
        val selectedMonthRecords = allRecords.filter { it["tarih"].toString().endsWith("05.2026") }
        val legacy = SummaryCalculator.computeSummary(allRecords, "Mayıs 2026")

        val result = TransitSummaryEngine().calculate(
            TransitSummaryRequest(
                selectedRecords = selectedMonthRecords,
                boundedTrendRecords = allRecords,
                availableSheetNames = legacy.second
            )
        )

        assertEquals(legacy.second, result.availableSheetNames)
        assertEquals(legacy.first.totalTrips, result.summary.totalTrips)
        assertEquals(legacy.first.totalDistanceKm, result.summary.totalDistanceKm, 0.0)
        assertEquals(legacy.first.avgDelay, result.summary.avgDelay, 0.0)
        assertEquals(legacy.first.freqLine, result.summary.freqLine)
        assertEquals(legacy.first.freqFrom, result.summary.freqFrom)
        assertEquals(legacy.first.freqTo, result.summary.freqTo)
        assertEquals(legacy.first.days, result.summary.days)
        assertEquals(legacy.first.monthlyTrend, result.summary.monthlyTrend)
        assertEquals(legacy.first.totalPlannedMin, result.summary.totalPlannedMin)
        assertEquals(legacy.first.totalActualMin, result.summary.totalActualMin)
        assertFalse(result.selectedDataQuality.assessmentApplied)
        assertEquals(selectedMonthRecords.size, result.selectedDataQuality.statisticsRecordCount)
    }

    @Test
    fun `health assessment is optional visible and reused for identical datasets`() = runBlocking {
        val calls = AtomicInteger(0)
        val records = listOf(
            record(id = "valid", date = "01.05.2026"),
            record(id = "excluded", date = "02.05.2026")
        )
        val assessor = TransitSummaryHealthAssessor { input ->
            calls.incrementAndGet()
            TransitSummaryHealthAssessment(
                recordsForStatistics = input.filterNot { it["id"] == "excluded" },
                informationalIssueCount = 1,
                warningIssueCount = 2,
                criticalIssueCount = 1
            )
        }

        val result = TransitSummaryEngine(healthAssessor = assessor).calculate(
            TransitSummaryRequest(selectedRecords = records)
        )

        assertEquals(1, calls.get())
        assertEquals(1, result.summary.totalTrips)
        assertTrue(result.selectedDataQuality.assessmentApplied)
        assertEquals(2, result.selectedDataQuality.scannedRecordCount)
        assertEquals(1, result.selectedDataQuality.statisticsRecordCount)
        assertEquals(1, result.selectedDataQuality.excludedRecordCount)
        assertEquals(4, result.selectedDataQuality.issueCount)
        assertTrue(result.selectedDataQuality.hasIssues)
        assertTrue(result.selectedDataQuality.statisticsWereFiltered)
        assertEquals(result.selectedDataQuality, result.trendDataQuality)
    }

    @Test
    fun `cancellation from health assessment propagates unchanged`() = runBlocking {
        val assessmentStarted = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Boolean>()
        val engine = TransitSummaryEngine(
            healthAssessor = TransitSummaryHealthAssessor {
                assessmentStarted.complete(Unit)
                awaitCancellation()
            }
        )

        val job = launch {
            try {
                engine.calculate(TransitSummaryRequest(selectedRecords = emptyList()))
                cancellationObserved.complete(false)
            } catch (cancellation: CancellationException) {
                cancellationObserved.complete(true)
                throw cancellation
            }
        }

        assessmentStarted.await()
        job.cancelAndJoin()

        assertTrue(cancellationObserved.await())
        assertTrue(job.isCancelled)
    }

    @Test(timeout = 20_000)
    fun `large fixture is calculated within a bounded CPU budget`() = runBlocking {
        val records = List(10_000) { index ->
            val month = index % 6 + 1
            val day = index % 28 + 1
            record(
                id = "trip-$index",
                date = "%02d.%02d.2026".format(day, month),
                day = DAYS[index % DAYS.size],
                line = "L${index % 8}",
                from = "Stop ${index % 20}",
                to = "Stop ${(index + 3) % 20}",
                plannedDuration = 20 + index % 30,
                actualDuration = 22 + index % 30,
                delay = index % 12,
                distanceKm = 1.0 + index % 25
            )
        }
        var result: TransitSummaryResult? = null

        val elapsedMillis = measureTimeMillis {
            result = TransitSummaryEngine().calculate(
                TransitSummaryRequest(
                    selectedRecords = records,
                    boundedTrendRecords = records
                )
            )
        }

        assertEquals(10_000, result?.summary?.totalTrips)
        assertEquals(6, result?.summary?.monthlyTrend?.size)
        assertTrue(result?.summary?.totalDistanceKm ?: 0.0 > 0.0)
        assertTrue("Calculation took ${elapsedMillis}ms", elapsedMillis < 12_000)
    }

    private fun record(
        id: String,
        date: String,
        day: String = "Pazartesi",
        line: String = "X26",
        from: String = "A",
        to: String = "B",
        plannedDuration: Int = 30,
        actualDuration: Int = 32,
        delay: Int = 2,
        distanceKm: Double = 10.0
    ): Map<String, Any> = mapOf(
        "id" to id,
        "firestoreDocId" to id,
        "tarih" to date,
        "gun" to day,
        "tur" to "Otobüs",
        "hat" to line,
        "binisDuragi" to from,
        "inisDuragi" to to,
        "planlananBinis" to "08:00",
        "planlananInis" to "08:30",
        "gercekBinis" to "08:02",
        "gercekInis" to "08:34",
        "planlananYolSuresi" to plannedDuration,
        "gercekYolSuresi" to actualDuration,
        "gecikme" to delay,
        "orsMesafeKm" to distanceKm
    )

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
