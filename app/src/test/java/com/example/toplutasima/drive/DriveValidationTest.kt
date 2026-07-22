package com.example.toplutasima.drive

import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.validation.DriveDecimalParseResult
import com.example.toplutasima.drive.validation.DriveDecimalParser
import com.example.toplutasima.drive.validation.DriveTripValidator
import com.example.toplutasima.drive.validation.DriveValidationCode
import com.example.toplutasima.drive.validation.DriveVehicleValidator
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveValidationTest {
    @Test
    fun `vehicle validation rejects invalid identity year and odometers`() {
        val issues = DriveVehicleValidator(currentYear = { 2026 }).validate(
            DriveVehicleDraft(
                displayName = " ",
                modelYear = 1800,
                initialOdometerKm = 100.0,
                currentOdometerKm = 90.0
            )
        )
        assertEquals(
            setOf(
                DriveValidationCode.DISPLAY_NAME_REQUIRED,
                DriveValidationCode.MODEL_YEAR_OUT_OF_RANGE,
                DriveValidationCode.CURRENT_ODOMETER_BEFORE_INITIAL
            ),
            issues.map { it.code }.toSet()
        )
    }

    @Test
    fun `validation rejects non finite kilometres`() {
        val vehicleIssues = DriveVehicleValidator(currentYear = { 2026 }).validate(
            DriveVehicleDraft(
                displayName = "Vehicle",
                initialOdometerKm = Double.NaN
            )
        )
        val tripResult = DriveTripValidator().validate(
            DriveTripDraft(
                vehicleId = "vehicle",
                startedAt = Instant.EPOCH,
                distanceKm = Double.POSITIVE_INFINITY
            ),
            vehicleExistsForOwner = true
        )

        assertTrue(vehicleIssues.any { it.code == DriveValidationCode.NEGATIVE_ODOMETER })
        assertTrue(tripResult.issues.any { it.code == DriveValidationCode.NEGATIVE_DISTANCE })
    }

    @Test
    fun `unknown fuel value safely falls back`() {
        assertEquals(VehicleFuelType.UNKNOWN, VehicleFuelType.fromStorage("hydrogen_future"))
        assertEquals(VehicleFuelType.ELECTRIC, VehicleFuelType.fromStorage("electric"))
    }

    @Test
    fun `trip derives decimal distance from odometers`() {
        val result = DriveTripValidator().validate(
            DriveTripDraft(
                vehicleId = "vehicle",
                startedAt = Instant.parse("2026-07-19T08:00:00Z"),
                startOdometerKm = 10.25,
                endOdometerKm = 22.75
            ),
            vehicleExistsForOwner = true
        )
        assertTrue(result.isValid)
        assertEquals(12.5, result.resolvedDistanceKm!!, 0.0001)
    }

    @Test
    fun `trip rejects end before start and inconsistent distance`() {
        val result = DriveTripValidator().validate(
            DriveTripDraft(
                vehicleId = "vehicle",
                startedAt = Instant.parse("2026-07-19T10:00:00Z"),
                endedAt = Instant.parse("2026-07-19T09:00:00Z"),
                startOdometerKm = 10.0,
                endOdometerKm = 20.0,
                distanceKm = 4.0
            ),
            vehicleExistsForOwner = true
        )
        assertFalse(result.isValid)
        assertTrue(result.issues.any { it.code == DriveValidationCode.END_BEFORE_START })
        assertTrue(result.issues.any { it.code == DriveValidationCode.DISTANCE_ODOMETER_MISMATCH })
    }

    @Test
    fun `trip relation is UID scoped through owned vehicle check`() {
        val result = DriveTripValidator().validate(
            DriveTripDraft(
                vehicleId = "other-users-vehicle",
                startedAt = Instant.EPOCH,
                distanceKm = 5.0
            ),
            vehicleExistsForOwner = false
        )
        assertTrue(result.issues.any { it.code == DriveValidationCode.VEHICLE_NOT_FOUND })
    }

    @Test
    fun `decimal parser accepts comma and dot but rejects ambiguity`() {
        assertEquals(12.5, (DriveDecimalParser.parse("12,5") as DriveDecimalParseResult.Valid).value, 0.0)
        assertEquals(12.5, (DriveDecimalParser.parse("12.5") as DriveDecimalParseResult.Valid).value, 0.0)
        assertEquals(DriveDecimalParseResult.Invalid, DriveDecimalParser.parse("1,234.5"))
        assertEquals(DriveDecimalParseResult.Empty, DriveDecimalParser.parse("  "))
    }
}
