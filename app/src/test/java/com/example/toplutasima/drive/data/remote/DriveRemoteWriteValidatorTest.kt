package com.example.toplutasima.drive.data.remote

import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.model.DriveSyncState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveRemoteWriteValidatorTest {
    @Test
    fun `finite vehicle odometers are accepted`() {
        assertTrue(DriveRemoteWriteValidator.isValid(vehicle()))
    }

    @Test
    fun `NaN vehicle odometer is rejected before Firebase`() {
        assertFalse(
            DriveRemoteWriteValidator.isValid(
                vehicle().copy(currentOdometerKm = Double.NaN)
            )
        )
    }

    @Test
    fun `infinite trip distance is rejected before Firebase`() {
        assertFalse(
            DriveRemoteWriteValidator.isValid(
                trip().copy(distanceKm = Double.POSITIVE_INFINITY)
            )
        )
    }

    @Test
    fun `NaN single odometer endpoint is rejected before Firebase`() {
        assertFalse(
            DriveRemoteWriteValidator.isValid(
                trip().copy(startOdometerKm = Double.NaN, endOdometerKm = null)
            )
        )
    }

    private fun vehicle() = DriveVehicleEntity(
        id = "vehicle-a",
        userId = "user-a",
        displayName = "Vehicle",
        initialOdometerKm = 100.0,
        currentOdometerKm = 120.0,
        createdAt = 100L,
        updatedAt = 100L,
        syncState = DriveSyncState.LOCAL_PENDING.name
    )

    private fun trip() = DriveTripEntity(
        id = "trip-a",
        userId = "user-a",
        vehicleId = "vehicle-a",
        startedAt = 100L,
        distanceKm = 12.5,
        purpose = "PERSONAL",
        entrySource = "MANUAL",
        createdAt = 100L,
        updatedAt = 100L,
        syncState = DriveSyncState.LOCAL_PENDING.name
    )
}
