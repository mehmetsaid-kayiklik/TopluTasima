package com.example.toplutasima.drive.health

import com.example.toplutasima.drive.model.DriveFieldProvenance
import com.example.toplutasima.drive.model.DriveHealthCode
import com.example.toplutasima.drive.model.DriveHealthIssue
import com.example.toplutasima.drive.model.DriveHealthSeverity
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.provenance.DriveProvenanceFields
import com.example.toplutasima.drive.assignment.VehicleAssignment
import com.example.toplutasima.drive.assignment.VehicleAssignmentHealthCode
import com.example.toplutasima.drive.photo.DriveVehiclePhoto
import com.example.toplutasima.drive.photo.VehiclePhotoHealthCode
import shared.vehiclephoto.contract.VehiclePhotoContractSpec
import java.util.Locale
import kotlin.math.abs

object DriveHealthChecker {
    private const val DISTANCE_TOLERANCE_KM = 0.1
    private const val DUPLICATE_TIME_WINDOW_MS = 60_000L

    fun scan(
        vehicles: List<DriveVehicle>,
        trips: List<DriveTrip>,
        provenance: List<DriveFieldProvenance> = emptyList(),
        assignments: List<VehicleAssignment> = emptyList(),
        photos: List<DriveVehiclePhoto> = emptyList()
    ): List<DriveHealthIssue> {
        val issues = mutableListOf<DriveHealthIssue>()
        val vehicleIds = vehicles.mapTo(hashSetOf(), DriveVehicle::id)
        val provenanceKeys = provenance.mapTo(hashSetOf()) {
            Triple(it.entityType, it.recordId, it.fieldName)
        }

        vehicles.forEach { vehicle ->
            if (vehicle.licensePlate.isNullOrBlank()) {
                issues += issue("VEHICLE", vehicle.id, vehicle.id,
                    DriveHealthCode.MISSING_LICENSE_PLATE, DriveHealthSeverity.INFO)
            }
            if (listOfNotNull(vehicle.initialOdometerKm, vehicle.currentOdometerKm).any { it < 0 }) {
                issues += issue("VEHICLE", vehicle.id, vehicle.id,
                    DriveHealthCode.NEGATIVE_ODOMETER, DriveHealthSeverity.CRITICAL)
            }
            if (
                vehicle.initialOdometerKm != null && vehicle.currentOdometerKm != null &&
                vehicle.currentOdometerKm < vehicle.initialOdometerKm
            ) {
                issues += issue("VEHICLE", vehicle.id, vehicle.id,
                    DriveHealthCode.CURRENT_ODOMETER_BEFORE_INITIAL, DriveHealthSeverity.CRITICAL)
            }
            if (DriveProvenanceFields.VEHICLE_FIELDS.any { field ->
                    Triple("VEHICLE", vehicle.id, field) !in provenanceKeys
                }
            ) {
                issues += issue("VEHICLE", vehicle.id, vehicle.id,
                    DriveHealthCode.UNKNOWN_PROVENANCE, DriveHealthSeverity.INFO)
            }
        }

        trips.forEach { trip ->
            if (trip.vehicleId !in vehicleIds) {
                issues += issue("TRIP", trip.id, trip.vehicleId,
                    DriveHealthCode.ORPHAN_TRIP, DriveHealthSeverity.CRITICAL)
            }
            if (trip.endedAt != null && trip.endedAt < trip.startedAt) {
                issues += issue("TRIP", trip.id, trip.vehicleId,
                    DriveHealthCode.END_BEFORE_START, DriveHealthSeverity.CRITICAL)
            }
            if (trip.distanceKm < 0) {
                issues += issue("TRIP", trip.id, trip.vehicleId,
                    DriveHealthCode.NEGATIVE_DISTANCE, DriveHealthSeverity.CRITICAL)
            }
            if (trip.startOdometerKm != null && trip.endOdometerKm != null) {
                val odometerDistance = trip.endOdometerKm - trip.startOdometerKm
                if (odometerDistance < 0 || abs(odometerDistance - trip.distanceKm) > DISTANCE_TOLERANCE_KM) {
                    issues += issue("TRIP", trip.id, trip.vehicleId,
                        DriveHealthCode.ODOMETER_DISTANCE_MISMATCH, DriveHealthSeverity.WARNING)
                }
            }
            if (DriveProvenanceFields.TRIP_FIELDS.any { field ->
                    Triple("TRIP", trip.id, field) !in provenanceKeys
                }
            ) {
                issues += issue("TRIP", trip.id, trip.vehicleId,
                    DriveHealthCode.UNKNOWN_PROVENANCE, DriveHealthSeverity.INFO)
            }
        }

        assignments.filter { it.contract.deletedAt == null }.forEach { assignment ->
            val code = when (assignment.healthCode) {
                VehicleAssignmentHealthCode.ASSIGNED_PERSON_NOT_FOUND ->
                    DriveHealthCode.ASSIGNED_PERSON_NOT_FOUND
                VehicleAssignmentHealthCode.ASSIGNED_PERSON_DELETED ->
                    DriveHealthCode.ASSIGNED_PERSON_DELETED
                VehicleAssignmentHealthCode.ASSIGNED_PERSON_NOT_SHARED ->
                    DriveHealthCode.ASSIGNED_PERSON_NOT_SHARED
                VehicleAssignmentHealthCode.PERSON_ID_DOCUMENT_ID_MISMATCH ->
                    DriveHealthCode.PERSON_ID_DOCUMENT_ID_MISMATCH
                VehicleAssignmentHealthCode.ASSIGNMENT_VEHICLE_NOT_FOUND ->
                    DriveHealthCode.ASSIGNMENT_VEHICLE_NOT_FOUND
                VehicleAssignmentHealthCode.ASSIGNMENT_SCHEMA_UNSUPPORTED ->
                    DriveHealthCode.ASSIGNMENT_SCHEMA_UNSUPPORTED
                null -> null
            }
            if (code != null) {
                issues += issue(
                    entityType = "ASSIGNMENT",
                    recordId = assignment.contract.vehicleId,
                    vehicleId = assignment.contract.vehicleId,
                    code = code,
                    severity = if (
                        code == DriveHealthCode.PERSON_ID_DOCUMENT_ID_MISMATCH ||
                        code == DriveHealthCode.ASSIGNMENT_SCHEMA_UNSUPPORTED
                    ) {
                        DriveHealthSeverity.CRITICAL
                    } else {
                        DriveHealthSeverity.WARNING
                    }
                )
            }
        }

        val activePhotos = photos.filter { it.deletedAt == null }
        activePhotos.forEach { photo ->
            if (photo.vehicleId !in vehicleIds) {
                issues += issue("PHOTO", photo.photoId, photo.vehicleId,
                    DriveHealthCode.PHOTO_ORPHANED_FROM_VEHICLE, DriveHealthSeverity.WARNING)
            }
            if (photo.width == null || photo.height == null ||
                photo.width !in 1..VehiclePhotoContractSpec.MAX_LONG_EDGE_PX ||
                photo.height !in 1..VehiclePhotoContractSpec.MAX_LONG_EDGE_PX
            ) {
                issues += issue("PHOTO", photo.photoId, photo.vehicleId,
                    DriveHealthCode.PHOTO_INVALID_DIMENSIONS, DriveHealthSeverity.WARNING)
            }
            if (photo.sizeBytes == null || photo.sizeBytes !in 1..VehiclePhotoContractSpec.MAX_PREPARED_BYTES) {
                issues += issue("PHOTO", photo.photoId, photo.vehicleId,
                    DriveHealthCode.PHOTO_INVALID_SIZE, DriveHealthSeverity.WARNING)
            }
            if (photo.mimeType != VehiclePhotoContractSpec.OUTPUT_MIME_TYPE) {
                issues += issue("PHOTO", photo.photoId, photo.vehicleId,
                    DriveHealthCode.PHOTO_UNSUPPORTED_MIME_TYPE, DriveHealthSeverity.CRITICAL)
            }
            photo.healthCode?.let { code ->
                issues += issue(
                    "PHOTO",
                    photo.photoId,
                    photo.vehicleId,
                    DriveHealthCode.valueOf(code.name),
                    if (code == VehiclePhotoHealthCode.PHOTO_UNSUPPORTED_SCHEMA) {
                        DriveHealthSeverity.CRITICAL
                    } else {
                        DriveHealthSeverity.WARNING
                    }
                )
            }
        }
        activePhotos.filter { !it.contentHash.isNullOrBlank() }
            .groupBy { it.contentHash }
            .values.filter { it.size > 1 }
            .flatten().forEach { photo ->
                issues += issue("PHOTO", photo.photoId, photo.vehicleId,
                    DriveHealthCode.PHOTO_DUPLICATE_CONTENT, DriveHealthSeverity.INFO)
            }
        activePhotos.groupBy { it.vehicleId }.forEach { (vehicleId, vehiclePhotos) ->
            val vehicle = vehicles.firstOrNull { it.id == vehicleId }
            val projectedPrimary = vehiclePhotos.filter(DriveVehiclePhoto::isPrimary)
            val mirrorMismatch = vehicle?.primaryPhotoId?.let { primaryId ->
                projectedPrimary.singleOrNull()?.photoId != primaryId
            } ?: projectedPrimary.isNotEmpty()
            if (projectedPrimary.size > 1 || mirrorMismatch) {
                vehiclePhotos.forEach { photo ->
                    issues += issue("PHOTO", photo.photoId, vehicleId,
                        DriveHealthCode.PHOTO_PRIMARY_CONFLICT, DriveHealthSeverity.WARNING)
                }
            }
        }

        val sortedTrips = trips.sortedWith(compareBy(DriveTrip::vehicleId, DriveTrip::startedAt))
        sortedTrips.forEachIndexed { index, first ->
            var nextIndex = index + 1
            while (nextIndex < sortedTrips.size) {
                val second = sortedTrips[nextIndex]
                if (first.vehicleId != second.vehicleId) break
                val timeDifference = second.startedAt.toEpochMilli() - first.startedAt.toEpochMilli()
                if (timeDifference > DUPLICATE_TIME_WINDOW_MS) break
                if (areLikelyDuplicates(first, second)) {
                    issues += issue("TRIP", first.id, first.vehicleId,
                        DriveHealthCode.POSSIBLE_DUPLICATE, DriveHealthSeverity.WARNING)
                    issues += issue("TRIP", second.id, second.vehicleId,
                        DriveHealthCode.POSSIBLE_DUPLICATE, DriveHealthSeverity.WARNING)
                }
                nextIndex++
            }
        }

        return issues.distinctBy { listOf(it.entityType, it.recordId, it.code.name) }
            .sortedWith(compareByDescending<DriveHealthIssue> { it.severity }.thenBy { it.recordId })
    }

    private fun areLikelyDuplicates(first: DriveTrip, second: DriveTrip): Boolean =
        abs(first.distanceKm - second.distanceKm) <= DISTANCE_TOLERANCE_KM &&
            first.startLocationName.normalized() == second.startLocationName.normalized() &&
            first.endLocationName.normalized() == second.endLocationName.normalized()

    private fun String?.normalized(): String = this?.trim()?.lowercase(Locale.ROOT).orEmpty()

    private fun issue(
        entityType: String,
        recordId: String,
        vehicleId: String?,
        code: DriveHealthCode,
        severity: DriveHealthSeverity
    ) = DriveHealthIssue(entityType, recordId, vehicleId, code, severity)
}
