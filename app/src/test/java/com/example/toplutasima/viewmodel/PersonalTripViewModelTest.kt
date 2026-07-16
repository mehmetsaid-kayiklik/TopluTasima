package com.example.toplutasima.viewmodel

import android.app.Application
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.model.PersonPlateSuggestion
import com.example.toplutasima.model.PersonalTrip
import com.example.toplutasima.repository.PersonalTripFirestoreOperations
import com.example.toplutasima.repository.PersonalTripRepository
import com.example.toplutasima.service.PersonalTripForegroundService
import com.example.toplutasima.viewmodel.personaltrip.PersonalTripRuntime
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.ArrayDeque

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class PersonalTripViewModelTest {
    @Test
    fun `successful boarding starts tracking and stores the active document`() {
        val firestore = FakePersonalFirestoreService().apply {
            updateResults.add(true)
        }
        val runtime = FakePersonalTripRuntime()
        val viewModel = createViewModel(firestore, runtime)

        viewModel.recordBindim(application, "board-success")
        drainMainLooper()

        assertEquals(1, runtime.startTrackingCalls)
        assertEquals("board-success", viewModel.uiState.value.activeDocId)
        assertFalse(viewModel.uiState.value.isResolvingLocation)
    }


    @Test
    fun `update false surfaces error and keeps edit dialog open`() {
        val firestore = FakePersonalFirestoreService().apply {
            updateResults.add(false)
        }
        val viewModel = createViewModel(firestore)
        val trip = testTrip()
        viewModel.openEditDialog(trip)
        val fetchCountBeforeUpdate = firestore.fetchAllCalls

        viewModel.updateTrip(trip)
        drainMainLooper()

        assertTrue(viewModel.uiState.value.statusMessage.startsWith("Güncellenemedi:"))
        assertTrue(viewModel.uiState.value.showAddDialog)
        assertSame(trip, viewModel.uiState.value.editingTrip)
        assertEquals(fetchCountBeforeUpdate, firestore.fetchAllCalls)
    }

    @Test
    fun `delete false surfaces error and does not stop active tracking`() {
        val firestore = FakePersonalFirestoreService().apply {
            updateResults.add(true)
            deleteResult = false
        }
        val runtime = FakePersonalTripRuntime()
        val viewModel = createViewModel(firestore, runtime)
        val docId = "active-doc"
        viewModel.recordBindim(application, docId)
        drainMainLooper()
        val fetchCountBeforeDelete = firestore.fetchAllCalls

        viewModel.deleteTrip(docId)
        drainMainLooper()

        assertTrue(viewModel.uiState.value.statusMessage.startsWith("Silinemedi:"))
        assertEquals(docId, viewModel.uiState.value.activeDocId)
        assertEquals(0, runtime.stopTrackingCalls)
        assertEquals(fetchCountBeforeDelete, firestore.fetchAllCalls)
    }

    @Test
    fun `board false surfaces error and does not start tracking`() {
        val firestore = FakePersonalFirestoreService().apply {
            updateResults.add(false)
        }
        val runtime = FakePersonalTripRuntime()
        val viewModel = createViewModel(firestore, runtime)
        val fetchCountBeforeBoarding = firestore.fetchAllCalls

        viewModel.recordBindim(application, "board-doc")
        drainMainLooper()

        assertTrue(viewModel.uiState.value.statusMessage.startsWith("Biniş kaydedilemedi:"))
        assertNull(viewModel.uiState.value.activeDocId)
        assertFalse(viewModel.uiState.value.isResolvingLocation)
        assertEquals(0, runtime.startTrackingCalls)
        assertEquals(fetchCountBeforeBoarding, firestore.fetchAllCalls)
    }

    @Test
    fun `alight false surfaces error and does not mark trip completed locally`() {
        val firestore = FakePersonalFirestoreService().apply {
            updateResults.add(true)
            updateResults.add(false)
        }
        val runtime = FakePersonalTripRuntime()
        val viewModel = createViewModel(firestore, runtime)
        val docId = "alight-doc"
        viewModel.recordBindim(application, docId)
        drainMainLooper()
        val fetchCountBeforeAlighting = firestore.fetchAllCalls

        viewModel.recordIndim(application, docId)
        drainMainLooper()

        assertTrue(viewModel.uiState.value.statusMessage.startsWith("İniş kaydedilemedi:"))
        assertFalse(viewModel.uiState.value.statusMessage.startsWith("Yolculuk tamamlandı"))
        assertEquals(docId, viewModel.uiState.value.activeDocId)
        assertFalse(viewModel.uiState.value.isResolvingLocation)
        assertEquals(1, runtime.finalizationCalls)
        assertEquals(fetchCountBeforeAlighting, firestore.fetchAllCalls)
    }

    @Test
    fun `alighting waits for finalization and writes the final batch distance`() {
        val firestore = FakePersonalFirestoreService().apply {
            updateResults.add(true)
            updateResults.add(true)
        }
        val finalizationGate =
            CompletableDeferred<PersonalTripForegroundService.TripFinalization?>()
        val runtime = FakePersonalTripRuntime().apply {
            pendingFinalization = finalizationGate
        }
        val viewModel = createViewModel(firestore, runtime)
        val docId = "final-distance-doc"
        viewModel.recordBindim(application, docId)
        drainMainLooper()
        val updateCountBeforeStop = firestore.updateRequests.size

        viewModel.recordIndim(application, docId)
        drainMainLooper()

        assertEquals(1, runtime.finalizationCalls)
        assertEquals(updateCountBeforeStop, firestore.updateRequests.size)
        assertFalse(viewModel.uiState.value.statusMessage.startsWith("Yolculuk tamamlandÄ±"))

        finalizationGate.complete(
            PersonalTripForegroundService.TripFinalization(
                sequence = 42L,
                tripDocumentId = docId,
                distanceKm = 15.25,
                flushSucceeded = true
            )
        )
        drainMainLooper()

        assertEquals(updateCountBeforeStop + 1, firestore.updateRequests.size)
        assertEquals("15.3 km", firestore.updateRequests.last().second["mesafe"])
        assertTrue(viewModel.uiState.value.statusMessage.contains("15.3 km"))
        assertNull(viewModel.uiState.value.activeDocId)
    }

    private fun createViewModel(
        firestore: FakePersonalFirestoreService,
        runtime: FakePersonalTripRuntime = FakePersonalTripRuntime()
    ): PersonalTripViewModel {
        val viewModel = PersonalTripViewModel(
            application = application,
            repository = PersonalTripRepository(firestore),
            runtime = runtime
        )
        drainMainLooper()
        return viewModel
    }

    private fun testTrip() = PersonalTrip(
        id = "local-id",
        firestoreDocId = "firestore-id",
        tarih = "15.07.2026",
        aracTuru = "Otomobil",
        plaka = "B-TEST 1"
    )

    private fun drainMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private val application: Application
        get() = ApplicationProvider.getApplicationContext()

    private class FakePersonalFirestoreService : PersonalTripFirestoreOperations {
        val updateResults = ArrayDeque<Boolean>()
        val updateRequests = mutableListOf<Pair<String, Map<String, Any?>>>()
        var deleteResult = true
        var fetchAllCalls = 0

        override suspend fun saveDraft(trip: PersonalTrip): String = "new-doc"

        override suspend fun updateTrip(
            docId: String,
            fields: Map<String, Any?>
        ): Boolean {
            updateRequests += docId to fields
            return if (updateResults.isEmpty()) true else updateResults.removeFirst()
        }

        override suspend fun deleteTrip(docId: String): Boolean = deleteResult

        override suspend fun fetchAll(): List<PersonalTrip> {
            fetchAllCalls += 1
            return emptyList()
        }

        override suspend fun fetchForMonth(yearMonth: String): List<PersonalTrip> = emptyList()

        override suspend fun fetchPlateSuggestions(): List<PersonPlateSuggestion> = emptyList()
    }

    private class FakePersonalTripRuntime : PersonalTripRuntime {
        var startTrackingCalls = 0
        var stopTrackingCalls = 0
        var finalizationCalls = 0
        var pendingFinalization:
            CompletableDeferred<PersonalTripForegroundService.TripFinalization?>? = null

        override fun hasLocationPermission(context: Context): Boolean = true

        override suspend fun resolveCurrentLocation(): Triple<String, Double, Double> =
            Triple("Test address", 52.52, 13.405)

        override fun startTracking(context: Context, docId: String) {
            startTrackingCalls += 1
        }

        override fun stopTracking(context: Context, docId: String?) {
            stopTrackingCalls += 1
        }

        override suspend fun stopTrackingAndAwaitFinalization(
            context: Context,
            docId: String
        ): PersonalTripForegroundService.TripFinalization? {
            finalizationCalls += 1
            return pendingFinalization?.await() ?: PersonalTripForegroundService.TripFinalization(
                sequence = finalizationCalls.toLong(),
                tripDocumentId = docId,
                distanceKm = 12.5,
                flushSucceeded = true
            )
        }
    }
}
