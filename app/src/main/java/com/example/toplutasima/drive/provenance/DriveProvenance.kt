package com.example.toplutasima.drive.provenance

import com.example.toplutasima.data.local.entity.DriveFieldProvenanceEntity
import com.example.toplutasima.drive.model.DriveFieldProvenance
import com.example.toplutasima.drive.model.DriveFieldSource
import java.time.Instant

object DriveProvenanceFields {
    val VEHICLE_FIELDS = setOf(
        "displayName",
        "brand",
        "model",
        "licensePlate",
        "modelYear",
        "fuelType",
        "initialOdometerKm",
        "currentOdometerKm",
        "assignedPersonId",
        "notes",
        "countryCode",
        "transmissionType",
        "bodyType",
        "color",
        "vin",
        "engineDisplacementCc",
        "enginePowerKw",
        "purchaseDate",
        "purchasePriceMinor",
        "currencyCode",
        "trimLevel",
        "engineCode",
        "registrationDate",
        "inspectionDueDate",
        "insuranceDueDate",
        "tireSize"
    )

    val TRIP_FIELDS = setOf(
        "vehicleId",
        "startedAt",
        "endedAt",
        "startOdometerKm",
        "endOdometerKm",
        "distanceKm",
        "purpose",
        "startLocationName",
        "endLocationName",
        "notes",
        "entrySource"
    )
}

fun DriveFieldProvenanceEntity.toDomain(): DriveFieldProvenance = DriveFieldProvenance(
    entityType = entityType,
    recordId = recordId,
    fieldName = fieldName,
    source = DriveFieldSource.fromStorage(source),
    updatedAt = Instant.ofEpochMilli(updatedAt)
)

fun provenanceEntities(
    userId: String,
    entityType: String,
    recordId: String,
    fields: Iterable<String>,
    source: DriveFieldSource,
    updatedAt: Long
): List<DriveFieldProvenanceEntity> = fields.map { field ->
    DriveFieldProvenanceEntity(
        userId = userId,
        entityType = entityType,
        recordId = recordId,
        fieldName = field,
        source = source.name,
        updatedAt = updatedAt
    )
}
