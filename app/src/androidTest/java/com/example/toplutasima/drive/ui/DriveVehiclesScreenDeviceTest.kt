package com.example.toplutasima.drive.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.repository.DriveMutationResult
import com.example.toplutasima.drive.repository.DriveTripRepository
import com.example.toplutasima.drive.repository.DriveVehicleRepository
import com.example.toplutasima.ui.AppLanguage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-side UI state coverage that does not rely on Espresso's removed API-36 input injector.
 * Compose rendering itself is verified separately through app launch, UI hierarchy and logcat.
 */
@RunWith(AndroidJUnit4::class)
class DriveVehiclesScreenDeviceTest {

    @Test
    fun featureGateDisabled_hidesUiAndDoesNotStartRepositoryWork() {
        val vehicles = FakeDriveVehicleRepository()
        val viewModel = newViewModel(vehicles = vehicles, enabled = false)

        assertFalse(viewModel.uiState.value.featureEnabled)
        assertEquals(DriveDestination.VehicleList, viewModel.uiState.value.destination)
        assertEquals(0, vehicles.overviewObserveCalls.get())
        assertEquals(0, vehicles.scheduleCalls.get())
    }

    @Test
    fun vehicleFlow_movesFromEmptyToAccessibleContent() = runBlocking {
        val vehicles = FakeDriveVehicleRepository()
        val viewModel = newViewModel(vehicles = vehicles)

        awaitCondition { !viewModel.uiState.value.vehiclesLoading }
        assertTrue(viewModel.uiState.value.vehicles.isEmpty())

        vehicles.setVehicles(listOf(testOverview()))
        awaitCondition { viewModel.uiState.value.vehicles.size == 1 }

        assertEquals(TEST_VEHICLE_NAME, viewModel.uiState.value.vehicles.single().vehicle.displayName)
        assertTrue(DriveSyncState.SYNCED.displayText(AppLanguage.TR).isNotBlank())
        assertFalse(viewModel.uiState.value.vehicles.single().vehicle.syncNeedsAttention())
    }

    @Test
    fun addVehicle_emptyNameShowsValidationAndDoesNotCallRepository() = runBlocking {
        val vehicles = FakeDriveVehicleRepository()
        val viewModel = newViewModel(vehicles = vehicles)
        awaitCondition { !viewModel.uiState.value.vehiclesLoading }

        viewModel.startAddVehicle()
        viewModel.saveVehicle()

        assertEquals(
            DriveFormError.DISPLAY_NAME_REQUIRED,
            viewModel.uiState.value.vehicleForm?.fieldErrors?.get("displayName")
        )
        assertEquals(0, vehicles.createCalls.get())
    }

    @Test
    fun vehicleEditorBackAction_returnsToListWithoutMutation() = runBlocking {
        val vehicles = FakeDriveVehicleRepository()
        val viewModel = newViewModel(vehicles = vehicles)
        awaitCondition { !viewModel.uiState.value.vehiclesLoading }

        viewModel.startAddVehicle()
        assertTrue(viewModel.uiState.value.destination is DriveDestination.VehicleEditor)
        viewModel.goBack()

        assertEquals(DriveDestination.VehicleList, viewModel.uiState.value.destination)
        assertNull(viewModel.uiState.value.vehicleForm)
        assertEquals(0, vehicles.createCalls.get())
    }

    @Test
    fun saveInProgress_disablesStateAndRejectsSecondMutation() = runBlocking {
        val vehicles = FakeDriveVehicleRepository(blockCreate = true)
        val viewModel = newViewModel(vehicles = vehicles)
        awaitCondition { !viewModel.uiState.value.vehiclesLoading }
        viewModel.startAddVehicle()
        viewModel.updateVehicleForm(
            requireNotNull(viewModel.uiState.value.vehicleForm).copy(displayName = "Test aracı")
        )

        viewModel.saveVehicle()
        viewModel.saveVehicle()
        awaitCondition { vehicles.createCalls.get() == 1 }

        assertTrue(viewModel.uiState.value.isMutating)
        assertEquals(1, vehicles.createCalls.get())
        vehicles.releaseCreate()
        awaitCondition { !viewModel.uiState.value.isMutating }
        assertEquals(1, vehicles.createCalls.get())
    }

    private fun newViewModel(
        vehicles: FakeDriveVehicleRepository,
        enabled: Boolean = true
    ): DriveViewModel = DriveViewModel(
        vehicleRepository = vehicles,
        tripRepository = FakeDriveTripRepository(),
        clock = Clock.fixed(TEST_NOW, ZoneOffset.UTC),
        enabled = enabled
    )

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withTimeout(5_000) {
            while (!condition()) delay(10)
        }
    }

    private fun testOverview(): DriveVehicleOverview {
        val vehicle = DriveVehicle(
            id = "vehicle-1",
            ownerUid = "test-user",
            displayName = TEST_VEHICLE_NAME,
            brand = "Test",
            model = "Model",
            fuelType = VehicleFuelType.ELECTRIC,
            createdAt = TEST_NOW,
            updatedAt = TEST_NOW,
            syncState = DriveSyncState.SYNCED
        )
        return DriveVehicleOverview(
            vehicle = vehicle,
            summary = DriveVehicleSummary(
                totalDistanceKm = 42.5,
                tripCount = 2,
                lastUsedAt = TEST_NOW,
                initialOdometerKm = null,
                manualCurrentOdometerKm = null,
                estimatedCurrentOdometerKm = null,
                displayedCurrentOdometerKm = null,
                displayedOdometerSource = DriveOdometerSource.UNAVAILABLE,
                hasOdometerInconsistency = false
            )
        )
    }

    private class FakeDriveVehicleRepository(
        private val blockCreate: Boolean = false
    ) : DriveVehicleRepository {
        private val overviews = MutableStateFlow<List<DriveVehicleOverview>>(emptyList())
        private val createRelease = CompletableDeferred<Unit>()
        val overviewObserveCalls = AtomicInteger(0)
        val scheduleCalls = AtomicInteger(0)
        val createCalls = AtomicInteger(0)

        fun setVehicles(value: List<DriveVehicleOverview>) {
            overviews.value = value
        }

        fun releaseCreate() {
            createRelease.complete(Unit)
        }

        override fun observeVehicles(): Flow<List<DriveVehicle>> =
            overviews.map { items -> items.map(DriveVehicleOverview::vehicle) }

        override fun observeVehicleOverviews(): Flow<List<DriveVehicleOverview>> {
            overviewObserveCalls.incrementAndGet()
            return overviews
        }

        override fun observeVehicle(vehicleId: String): Flow<DriveVehicle?> =
            overviews.map { items -> items.firstOrNull { it.vehicle.id == vehicleId }?.vehicle }

        override fun observeSummary(vehicleId: String): Flow<DriveVehicleSummary?> =
            overviews.map { items -> items.firstOrNull { it.vehicle.id == vehicleId }?.summary }

        override suspend fun createVehicle(
            draft: DriveVehicleDraft
        ): DriveMutationResult<DriveVehicle> {
            createCalls.incrementAndGet()
            if (blockCreate) createRelease.await()
            val vehicle = DriveVehicle(
                id = "created-vehicle",
                ownerUid = "test-user",
                displayName = draft.displayName,
                brand = draft.brand,
                model = draft.model,
                licensePlate = draft.licensePlate,
                modelYear = draft.modelYear,
                fuelType = draft.fuelType,
                initialOdometerKm = draft.initialOdometerKm,
                currentOdometerKm = draft.currentOdometerKm,
                assignedPersonId = draft.assignedPersonId,
                notes = draft.notes,
                createdAt = TEST_NOW,
                updatedAt = TEST_NOW
            )
            return DriveMutationResult.Success(vehicle)
        }

        override suspend fun updateVehicle(
            vehicleId: String,
            draft: DriveVehicleDraft
        ): DriveMutationResult<DriveVehicle> = DriveMutationResult.NotFound

        override suspend fun assignPerson(
            vehicleId: String,
            assignedPersonId: String?
        ): DriveMutationResult<DriveVehicle> = DriveMutationResult.NotFound

        override suspend fun deleteVehicle(
            vehicleId: String,
            deleteLinkedTrips: Boolean
        ): DriveMutationResult<Unit> = DriveMutationResult.Success(Unit)

        override fun schedulePendingSync() {
            scheduleCalls.incrementAndGet()
        }
    }

    private class FakeDriveTripRepository : DriveTripRepository {
        override fun observeTrips(vehicleId: String): Flow<List<DriveTrip>> =
            MutableStateFlow(emptyList())

        override suspend fun createTrip(
            draft: DriveTripDraft
        ): DriveMutationResult<DriveTrip> = DriveMutationResult.NotFound

        override suspend fun updateTrip(
            tripId: String,
            draft: DriveTripDraft
        ): DriveMutationResult<DriveTrip> = DriveMutationResult.NotFound

        override suspend fun deleteTrip(tripId: String): DriveMutationResult<Unit> =
            DriveMutationResult.NotFound
    }

    private companion object {
        const val TEST_VEHICLE_NAME = "Accessible test vehicle"
        val TEST_NOW: Instant = Instant.parse("2026-07-20T10:15:30Z")
    }
}
