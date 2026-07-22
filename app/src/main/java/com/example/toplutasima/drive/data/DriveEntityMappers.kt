package com.example.toplutasima.drive.data

import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripEntrySource
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.VehicleFuelType
import java.time.Instant

fun DriveVehicleEntity.toDomain(): DriveVehicle = DriveVehicle(
    id = id,
    ownerUid = userId,
    displayName = displayName,
    brand = brand,
    model = model,
    licensePlate = licensePlate,
    modelYear = modelYear,
    fuelType = VehicleFuelType.fromStorage(fuelType),
    initialOdometerKm = initialOdometerKm,
    currentOdometerKm = currentOdometerKm,
    assignedPersonId = assignedPersonId,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    deletePending = deletedAt != null && syncState != DriveSyncState.SYNCED.name,
    syncState = DriveSyncState.fromStorage(syncState)
)

fun DriveVehicle.toEntity(): DriveVehicleEntity = DriveVehicleEntity(
    id = id,
    userId = ownerUid,
    displayName = displayName.trim(),
    brand = brand.normalizedOrNull(),
    model = model.normalizedOrNull(),
    licensePlate = licensePlate.normalizedOrNull(),
    modelYear = modelYear,
    fuelType = fuelType.name,
    initialOdometerKm = initialOdometerKm,
    currentOdometerKm = currentOdometerKm,
    assignedPersonId = assignedPersonId.normalizedOrNull(),
    notes = notes.normalizedOrNull(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncState = syncState.name
)

fun DriveTripEntity.toDomain(): DriveTrip = DriveTrip(
    id = id,
    ownerUid = userId,
    vehicleId = vehicleId,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let(Instant::ofEpochMilli),
    startOdometerKm = startOdometerKm,
    endOdometerKm = endOdometerKm,
    distanceKm = distanceKm,
    purpose = DriveTripPurpose.fromStorage(purpose),
    startLocationName = startLocationName,
    endLocationName = endLocationName,
    notes = notes,
    entrySource = DriveTripEntrySource.fromStorage(entrySource),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
    deletePending = deletedAt != null && syncState != DriveSyncState.SYNCED.name,
    syncState = DriveSyncState.fromStorage(syncState)
)

fun DriveTrip.toEntity(): DriveTripEntity = DriveTripEntity(
    id = id,
    userId = ownerUid,
    vehicleId = vehicleId,
    startedAt = startedAt.toEpochMilli(),
    endedAt = endedAt?.toEpochMilli(),
    startOdometerKm = startOdometerKm,
    endOdometerKm = endOdometerKm,
    distanceKm = distanceKm,
    purpose = purpose.name,
    startLocationName = startLocationName.normalizedOrNull(),
    endLocationName = endLocationName.normalizedOrNull(),
    notes = notes.normalizedOrNull(),
    entrySource = entrySource.name,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
    syncState = syncState.name
)

private fun String?.normalizedOrNull(): String? = this?.trim()?.takeIf(String::isNotEmpty)
