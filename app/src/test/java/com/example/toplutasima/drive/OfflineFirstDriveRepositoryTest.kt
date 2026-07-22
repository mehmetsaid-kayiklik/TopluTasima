package com.example.toplutasima.drive

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.repository.DriveIdGenerator
import com.example.toplutasima.drive.repository.DriveMutationResult
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import com.example.toplutasima.drive.repository.OfflineFirstDriveRepository
import com.example.toplutasima.drive.sync.DriveSyncEntityType
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class OfflineFirstDriveRepositoryTest {
    private lateinit var database: AppDatabase
    private lateinit var authUid: MutableStateFlow<String?>
    private lateinit var scheduler: RecordingScheduler
    private lateinit var repository: OfflineFirstDriveRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        authUid = MutableStateFlow(USER_A)
        scheduler = RecordingScheduler()
        val sequence = AtomicInteger()
        repository = OfflineFirstDriveRepository(
            database = database,
            authenticatedUid = { authUid.value },
            syncScheduler = scheduler,
            authenticatedUidChanges = authUid,
            idGenerator = DriveIdGenerator { "local-${sequence.incrementAndGet()}" },
            nowEpochMillis = { NOW }
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `offline create update and trip summary stay local first`() = runBlocking {
        val created = repository.createVehicle(DriveVehicleDraft(displayName = "Car"))
        assertTrue(created is DriveMutationResult.Success)
        val vehicle = successValue(created)
        assertEquals(USER_A, vehicle.ownerUid)
        assertEquals(listOf(USER_A), scheduler.scheduledOwners)
        assertEquals("CREATE_VEHICLE", vehicleOperation(vehicle.id)?.operationType)

        val updated = repository.updateVehicle(
            vehicle.id,
            DriveVehicleDraft(displayName = "Updated car", initialOdometerKm = 100.0)
        )
        assertTrue(updated is DriveMutationResult.Success)
        assertEquals("CREATE_VEHICLE", vehicleOperation(vehicle.id)?.operationType)

        val trip = repository.createTrip(
            DriveTripDraft(
                vehicleId = vehicle.id,
                startedAt = Instant.parse("2026-07-20T08:00:00Z"),
                startOdometerKm = 100.0,
                endOdometerKm = 112.5
            )
        )
        assertTrue(trip is DriveMutationResult.Success)
        val summary = repository.observeSummary(vehicle.id).first()!!
        assertEquals(12.5, summary.totalDistanceKm, 0.0)
        assertEquals(1, summary.tripCount)
        assertEquals(112.5, summary.estimatedCurrentOdometerKm!!, 0.0)
    }

    @Test
    fun `vehicle delete requires confirmation and then atomically tombstones linked trips`() = runBlocking {
        val vehicle = successValue(repository.createVehicle(DriveVehicleDraft(displayName = "Car")))
        val trip = successValue(
            repository.createTrip(
                DriveTripDraft(
                    vehicleId = vehicle.id,
                    startedAt = Instant.EPOCH,
                    distanceKm = 4.5
                )
            )
        )

        val unconfirmed = repository.deleteVehicle(vehicle.id, deleteLinkedTrips = false)
        assertEquals(DriveMutationResult.CascadeConfirmationRequired(1), unconfirmed)
        assertEquals(1, repository.observeVehicles().first().size)

        assertTrue(
            repository.deleteVehicle(vehicle.id, deleteLinkedTrips = true) is DriveMutationResult.Success
        )
        assertTrue(repository.observeVehicles().first().isEmpty())
        assertTrue(repository.observeTrips(vehicle.id).first().isEmpty())
        assertTrue(database.driveVehicleDao().getVehicle(USER_A, vehicle.id)?.deletedAt != null)
        assertTrue(database.driveTripDao().getTrip(USER_A, trip.id)?.deletedAt != null)
        assertEquals("DELETE_VEHICLE", vehicleOperation(vehicle.id)?.operationType)
        assertEquals(
            "DELETE_DRIVE_TRIP",
            database.driveSyncOperationDao().getOperation(USER_A, "TRIP", trip.id)?.operationType
        )
    }

    @Test
    fun `authenticated UID changes cancel the old Room query`() = runBlocking {
        database.driveVehicleDao().upsert(vehicleEntity(USER_A, "vehicle-a", "A"))
        database.driveVehicleDao().upsert(vehicleEntity(USER_B, "vehicle-b", "B"))
        val emissions = Channel<List<String>>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            repository.observeVehicles().collect { vehicles ->
                emissions.send(vehicles.map { it.displayName })
            }
        }

        assertEquals(listOf("A"), withTimeout(5_000) { emissions.receive() })
        authUid.value = USER_B
        assertEquals(listOf("B"), withTimeout(5_000) { emissions.receive() })
        collection.cancelAndJoin()
    }

    @Test
    fun `trip cannot reference a vehicle owned by another UID`() = runBlocking {
        database.driveVehicleDao().upsert(vehicleEntity(USER_B, "other-vehicle", "Other"))
        val result = repository.createTrip(
            DriveTripDraft(
                vehicleId = "other-vehicle",
                startedAt = Instant.EPOCH,
                distanceKm = 3.0
            )
        )

        assertTrue(result is DriveMutationResult.ValidationFailed)
        assertTrue(database.driveTripDao().getTripsForVehicle(USER_A, "other-vehicle").isEmpty())
    }

    @Test
    fun `scheduler failure preserves the successful local write`() = runBlocking {
        scheduler.throwOnSchedule = true
        val result = repository.createVehicle(DriveVehicleDraft(displayName = "Offline car"))

        assertTrue(result is DriveMutationResult.LocalSavedSyncSchedulingFailed)
        val vehicle = when (result) {
            is DriveMutationResult.LocalSavedSyncSchedulingFailed -> result.value
            else -> error("Expected a locally saved mutation")
        }
        assertEquals("Offline car", database.driveVehicleDao().getVehicle(USER_A, vehicle.id)?.displayName)
        assertEquals("CREATE_VEHICLE", vehicleOperation(vehicle.id)?.operationType)
    }

    @Test
    fun `disabled feature neither exposes nor writes drive data`() = runBlocking {
        val disabled = OfflineFirstDriveRepository(
            database = database,
            authenticatedUid = { USER_A },
            syncScheduler = scheduler,
            authenticatedUidChanges = MutableStateFlow(USER_A),
            enabled = false
        )

        assertTrue(disabled.observeVehicles().first().isEmpty())
        assertTrue(
            disabled.createVehicle(DriveVehicleDraft(displayName = "Hidden"))
                is DriveMutationResult.StorageFailure
        )
        assertFalse(database.driveVehicleDao().observeActiveVehicles(USER_A).first().isNotEmpty())
    }

    @Test
    fun `bulk delete atomically tombstones selected vehicles and linked trips`() = runBlocking {
        val first = successValue(repository.createVehicle(DriveVehicleDraft(displayName = "One")))
        val second = successValue(repository.createVehicle(DriveVehicleDraft(displayName = "Two")))
        val trip = successValue(
            repository.createTrip(
                DriveTripDraft(
                    vehicleId = first.id,
                    startedAt = Instant.EPOCH,
                    distanceKm = 3.0
                )
            )
        )

        val result = repository.bulkDeleteVehicles(setOf(first.id, second.id))

        assertEquals(2, successValue(result))
        assertTrue(repository.observeVehicles().first().isEmpty())
        assertTrue(database.driveVehicleDao().getVehicle(USER_A, first.id)?.deletedAt != null)
        assertTrue(database.driveVehicleDao().getVehicle(USER_A, second.id)?.deletedAt != null)
        assertTrue(database.driveTripDao().getTrip(USER_A, trip.id)?.deletedAt != null)
        assertEquals("DELETE_VEHICLE", vehicleOperation(first.id)?.operationType)
    }

    @Test
    fun `local edit changes only edited field provenance`() = runBlocking {
        val vehicle = successValue(
            repository.createVehicle(
                DriveVehicleDraft(displayName = "Car", brand = "Remote brand")
            )
        )
        database.driveFieldProvenanceDao().upsertAll(
            listOf(
                com.example.toplutasima.data.local.entity.DriveFieldProvenanceEntity(
                    userId = USER_A,
                    entityType = DriveSyncEntityType.VEHICLE.name,
                    recordId = vehicle.id,
                    fieldName = "brand",
                    source = "REMOTE",
                    updatedAt = NOW
                )
            )
        )

        repository.updateVehicle(
            vehicle.id,
            DriveVehicleDraft(displayName = "Edited", brand = "Remote brand")
        )

        val provenance = database.driveFieldProvenanceDao().getForRecord(
            USER_A,
            DriveSyncEntityType.VEHICLE.name,
            vehicle.id
        ).associateBy { it.fieldName }
        assertEquals("LOCAL", provenance.getValue("displayName").source)
        assertEquals("REMOTE", provenance.getValue("brand").source)
    }

    private suspend fun vehicleOperation(vehicleId: String) =
        database.driveSyncOperationDao().getOperation(
            USER_A,
            DriveSyncEntityType.VEHICLE.name,
            vehicleId
        )

    private fun vehicleEntity(userId: String, id: String, name: String) = DriveVehicleEntity(
        id = id,
        userId = userId,
        displayName = name,
        createdAt = NOW,
        updatedAt = NOW,
        syncState = "SYNCED"
    )

    private fun <T> successValue(result: DriveMutationResult<T>): T = when (result) {
        is DriveMutationResult.Success -> result.value
        else -> error("Expected a successful local mutation")
    }

    private class RecordingScheduler : DriveSyncWorkScheduler {
        val scheduledOwners = mutableListOf<String>()
        var throwOnSchedule = false

        override fun schedule(ownerUid: String) {
            if (throwOnSchedule) error("scheduler unavailable")
            scheduledOwners += ownerUid
        }
    }

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
        const val NOW = 1_700_000_000_000L
    }
}
