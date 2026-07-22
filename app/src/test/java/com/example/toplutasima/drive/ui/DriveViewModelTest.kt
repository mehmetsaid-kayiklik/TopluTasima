package com.example.toplutasima.drive.ui

import android.os.Looper
import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.repository.DriveMutationResult
import com.example.toplutasima.drive.repository.DriveTripRepository
import com.example.toplutasima.drive.repository.DriveVehicleRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class DriveViewModelTest {

    @Test
    fun `feature gate disabled does not observe or schedule`() {
        val vehicles = FakeVehicleRepository()
        val viewModel = createViewModel(vehicles, FakeTripRepository(), enabled = false)

        idleMainLooper()

        assertFalse(viewModel.uiState.value.featureEnabled)
        assertFalse(viewModel.uiState.value.vehiclesLoading)
        assertEquals(0, vehicles.overviewObservationCount)
        assertEquals(0, vehicles.scheduleCount)
    }

    @Test
    fun `vehicle list exposes loading empty and content states`() {
        val loadingVehicles = FakeVehicleRepository().apply {
            overviewFlow = flow { awaitCancellation() }
        }
        val loadingViewModel = createViewModel(loadingVehicles, FakeTripRepository())
        idleMainLooper()
        assertTrue(loadingViewModel.uiState.value.vehiclesLoading)

        val vehicles = FakeVehicleRepository()
        val viewModel = createViewModel(vehicles, FakeTripRepository())
        idleMainLooper()
        assertFalse(viewModel.uiState.value.vehiclesLoading)
        assertTrue(viewModel.uiState.value.vehicles.isEmpty())

        vehicles.overviews.value = listOf(overview(vehicle("vehicle-a")))
        idleMainLooper()
        assertEquals("vehicle-a", viewModel.uiState.value.vehicles.single().vehicle.id)
        assertNull(viewModel.uiState.value.vehiclesError)
    }

    @Test
    fun `vehicle list exposes typed error without exception details`() {
        val vehicles = FakeVehicleRepository().apply {
            overviewFlow = flow { throw IllegalStateException("sensitive detail") }
        }
        val viewModel = createViewModel(vehicles, FakeTripRepository())

        idleMainLooper()

        assertFalse(viewModel.uiState.value.vehiclesLoading)
        assertEquals(DriveUiMessage.UNKNOWN_FAILURE, viewModel.uiState.value.vehiclesError)
    }

    @Test
    fun `vehicle add validates required name and odometer before repository`() {
        val vehicles = FakeVehicleRepository()
        val viewModel = createViewModel(vehicles, FakeTripRepository())
        idleMainLooper()

        viewModel.startAddVehicle()
        val form = requireNotNull(viewModel.uiState.value.vehicleForm)
        viewModel.updateVehicleForm(
            form.copy(displayName = "  ", initialOdometerKm = "-1")
        )
        viewModel.saveVehicle()

        val errors = requireNotNull(viewModel.uiState.value.vehicleForm).fieldErrors
        assertEquals(DriveFormError.DISPLAY_NAME_REQUIRED, errors["displayName"])
        assertEquals(DriveFormError.NEGATIVE_ODOMETER, errors["initialOdometerKm"])
        assertEquals(0, vehicles.createCount)
    }

    @Test
    fun `vehicle add accepts comma and dot decimal input`() {
        val vehicles = FakeVehicleRepository()
        val viewModel = createViewModel(vehicles, FakeTripRepository())
        idleMainLooper()
        viewModel.startAddVehicle()
        val form = requireNotNull(viewModel.uiState.value.vehicleForm)
        viewModel.updateVehicleForm(
            form.copy(
                displayName = "Aile aracı",
                initialOdometerKm = "12,5",
                currentOdometerKm = "20.5"
            )
        )
        vehicles.createResult = { draft ->
            DriveMutationResult.Success(
                vehicle("created").copy(
                    displayName = draft.displayName,
                    initialOdometerKm = draft.initialOdometerKm,
                    currentOdometerKm = draft.currentOdometerKm
                )
            )
        }

        viewModel.saveVehicle()
        idleMainLooper()

        assertEquals(1, vehicles.createCount)
        assertEquals(12.5, vehicles.lastVehicleDraft?.initialOdometerKm ?: -1.0, 0.0)
        assertEquals(20.5, vehicles.lastVehicleDraft?.currentOdometerKm ?: -1.0, 0.0)
        assertEquals(DriveDestination.VehicleDetail("created"), viewModel.uiState.value.destination)
    }

    @Test
    fun `vehicle edit keeps assigned person when directory is unavailable`() {
        val original = vehicle("vehicle-a").copy(assignedPersonId = "stable-person")
        val vehicles = FakeVehicleRepository().apply {
            overviews.value = listOf(overview(original))
            vehicleFlows[original.id] = MutableStateFlow(original)
            summaryFlows[original.id] = MutableStateFlow(summary())
        }
        val trips = FakeTripRepository()
        val viewModel = createViewModel(vehicles, trips)
        idleMainLooper()

        viewModel.openVehicle(original.id)
        idleMainLooper()
        viewModel.startEditVehicle()
        val form = requireNotNull(viewModel.uiState.value.vehicleForm)
        viewModel.updateVehicleForm(form.copy(notes = "koru"))
        vehicles.updateResult = { _, draft -> DriveMutationResult.Success(original.copy(notes = draft.notes)) }

        viewModel.saveVehicle()
        idleMainLooper()

        assertEquals("stable-person", vehicles.lastVehicleDraft?.assignedPersonId)
    }

    @Test
    fun `detail updates live when summary and trips change`() {
        val selected = vehicle("vehicle-a")
        val vehicleFlow = MutableStateFlow<DriveVehicle?>(selected)
        val summaryFlow = MutableStateFlow<DriveVehicleSummary?>(summary())
        val tripFlow = MutableStateFlow<List<DriveTrip>>(emptyList())
        val vehicles = FakeVehicleRepository().apply {
            overviews.value = listOf(overview(selected))
            vehicleFlows[selected.id] = vehicleFlow
            summaryFlows[selected.id] = summaryFlow
        }
        val trips = FakeTripRepository().apply { tripFlows[selected.id] = tripFlow }
        val viewModel = createViewModel(vehicles, trips)
        idleMainLooper()

        viewModel.openVehicle(selected.id)
        idleMainLooper()
        assertEquals(0, viewModel.uiState.value.selectedVehicleSummary?.tripCount)

        tripFlow.value = listOf(trip("trip-a", selected.id, 12.5))
        summaryFlow.value = summary(totalDistanceKm = 12.5, tripCount = 1)
        idleMainLooper()

        assertEquals(1, viewModel.uiState.value.selectedVehicleTrips.size)
        assertEquals(12.5, viewModel.uiState.value.selectedVehicleSummary?.totalDistanceKm ?: -1.0, 0.0)
    }

    @Test
    fun `vehicle delete requires confirmation and includes linked trips`() {
        val selected = vehicle("vehicle-a")
        val vehicles = FakeVehicleRepository().apply {
            overviews.value = listOf(overview(selected))
            vehicleFlows[selected.id] = MutableStateFlow(selected)
            summaryFlows[selected.id] = MutableStateFlow(summary(totalDistanceKm = 3.0, tripCount = 1))
        }
        val trips = FakeTripRepository().apply {
            tripFlows[selected.id] = MutableStateFlow(listOf(trip("trip-a", selected.id, 3.0)))
        }
        val viewModel = createViewModel(vehicles, trips)
        idleMainLooper()
        viewModel.openVehicle(selected.id)
        idleMainLooper()

        viewModel.requestVehicleDelete()

        val prompt = viewModel.uiState.value.deletePrompt as? DriveDeletePrompt.Vehicle
        assertNotNull(prompt)
        assertEquals(1, prompt?.activeTripCount)
        assertEquals(0, vehicles.deleteCount)

        viewModel.confirmDelete()
        idleMainLooper()

        assertEquals(1, vehicles.deleteCount)
        assertTrue(vehicles.lastDeleteLinkedTrips)
        assertEquals(DriveDestination.VehicleList, viewModel.uiState.value.destination)
    }

    @Test
    fun `trip mismatch remains visible and is not silently corrected`() {
        val selected = vehicle("vehicle-a")
        val vehicles = FakeVehicleRepository().apply {
            overviews.value = listOf(overview(selected))
            vehicleFlows[selected.id] = MutableStateFlow(selected)
            summaryFlows[selected.id] = MutableStateFlow(summary())
        }
        val trips = FakeTripRepository()
        val viewModel = createViewModel(vehicles, trips)
        idleMainLooper()
        viewModel.openVehicle(selected.id)
        idleMainLooper()
        viewModel.startAddTrip()
        val form = requireNotNull(viewModel.uiState.value.tripForm)
        viewModel.updateTripForm(
            form.copy(
                startOdometerKm = "10",
                endOdometerKm = "20",
                distanceKm = "7"
            )
        )

        viewModel.saveTrip()

        assertEquals(
            DriveFormError.DISTANCE_ODOMETER_MISMATCH,
            viewModel.uiState.value.tripForm?.fieldErrors?.get("distanceKm")
        )
        assertEquals("7", viewModel.uiState.value.tripForm?.distanceKm)
        assertEquals(0, trips.createCount)
    }

    @Test
    fun `restart recovery scheduling failure remains visible while list still loads`() {
        val vehicles = FakeVehicleRepository().apply { scheduleFailure = true }
        val viewModel = createViewModel(vehicles, FakeTripRepository())
        idleMainLooper()

        assertEquals(1, vehicles.scheduleCount)
        assertEquals(
            DriveUiMessage.SCHEDULING_FAILURE,
            viewModel.uiState.value.notice?.message
        )
        assertFalse(viewModel.uiState.value.vehiclesLoading)
    }

    private fun createViewModel(
        vehicles: FakeVehicleRepository,
        trips: FakeTripRepository,
        enabled: Boolean = true
    ): DriveViewModel = DriveViewModel(
        vehicleRepository = vehicles,
        tripRepository = trips,
        ioDispatcher = Dispatchers.Unconfined,
        calculationDispatcher = Dispatchers.Unconfined,
        clock = Clock.fixed(Instant.parse("2026-07-20T10:15:30Z"), ZoneId.of("Europe/Berlin")),
        enabled = enabled
    )

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private class FakeVehicleRepository : DriveVehicleRepository {
        val overviews = MutableStateFlow<List<DriveVehicleOverview>>(emptyList())
        var overviewFlow: Flow<List<DriveVehicleOverview>> = overviews
        val vehicleFlows = mutableMapOf<String, MutableStateFlow<DriveVehicle?>>()
        val summaryFlows = mutableMapOf<String, MutableStateFlow<DriveVehicleSummary?>>()
        var overviewObservationCount = 0
        var scheduleCount = 0
        var scheduleFailure = false
        var createCount = 0
        var deleteCount = 0
        var lastDeleteLinkedTrips = false
        var lastVehicleDraft: DriveVehicleDraft? = null
        var createResult: (DriveVehicleDraft) -> DriveMutationResult<DriveVehicle> = {
            DriveMutationResult.Success(vehicle("created"))
        }
        var updateResult: (String, DriveVehicleDraft) -> DriveMutationResult<DriveVehicle> = { id, _ ->
            DriveMutationResult.Success(vehicle(id))
        }

        override fun observeVehicleOverviews(): Flow<List<DriveVehicleOverview>> {
            overviewObservationCount++
            return overviewFlow
        }

        override fun observeVehicles(): Flow<List<DriveVehicle>> = flow {
            throw AssertionError("overview flow must be used")
        }

        override fun observeVehicle(vehicleId: String): Flow<DriveVehicle?> =
            vehicleFlows.getOrPut(vehicleId) { MutableStateFlow(null) }

        override fun observeSummary(vehicleId: String): Flow<DriveVehicleSummary?> =
            summaryFlows.getOrPut(vehicleId) { MutableStateFlow(null) }

        override suspend fun createVehicle(
            draft: DriveVehicleDraft
        ): DriveMutationResult<DriveVehicle> {
            createCount++
            lastVehicleDraft = draft
            return createResult(draft)
        }

        override suspend fun updateVehicle(
            vehicleId: String,
            draft: DriveVehicleDraft
        ): DriveMutationResult<DriveVehicle> {
            lastVehicleDraft = draft
            return updateResult(vehicleId, draft)
        }

        override suspend fun assignPerson(
            vehicleId: String,
            assignedPersonId: String?
        ): DriveMutationResult<DriveVehicle> = DriveMutationResult.Success(vehicle(vehicleId))

        override suspend fun deleteVehicle(
            vehicleId: String,
            deleteLinkedTrips: Boolean
        ): DriveMutationResult<Unit> {
            deleteCount++
            lastDeleteLinkedTrips = deleteLinkedTrips
            return DriveMutationResult.Success(Unit)
        }

        override fun schedulePendingSync() {
            scheduleCount++
            if (scheduleFailure) throw IllegalStateException("scheduler unavailable")
        }
    }

    private class FakeTripRepository : DriveTripRepository {
        val tripFlows = mutableMapOf<String, MutableStateFlow<List<DriveTrip>>>()
        var createCount = 0

        override fun observeTrips(vehicleId: String): Flow<List<DriveTrip>> =
            tripFlows.getOrPut(vehicleId) { MutableStateFlow(emptyList()) }

        override suspend fun createTrip(draft: DriveTripDraft): DriveMutationResult<DriveTrip> {
            createCount++
            return DriveMutationResult.Success(
                trip("created-trip", draft.vehicleId, draft.distanceKm ?: 0.0)
            )
        }

        override suspend fun updateTrip(
            tripId: String,
            draft: DriveTripDraft
        ): DriveMutationResult<DriveTrip> = DriveMutationResult.Success(
            trip(tripId, draft.vehicleId, draft.distanceKm ?: 0.0)
        )

        override suspend fun deleteTrip(tripId: String): DriveMutationResult<Unit> =
            DriveMutationResult.Success(Unit)
    }

    private companion object {
        val CREATED_AT: Instant = Instant.parse("2026-07-19T08:00:00Z")

        fun vehicle(id: String): DriveVehicle = DriveVehicle(
            id = id,
            ownerUid = "owner",
            displayName = "Araç $id",
            fuelType = VehicleFuelType.UNKNOWN,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT,
            syncState = DriveSyncState.LOCAL_PENDING
        )

        fun trip(id: String, vehicleId: String, distanceKm: Double): DriveTrip = DriveTrip(
            id = id,
            ownerUid = "owner",
            vehicleId = vehicleId,
            startedAt = CREATED_AT,
            distanceKm = distanceKm,
            purpose = DriveTripPurpose.PERSONAL,
            createdAt = CREATED_AT,
            updatedAt = CREATED_AT
        )

        fun summary(
            totalDistanceKm: Double = 0.0,
            tripCount: Int = 0
        ): DriveVehicleSummary = DriveVehicleSummary(
            totalDistanceKm = totalDistanceKm,
            tripCount = tripCount,
            lastUsedAt = null,
            initialOdometerKm = null,
            manualCurrentOdometerKm = null,
            estimatedCurrentOdometerKm = null,
            displayedCurrentOdometerKm = null,
            displayedOdometerSource = DriveOdometerSource.UNAVAILABLE,
            hasOdometerInconsistency = false
        )

        fun overview(vehicle: DriveVehicle): DriveVehicleOverview =
            DriveVehicleOverview(vehicle, summary())
    }
}
