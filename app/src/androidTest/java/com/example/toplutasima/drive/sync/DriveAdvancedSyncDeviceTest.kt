package com.example.toplutasima.drive.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.TestDatabaseFactory
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.data.remote.DriveRemoteDataSource
import com.example.toplutasima.drive.management.DriveVehicleListProcessor
import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleAssignmentFilter
import com.example.toplutasima.drive.model.DriveVehicleListCriteria
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSort
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.repository.DriveMutationResult
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import com.example.toplutasima.drive.repository.OfflineFirstDriveRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveAdvancedSyncDeviceTest {
    private lateinit var database: AppDatabase
    private lateinit var remote: FakeRemoteDataSource
    private var authenticatedUser: String? = USER_A
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = TestDatabaseFactory.createInMemoryDatabase()
        remote = FakeRemoteDataSource()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun initialHydrationPopulatesRoomWithoutDuplicates() = runBlocking {
        remote.initial = batch(vehicle(name = "Remote car"), trip())

        val result = coordinator().pull(USER_A)

        assertEquals(2, result.pulledCount)
        assertEquals(
            listOf(VEHICLE_ID),
            database.driveVehicleDao().observeActiveVehicles(USER_A).first().map { it.id }
        )
        assertEquals(
            listOf(TRIP_ID),
            database.driveTripDao().observeActiveTripsForVehicle(USER_A, VEHICLE_ID)
                .first().map { it.id }
        )
        assertEquals(true, database.driveSyncMetadataDao().get(USER_A)?.initialHydrationCompleted)
    }

    @Test
    fun incrementalPullAppliesRemoteUpdate() = runBlocking {
        remote.initial = batch(vehicle(name = "Before"), trip())
        coordinator().pull(USER_A)
        remote.incremental = DriveRemotePullBatch(
            vehicles = listOf(
                DriveRemoteVehicle(
                    vehicle(name = "After", updatedAt = 200L),
                    CURSOR_2
                )
            ),
            trips = emptyList(),
            vehicleCursor = CURSOR_2,
            tripCursor = null
        )

        coordinator().pull(USER_A)

        assertEquals("After", database.driveVehicleDao().getVehicle(USER_A, VEHICLE_ID)?.displayName)
        assertEquals(CURSOR_1, remote.requestedVehicleCursor)
    }

    @Test
    fun incrementalPullAppliesRemoteDeleteAndHidesVehicle() = runBlocking {
        remote.initial = batch(vehicle(), trip())
        coordinator().pull(USER_A)
        remote.incremental = DriveRemotePullBatch(
            vehicles = listOf(
                DriveRemoteVehicle(
                    vehicle(name = "", updatedAt = 300L, deletedAt = 300L),
                    CURSOR_2
                )
            ),
            trips = emptyList(),
            vehicleCursor = CURSOR_2,
            tripCursor = null
        )

        coordinator().pull(USER_A)

        assertTrue(database.driveVehicleDao().observeActiveVehicles(USER_A).first().isEmpty())
        assertEquals(300L, database.driveVehicleDao().getVehicle(USER_A, VEHICLE_ID)?.deletedAt)
        assertEquals(300L, database.driveTripDao().getTrip(USER_A, TRIP_ID)?.deletedAt)
    }

    @Test
    fun accountSwitchPurgesOldUidAndKeepsActiveUid() = runBlocking {
        database.driveVehicleDao().upsert(vehicle(userId = USER_A, id = "vehicle-a"))
        database.driveVehicleDao().upsert(vehicle(userId = USER_B, id = "vehicle-b"))
        database.driveSyncMetadataDao().upsert(metadata(USER_A))
        database.driveSyncMetadataDao().upsert(metadata(USER_B))
        val manager = DriveAccountScopeManager(
            databaseProvider = { database },
            onAuthenticatedUserChanged = {},
            authChanges = emptyFlow(),
            currentUserId = { USER_B },
            ioDispatcher = Dispatchers.Unconfined
        )

        manager.purgeOutsideActiveAccount(USER_B)

        assertNull(database.driveVehicleDao().getVehicle(USER_A, "vehicle-a"))
        assertNull(database.driveSyncMetadataDao().get(USER_A))
        assertNotNull(database.driveVehicleDao().getVehicle(USER_B, "vehicle-b"))
        assertNotNull(database.driveSyncMetadataDao().get(USER_B))
    }

    @Test
    fun searchFilterAndSortComposeDeterministically() {
        val result = DriveVehicleListProcessor.apply(
            listOf(
                overview("a", "Family Car", "34 ABC 12", VehicleFuelType.PETROL, "person", 20.0),
                overview("b", "City EV", null, VehicleFuelType.ELECTRIC, null, 10.0),
                overview("c", "Other EV", "34 XYZ 90", VehicleFuelType.ELECTRIC, null, 30.0)
            ),
            DriveVehicleListCriteria(
                query = "34",
                fuelType = VehicleFuelType.ELECTRIC,
                assignment = DriveVehicleAssignmentFilter.UNASSIGNED,
                sort = DriveVehicleSort.TOTAL_DISTANCE,
                descending = true
            )
        )

        assertEquals(listOf("c"), result.map { it.vehicle.id })
    }

    @Test
    fun bulkDeleteTombstonesEntireSelectionBeforeSchedulingSync() = runBlocking {
        database.driveVehicleDao().upsert(vehicle(id = "vehicle-a"))
        database.driveVehicleDao().upsert(vehicle(id = "vehicle-b"))
        database.driveTripDao().upsert(trip(id = "trip-a", vehicleId = "vehicle-a"))
        val auth = MutableStateFlow<String?>(USER_A)
        val scheduledUsers = mutableListOf<String>()
        val repository = OfflineFirstDriveRepository(
            database = database,
            authenticatedUid = { auth.value },
            syncScheduler = DriveSyncWorkScheduler { scheduledUsers += it },
            authenticatedUidChanges = auth,
            nowEpochMillis = { 500L },
            ioDispatcher = Dispatchers.Unconfined,
            calculationDispatcher = Dispatchers.Unconfined
        )

        val result = repository.bulkDeleteVehicles(setOf("vehicle-a", "vehicle-b"))

        assertTrue(result is DriveMutationResult.Success)
        assertEquals(2, (result as DriveMutationResult.Success).value)
        assertTrue(database.driveVehicleDao().observeActiveVehicles(USER_A).first().isEmpty())
        assertNotNull(database.driveVehicleDao().getVehicle(USER_A, "vehicle-a")?.deletedAt)
        assertNotNull(database.driveVehicleDao().getVehicle(USER_A, "vehicle-b")?.deletedAt)
        assertNotNull(database.driveTripDao().getTrip(USER_A, "trip-a")?.deletedAt)
        assertEquals(3, database.driveSyncOperationDao().pendingCount(USER_A))
        assertEquals(listOf(USER_A), scheduledUsers)
    }

    private fun coordinator() = RoomDrivePullCoordinator(
        database = database,
        vehicleDao = database.driveVehicleDao(),
        tripDao = database.driveTripDao(),
        operationDao = database.driveSyncOperationDao(),
        metadataDao = database.driveSyncMetadataDao(),
        provenanceDao = database.driveFieldProvenanceDao(),
        remoteDataSource = remote,
        receiptStore = RoomDriveSyncReceiptStore(database.driveSyncReceiptDao()),
        currentUserId = { authenticatedUser },
        now = { clock++ }
    )

    private class FakeRemoteDataSource : DriveRemoteDataSource {
        var initial = DriveRemotePullBatch.EMPTY
        var incremental = DriveRemotePullBatch.EMPTY
        var requestedVehicleCursor: DriveRemoteCursor? = null

        override suspend fun fetchInitial(ownerUid: String): DriveRemotePullBatch = initial

        override suspend fun fetchIncremental(
            ownerUid: String,
            vehicleCursor: DriveRemoteCursor?,
            tripCursor: DriveRemoteCursor?
        ): DriveRemotePullBatch {
            requestedVehicleCursor = vehicleCursor
            return incremental
        }

        override suspend fun upsertVehicle(
            ownerUid: String,
            vehicle: DriveVehicleEntity,
            operationId: String
        ) = DriveRemoteWriteResult.Applied

        override suspend fun tombstoneVehicle(
            ownerUid: String,
            vehicle: DriveVehicleEntity,
            operationId: String
        ) = DriveRemoteWriteResult.Applied

        override suspend fun upsertTrip(
            ownerUid: String,
            trip: DriveTripEntity,
            operationId: String
        ) = DriveRemoteWriteResult.Applied

        override suspend fun tombstoneTrip(
            ownerUid: String,
            trip: DriveTripEntity,
            operationId: String
        ) = DriveRemoteWriteResult.Applied
    }

    private fun metadata(userId: String) = DriveSyncMetadataEntity(
        userId = userId,
        initialHydrationCompleted = true,
        updatedAt = 1L
    )

    private fun vehicle(
        userId: String = USER_A,
        id: String = VEHICLE_ID,
        name: String = "Remote car",
        updatedAt: Long = 100L,
        deletedAt: Long? = null
    ) = DriveVehicleEntity(
        id = id,
        userId = userId,
        displayName = name,
        licensePlate = "34 ABC 12",
        fuelType = VehicleFuelType.PETROL.name,
        createdAt = 100L,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        syncState = DriveSyncState.SYNCED.name
    )

    private fun trip(
        id: String = TRIP_ID,
        vehicleId: String = VEHICLE_ID
    ) = DriveTripEntity(
        id = id,
        userId = USER_A,
        vehicleId = vehicleId,
        startedAt = 100L,
        distanceKm = 12.5,
        purpose = "PERSONAL",
        entrySource = "MANUAL",
        createdAt = 100L,
        updatedAt = 100L,
        syncState = DriveSyncState.SYNCED.name
    )

    private fun batch(
        vehicle: DriveVehicleEntity,
        trip: DriveTripEntity
    ) = DriveRemotePullBatch(
        vehicles = listOf(DriveRemoteVehicle(vehicle, CURSOR_1)),
        trips = listOf(DriveRemoteTrip(trip, TRIP_CURSOR_1)),
        vehicleCursor = CURSOR_1,
        tripCursor = TRIP_CURSOR_1
    )

    private fun overview(
        id: String,
        name: String,
        plate: String?,
        fuel: VehicleFuelType,
        person: String?,
        distance: Double
    ) = DriveVehicleOverview(
        vehicle = DriveVehicle(
            id = id,
            ownerUid = USER_A,
            displayName = name,
            licensePlate = plate,
            fuelType = fuel,
            assignedPersonId = person,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            syncState = DriveSyncState.SYNCED
        ),
        summary = DriveVehicleSummary(
            totalDistanceKm = distance,
            tripCount = 0,
            lastUsedAt = null,
            initialOdometerKm = null,
            manualCurrentOdometerKm = null,
            estimatedCurrentOdometerKm = null,
            displayedCurrentOdometerKm = null,
            displayedOdometerSource = DriveOdometerSource.UNAVAILABLE,
            hasOdometerInconsistency = false
        )
    )

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
        const val VEHICLE_ID = "vehicle"
        const val TRIP_ID = "trip"
        val CURSOR_1 = DriveRemoteCursor(1L, 0, VEHICLE_ID)
        val CURSOR_2 = DriveRemoteCursor(2L, 0, VEHICLE_ID)
        val TRIP_CURSOR_1 = DriveRemoteCursor(1L, 0, TRIP_ID)
    }
}
