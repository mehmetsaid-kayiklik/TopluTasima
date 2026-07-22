package com.example.toplutasima.drive.management

import com.example.toplutasima.drive.model.DriveVehicleAssignmentFilter
import com.example.toplutasima.drive.model.DriveVehicleListCriteria
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSort
import java.util.Locale

object DriveVehicleListProcessor {
    fun apply(
        vehicles: List<DriveVehicleOverview>,
        criteria: DriveVehicleListCriteria
    ): List<DriveVehicleOverview> {
        val query = criteria.query.trim().lowercase(Locale.ROOT)
        val filtered = vehicles.asSequence()
            .filter { overview ->
                query.isEmpty() || overview.searchableValues().any { value ->
                    value.lowercase(Locale.ROOT).contains(query)
                }
            }
            .filter { overview ->
                criteria.fuelType == null || overview.vehicle.fuelType == criteria.fuelType
            }
            .filter { overview ->
                when (criteria.assignment) {
                    DriveVehicleAssignmentFilter.ALL -> true
                    DriveVehicleAssignmentFilter.ASSIGNED ->
                        !overview.vehicle.assignedPersonId.isNullOrBlank()
                    DriveVehicleAssignmentFilter.UNASSIGNED ->
                        overview.vehicle.assignedPersonId.isNullOrBlank()
                }
            }
            .toList()

        val comparator = when (criteria.sort) {
            DriveVehicleSort.NAME -> compareBy<DriveVehicleOverview>(
                { it.vehicle.displayName.lowercase(Locale.ROOT) },
                { it.vehicle.id }
            )
            DriveVehicleSort.LICENSE_PLATE -> compareBy(
                { it.vehicle.licensePlate?.lowercase(Locale.ROOT).orEmpty() },
                { it.vehicle.displayName.lowercase(Locale.ROOT) },
                { it.vehicle.id }
            )
            DriveVehicleSort.FUEL_TYPE -> compareBy(
                { it.vehicle.fuelType.name },
                { it.vehicle.displayName.lowercase(Locale.ROOT) },
                { it.vehicle.id }
            )
            DriveVehicleSort.ASSIGNED_PERSON -> compareBy(
                { it.vehicle.assignedPersonId.isNullOrBlank() },
                { it.vehicle.displayName.lowercase(Locale.ROOT) },
                { it.vehicle.id }
            )
            DriveVehicleSort.LAST_USED -> compareBy(
                { it.summary.lastUsedAt },
                { it.vehicle.displayName.lowercase(Locale.ROOT) },
                { it.vehicle.id }
            )
            DriveVehicleSort.TOTAL_DISTANCE -> compareBy(
                { it.summary.totalDistanceKm },
                { it.vehicle.displayName.lowercase(Locale.ROOT) },
                { it.vehicle.id }
            )
        }
        return filtered.sortedWith(if (criteria.descending) comparator.reversed() else comparator)
    }

    private fun DriveVehicleOverview.searchableValues(): List<String> = listOfNotNull(
        vehicle.displayName,
        vehicle.brand,
        vehicle.model,
        vehicle.licensePlate,
        vehicle.fuelType.name
    )
}
