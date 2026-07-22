package com.example.toplutasima.drive

import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripEntrySource
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.summary.DriveVehicleSummaryCalculator
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveVehicleSummaryCalculatorTest {
    @Test
    fun `summary excludes deleted other vehicle and other UID trips`() {
        val vehicle = vehicle(initial = 100.0)
        val trips = listOf(
            trip("one", "user", "vehicle", 12.5, "2026-07-10T08:00:00Z"),
            trip("two", "user", "vehicle", 7.5, "2026-07-12T08:00:00Z"),
            trip("deleted", "user", "vehicle", 99.0, "2026-07-15T08:00:00Z", deleted = true),
            trip("other-vehicle", "user", "other", 50.0, "2026-07-16T08:00:00Z"),
            trip("other-user", "other-user", "vehicle", 50.0, "2026-07-17T08:00:00Z")
        )
        val summary = DriveVehicleSummaryCalculator.calculate(vehicle, trips)
        assertEquals(20.0, summary.totalDistanceKm, 0.0)
        assertEquals(2, summary.tripCount)
        assertEquals(Instant.parse("2026-07-12T08:00:00Z"), summary.lastUsedAt)
        assertEquals(120.0, summary.estimatedCurrentOdometerKm!!, 0.0)
        assertEquals(DriveOdometerSource.ESTIMATED, summary.displayedOdometerSource)
    }

    @Test
    fun `manual odometer remains separate and inconsistency is visible`() {
        val summary = DriveVehicleSummaryCalculator.fromAggregate(
            vehicle = vehicle(initial = 100.0, current = 105.0),
            totalDistanceKm = 20.0,
            tripCount = 2,
            lastUsedAt = Instant.EPOCH
        )
        assertEquals(105.0, summary.displayedCurrentOdometerKm!!, 0.0)
        assertEquals(120.0, summary.estimatedCurrentOdometerKm!!, 0.0)
        assertEquals(DriveOdometerSource.MANUAL, summary.displayedOdometerSource)
        assertTrue(summary.hasOdometerInconsistency)
    }

    @Test
    fun `valid manual odometer does not report inconsistency`() {
        val summary = DriveVehicleSummaryCalculator.fromAggregate(
            vehicle = vehicle(initial = 100.0, current = 130.0),
            totalDistanceKm = 20.0,
            tripCount = 2,
            lastUsedAt = null
        )
        assertFalse(summary.hasOdometerInconsistency)
    }

    private fun vehicle(initial: Double?, current: Double? = null) = DriveVehicle(
        id = "vehicle",
        ownerUid = "user",
        displayName = "Family car",
        initialOdometerKm = initial,
        currentOdometerKm = current,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun trip(
        id: String,
        ownerUid: String,
        vehicleId: String,
        distance: Double,
        startedAt: String,
        deleted: Boolean = false
    ) = DriveTrip(
        id = id,
        ownerUid = ownerUid,
        vehicleId = vehicleId,
        startedAt = Instant.parse(startedAt),
        distanceKm = distance,
        purpose = DriveTripPurpose.PERSONAL,
        entrySource = DriveTripEntrySource.MANUAL,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = Instant.EPOCH.takeIf { deleted }
    )
}
