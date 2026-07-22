package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.model.DriveFieldSource
import com.example.toplutasima.drive.model.DriveSyncState

data class DriveConflictResolution<T>(
    val entity: T,
    val provenance: Map<String, DriveFieldSource>,
    val clearPendingOperation: Boolean
)

object DriveConflictResolver {
    fun resolveVehicle(
        local: DriveVehicleEntity?,
        remote: DriveVehicleEntity,
        pendingOperation: DriveSyncOperationEntity?,
        localProvenance: Map<String, DriveFieldSource>
    ): DriveConflictResolution<DriveVehicleEntity> {
        require(remote.userId.isNotBlank()) { "Remote drive owner must not be blank" }
        if (local == null) {
            return DriveConflictResolution(
                entity = remote.copy(syncState = DriveSyncState.SYNCED.name),
                provenance = vehicleValues(remote).keys.associateWith { DriveFieldSource.REMOTE },
                clearPendingOperation = true
            )
        }
        require(local.userId == remote.userId && local.id == remote.id) {
            "Drive vehicle conflict scope mismatch"
        }
        if (remote.deletedAt != null) {
            return DriveConflictResolution(
                entity = local.copy(
                    deletedAt = remote.deletedAt,
                    updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                    syncState = DriveSyncState.SYNCED.name
                ),
                provenance = localProvenance,
                clearPendingOperation = true
            )
        }
        if (local.deletedAt != null || pendingOperation?.operationType ==
            DriveSyncOperationType.DELETE_VEHICLE.name
        ) {
            return DriveConflictResolution(local, localProvenance, clearPendingOperation = false)
        }
        if (pendingOperation == null) {
            return DriveConflictResolution(
                entity = remote.copy(syncState = DriveSyncState.SYNCED.name),
                provenance = vehicleValues(remote).keys.associateWith { DriveFieldSource.REMOTE },
                clearPendingOperation = true
            )
        }

        val sources = linkedMapOf<String, DriveFieldSource>()
        fun <T> choose(field: String, localValue: T, remoteValue: T): T = chooseValue(
            field,
            localValue,
            remoteValue,
            localProvenance,
            sources
        )
        return DriveConflictResolution(
            entity = local.copy(
                displayName = choose("displayName", local.displayName, remote.displayName),
                brand = choose("brand", local.brand, remote.brand),
                model = choose("model", local.model, remote.model),
                licensePlate = choose("licensePlate", local.licensePlate, remote.licensePlate),
                modelYear = choose("modelYear", local.modelYear, remote.modelYear),
                fuelType = choose("fuelType", local.fuelType, remote.fuelType),
                initialOdometerKm = choose(
                    "initialOdometerKm",
                    local.initialOdometerKm,
                    remote.initialOdometerKm
                ),
                currentOdometerKm = choose(
                    "currentOdometerKm",
                    local.currentOdometerKm,
                    remote.currentOdometerKm
                ),
                assignedPersonId = choose(
                    "assignedPersonId",
                    local.assignedPersonId,
                    remote.assignedPersonId
                ),
                notes = choose("notes", local.notes, remote.notes),
                createdAt = minOf(local.createdAt, remote.createdAt),
                updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                syncState = DriveSyncState.LOCAL_PENDING.name
            ),
            provenance = sources,
            clearPendingOperation = false
        )
    }

    fun resolveTrip(
        local: DriveTripEntity?,
        remote: DriveTripEntity,
        pendingOperation: DriveSyncOperationEntity?,
        localProvenance: Map<String, DriveFieldSource>
    ): DriveConflictResolution<DriveTripEntity> {
        require(remote.userId.isNotBlank()) { "Remote drive owner must not be blank" }
        if (local == null) {
            return DriveConflictResolution(
                entity = remote.copy(syncState = DriveSyncState.SYNCED.name),
                provenance = tripValues(remote).keys.associateWith { DriveFieldSource.REMOTE },
                clearPendingOperation = true
            )
        }
        require(local.userId == remote.userId && local.id == remote.id) {
            "Drive trip conflict scope mismatch"
        }
        if (remote.deletedAt != null) {
            return DriveConflictResolution(
                entity = local.copy(
                    deletedAt = remote.deletedAt,
                    updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                    syncState = DriveSyncState.SYNCED.name
                ),
                provenance = localProvenance,
                clearPendingOperation = true
            )
        }
        if (local.deletedAt != null || pendingOperation?.operationType ==
            DriveSyncOperationType.DELETE_DRIVE_TRIP.name
        ) {
            return DriveConflictResolution(local, localProvenance, clearPendingOperation = false)
        }
        if (pendingOperation == null) {
            return DriveConflictResolution(
                entity = remote.copy(syncState = DriveSyncState.SYNCED.name),
                provenance = tripValues(remote).keys.associateWith { DriveFieldSource.REMOTE },
                clearPendingOperation = true
            )
        }

        val sources = linkedMapOf<String, DriveFieldSource>()
        fun <T> choose(field: String, localValue: T, remoteValue: T): T = chooseValue(
            field,
            localValue,
            remoteValue,
            localProvenance,
            sources
        )
        return DriveConflictResolution(
            entity = local.copy(
                vehicleId = choose("vehicleId", local.vehicleId, remote.vehicleId),
                startedAt = choose("startedAt", local.startedAt, remote.startedAt),
                endedAt = choose("endedAt", local.endedAt, remote.endedAt),
                startOdometerKm = choose(
                    "startOdometerKm",
                    local.startOdometerKm,
                    remote.startOdometerKm
                ),
                endOdometerKm = choose(
                    "endOdometerKm",
                    local.endOdometerKm,
                    remote.endOdometerKm
                ),
                distanceKm = choose("distanceKm", local.distanceKm, remote.distanceKm),
                purpose = choose("purpose", local.purpose, remote.purpose),
                startLocationName = choose(
                    "startLocationName",
                    local.startLocationName,
                    remote.startLocationName
                ),
                endLocationName = choose(
                    "endLocationName",
                    local.endLocationName,
                    remote.endLocationName
                ),
                notes = choose("notes", local.notes, remote.notes),
                entrySource = choose("entrySource", local.entrySource, remote.entrySource),
                createdAt = minOf(local.createdAt, remote.createdAt),
                updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                syncState = DriveSyncState.LOCAL_PENDING.name
            ),
            provenance = sources,
            clearPendingOperation = false
        )
    }

    private fun <T> chooseValue(
        field: String,
        localValue: T,
        remoteValue: T,
        localProvenance: Map<String, DriveFieldSource>,
        resultSources: MutableMap<String, DriveFieldSource>
    ): T {
        if (localValue == remoteValue) {
            resultSources[field] = localProvenance[field] ?: DriveFieldSource.REMOTE
            return localValue
        }
        val localSource = localProvenance[field] ?: DriveFieldSource.UNKNOWN
        val keepLocal = localSource != DriveFieldSource.REMOTE
        resultSources[field] = DriveFieldSource.MERGED
        return if (keepLocal) localValue else remoteValue
    }

    private fun vehicleValues(entity: DriveVehicleEntity): Map<String, Any?> = mapOf(
        "displayName" to entity.displayName,
        "brand" to entity.brand,
        "model" to entity.model,
        "licensePlate" to entity.licensePlate,
        "modelYear" to entity.modelYear,
        "fuelType" to entity.fuelType,
        "initialOdometerKm" to entity.initialOdometerKm,
        "currentOdometerKm" to entity.currentOdometerKm,
        "assignedPersonId" to entity.assignedPersonId,
        "notes" to entity.notes
    )

    private fun tripValues(entity: DriveTripEntity): Map<String, Any?> = mapOf(
        "vehicleId" to entity.vehicleId,
        "startedAt" to entity.startedAt,
        "endedAt" to entity.endedAt,
        "startOdometerKm" to entity.startOdometerKm,
        "endOdometerKm" to entity.endOdometerKm,
        "distanceKm" to entity.distanceKm,
        "purpose" to entity.purpose,
        "startLocationName" to entity.startLocationName,
        "endLocationName" to entity.endLocationName,
        "notes" to entity.notes,
        "entrySource" to entity.entrySource
    )
}
