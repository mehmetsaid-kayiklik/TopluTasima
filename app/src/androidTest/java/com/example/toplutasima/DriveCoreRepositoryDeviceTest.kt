package com.example.toplutasima

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.repository.DriveIdGenerator
import com.example.toplutasima.drive.repository.DriveMutationResult
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import com.example.toplutasima.drive.repository.OfflineFirstDriveRepository
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DriveCoreRepositoryDeviceTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun vehicleAndManualTripAreImmediatelyVisibleAndUpdateLiveSummary() = runBlocking {
        val authUid = MutableStateFlow<String?>(USER_ID)
        val ids = AtomicInteger()
        val scheduledOwners = mutableListOf<String>()
        val repository = OfflineFirstDriveRepository(
            database = database,
            authenticatedUid = { authUid.value },
            syncScheduler = DriveSyncWorkScheduler { ownerUid ->
                scheduledOwners += ownerUid
            },
            authenticatedUidChanges = authUid,
            idGenerator = DriveIdGenerator { "device-${ids.incrementAndGet()}" },
            nowEpochMillis = { NOW }
        )

        val vehicleResult = repository.createVehicle(
            DriveVehicleDraft(displayName = "Device car", initialOdometerKm = 1_000.0)
        )
        assertTrue(vehicleResult is DriveMutationResult.Success)
        val vehicle = when (vehicleResult) {
            is DriveMutationResult.Success -> vehicleResult.value
            else -> error("Vehicle must be stored locally")
        }
        assertEquals(listOf("Device car"), repository.observeVehicles().first().map { it.displayName })

        val tripResult = repository.createTrip(
            DriveTripDraft(
                vehicleId = vehicle.id,
                startedAt = Instant.parse("2026-07-20T10:00:00Z"),
                startOdometerKm = 1_000.0,
                endOdometerKm = 1_012.5
            )
        )
        assertTrue(tripResult is DriveMutationResult.Success)

        val summary = repository.observeSummary(vehicle.id).first()!!
        assertEquals(12.5, summary.totalDistanceKm, 0.0)
        assertEquals(1, summary.tripCount)
        assertEquals(1_012.5, summary.estimatedCurrentOdometerKm!!, 0.0)
        assertEquals(listOf(USER_ID, USER_ID), scheduledOwners)

        authUid.value = OTHER_USER_ID
        assertTrue(repository.observeVehicles().first().isEmpty())
    }

    private companion object {
        const val USER_ID = "device-user"
        const val OTHER_USER_ID = "other-device-user"
        const val NOW = 1_700_000_000_000L
    }
}
