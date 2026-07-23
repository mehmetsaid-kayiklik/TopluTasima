package com.example.toplutasima.drive.provenance

import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity

fun changedVehicleFields(
    before: DriveVehicleEntity,
    after: DriveVehicleEntity
): Set<String> = buildSet {
    if (before.displayName != after.displayName) add("displayName")
    if (before.brand != after.brand) add("brand")
    if (before.model != after.model) add("model")
    if (before.licensePlate != after.licensePlate) add("licensePlate")
    if (before.modelYear != after.modelYear) add("modelYear")
    if (before.fuelType != after.fuelType) add("fuelType")
    if (before.initialOdometerKm != after.initialOdometerKm) add("initialOdometerKm")
    if (before.currentOdometerKm != after.currentOdometerKm) add("currentOdometerKm")
    if (before.assignedPersonId != after.assignedPersonId) add("assignedPersonId")
    if (before.notes != after.notes) add("notes")
    if (before.countryCode != after.countryCode) add("countryCode")
    if (before.transmissionType != after.transmissionType) add("transmissionType")
    if (before.bodyType != after.bodyType) add("bodyType")
    if (before.color != after.color) add("color")
    if (before.vin != after.vin) add("vin")
    if (before.engineDisplacementCc != after.engineDisplacementCc) add("engineDisplacementCc")
    if (before.enginePowerKw != after.enginePowerKw) add("enginePowerKw")
    if (before.purchaseDate != after.purchaseDate) add("purchaseDate")
    if (before.purchasePriceMinor != after.purchasePriceMinor) add("purchasePriceMinor")
    if (before.currencyCode != after.currencyCode) add("currencyCode")
    if (before.trimLevel != after.trimLevel) add("trimLevel")
    if (before.engineCode != after.engineCode) add("engineCode")
    if (before.registrationDate != after.registrationDate) add("registrationDate")
    if (before.inspectionDueDate != after.inspectionDueDate) add("inspectionDueDate")
    if (before.insuranceDueDate != after.insuranceDueDate) add("insuranceDueDate")
    if (before.tireSize != after.tireSize) add("tireSize")
}

fun changedTripFields(before: DriveTripEntity, after: DriveTripEntity): Set<String> = buildSet {
    if (before.vehicleId != after.vehicleId) add("vehicleId")
    if (before.startedAt != after.startedAt) add("startedAt")
    if (before.endedAt != after.endedAt) add("endedAt")
    if (before.startOdometerKm != after.startOdometerKm) add("startOdometerKm")
    if (before.endOdometerKm != after.endOdometerKm) add("endOdometerKm")
    if (before.distanceKm != after.distanceKm) add("distanceKm")
    if (before.purpose != after.purpose) add("purpose")
    if (before.startLocationName != after.startLocationName) add("startLocationName")
    if (before.endLocationName != after.endLocationName) add("endLocationName")
    if (before.notes != after.notes) add("notes")
    if (before.entrySource != after.entrySource) add("entrySource")
}
