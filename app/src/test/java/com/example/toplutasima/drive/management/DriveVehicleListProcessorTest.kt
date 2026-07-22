package com.example.toplutasima.drive.management

import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleAssignmentFilter
import com.example.toplutasima.drive.model.DriveVehicleListCriteria
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSort
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.VehicleFuelType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class DriveVehicleListProcessorTest {
    @Test
    fun `search matches name brand model and plate without locale dependence`() {
        val result = DriveVehicleListProcessor.apply(
            vehicles(),
            DriveVehicleListCriteria(query = "34 abc")
        )
        assertEquals(listOf("a"), result.map { it.vehicle.id })
    }

    @Test
    fun `fuel and assignment filters compose`() {
        val result = DriveVehicleListProcessor.apply(
            vehicles(),
            DriveVehicleListCriteria(
                fuelType = VehicleFuelType.ELECTRIC,
                assignment = DriveVehicleAssignmentFilter.UNASSIGNED
            )
        )
        assertEquals(listOf("b"), result.map { it.vehicle.id })
    }

    @Test
    fun `distance sort is deterministic in both directions`() {
        val ascending = DriveVehicleListProcessor.apply(
            vehicles(),
            DriveVehicleListCriteria(sort = DriveVehicleSort.TOTAL_DISTANCE)
        )
        val descending = DriveVehicleListProcessor.apply(
            vehicles(),
            DriveVehicleListCriteria(sort = DriveVehicleSort.TOTAL_DISTANCE, descending = true)
        )
        assertEquals(listOf("b", "a"), ascending.map { it.vehicle.id })
        assertEquals(listOf("a", "b"), descending.map { it.vehicle.id })
    }

    @Test
    fun `plate and last used sorts retain stable IDs`() {
        assertEquals(
            listOf("b", "a"),
            DriveVehicleListProcessor.apply(
                vehicles(),
                DriveVehicleListCriteria(sort = DriveVehicleSort.LICENSE_PLATE)
            ).map { it.vehicle.id }
        )
        assertEquals(
            listOf("b", "a"),
            DriveVehicleListProcessor.apply(
                vehicles(),
                DriveVehicleListCriteria(sort = DriveVehicleSort.LAST_USED)
            ).map { it.vehicle.id }
        )
    }

    private fun vehicles() = listOf(
        overview("a", "Family Car", "34 ABC 12", VehicleFuelType.PETROL, "person", 20.0, 200L),
        overview("b", "City EV", null, VehicleFuelType.ELECTRIC, null, 10.0, 100L)
    )

    private fun overview(
        id: String,
        name: String,
        plate: String?,
        fuel: VehicleFuelType,
        person: String?,
        distance: Double,
        lastUsed: Long
    ) = DriveVehicleOverview(
        vehicle = DriveVehicle(
            id = id,
            ownerUid = "owner",
            displayName = name,
            brand = if (id == "a") "Toyota" else "Tesla",
            model = if (id == "a") "Corolla" else "Model 3",
            licensePlate = plate,
            fuelType = fuel,
            assignedPersonId = person,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
            syncState = DriveSyncState.SYNCED
        ),
        summary = DriveVehicleSummary(
            totalDistanceKm = distance,
            tripCount = 1,
            lastUsedAt = Instant.ofEpochMilli(lastUsed),
            initialOdometerKm = null,
            manualCurrentOdometerKm = null,
            estimatedCurrentOdometerKm = null,
            displayedCurrentOdometerKm = null,
            displayedOdometerSource = DriveOdometerSource.UNAVAILABLE,
            hasOdometerInconsistency = false
        )
    )
}
