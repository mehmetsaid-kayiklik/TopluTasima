package com.example.toplutasima.drive.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.data.remote.DriveRemoteDataSource
import com.example.toplutasima.drive.model.DriveFieldSource
import com.example.toplutasima.drive.model.DriveSyncReceiptStatus
import com.example.toplutasima.drive.model.DriveSyncState
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class RoomDrivePullCoordinatorTest {
    private lateinit var database: AppDatabase
    private lateinit var remote: FakeRemoteDataSource
    private var authenticatedUser: String? = USER_ID
    private var clock = 1_000L

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        remote = FakeRemoteDataSource()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `initial hydration atomically creates vehicles trips metadata provenance and receipt`() =
        runBlocking {
            remote.initial = batch(vehicle(), trip())

            val result = coordinator().pull(USER_ID)

            assertEquals(2, result.pulledCount)
            assertEquals("Remote car", database.driveVehicleDao()
                .getVehicle(USER_ID, VEHICLE_ID)?.displayName)
            assertNotNull(database.driveTripDao().getTrip(USER_ID, TRIP_ID))
            assertEquals(true, database.driveSyncMetadataDao().get(USER_ID)
                ?.initialHydrationCompleted)
            assertTrue(
                database.driveFieldProvenanceDao().getForRecord(
                    USER_ID,
                    DriveSyncEntityType.VEHICLE.name,
                    VEHICLE_ID
                ).all { it.source == DriveFieldSource.REMOTE.name }
            )
            assertEquals(
                DriveSyncReceiptStatus.SUCCEEDED.name,
                database.driveSyncReceiptDao().observeRecent(USER_ID, 1).first().single().status
            )
        }

    @Test
    fun `second run uses incremental cursor and upsert does not duplicate`() = runBlocking {
        remote.initial = batch(vehicle(), trip())
        coordinator().pull(USER_ID)
        remote.incremental = DriveRemotePullBatch(
            vehicles = listOf(
                DriveRemoteVehicle(
                    vehicle().copy(displayName = "Updated", updatedAt = 200L),
                    CURSOR_2
                )
            ),
            trips = emptyList(),
            vehicleCursor = CURSOR_2,
            tripCursor = null
        )

        val result = coordinator().pull(USER_ID)

        assertEquals(1, result.pulledCount)
        assertEquals(CURSOR_1, remote.requestedVehicleCursor)
        assertEquals("Updated", database.driveVehicleDao()
            .getVehicle(USER_ID, VEHICLE_ID)?.displayName)
        assertEquals(1, database.driveVehicleDao().observeActiveVehicles(USER_ID).first().size)
    }

    @Test
    fun `remote tombstone wins over pending offline edit`() = runBlocking {
        database.driveVehicleDao().upsert(
            vehicle().copy(displayName = "Offline", syncState = DriveSyncState.LOCAL_PENDING.name)
        )
        database.driveSyncOperationDao().upsert(
            operation(DriveSyncOperationType.UPDATE_VEHICLE)
        )
        remote.initial = DriveRemotePullBatch(
            vehicles = listOf(
                DriveRemoteVehicle(
                    vehicle().copy(displayName = "", deletedAt = 300L, updatedAt = 300L),
                    CURSOR_1
                )
            ),
            trips = emptyList(),
            vehicleCursor = CURSOR_1,
            tripCursor = null
        )

        coordinator().pull(USER_ID)

        assertEquals(300L, database.driveVehicleDao().getVehicle(USER_ID, VEHICLE_ID)?.deletedAt)
        assertNull(
            database.driveSyncOperationDao().getOperation(
                USER_ID,
                DriveSyncEntityType.VEHICLE.name,
                VEHICLE_ID
            )
        )
    }

    @Test
    fun `orphan trip rolls back whole initial hydration`() = runBlocking {
        remote.initial = DriveRemotePullBatch(
            vehicles = listOf(DriveRemoteVehicle(vehicle(), CURSOR_1)),
            trips = listOf(DriveRemoteTrip(trip().copy(vehicleId = "missing"), CURSOR_1)),
            vehicleCursor = CURSOR_1,
            tripCursor = CURSOR_1
        )

        val result = coordinator().pull(USER_ID)

        assertEquals(1, result.permanentFailureCount)
        assertNull(database.driveVehicleDao().getVehicle(USER_ID, VEHICLE_ID))
        assertNull(database.driveSyncMetadataDao().get(USER_ID))
        assertEquals(
            DriveSyncReceiptStatus.FATAL.name,
            database.driveSyncReceiptDao().observeRecent(USER_ID, 1).first().single().status
        )
    }

    @Test
    fun `authentication change cancels hydration without committing`() {
        remote.initial = batch(vehicle(), trip())
        remote.afterInitialFetch = { authenticatedUser = "other" }
        var cancelled = false
        try {
            runBlocking { coordinator().pull(USER_ID) }
        } catch (_: kotlinx.coroutines.CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        runBlocking {
            assertNull(database.driveVehicleDao().getVehicle(USER_ID, VEHICLE_ID))
        }
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
        var afterInitialFetch: () -> Unit = {}

        override suspend fun fetchInitial(ownerUid: String): DriveRemotePullBatch {
            afterInitialFetch()
            return initial
        }

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

    private companion object {
        const val USER_ID = "owner"
        const val VEHICLE_ID = "vehicle"
        const val TRIP_ID = "trip"
        val CURSOR_1 = DriveRemoteCursor(1L, 1, "vehicle")
        val CURSOR_2 = DriveRemoteCursor(2L, 1, "vehicle")

        fun vehicle() = DriveVehicleEntity(
            id = VEHICLE_ID,
            userId = USER_ID,
            displayName = "Remote car",
            createdAt = 100L,
            updatedAt = 100L,
            syncState = DriveSyncState.SYNCED.name
        )

        fun trip() = DriveTripEntity(
            id = TRIP_ID,
            userId = USER_ID,
            vehicleId = VEHICLE_ID,
            startedAt = 100L,
            distanceKm = 5.0,
            purpose = "PERSONAL",
            entrySource = "MANUAL",
            createdAt = 100L,
            updatedAt = 100L,
            syncState = DriveSyncState.SYNCED.name
        )

        fun batch(vehicle: DriveVehicleEntity, trip: DriveTripEntity) = DriveRemotePullBatch(
            vehicles = listOf(DriveRemoteVehicle(vehicle, CURSOR_1)),
            trips = listOf(DriveRemoteTrip(trip, DriveRemoteCursor(1L, 1, "trip"))),
            vehicleCursor = CURSOR_1,
            tripCursor = DriveRemoteCursor(1L, 1, "trip")
        )

        fun operation(type: DriveSyncOperationType) =
            com.example.toplutasima.data.local.entity.DriveSyncOperationEntity(
                operationId = "operation",
                userId = USER_ID,
                entityType = type.entityType.name,
                recordId = VEHICLE_ID,
                operationType = type.name,
                createdAt = 100L,
                updatedAt = 100L
            )
    }
}
