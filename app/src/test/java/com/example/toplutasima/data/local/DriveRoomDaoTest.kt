package com.example.toplutasima.data.local

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.dao.DriveVehicleWithSummary
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class DriveRoomDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `vehicle and trip queries are isolated by UID`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle(userId = USER_A, name = "A vehicle"))
        database.driveVehicleDao().upsert(vehicle(userId = USER_B, name = "B vehicle"))
        database.driveTripDao().upsert(trip(userId = USER_A, distanceKm = 12.5))
        database.driveTripDao().upsert(trip(userId = USER_B, distanceKm = 7.25))

        assertEquals("A vehicle", database.driveVehicleDao().getVehicle(USER_A, VEHICLE_ID)?.displayName)
        assertEquals("B vehicle", database.driveVehicleDao().getVehicle(USER_B, VEHICLE_ID)?.displayName)
        assertEquals(12.5, database.driveTripDao().observeSummary(USER_A, VEHICLE_ID).firstValue().totalDistanceKm, 0.0)
        assertEquals(7.25, database.driveTripDao().observeSummary(USER_B, VEHICLE_ID).firstValue().totalDistanceKm, 0.0)
        assertNull(database.driveVehicleDao().getVehicle("unknown-user", VEHICLE_ID))
    }

    @Test
    fun `trip cannot reference a vehicle owned by another UID`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle(userId = USER_A))

        try {
            database.driveTripDao().upsert(trip(userId = USER_B))
            fail("Cross-UID vehicle reference must violate the composite foreign key")
        } catch (_: SQLiteConstraintException) {
            // Expected: (userId, vehicleId) must match one vehicle row owned by the same UID.
        }
    }

    @Test
    fun `summary flow updates and excludes trip tombstones`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle())
        val emissions = Channel<DriveVehicleWithSummary?>(Channel.UNLIMITED)
        val collection = launch(Dispatchers.Default) {
            database.driveVehicleDao()
                .observeActiveVehicleWithSummary(USER_A, VEHICLE_ID)
                .collect(emissions::send)
        }

        assertEquals(0.0, emissions.next()!!.totalDistanceKm, 0.0)
        database.driveTripDao().upsert(trip(id = "trip-1", startedAt = 100, distanceKm = 4.75))
        assertEquals(4.75, emissions.next()!!.totalDistanceKm, 0.0)
        database.driveTripDao().upsert(trip(id = "trip-2", startedAt = 200, distanceKm = 6.25))
        val withTwoTrips = emissions.next()!!
        assertEquals(11.0, withTwoTrips.totalDistanceKm, 0.0)
        assertEquals(2, withTwoTrips.tripCount)
        assertEquals(200L, withTwoTrips.lastUsedAt)

        database.driveTripDao().markDeleted(
            userId = USER_A,
            id = "trip-2",
            deletedAt = 300,
            updatedAt = 300,
            syncState = "PENDING_DELETE"
        )
        val afterDelete = emissions.next()!!
        assertEquals(4.75, afterDelete.totalDistanceKm, 0.0)
        assertEquals(1, afterDelete.tripCount)
        assertEquals(100L, afterDelete.lastUsedAt)
        assertNotNull(database.driveTripDao().getTrip(USER_A, "trip-2")?.deletedAt)

        collection.cancelAndJoin()
    }

    @Test
    fun `vehicle tombstone is hidden immediately and retained`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle())

        assertEquals(1, database.driveVehicleDao().observeActiveVehicles(USER_A).firstValue().size)
        database.driveVehicleDao().markDeleted(
            userId = USER_A,
            id = VEHICLE_ID,
            deletedAt = 50,
            updatedAt = 50,
            syncState = "PENDING_DELETE"
        )

        assertTrue(database.driveVehicleDao().observeActiveVehicles(USER_A).firstValue().isEmpty())
        assertEquals(listOf(VEHICLE_ID), database.driveVehicleDao().getTombstones(USER_A).map { it.id })
    }

    @Test
    fun `vehicle cascade tombstones only trips that were active`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle())
        database.driveTripDao().upsert(trip(id = "active"))
        database.driveTripDao().upsert(
            trip(id = "already-deleted").copy(
                updatedAt = 20,
                deletedAt = 20,
                syncState = "SYNCED"
            )
        )

        assertEquals(
            listOf("active"),
            database.driveTripDao().getActiveTripIdsForVehicle(USER_A, VEHICLE_ID)
        )
        assertEquals(
            1,
            database.driveTripDao().markDeletedForVehicle(
                userId = USER_A,
                vehicleId = VEHICLE_ID,
                deletedAt = 30,
                updatedAt = 30,
                syncState = "PENDING_DELETE"
            )
        )

        assertEquals("PENDING_DELETE", database.driveTripDao().getTrip(USER_A, "active")?.syncState)
        val existingTombstone = database.driveTripDao().getTrip(USER_A, "already-deleted")
        assertEquals(20L, existingTombstone?.deletedAt)
        assertEquals("SYNCED", existingTombstone?.syncState)
    }

    @Test
    fun `stale queue completion cannot remove superseding delete`() = runBlocking {
        val queue = database.driveSyncOperationDao()
        queue.upsert(operation(operationId = "update-token", operationType = "UPDATE_VEHICLE"))
        queue.upsert(operation(operationId = "delete-token", operationType = "DELETE_VEHICLE"))

        assertEquals(
            0,
            queue.deleteIfCurrent(USER_A, "VEHICLE", VEHICLE_ID, "update-token")
        )
        assertEquals("DELETE_VEHICLE", queue.getOperation(USER_A, "VEHICLE", VEHICLE_ID)?.operationType)
        assertEquals(
            1,
            queue.recordAttemptIfCurrent(
                userId = USER_A,
                entityType = "VEHICLE",
                recordId = VEHICLE_ID,
                operationId = "delete-token",
                updatedAt = 60,
                lastErrorCode = "UNAVAILABLE",
                retryEligible = true,
                nextAttemptAt = 100
            )
        )
        assertEquals(1, queue.getOperation(USER_A, "VEHICLE", VEHICLE_ID)?.attemptCount)
        assertEquals(0, queue.pendingCount(USER_B))
    }

    @Test
    fun `large vehicle fixture remains vehicle and UID scoped`() = runBlocking {
        database.driveVehicleDao().upsert(vehicle())
        database.driveVehicleDao().upsert(vehicle(id = "other-vehicle", name = "Other"))
        database.driveVehicleDao().upsert(vehicle(userId = USER_B))
        database.driveTripDao().upsertAll(
            (0 until LARGE_FIXTURE_SIZE).flatMap { index ->
                listOf(
                    trip(id = "selected-$index", distanceKm = 0.1),
                    trip(id = "other-$index", vehicleId = "other-vehicle", distanceKm = 1.0),
                    trip(id = "other-user-$index", userId = USER_B, distanceKm = 2.0)
                )
            }
        )

        val summary = database.driveTripDao().observeSummary(USER_A, VEHICLE_ID).firstValue()
        assertEquals(LARGE_FIXTURE_SIZE, summary.tripCount)
        assertEquals(100.0, summary.totalDistanceKm, 0.000_001)
    }

    private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.firstValue(): T =
        first()

    private suspend fun Channel<DriveVehicleWithSummary?>.next(): DriveVehicleWithSummary? =
        withTimeout(5_000) { receive() }

    private fun vehicle(
        id: String = VEHICLE_ID,
        userId: String = USER_A,
        name: String = "Vehicle"
    ) = DriveVehicleEntity(
        id = id,
        userId = userId,
        displayName = name,
        initialOdometerKm = 1_000.0,
        createdAt = 1,
        updatedAt = 1,
        syncState = "PENDING_CREATE"
    )

    private fun trip(
        id: String = TRIP_ID,
        userId: String = USER_A,
        vehicleId: String = VEHICLE_ID,
        startedAt: Long = 10,
        distanceKm: Double = 5.5
    ) = DriveTripEntity(
        id = id,
        userId = userId,
        vehicleId = vehicleId,
        startedAt = startedAt,
        distanceKm = distanceKm,
        purpose = "PERSONAL",
        entrySource = "MANUAL",
        createdAt = startedAt,
        updatedAt = startedAt,
        syncState = "PENDING_CREATE"
    )

    private fun operation(
        operationId: String,
        operationType: String
    ) = DriveSyncOperationEntity(
        operationId = operationId,
        userId = USER_A,
        entityType = "VEHICLE",
        recordId = VEHICLE_ID,
        operationType = operationType,
        createdAt = 1,
        updatedAt = 1
    )

    private companion object {
        const val USER_A = "user-A"
        const val USER_B = "user-B"
        const val VEHICLE_ID = "vehicle-1"
        const val TRIP_ID = "trip-1"
        const val LARGE_FIXTURE_SIZE = 1_000
    }
}
