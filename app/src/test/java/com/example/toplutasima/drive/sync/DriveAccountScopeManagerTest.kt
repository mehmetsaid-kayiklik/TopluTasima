package com.example.toplutasima.drive.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveSyncMetadataEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.data.local.entity.TripEntity
import com.example.toplutasima.drive.model.DriveSyncState
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, manifest = Config.NONE, sdk = [28])
class DriveAccountScopeManagerTest {
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
    fun `account switch purges every old UID drive row and preserves transit`() = runBlocking {
        insertDriveData(USER_A)
        insertDriveData(USER_B)
        database.tripDao().upsertAll(listOf(TripEntity(id = "transit", userId = USER_A)))
        val manager = manager(USER_B)

        manager.purgeOutsideActiveAccount(USER_B)

        assertNull(database.driveVehicleDao().getVehicle(USER_A, "vehicle-$USER_A"))
        assertNull(database.driveTripDao().getTrip(USER_A, "trip-$USER_A"))
        assertNull(database.driveSyncMetadataDao().get(USER_A))
        assertNotNull(database.driveVehicleDao().getVehicle(USER_B, "vehicle-$USER_B"))
        assertNotNull(database.driveTripDao().getTrip(USER_B, "trip-$USER_B"))
        assertNotNull(database.tripDao().getTripById(USER_A, "transit"))
    }

    @Test
    fun `logout clears all drive users`() = runBlocking {
        insertDriveData(USER_A)
        insertDriveData(USER_B)

        manager(null).purgeOutsideActiveAccount(null)

        assertNull(database.driveVehicleDao().getVehicle(USER_A, "vehicle-$USER_A"))
        assertNull(database.driveVehicleDao().getVehicle(USER_B, "vehicle-$USER_B"))
        assertNull(database.driveSyncMetadataDao().get(USER_A))
        assertNull(database.driveSyncMetadataDao().get(USER_B))
    }

    private suspend fun insertDriveData(userId: String) {
        database.driveVehicleDao().upsert(
            DriveVehicleEntity(
                id = "vehicle-$userId",
                userId = userId,
                displayName = "Car",
                createdAt = 1L,
                updatedAt = 1L,
                syncState = DriveSyncState.SYNCED.name
            )
        )
        database.driveTripDao().upsert(
            DriveTripEntity(
                id = "trip-$userId",
                userId = userId,
                vehicleId = "vehicle-$userId",
                startedAt = 1L,
                distanceKm = 1.0,
                purpose = "PERSONAL",
                entrySource = "MANUAL",
                createdAt = 1L,
                updatedAt = 1L,
                syncState = DriveSyncState.SYNCED.name
            )
        )
        database.driveSyncMetadataDao().upsert(
            DriveSyncMetadataEntity(
                userId = userId,
                initialHydrationCompleted = true,
                updatedAt = 1L
            )
        )
    }

    private fun manager(currentUser: String?) = DriveAccountScopeManager(
        databaseProvider = { database },
        onAuthenticatedUserChanged = {},
        authChanges = emptyFlow(),
        currentUserId = { currentUser }
    )

    private companion object {
        const val USER_A = "user-a"
        const val USER_B = "user-b"
    }
}
