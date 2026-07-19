package com.example.toplutasima.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.toMap
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.model.SummaryData
import com.example.toplutasima.transit.insights.TransitInsightType
import com.example.toplutasima.transit.insights.TransitInsightsEngine
import com.example.toplutasima.transit.summary.TransitSummaryEngine
import com.example.toplutasima.transit.summary.TransitSummaryHealthAssessment
import com.example.toplutasima.transit.summary.TransitSummaryHealthAssessor
import com.example.toplutasima.usecase.SummaryCalculator
import com.example.toplutasima.viewmodel.summary.TransitSummaryDataSource
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class SummaryViewModelLiveFlowTest {

    @Test
    fun `live month summary reacts to insert update and delete`() {
        val source = FakeTransitSummaryDataSource().apply {
            monthSummaries.value = listOf(monthSummary("Temmuz", "2026", 0))
        }
        val selected = source.monthFlow("2026-07")
        val trend = source.rangeFlow("2026-02", "2026-07")
        val viewModel = createViewModel(source, liveEnabled = true)

        viewModel.loadData("Temmuz 2026")
        awaitUiState(viewModel) { it.summary?.totalTrips == 0 }

        val inserted = trip(id = "trip-1", line = "S1", distanceKm = 5.0)
        selected.value = listOf(inserted)
        trend.value = listOf(inserted)
        awaitUiState(viewModel) {
            it.summary?.totalTrips == 1 && it.summary.freqLine == "S1"
        }

        val updated = inserted.copy(
            hat = "S8",
            gecikme = "7",
            gercekYolSuresi = "37",
            orsMesafeKm = 9.5
        )
        selected.value = listOf(updated)
        trend.value = listOf(updated)
        awaitUiState(viewModel) {
            it.summary?.freqLine == "S8" &&
                it.summary.totalDistanceKm == 9.5 &&
                it.summary.avgDelay == 7.0
        }

        selected.value = emptyList()
        trend.value = emptyList()
        awaitUiState(viewModel) { it.summary?.totalTrips == 0 }

        assertEquals("Temmuz 2026", viewModel.uiState.value.selectedSheet)
        assertTrue(viewModel.uiState.value.usingLiveRoomFlow)
    }

    @Test
    fun `selected month and scoped observer survive unrelated emits`() {
        val source = FakeTransitSummaryDataSource().apply {
            monthSummaries.value = listOf(
                monthSummary("Haziran", "2026", 1),
                monthSummary("Temmuz", "2026", 1)
            )
            monthFlow("2026-07").value = listOf(trip("july", line = "U4"))
            rangeFlow("2026-02", "2026-07").value = listOf(trip("july", line = "U4"))
        }
        val viewModel = createViewModel(source, liveEnabled = true)

        viewModel.loadData("Temmuz 2026")
        awaitUiState(viewModel) { it.summary?.freqLine == "U4" }

        source.allTrips.value = listOf(
            trip("unrelated", line = "X26", yearMonth = "2026-06", date = "01.06.2026")
        )
        source.monthSummaries.value = source.monthSummaries.value.map {
            if (it.monthName == "Haziran") it.copy(count = 2) else it
        }
        Thread.sleep(100)
        drainMainLooper()

        assertEquals("Temmuz 2026", viewModel.uiState.value.selectedSheet)
        assertEquals("U4", viewModel.uiState.value.summary?.freqLine)
        assertEquals(0, source.observeAllCalls.get())
        assertEquals(listOf("2026-07"), source.observedMonths.toList())
    }

    @Test
    fun `rapid emit cancels stale calculation before it reaches UI`() {
        val slowStarted = CountDownLatch(1)
        val cancellationCount = AtomicInteger(0)
        val assessor = TransitSummaryHealthAssessor { records ->
            if (records.any { it["hat"] == "SLOW" }) {
                slowStarted.countDown()
                try {
                    delay(750)
                } catch (cancellation: CancellationException) {
                    cancellationCount.incrementAndGet()
                    throw cancellation
                }
            }
            TransitSummaryHealthAssessment(recordsForStatistics = records)
        }
        val source = FakeTransitSummaryDataSource().apply {
            monthSummaries.value = listOf(monthSummary("Temmuz", "2026", 1))
        }
        val selected = source.monthFlow("2026-07")
        val trend = source.rangeFlow("2026-02", "2026-07")
        val viewModel = createViewModel(
            source = source,
            liveEnabled = true,
            engine = TransitSummaryEngine(healthAssessor = assessor)
        )

        viewModel.loadData("Temmuz 2026")
        awaitUiState(viewModel) { it.summary != null }

        val slow = trip("slow", line = "SLOW")
        selected.value = listOf(slow)
        trend.value = listOf(slow)
        awaitLatch(slowStarted, "Slow calculation did not start")

        val newest = trip("newest", line = "S8")
        selected.value = listOf(newest)
        trend.value = listOf(newest)
        awaitUiState(viewModel) { it.summary?.freqLine == "S8" }

        Thread.sleep(850)
        drainMainLooper()
        assertEquals("S8", viewModel.uiState.value.summary?.freqLine)
        assertTrue("The stale calculation was not cancelled", cancellationCount.get() > 0)
    }

    @Test
    fun `selecting a new month cancels the previous Room collector`() {
        val januarySubscribed = CountDownLatch(1)
        val januaryCancelled = CountDownLatch(1)
        val source = FakeTransitSummaryDataSource().apply {
            monthSummaries.value = listOf(
                monthSummary("Ocak", "2026", 1),
                monthSummary("Şubat", "2026", 1)
            )
            monthFlow("2026-02").value = listOf(
                trip("february", line = "S8", yearMonth = "2026-02", date = "02.02.2026")
            )
            rangeFlow("2025-09", "2026-02").value = monthFlow("2026-02").value
            monthFlowOverride = { yearMonth ->
                if (yearMonth == "2026-01") {
                    flow {
                        januarySubscribed.countDown()
                        try {
                            emit(
                                listOf(
                                    trip(
                                        "january",
                                        line = "U1",
                                        yearMonth = "2026-01",
                                        date = "02.01.2026"
                                    )
                                )
                            )
                            awaitCancellation()
                        } finally {
                            januaryCancelled.countDown()
                        }
                    }
                } else {
                    monthFlow(yearMonth)
                }
            }
        }
        val viewModel = createViewModel(source, liveEnabled = true)

        viewModel.loadData("Ocak 2026")
        awaitLatch(januarySubscribed, "January collector did not start")
        awaitUiState(viewModel) { it.summary?.freqLine == "U1" }

        viewModel.loadData("Şubat 2026")
        awaitLatch(januaryCancelled, "January collector was not cancelled")
        awaitUiState(viewModel) {
            it.selectedSheet == "Şubat 2026" && it.summary?.freqLine == "S8"
        }

        assertEquals("Şubat 2026", viewModel.uiState.value.selectedSheet)
        assertEquals("S8", viewModel.uiState.value.summary?.freqLine)
    }

    @Test
    fun `disabled live summary gate keeps the legacy snapshot path`() {
        val legacyTrips = listOf(
            trip("legacy-1", line = "X26", distanceKm = 12.0),
            trip("legacy-2", line = "X26", distanceKm = 8.0, date = "19.07.2026")
        )
        val source = FakeTransitSummaryDataSource().apply {
            legacyRecords = legacyTrips
            legacyResult = SummaryCalculator.computeSummary(legacyTrips.map { it.summaryRow() })
                .first to emptyList()
        }
        val viewModel = createViewModel(source, liveEnabled = false)

        viewModel.loadData()
        awaitUiState(viewModel) { it.summary?.totalTrips == 2 }

        val expected = source.legacyResult.first
        val actual = viewModel.uiState.value.summary
        assertEquals(expected.totalTrips, actual?.totalTrips)
        assertEquals(expected.totalDistanceKm, actual?.totalDistanceKm ?: 0.0, 0.0)
        assertEquals(expected.avgDelay, actual?.avgDelay ?: 0.0, 0.0)
        assertEquals(expected.freqLine, actual?.freqLine)
        assertFalse(viewModel.uiState.value.usingLiveRoomFlow)
        assertEquals(1, source.legacySummaryCalls.get())
        assertEquals(0, source.liveObserverCalls.get())
    }

    @Test
    fun `insights use only selected and immediately previous month`() {
        val source = FakeTransitSummaryDataSource().apply {
            monthSummaries.value = listOf(
                monthSummary("Haziran", "2026", 2),
                monthSummary("Temmuz", "2026", 3)
            )
            val current = listOf(
                trip("july-1", line = "S8"),
                trip("july-2", line = "S8", date = "19.07.2026"),
                trip("july-3", line = "S8", date = "20.07.2026")
            )
            val previous = listOf(
                trip("june-1", line = "U4", yearMonth = "2026-06", date = "18.06.2026"),
                trip("june-2", line = "U4", yearMonth = "2026-06", date = "19.06.2026")
            )
            monthFlow("2026-07").value = current
            rangeFlow("2026-02", "2026-07").value = previous + current
        }
        val viewModel = createViewModel(
            source = source,
            liveEnabled = true,
            insightsEnabled = true
        )

        viewModel.loadData("Temmuz 2026")
        awaitUiState(viewModel) { state ->
            state.insights.any { it.type == TransitInsightType.USAGE_CHANGE }
        }

        val usage = viewModel.uiState.value.insights.single {
            it.type == TransitInsightType.USAGE_CHANGE
        }
        assertEquals("Temmuz 2026", usage.periodLabel)
        assertEquals("Haziran 2026", usage.explanation.comparisonPeriodLabel)
        assertEquals(3, usage.explanation.recordCount)
        assertEquals(listOf("2026-07"), source.observedMonths.toList())
    }

    @Test
    fun `January insight comparison crosses to December of the previous year`() {
        val source = FakeTransitSummaryDataSource().apply {
            monthSummaries.value = listOf(
                monthSummary("Aralık", "2025", 2),
                monthSummary("Ocak", "2026", 3)
            )
            val current = listOf(
                trip("jan-1", line = "S8", yearMonth = "2026-01", date = "18.01.2026"),
                trip("jan-2", line = "S8", yearMonth = "2026-01", date = "19.01.2026"),
                trip("jan-3", line = "S8", yearMonth = "2026-01", date = "20.01.2026")
            )
            val previous = listOf(
                trip("dec-1", line = "U4", yearMonth = "2025-12", date = "18.12.2025"),
                trip("dec-2", line = "U4", yearMonth = "2025-12", date = "19.12.2025")
            )
            monthFlow("2026-01").value = current
            rangeFlow("2025-08", "2026-01").value = previous + current
        }
        val viewModel = createViewModel(
            source = source,
            liveEnabled = true,
            insightsEnabled = true
        )

        viewModel.loadData("Ocak 2026")
        awaitUiState(viewModel) { state ->
            state.insights.any { it.type == TransitInsightType.USAGE_CHANGE }
        }

        val usage = viewModel.uiState.value.insights.single {
            it.type == TransitInsightType.USAGE_CHANGE
        }
        assertEquals("Aralık 2025", usage.explanation.comparisonPeriodLabel)
        assertEquals(listOf("2026-01"), source.observedMonths.toList())
    }

    @Test
    fun `disabled insights gate never invokes insights engine`() {
        val insightCalculationCount = AtomicInteger(0)
        val guardedInsightEngine = TransitInsightsEngine(
            summaryEngine = TransitSummaryEngine(
                healthAssessor = TransitSummaryHealthAssessor { records ->
                    insightCalculationCount.incrementAndGet()
                    TransitSummaryHealthAssessment(recordsForStatistics = records)
                }
            )
        )
        val source = FakeTransitSummaryDataSource().apply {
            monthSummaries.value = listOf(monthSummary("Temmuz", "2026", 2))
            val records = listOf(
                trip("gate-1", line = "S8"),
                trip("gate-2", line = "S8", date = "19.07.2026")
            )
            monthFlow("2026-07").value = records
            rangeFlow("2026-02", "2026-07").value = records
        }
        val viewModel = createViewModel(
            source = source,
            liveEnabled = true,
            insightsEnabled = false,
            insightsEngine = guardedInsightEngine
        )

        viewModel.loadData("Temmuz 2026")
        awaitUiState(viewModel) { it.summary?.totalTrips == 2 }

        assertTrue(viewModel.uiState.value.insights.isEmpty())
        assertFalse(viewModel.uiState.value.isInsightsLoading)
        assertEquals(0, insightCalculationCount.get())
    }

    @Test
    fun `insights remain available through scoped snapshots when live summaries are disabled`() {
        val current = listOf(
            trip("legacy-july-1", line = "S8"),
            trip("legacy-july-2", line = "S8", date = "19.07.2026")
        )
        val previous = listOf(
            trip("legacy-june-1", line = "U4", yearMonth = "2026-06", date = "18.06.2026"),
            trip("legacy-june-2", line = "U4", yearMonth = "2026-06", date = "19.06.2026")
        )
        val source = FakeTransitSummaryDataSource().apply {
            legacyRecords = current
            legacyResult = SummaryCalculator.computeSummary(current.map { it.summaryRow() })
            monthFlow("2026-07").value = current
            monthFlow("2026-06").value = previous
        }
        val viewModel = createViewModel(
            source = source,
            liveEnabled = false,
            insightsEnabled = true
        )

        viewModel.loadData("Temmuz 2026")
        awaitUiState(viewModel) { state ->
            state.insights.any { it.type == TransitInsightType.USAGE_CHANGE }
        }

        assertFalse(viewModel.uiState.value.usingLiveRoomFlow)
        assertEquals(0, source.liveObserverCalls.get())
        assertEquals("Haziran 2026", viewModel.uiState.value.insights
            .single { it.type == TransitInsightType.USAGE_CHANGE }
            .explanation.comparisonPeriodLabel)
    }

    private fun createViewModel(
        source: TransitSummaryDataSource,
        liveEnabled: Boolean,
        engine: TransitSummaryEngine = TransitSummaryEngine(),
        insightsEnabled: Boolean = false,
        insightsEngine: TransitInsightsEngine = TransitInsightsEngine(engine)
    ): SummaryViewModel = SummaryViewModel(
        application = application,
        dataSource = source,
        summaryEngine = engine,
        liveSummariesEnabled = liveEnabled,
        insightsEngine = insightsEngine,
        insightsEnabled = insightsEnabled,
        autoLoad = false
    )

    private fun awaitUiState(
        viewModel: SummaryViewModel,
        timeoutSeconds: Long = 5,
        predicate: (SummaryUiState) -> Boolean
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (System.nanoTime() < deadline) {
            drainMainLooper()
            if (predicate(viewModel.uiState.value)) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for SummaryViewModel state: ${viewModel.uiState.value}")
    }

    private fun awaitLatch(latch: CountDownLatch, message: String) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (System.nanoTime() < deadline) {
            drainMainLooper()
            if (latch.await(10, TimeUnit.MILLISECONDS)) return
        }
        fail(message)
    }

    private fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    private fun monthSummary(month: String, year: String, count: Int) = MonthSummary(
        monthName = month,
        year = year,
        count = count,
        sortKey = year + MONTH_NUMBERS.getValue(month).toString().padStart(2, '0')
    )

    private fun trip(
        id: String,
        line: String,
        yearMonth: String = "2026-07",
        date: String = "18.07.2026",
        distanceKm: Double = 5.0
    ) = TripEntity(
        id = id,
        firestoreDocId = id,
        tarih = date,
        gun = "Cumartesi",
        tur = "Tren",
        hat = line,
        binisDuragi = "Başlangıç",
        inisDuragi = "Varış",
        planlananBinis = "08:00",
        planlananInis = "08:30",
        gercekBinis = "08:02",
        gercekInis = "08:34",
        planlananYolSuresi = "30",
        gercekYolSuresi = "32",
        gecikme = "2",
        orsMesafeKm = distanceKm,
        yearMonth = yearMonth,
        sortDate = yearMonth + "-18",
        userId = "summary-user"
    )

    @Suppress("UNCHECKED_CAST")
    private fun TripEntity.summaryRow(): Map<String, Any> = toMap() as Map<String, Any>

    private class FakeTransitSummaryDataSource : TransitSummaryDataSource {
        val allTrips = MutableStateFlow<List<TripEntity>>(emptyList())
        val monthSummaries = MutableStateFlow<List<MonthSummary>>(emptyList())
        val observeAllCalls = AtomicInteger(0)
        val liveObserverCalls = AtomicInteger(0)
        val legacySummaryCalls = AtomicInteger(0)
        val observedMonths = ConcurrentHashMap.newKeySet<String>()
        var legacyRecords: List<TripEntity> = emptyList()
        var legacyResult: Pair<SummaryData, List<String>> =
            SummaryCalculator.computeSummary(emptyList())
        var monthFlowOverride: ((String) -> Flow<List<TripEntity>>)? = null

        private val monthFlows = ConcurrentHashMap<String, MutableStateFlow<List<TripEntity>>>()
        private val rangeFlows = ConcurrentHashMap<String, MutableStateFlow<List<TripEntity>>>()

        fun monthFlow(yearMonth: String): MutableStateFlow<List<TripEntity>> =
            monthFlows.computeIfAbsent(yearMonth) { MutableStateFlow(emptyList()) }

        fun rangeFlow(
            startYearMonth: String,
            endYearMonth: String
        ): MutableStateFlow<List<TripEntity>> = rangeFlows.computeIfAbsent(
            "$startYearMonth..$endYearMonth"
        ) { MutableStateFlow(emptyList()) }

        override suspend fun syncFromFirestore(fullSync: Boolean) = Unit

        override suspend fun getLegacySummaryStats(
            sheetName: String
        ): Pair<SummaryData, List<String>> {
            legacySummaryCalls.incrementAndGet()
            return legacyResult
        }

        override fun getLegacyAllTrips(): Flow<List<TripEntity>> = flowOf(legacyRecords)

        override suspend fun getTripsForMonthSnapshot(yearMonth: String): List<TripEntity> =
            monthFlow(yearMonth).value

        override fun observeTripsForMonth(yearMonth: String): Flow<List<TripEntity>> {
            liveObserverCalls.incrementAndGet()
            observedMonths += yearMonth
            return monthFlowOverride?.invoke(yearMonth) ?: monthFlow(yearMonth)
        }

        override fun observeTripsForMonthRange(
            startYearMonth: String,
            endYearMonth: String
        ): Flow<List<TripEntity>> {
            liveObserverCalls.incrementAndGet()
            return rangeFlow(startYearMonth, endYearMonth)
        }

        override fun observeAllTrips(): Flow<List<TripEntity>> {
            liveObserverCalls.incrementAndGet()
            observeAllCalls.incrementAndGet()
            return allTrips
        }

        override fun observeMonthSummaries(): Flow<List<MonthSummary>> {
            liveObserverCalls.incrementAndGet()
            return monthSummaries
        }
    }

    private companion object {
        val MONTH_NUMBERS = mapOf(
            "Ocak" to 1,
            "Şubat" to 2,
            "Mart" to 3,
            "Nisan" to 4,
            "Mayıs" to 5,
            "Haziran" to 6,
            "Temmuz" to 7,
            "Ağustos" to 8,
            "Eylül" to 9,
            "Ekim" to 10,
            "Kasım" to 11,
            "Aralık" to 12
        )
    }
}
