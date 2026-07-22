package com.example.toplutasima.drive.data.remote

import com.example.toplutasima.data.local.entity.DriveTripEntity
import com.example.toplutasima.data.local.entity.DriveVehicleEntity
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveTripEntrySource
import com.example.toplutasima.drive.model.DriveTripPurpose
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.model.VehicleFuelType
import com.example.toplutasima.drive.sync.DriveRemoteWriteResult
import com.example.toplutasima.drive.sync.DriveRemoteCursor
import com.example.toplutasima.drive.sync.DriveRemotePullBatch
import com.example.toplutasima.drive.sync.DriveRemoteTrip
import com.example.toplutasima.drive.sync.DriveRemoteVehicle
import com.example.toplutasima.drive.validation.DriveTripValidator
import com.example.toplutasima.drive.validation.DriveVehicleValidator
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.time.Instant
import kotlinx.coroutines.tasks.await

internal interface DriveRemoteDataSource {
    suspend fun fetchInitial(ownerUid: String): DriveRemotePullBatch = DriveRemotePullBatch.EMPTY

    suspend fun fetchIncremental(
        ownerUid: String,
        vehicleCursor: DriveRemoteCursor?,
        tripCursor: DriveRemoteCursor?
    ): DriveRemotePullBatch = DriveRemotePullBatch.EMPTY

    suspend fun upsertVehicle(
        ownerUid: String,
        vehicle: DriveVehicleEntity,
        operationId: String
    ): DriveRemoteWriteResult

    suspend fun tombstoneVehicle(
        ownerUid: String,
        vehicle: DriveVehicleEntity,
        operationId: String
    ): DriveRemoteWriteResult

    suspend fun upsertTrip(
        ownerUid: String,
        trip: DriveTripEntity,
        operationId: String
    ): DriveRemoteWriteResult

    suspend fun tombstoneTrip(
        ownerUid: String,
        trip: DriveTripEntity,
        operationId: String
    ): DriveRemoteWriteResult
}

internal class FirestoreDriveRemoteDataSource(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) : DriveRemoteDataSource {
    override suspend fun fetchInitial(ownerUid: String): DriveRemotePullBatch {
        val user = userDocument(ownerUid)
        val vehicleDocuments = user.collection(COLLECTION_VEHICLES).get().await().documents
        val tripDocuments = user.collection(COLLECTION_DRIVE_TRIPS).get().await().documents
        return pullBatch(ownerUid, vehicleDocuments, tripDocuments)
    }

    override suspend fun fetchIncremental(
        ownerUid: String,
        vehicleCursor: DriveRemoteCursor?,
        tripCursor: DriveRemoteCursor?
    ): DriveRemotePullBatch {
        val user = userDocument(ownerUid)
        val vehicleDocuments = incrementalQuery(
            user.collection(COLLECTION_VEHICLES),
            vehicleCursor
        ).get().await().documents
        val tripDocuments = incrementalQuery(
            user.collection(COLLECTION_DRIVE_TRIPS),
            tripCursor
        ).get().await().documents
        return pullBatch(ownerUid, vehicleDocuments, tripDocuments)
    }

    override suspend fun upsertVehicle(
        ownerUid: String,
        vehicle: DriveVehicleEntity,
        operationId: String
    ): DriveRemoteWriteResult {
        requireVehicleWrite(ownerUid, vehicle, operationId, requiresTombstone = false)
        return writeActiveDocument(
            document = vehicleDocument(ownerUid, vehicle.id),
            operationId = operationId,
            fields = vehicle.toFirestoreFields(ownerUid)
        )
    }

    override suspend fun tombstoneVehicle(
        ownerUid: String,
        vehicle: DriveVehicleEntity,
        operationId: String
    ): DriveRemoteWriteResult {
        requireVehicleWrite(ownerUid, vehicle, operationId, requiresTombstone = true)
        return writeTombstone(
            document = vehicleDocument(ownerUid, vehicle.id),
            operationId = operationId,
            fields = mapOf(
                FIELD_ID to vehicle.id,
                FIELD_OWNER_UID to ownerUid,
                FIELD_UPDATED_AT to vehicle.updatedAt,
                FIELD_DELETED_AT to requireNotNull(vehicle.deletedAt)
            )
        )
    }

    override suspend fun upsertTrip(
        ownerUid: String,
        trip: DriveTripEntity,
        operationId: String
    ): DriveRemoteWriteResult {
        requireTripWrite(ownerUid, trip, operationId, requiresTombstone = false)
        return writeActiveDocument(
            document = tripDocument(ownerUid, trip.id),
            operationId = operationId,
            fields = trip.toFirestoreFields(ownerUid)
        )
    }

    override suspend fun tombstoneTrip(
        ownerUid: String,
        trip: DriveTripEntity,
        operationId: String
    ): DriveRemoteWriteResult {
        requireTripWrite(ownerUid, trip, operationId, requiresTombstone = true)
        return writeTombstone(
            document = tripDocument(ownerUid, trip.id),
            operationId = operationId,
            fields = mapOf(
                FIELD_ID to trip.id,
                FIELD_OWNER_UID to ownerUid,
                FIELD_VEHICLE_ID to trip.vehicleId,
                FIELD_UPDATED_AT to trip.updatedAt,
                FIELD_DELETED_AT to requireNotNull(trip.deletedAt)
            )
        )
    }

    private suspend fun writeActiveDocument(
        document: DocumentReference,
        operationId: String,
        fields: Map<String, Any?>
    ): DriveRemoteWriteResult = firestore.runTransaction { transaction ->
        val snapshot = transaction.get(document)
        if (snapshot.getString(FIELD_SYNC_OPERATION_ID) == operationId) {
            return@runTransaction DriveRemoteWriteResult.AlreadyApplied
        }
        val deletedAt = snapshot.epochMillisOrNull(FIELD_DELETED_AT)
        if (deletedAt != null) {
            return@runTransaction DriveRemoteWriteResult.DeletePrecedence(deletedAt)
        }
        transaction.set(
            document,
            fields + mapOf(
                FIELD_SYNC_OPERATION_ID to operationId,
                FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        DriveRemoteWriteResult.Applied
    }.await()

    private suspend fun writeTombstone(
        document: DocumentReference,
        operationId: String,
        fields: Map<String, Any>
    ): DriveRemoteWriteResult = firestore.runTransaction { transaction ->
        val snapshot = transaction.get(document)
        if (snapshot.getString(FIELD_SYNC_OPERATION_ID) == operationId) {
            return@runTransaction DriveRemoteWriteResult.AlreadyApplied
        }
        if (snapshot.epochMillisOrNull(FIELD_DELETED_AT) != null) {
            return@runTransaction DriveRemoteWriteResult.AlreadyApplied
        }
        transaction.set(
            document,
            fields + mapOf(
                FIELD_SYNC_OPERATION_ID to operationId,
                FIELD_SERVER_UPDATED_AT to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        )
        DriveRemoteWriteResult.Applied
    }.await()

    private fun incrementalQuery(
        collection: CollectionReference,
        cursor: DriveRemoteCursor?
    ): Query {
        val ordered = collection
            .orderBy(FIELD_SERVER_UPDATED_AT, Query.Direction.ASCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
        return if (cursor == null) {
            ordered
        } else {
            ordered.startAfter(
                Timestamp(cursor.seconds, cursor.nanoseconds),
                cursor.documentId
            )
        }
    }

    private fun pullBatch(
        ownerUid: String,
        vehicleDocuments: List<DocumentSnapshot>,
        tripDocuments: List<DocumentSnapshot>
    ): DriveRemotePullBatch {
        val vehicles = vehicleDocuments.map { snapshot ->
            DriveRemoteVehicle(snapshot.toVehicle(ownerUid), snapshot.cursorOrNull())
        }
        val trips = tripDocuments.map { snapshot ->
            DriveRemoteTrip(snapshot.toTrip(ownerUid), snapshot.cursorOrNull())
        }
        return DriveRemotePullBatch(
            vehicles = vehicles,
            trips = trips,
            vehicleCursor = vehicles.mapNotNull(DriveRemoteVehicle::cursor).maxOrNull(),
            tripCursor = trips.mapNotNull(DriveRemoteTrip::cursor).maxOrNull()
        )
    }

    private fun DocumentSnapshot.toVehicle(ownerUid: String): DriveVehicleEntity {
        require(id.isNotBlank() && getString(FIELD_ID) == id) { "Invalid drive vehicle ID" }
        require(getString(FIELD_OWNER_UID) == ownerUid) { "Drive owner mismatch" }
        val deletedAt = epochMillisOrNull(FIELD_DELETED_AT)
        val updatedAt = epochMillisOrNull(FIELD_UPDATED_AT)
            ?: getTimestamp(FIELD_SERVER_UPDATED_AT)?.toDate()?.time
            ?: throw IllegalArgumentException("Drive vehicle update time missing")
        val entity = DriveVehicleEntity(
            id = id,
            userId = ownerUid,
            displayName = if (deletedAt == null) getString("displayName").orEmpty() else "",
            brand = getString("brand"),
            model = getString("model"),
            licensePlate = getString("licensePlate"),
            modelYear = getLong("modelYear")?.toInt(),
            fuelType = getString("fuelType"),
            initialOdometerKm = numberOrNull("initialOdometerKm"),
            currentOdometerKm = numberOrNull("currentOdometerKm"),
            assignedPersonId = getString("assignedPersonId"),
            notes = getString("notes"),
            createdAt = epochMillisOrNull("createdAt") ?: updatedAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncState = "SYNCED"
        )
        require(deletedAt != null || DriveRemoteWriteValidator.isValid(entity)) {
            "Invalid remote drive vehicle"
        }
        return entity
    }

    private fun DocumentSnapshot.toTrip(ownerUid: String): DriveTripEntity {
        require(id.isNotBlank() && getString(FIELD_ID) == id) { "Invalid drive trip ID" }
        require(getString(FIELD_OWNER_UID) == ownerUid) { "Drive owner mismatch" }
        val vehicleId = getString(FIELD_VEHICLE_ID).orEmpty()
        require(vehicleId.isNotBlank()) { "Drive vehicle relation missing" }
        val deletedAt = epochMillisOrNull(FIELD_DELETED_AT)
        val updatedAt = epochMillisOrNull(FIELD_UPDATED_AT)
            ?: getTimestamp(FIELD_SERVER_UPDATED_AT)?.toDate()?.time
            ?: throw IllegalArgumentException("Drive trip update time missing")
        val entity = DriveTripEntity(
            id = id,
            userId = ownerUid,
            vehicleId = vehicleId,
            startedAt = epochMillisOrNull("startedAt") ?: updatedAt,
            endedAt = epochMillisOrNull("endedAt"),
            startOdometerKm = numberOrNull("startOdometerKm"),
            endOdometerKm = numberOrNull("endOdometerKm"),
            distanceKm = numberOrNull("distanceKm") ?: 0.0,
            purpose = getString("purpose") ?: "UNCLASSIFIED",
            startLocationName = getString("startLocationName"),
            endLocationName = getString("endLocationName"),
            notes = getString("notes"),
            entrySource = getString("entrySource") ?: "UNKNOWN",
            createdAt = epochMillisOrNull("createdAt") ?: updatedAt,
            updatedAt = updatedAt,
            deletedAt = deletedAt,
            syncState = "SYNCED"
        )
        require(deletedAt != null || DriveRemoteWriteValidator.isValid(entity)) {
            "Invalid remote drive trip"
        }
        return entity
    }

    private fun DocumentSnapshot.cursorOrNull(): DriveRemoteCursor? =
        getTimestamp(FIELD_SERVER_UPDATED_AT)?.let { timestamp ->
            DriveRemoteCursor(timestamp.seconds, timestamp.nanoseconds, id)
        }

    private fun DocumentSnapshot.epochMillisOrNull(field: String): Long? = when (
        val value = get(field)
    ) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    private fun DocumentSnapshot.numberOrNull(field: String): Double? =
        (get(field) as? Number)?.toDouble()?.takeIf(Double::isFinite)

    private fun vehicleDocument(ownerUid: String, vehicleId: String): DocumentReference =
        userDocument(ownerUid).collection(COLLECTION_VEHICLES).document(vehicleId)

    private fun tripDocument(ownerUid: String, tripId: String): DocumentReference =
        userDocument(ownerUid).collection(COLLECTION_DRIVE_TRIPS).document(tripId)

    private fun userDocument(ownerUid: String): DocumentReference {
        require(ownerUid.isNotBlank()) { "Drive owner must not be blank" }
        return firestore.collection(COLLECTION_USERS).document(ownerUid)
    }

    private fun requireVehicleWrite(
        ownerUid: String,
        vehicle: DriveVehicleEntity,
        operationId: String,
        requiresTombstone: Boolean
    ) {
        require(ownerUid.isNotBlank() && vehicle.userId == ownerUid) { "Drive owner mismatch" }
        require(vehicle.id.isNotBlank()) { "Drive record ID must not be blank" }
        require(operationId.isNotBlank()) { "Drive operation ID must not be blank" }
        require((vehicle.deletedAt != null) == requiresTombstone) { "Invalid drive delete state" }
        if (!requiresTombstone) {
            require(DriveRemoteWriteValidator.isValid(vehicle)) { "Invalid drive vehicle" }
        }
    }

    private fun requireTripWrite(
        ownerUid: String,
        trip: DriveTripEntity,
        operationId: String,
        requiresTombstone: Boolean
    ) {
        require(ownerUid.isNotBlank() && trip.userId == ownerUid) { "Drive owner mismatch" }
        require(trip.id.isNotBlank() && trip.vehicleId.isNotBlank()) {
            "Drive relation must not be blank"
        }
        require(operationId.isNotBlank()) { "Drive operation ID must not be blank" }
        require((trip.deletedAt != null) == requiresTombstone) { "Invalid drive delete state" }
        if (!requiresTombstone) {
            require(DriveRemoteWriteValidator.isValid(trip)) { "Invalid drive trip" }
        }
    }

    private fun DriveVehicleEntity.toFirestoreFields(ownerUid: String): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        FIELD_OWNER_UID to ownerUid,
        "displayName" to displayName,
        "brand" to brand,
        "model" to model,
        "licensePlate" to licensePlate,
        "modelYear" to modelYear,
        "fuelType" to fuelType,
        "initialOdometerKm" to initialOdometerKm,
        "currentOdometerKm" to currentOdometerKm,
        "notes" to notes,
        "createdAt" to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED_AT to null
    )

    private fun DriveTripEntity.toFirestoreFields(ownerUid: String): Map<String, Any?> = mapOf(
        FIELD_ID to id,
        FIELD_OWNER_UID to ownerUid,
        FIELD_VEHICLE_ID to vehicleId,
        "startedAt" to startedAt,
        "endedAt" to endedAt,
        "startOdometerKm" to startOdometerKm,
        "endOdometerKm" to endOdometerKm,
        "distanceKm" to distanceKm,
        "purpose" to purpose,
        "startLocationName" to startLocationName,
        "endLocationName" to endLocationName,
        "notes" to notes,
        "entrySource" to entrySource,
        "createdAt" to createdAt,
        FIELD_UPDATED_AT to updatedAt,
        FIELD_DELETED_AT to null
    )

    private companion object {
        const val COLLECTION_USERS = "users"
        const val COLLECTION_VEHICLES = "vehicles"
        const val COLLECTION_DRIVE_TRIPS = "driveTrips"
        const val FIELD_ID = "id"
        const val FIELD_OWNER_UID = "ownerUid"
        const val FIELD_VEHICLE_ID = "vehicleId"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_DELETED_AT = "deletedAt"
        const val FIELD_SYNC_OPERATION_ID = "_syncOperationId"
        const val FIELD_SERVER_UPDATED_AT = "_serverUpdatedAt"
    }
}

internal object DriveRemoteWriteValidator {
    private val vehicleValidator = DriveVehicleValidator()
    private val tripValidator = DriveTripValidator()

    fun isValid(vehicle: DriveVehicleEntity): Boolean = vehicleValidator.validate(
        DriveVehicleDraft(
            displayName = vehicle.displayName,
            brand = vehicle.brand,
            model = vehicle.model,
            licensePlate = vehicle.licensePlate,
            modelYear = vehicle.modelYear,
            fuelType = VehicleFuelType.fromStorage(vehicle.fuelType),
            initialOdometerKm = vehicle.initialOdometerKm,
            currentOdometerKm = vehicle.currentOdometerKm,
            assignedPersonId = vehicle.assignedPersonId,
            notes = vehicle.notes
        )
    ).isEmpty()

    fun isValid(trip: DriveTripEntity): Boolean {
        if (DriveTripEntrySource.fromStorage(trip.entrySource) != DriveTripEntrySource.MANUAL) {
            return false
        }
        return tripValidator.validate(
            draft = DriveTripDraft(
                vehicleId = trip.vehicleId,
                startedAt = Instant.ofEpochMilli(trip.startedAt),
                endedAt = trip.endedAt?.let(Instant::ofEpochMilli),
                startOdometerKm = trip.startOdometerKm,
                endOdometerKm = trip.endOdometerKm,
                distanceKm = trip.distanceKm,
                purpose = DriveTripPurpose.fromStorage(trip.purpose),
                startLocationName = trip.startLocationName,
                endLocationName = trip.endLocationName,
                notes = trip.notes
            ),
            vehicleExistsForOwner = true
        ).issues.isEmpty()
    }
}
