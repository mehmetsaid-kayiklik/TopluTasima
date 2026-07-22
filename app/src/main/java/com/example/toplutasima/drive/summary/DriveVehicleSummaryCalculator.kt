package com.example.toplutasima.drive.summary

import com.example.toplutasima.drive.model.DriveOdometerSource
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleSummary
import java.time.Instant

object DriveVehicleSummaryCalculator {
    fun calculate(vehicle: DriveVehicle, trips: List<DriveTrip>): DriveVehicleSummary {
        val activeTrips = trips.asSequence()
            .filter { it.ownerUid == vehicle.ownerUid }
            .filter { it.vehicleId == vehicle.id }
            .filter { it.deletedAt == null }
            .toList()
        return fromAggregate(
            vehicle = vehicle,
            totalDistanceKm = activeTrips.sumOf { it.distanceKm },
            tripCount = activeTrips.size,
            lastUsedAt = activeTrips.maxOfOrNull { it.startedAt }
        )
    }

    fun fromAggregate(
        vehicle: DriveVehicle,
        totalDistanceKm: Double,
        tripCount: Int,
        lastUsedAt: Instant?
    ): DriveVehicleSummary {
        val safeTotal = totalDistanceKm.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        val estimated = vehicle.initialOdometerKm?.plus(safeTotal)
        val manual = vehicle.currentOdometerKm
        val displayed = manual ?: estimated
        val source = when {
            manual != null -> DriveOdometerSource.MANUAL
            estimated != null -> DriveOdometerSource.ESTIMATED
            else -> DriveOdometerSource.UNAVAILABLE
        }
        return DriveVehicleSummary(
            totalDistanceKm = safeTotal,
            tripCount = tripCount.coerceAtLeast(0),
            lastUsedAt = lastUsedAt,
            initialOdometerKm = vehicle.initialOdometerKm,
            manualCurrentOdometerKm = manual,
            estimatedCurrentOdometerKm = estimated,
            displayedCurrentOdometerKm = displayed,
            displayedOdometerSource = source,
            hasOdometerInconsistency = manual != null && estimated != null && manual < estimated
        )
    }
}
