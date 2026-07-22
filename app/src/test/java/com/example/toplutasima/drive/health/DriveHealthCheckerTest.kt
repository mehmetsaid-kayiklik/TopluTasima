package com.example.toplutasima.drive.health

import com.example.toplutasima.drive.model.DriveFieldProvenance
import com.example.toplutasima.drive.model.DriveFieldSource
import com.example.toplutasima.drive.model.DriveHealthCode
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripEntrySource
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.provenance.DriveProvenanceFields
import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveHealthCheckerTest {
    @Test
    fun `detects odometer mismatch missing plate and unknown provenance`() {
        val issues = DriveHealthChecker.scan(
            vehicles = listOf(vehicle()),
            trips = listOf(trip(startOdometer = 10.0, endOdometer = 15.0, distance = 2.0))
        )
        assertTrue(issues.any { it.code == DriveHealthCode.MISSING_LICENSE_PLATE })
        assertTrue(issues.any { it.code == DriveHealthCode.ODOMETER_DISTANCE_MISMATCH })
        assertTrue(issues.any { it.code == DriveHealthCode.UNKNOWN_PROVENANCE })
    }

    @Test
    fun `detects orphan negative and broken date`() {
        val broken = trip(vehicleId = "missing", distance = -1.0).copy(
            endedAt = Instant.ofEpochMilli(50L)
        )
        val issues = DriveHealthChecker.scan(listOf(vehicle()), listOf(broken))
        assertTrue(issues.any { it.code == DriveHealthCode.ORPHAN_TRIP })
        assertTrue(issues.any { it.code == DriveHealthCode.NEGATIVE_DISTANCE })
        assertTrue(issues.any { it.code == DriveHealthCode.END_BEFORE_START })
    }

    @Test
    fun `duplicate candidates are central and deterministic`() {
        val first = trip(id = "first")
        val second = trip(id = "second").copy(startedAt = first.startedAt.plusSeconds(30))
        val issues = DriveHealthChecker.scan(listOf(vehicle()), listOf(first, second))
        assertTrue(issues.count { it.code == DriveHealthCode.POSSIBLE_DUPLICATE } == 2)
    }

    @Test
    fun `complete provenance avoids unknown warning`() {
        val provenance = DriveProvenanceFields.VEHICLE_FIELDS.map {
            DriveFieldProvenance("VEHICLE", "vehicle", it, DriveFieldSource.LOCAL, Instant.EPOCH)
        } + DriveProvenanceFields.TRIP_FIELDS.map {
            DriveFieldProvenance("TRIP", "trip", it, DriveFieldSource.LOCAL, Instant.EPOCH)
        }
        val issues = DriveHealthChecker.scan(listOf(vehicle()), listOf(trip()), provenance)
        assertFalse(issues.any { it.code == DriveHealthCode.UNKNOWN_PROVENANCE })
    }

    private fun vehicle() = DriveVehicle(
        id = "vehicle",
        ownerUid = "owner",
        displayName = "Car",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )

    private fun trip(
        id: String = "trip",
        vehicleId: String = "vehicle",
        startOdometer: Double? = null,
        endOdometer: Double? = null,
        distance: Double = 5.0
    ) = DriveTrip(
        id = id,
        ownerUid = "owner",
        vehicleId = vehicleId,
        startedAt = Instant.ofEpochMilli(100L),
        startOdometerKm = startOdometer,
        endOdometerKm = endOdometer,
        distanceKm = distance,
        purpose = DriveTripPurpose.PERSONAL,
        entrySource = DriveTripEntrySource.MANUAL,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH
    )
}
