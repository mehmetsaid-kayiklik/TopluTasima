package com.example.toplutasima.viewmodel

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.data.repository.ProfileSyncRepository
import com.example.toplutasima.model.MonthSummary
import com.example.toplutasima.usecase.RecordFilterState
import com.example.toplutasima.viewmodel.records.RecordsTripDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class RecordsViewModelTest {

    @Test
    fun `late month A result cannot replace newer month B result`() {
        val monthA = MonthSummary("Ocak", "2026", 1, "202601")
        val monthB = MonthSummary("Şubat", "2026", 1, "202602")
        val source = BlockingRecordsTripDataSource().apply {
            monthLoads["2026-01"] = BlockingLoad(listOf(testTrip("trip-a", "01.01.2026")))
            monthLoads["2026-02"] = BlockingLoad(listOf(testTrip("trip-b", "01.02.2026")))
        }
        val viewModel = createViewModel(source)

        viewModel.selectMonth(monthA)
        drainMainLooper()
        source.monthLoads.getValue("2026-01").awaitStarted()

        viewModel.selectMonth(monthB)
        drainMainLooper()
        source.monthLoads.getValue("2026-02").awaitStarted()

        source.monthLoads.getValue("2026-02").complete()
        awaitUiState(viewModel) {
            it.selectedMonth == monthB &&
                it.selectedMonthTrips.flatMap { group -> group.trips }.map { row -> row.id } ==
                listOf("trip-b")
        }

        source.monthLoads.getValue("2026-01").complete()
        source.monthLoads.getValue("2026-01").awaitFinished()
        drainMainLooper()

        assertEquals(monthB, viewModel.uiState.value.selectedMonth)
        assertEquals(
            listOf("trip-b"),
            viewModel.uiState.value.selectedMonthTrips
                .flatMap { it.trips }
                .map { it.id }
        )
    }

    @Test
    fun `late search result cannot replace a newer search`() {
        val source = BlockingRecordsTripDataSource().apply {
            searchLoads["first"] = BlockingLoad(listOf(testTrip("first-result", "01.01.2026")))
            searchLoads["second"] = BlockingLoad(listOf(testTrip("second-result", "02.01.2026")))
        }
        val viewModel = createViewModel(source)

        viewModel.runGlobalSearch("first")
        drainMainLooper()
        source.searchLoads.getValue("first").awaitStarted()

        viewModel.runGlobalSearch("second")
        drainMainLooper()
        source.searchLoads.getValue("second").awaitStarted()
        source.searchLoads.getValue("second").complete()
        awaitUiState(viewModel) {
            it.globalSearchResults.map { row -> row.id } == listOf("second-result")
        }

        source.searchLoads.getValue("first").complete()
        source.searchLoads.getValue("first").awaitFinished()
        drainMainLooper()

        assertEquals(
            listOf("second-result"),
            viewModel.uiState.value.globalSearchResults.map { it.id }
        )
    }

    @Test
    fun `clearing search prevents an in-flight result from reappearing`() {
        val source = BlockingRecordsTripDataSource().apply {
            searchLoads["pending"] = BlockingLoad(listOf(testTrip("stale-result", "01.01.2026")))
        }
        val viewModel = createViewModel(source)

        viewModel.runGlobalSearch("pending")
        drainMainLooper()
        source.searchLoads.getValue("pending").awaitStarted()

        viewModel.clearGlobalSearch()
        source.searchLoads.getValue("pending").complete()
        source.searchLoads.getValue("pending").awaitFinished()
        drainMainLooper()

        assertTrue(viewModel.uiState.value.globalSearchResults.isEmpty())
        assertFalse(viewModel.uiState.value.globalSearchLoading)
        assertEquals("", viewModel.uiState.value.globalSearchError)
    }

    @Test
    fun `live month emit preserves selected month and active filter`() {
        val month = MonthSummary("Ocak", "2026", 2, "202601")
        val source = BlockingRecordsTripDataSource().apply {
            liveMonthFlows["2026-01"] = MutableStateFlow(
                listOf(testTrip("wanted", "01.01.2026"), testTrip("other", "02.01.2026"))
            )
        }
        val viewModel = createViewModel(source)

        viewModel.selectMonth(month)
        awaitUiState(viewModel) { it.unfilteredTotalCount == 2 }
        val filter = RecordFilterState(searchQuery = "wanted")
        viewModel.updateFilter(filter)
        assertEquals(listOf("wanted"), viewModel.uiState.value.filteredTrips.flatMap { it.trips }.map { it.id })

        source.liveMonthFlows.getValue("2026-01").value = listOf(
            testTrip("wanted", "01.01.2026").copy(not = "updated"),
            testTrip("other", "02.01.2026"),
            testTrip("new", "03.01.2026")
        )
        awaitUiState(viewModel) { it.unfilteredTotalCount == 3 }

        assertEquals(month, viewModel.uiState.value.selectedMonth)
        assertEquals(filter, viewModel.uiState.value.filterState)
        assertEquals(listOf("wanted"), viewModel.uiState.value.filteredTrips.flatMap { it.trips }.map { it.id })
    }

    @Test
    fun `disabled post-save health gate leaves legacy record projection unchanged`() {
        val month = MonthSummary("Ocak", "2026", 1, "202601")
        val source = BlockingRecordsTripDataSource().apply {
            liveMonthFlows["2026-01"] = MutableStateFlow(listOf(testTrip("legacy", "01.01.2026")))
        }
        val viewModel = createViewModel(
            source = source,
            postSaveHealthEnabled = false,
            provenanceEnabled = false
        )

        viewModel.selectMonth(month)
        awaitUiState(viewModel) { it.unfilteredTotalCount == 1 }

        val row = viewModel.uiState.value.selectedMonthTrips.single().trips.single()
        assertTrue(row.healthIssues.isEmpty())
        assertTrue(row.provenanceByField.isEmpty())
        assertTrue(viewModel.uiState.value.healthIssuesByRecordId.isEmpty())
        assertTrue(viewModel.uiState.value.healthCorrections.isEmpty())
    }

    private fun createViewModel(
        source: RecordsTripDataSource,
        postSaveHealthEnabled: Boolean = true,
        provenanceEnabled: Boolean = true
    ): RecordsViewModel =
        RecordsViewModel(
            application = application,
            profileSyncRepository = ProfileSyncRepository(application),
            tripRepository = source,
            autoLoad = false,
            postSaveHealthEnabled = postSaveHealthEnabled,
            provenanceEnabled = provenanceEnabled
        )

    private fun testTrip(id: String, date: String) = TripEntity(
        id = id,
        tarih = date,
        gun = "Test",
        tur = "Tren",
        hat = id,
        planlananBinis = "08:00"
    )

    private fun awaitUiState(
        viewModel: RecordsViewModel,
        predicate: (com.example.toplutasima.viewmodel.records.RecordsUiState) -> Boolean
    ) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (System.nanoTime() < deadline) {
            drainMainLooper()
            if (predicate(viewModel.uiState.value)) return
            Thread.sleep(10)
        }
        fail("Timed out waiting for RecordsViewModel state")
    }

    private fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    private class BlockingLoad<T>(private val result: T) {
        private val started = CountDownLatch(1)
        private val released = CountDownLatch(1)
        private val finished = CountDownLatch(1)

        fun markStarted() {
            started.countDown()
        }

        fun awaitResult(): T {
            released.await()
            return result
        }

        fun markFinished() {
            finished.countDown()
        }

        fun complete() {
            released.countDown()
        }

        fun awaitStarted() {
            assertTrue("Load did not start", started.await(2, TimeUnit.SECONDS))
        }

        fun awaitFinished() {
            assertTrue("Load did not finish", finished.await(2, TimeUnit.SECONDS))
        }
    }

    private class BlockingRecordsTripDataSource : RecordsTripDataSource {
        val monthLoads = mutableMapOf<String, BlockingLoad<List<TripEntity>>>()
        val searchLoads = mutableMapOf<String, BlockingLoad<List<TripEntity>>>()
        val liveMonthFlows = mutableMapOf<String, MutableStateFlow<List<TripEntity>>>()

        override suspend fun syncFromFirestore(fullSync: Boolean) = Unit

        override fun getTripsForMonth(yearMonth: String): Flow<List<TripEntity>> = flow {
            val load = monthLoads.getValue(yearMonth)
            load.markStarted()
            try {
                emit(load.awaitResult())
            } finally {
                load.markFinished()
            }
        }

        override fun observeTripsForMonth(yearMonth: String): Flow<List<TripEntity>> =
            liveMonthFlows[yearMonth] ?: getTripsForMonth(yearMonth)

        override suspend fun getTripById(id: String): TripEntity? = null

        override suspend fun getTripByFirestoreDocId(firestoreDocId: String): TripEntity? = null

        override fun getAllTrips(): Flow<List<TripEntity>> = flow { emit(emptyList()) }

        override suspend fun searchTrips(query: String): List<TripEntity> {
            val load = searchLoads.getValue(query)
            load.markStarted()
            return try {
                load.awaitResult()
            } finally {
                load.markFinished()
            }
        }

        override suspend fun saveTrip(trip: TripEntity) = Unit

        override suspend fun deleteTrip(id: String) = Unit

        override suspend fun getMonthSummaries(): List<MonthSummary> = emptyList()
    }
}
