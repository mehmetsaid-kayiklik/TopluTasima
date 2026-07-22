package com.example.toplutasima

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveRoomDeviceTest {
    private val context: Context
        get() = TestDatabaseFactory.targetContext()

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun driveSummaryFlowTracksTripInsertAndTombstone() = runBlocking {
        val database = TestDatabaseFactory.createInMemoryDatabase()
        try {
            database.driveVehicleDao().upsert(vehicle())
            assertEquals(
                0.0,
                database.driveTripDao().observeSummary(USER_A, VEHICLE_ID).first().totalDistanceKm,
                0.0
            )

            database.driveTripDao().upsert(trip(id = "trip-1", distanceKm = 12.5, startedAt = 100))
            database.driveTripDao().upsert(trip(id = "trip-2", distanceKm = 7.25, startedAt = 200))
            val summary = database.driveTripDao().observeSummary(USER_A, VEHICLE_ID).first()
            assertEquals(19.75, summary.totalDistanceKm, 0.0)
            assertEquals(2, summary.tripCount)
            assertEquals(200L, summary.lastUsedAt)

            database.driveTripDao().markDeleted(
                userId = USER_A,
                id = "trip-2",
                deletedAt = 300,
                updatedAt = 300,
                syncState = "PENDING_DELETE"
            )
            val afterDelete = database.driveTripDao().observeSummary(USER_A, VEHICLE_ID).first()
            assertEquals(12.5, afterDelete.totalDistanceKm, 0.0)
            assertEquals(1, afterDelete.tripCount)
            assertEquals(100L, afterDelete.lastUsedAt)
            assertNotNull(database.driveTripDao().getTrip(USER_A, "trip-2")?.deletedAt)
        } finally {
            database.close()
        }
    }

    @Test
    fun driveRowsSurviveDatabaseReopenAndRemainUidIsolated() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        val database = openDiskDatabase()
        try {
            database.driveVehicleDao().upsert(vehicle(userId = USER_A, name = "A vehicle"))
            database.driveVehicleDao().upsert(vehicle(userId = USER_B, name = "B vehicle"))
            database.driveTripDao().upsert(trip(userId = USER_A, distanceKm = 4.5))
            database.driveTripDao().upsert(trip(userId = USER_B, distanceKm = 8.0))
        } finally {
            database.close()
        }

        val reopened = openDiskDatabase()
        try {
            assertEquals("A vehicle", reopened.driveVehicleDao().getVehicle(USER_A, VEHICLE_ID)?.displayName)
            assertEquals("B vehicle", reopened.driveVehicleDao().getVehicle(USER_B, VEHICLE_ID)?.displayName)
            assertEquals(
                4.5,
                reopened.driveTripDao().observeSummary(USER_A, VEHICLE_ID).first().totalDistanceKm,
                0.0
            )
            assertEquals(
                8.0,
                reopened.driveTripDao().observeSummary(USER_B, VEHICLE_ID).first().totalDistanceKm,
                0.0
            )
            assertNull(reopened.driveVehicleDao().getVehicle("unknown-user", VEHICLE_ID))
        } finally {
            reopened.close()
        }
    }

    @Test
    fun compositeForeignKeyRejectsCrossUidVehicleReference() = runBlocking {
        val database = TestDatabaseFactory.createInMemoryDatabase()
        try {
            database.driveVehicleDao().upsert(vehicle(userId = USER_A))
            try {
                database.driveTripDao().upsert(trip(userId = USER_B))
                fail("Cross-UID drive trip must not be persisted")
            } catch (_: SQLiteConstraintException) {
                // Expected on a real device: the composite FK includes the UID.
            }
            assertTrue(database.driveTripDao().getTripsForVehicle(USER_B, VEHICLE_ID).isEmpty())
        } finally {
            database.close()
        }
    }

    private fun openDiskDatabase(): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()

    private fun vehicle(
        userId: String = USER_A,
        name: String = "Vehicle"
    ) = DriveVehicleEntity(
        id = VEHICLE_ID,
        userId = userId,
        displayName = name,
        createdAt = 1,
        updatedAt = 1,
        syncState = "PENDING_CREATE"
    )

    private fun trip(
        id: String = TRIP_ID,
        userId: String = USER_A,
        distanceKm: Double = 5.5,
        startedAt: Long = 10
    ) = DriveTripEntity(
        id = id,
        userId = userId,
        vehicleId = VEHICLE_ID,
        startedAt = startedAt,
        distanceKm = distanceKm,
        purpose = "PERSONAL",
        entrySource = "MANUAL",
        createdAt = startedAt,
        updatedAt = startedAt,
        syncState = "PENDING_CREATE"
    )

    private companion object {
        const val DATABASE_NAME = "drive-room-device-test.db"
        const val USER_A = "user-A"
        const val USER_B = "user-B"
        const val VEHICLE_ID = "vehicle-1"
        const val TRIP_ID = "trip-1"
    }
}
