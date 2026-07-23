package com.example.toplutasima.drive.repository

import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.dao.DriveVehicleWithSummary
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.data.toDomain
import com.example.toplutasima.drive.data.toEntity
import com.example.toplutasima.drive.model.DriveSyncState
import com.example.toplutasima.drive.model.DriveFieldSource
import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveTripEntrySource
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.summary.DriveVehicleSummaryCalculator
import com.example.toplutasima.drive.health.DriveHealthChecker
import com.example.toplutasima.drive.provenance.DriveProvenanceFields
import com.example.toplutasima.drive.provenance.changedTripFields
import com.example.toplutasima.drive.provenance.changedVehicleFields
import com.example.toplutasima.drive.provenance.provenanceEntities
import com.example.toplutasima.drive.provenance.toDomain as provenanceToDomain
import com.example.toplutasima.drive.sync.DriveSyncOperationType
import com.example.toplutasima.drive.sync.DriveSyncPlanner
import com.example.toplutasima.drive.sync.DriveSyncEntityType
import com.example.toplutasima.drive.sync.entityType
import com.example.toplutasima.drive.sync.toDomain as receiptToDomain
import com.example.toplutasima.drive.validation.DriveTripValidator
import com.example.toplutasima.drive.validation.DriveValidationCode
import com.example.toplutasima.drive.validation.DriveValidationIssue
import com.example.toplutasima.drive.validation.DriveVehicleValidator
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.example.toplutasima.drive.assignment.OfflineFirstVehicleAssignmentRepository
import com.example.toplutasima.drive.assignment.VehicleAssignmentFailure
import com.example.toplutasima.drive.assignment.VehicleAssignmentMutationResult
import com.example.toplutasima.drive.assignment.VehicleAssignmentRepository
import com.example.toplutasima.drive.assignment.toDomain as assignmentToDomain
import com.example.toplutasima.drive.photo.toDomain as photoToDomain

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstDriveRepository(
    private val database: AppDatabase,
    private val authenticatedUid: () -> String?,
    private val syncScheduler: DriveSyncWorkScheduler,
    private val authenticatedUidChanges: Flow<String?> = flow { emit(authenticatedUid()) },
    private val idGenerator: DriveIdGenerator = DriveIdGenerator.UUID,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val vehicleValidator: DriveVehicleValidator = DriveVehicleValidator(),
    private val tripValidator: DriveTripValidator = DriveTripValidator(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val calculationDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_CORE,
    vehicleAssignmentRepository: VehicleAssignmentRepository? = null
) : DriveVehicleRepository, DriveTripRepository, DriveAdvancedRepository {
    private companion object {
        const val RECEIPT_UI_LIMIT = 20
    }

    private val lastMutationTimestamp = AtomicLong(0L)
    private val vehicleDao get() = database.driveVehicleDao()
    private val tripDao get() = database.driveTripDao()
    private val operationDao get() = database.driveSyncOperationDao()
    private val provenanceDao get() = database.driveFieldProvenanceDao()
    private val receiptDao get() = database.driveSyncReceiptDao()
    private val assignmentDao get() = database.driveVehicleAssignmentDao()
    private val canonicalAssignmentRepository = vehicleAssignmentRepository
        ?: OfflineFirstVehicleAssignmentRepository(
            database = database,
            currentUserId = authenticatedUid,
            authenticatedUidChanges = authenticatedUidChanges,
            syncScheduler = syncScheduler,
            directoryRefresher = {}
        )

    override fun observeVehicles(): Flow<List<DriveVehicle>> {
        if (!enabled) return flowOf(emptyList())
        return authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                vehicleDao.observeActiveVehicles(uid).map { entities ->
                    withContext(calculationDispatcher) { entities.map { it.toDomain() } }
                }
            }
        }
    }

    override fun observeVehicleOverviews(): Flow<List<DriveVehicleOverview>> {
        if (!enabled) return flowOf(emptyList())
        return authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                vehicleDao.observeActiveVehiclesWithSummary(uid).map { rows ->
                    withContext(calculationDispatcher) {
                        rows.mapNotNull { row -> row.toOverviewOrNull(uid) }
                    }
                }
            }
        }
    }

    override fun observeVehicle(vehicleId: String): Flow<DriveVehicle?> {
        if (!enabled || vehicleId.isBlank()) return flowOf(null)
        return authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
            if (uid == null) flowOf(null)
            else vehicleDao.observeActiveVehicle(uid, vehicleId).map { it?.toDomain() }
        }
    }

    override fun observeSummary(vehicleId: String): Flow<DriveVehicleSummary?> {
        if (!enabled || vehicleId.isBlank()) return flowOf(null)
        return authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(null)
            } else {
                vehicleDao.observeActiveVehicleWithSummary(uid, vehicleId).map { row ->
                    withContext(calculationDispatcher) { row?.toOverviewOrNull(uid)?.summary }
                }
            }
        }
    }

    override fun observeTrips(vehicleId: String): Flow<List<DriveTrip>> {
        if (!enabled || vehicleId.isBlank()) return flowOf(emptyList())
        return authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                tripDao.observeActiveTripsForVehicle(uid, vehicleId).map { entities ->
                    withContext(calculationDispatcher) { entities.map { it.toDomain() } }
                }
            }
        }
    }

    override fun observeHealth() = if (!enabled) {
        flowOf(emptyList())
    } else {
        authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
            if (uid == null) {
                flowOf(emptyList())
            } else {
                combine(
                    vehicleDao.observeActiveVehicles(uid),
                    tripDao.observeActiveTrips(uid),
                    provenanceDao.observeForUser(uid),
                    assignmentDao.observeAll(uid),
                    database.driveVehiclePhotoDao().observeAll(uid)
                ) { vehicles, trips, provenance, assignments, photos ->
                    withContext(calculationDispatcher) {
                        DriveHealthChecker.scan(
                            vehicles = vehicles.map { it.toDomain() },
                            trips = trips.map { it.toDomain() },
                            provenance = provenance.map { it.provenanceToDomain() },
                            assignments = assignments.map { it.assignmentToDomain() },
                            photos = photos.map { it.photoToDomain() }
                        )
                    }
                }
            }
        }
    }

    override fun observeSyncReceipts() = if (!enabled) {
        flowOf(emptyList())
    } else {
        authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList())
            else receiptDao.observeRecent(uid, RECEIPT_UI_LIMIT).map { receipts ->
                receipts.map { it.receiptToDomain() }
            }
        }
    }

    override fun observeProvenance(entityType: String, recordId: String) =
        if (!enabled || entityType.isBlank() || recordId.isBlank()) {
            flowOf(emptyList())
        } else {
            authenticatedUidChanges.ownerUidChanges().flatMapLatest { uid ->
                if (uid == null) flowOf(emptyList())
                else provenanceDao.observeForRecord(uid, entityType, recordId).map { rows ->
                    val existing = rows.associateBy { it.fieldName }
                    val expected = when (entityType) {
                        DriveSyncEntityType.VEHICLE.name -> DriveProvenanceFields.VEHICLE_FIELDS
                        DriveSyncEntityType.TRIP.name -> DriveProvenanceFields.TRIP_FIELDS
                        else -> emptySet()
                    }
                    expected.sorted().map { fieldName ->
                        existing[fieldName]?.provenanceToDomain()
                            ?: com.example.toplutasima.drive.model.DriveFieldProvenance(
                                entityType = entityType,
                                recordId = recordId,
                                fieldName = fieldName,
                                source = DriveFieldSource.UNKNOWN,
                                updatedAt = Instant.EPOCH
                            )
                    }
                }
            }
        }

    override suspend fun createVehicle(
        draft: DriveVehicleDraft
    ): DriveMutationResult<DriveVehicle> = mutate { uid ->
        val issues = vehicleValidator.validate(draft)
        if (issues.isNotEmpty()) return@mutate DriveMutationResult.ValidationFailed(issues)
        val timestamp = nextTimestamp()
        val vehicle = DriveVehicle(
            id = idGenerator.newId(),
            ownerUid = uid,
            displayName = draft.displayName.trim(),
            brand = draft.brand,
            model = draft.model,
            licensePlate = draft.licensePlate,
            modelYear = draft.modelYear,
            fuelType = draft.fuelType,
            initialOdometerKm = draft.initialOdometerKm,
            currentOdometerKm = draft.currentOdometerKm,
            assignedPersonId = null,
            notes = draft.notes,
            createdAt = Instant.ofEpochMilli(timestamp),
            updatedAt = Instant.ofEpochMilli(timestamp),
            syncState = DriveSyncState.LOCAL_PENDING,
            countryCode = draft.countryCode,
            transmissionType = draft.transmissionType,
            bodyType = draft.bodyType,
            color = draft.color,
            vin = draft.vin,
            engineDisplacementCc = draft.engineDisplacementCc,
            enginePowerKw = draft.enginePowerKw,
            purchaseDate = draft.purchaseDate,
            purchasePriceMinor = draft.purchasePriceMinor,
            currencyCode = draft.currencyCode,
            trimLevel = draft.trimLevel,
            engineCode = draft.engineCode,
            registrationDate = draft.registrationDate,
            inspectionDueDate = draft.inspectionDueDate,
            insuranceDueDate = draft.insuranceDueDate,
            tireSize = draft.tireSize
        )
        database.withTransaction {
            vehicleDao.upsert(vehicle.toEntity())
            provenanceDao.upsertAll(
                provenanceEntities(
                    uid,
                    DriveSyncOperationType.CREATE_VEHICLE.entityType.name,
                    vehicle.id,
                    DriveProvenanceFields.VEHICLE_FIELDS,
                    DriveFieldSource.LOCAL,
                    timestamp
                )
            )
            enqueue(uid, vehicle.id, DriveSyncOperationType.CREATE_VEHICLE, timestamp)
        }
        scheduleResult(uid, vehicle)
    }

    override suspend fun updateVehicle(
        vehicleId: String,
        draft: DriveVehicleDraft
    ): DriveMutationResult<DriveVehicle> = mutate { uid ->
        val issues = vehicleValidator.validate(draft)
        if (issues.isNotEmpty()) return@mutate DriveMutationResult.ValidationFailed(issues)
        val timestamp = nextTimestamp()
        val result = database.withTransaction {
            val existing = vehicleDao.getVehicle(uid, vehicleId)
                ?: return@withTransaction DriveMutationResult.NotFound
            if (existing.deletedAt != null) return@withTransaction DriveMutationResult.DeletedRecord
            val updated = existing.toDomain().copy(
                displayName = draft.displayName.trim(),
                brand = draft.brand,
                model = draft.model,
                licensePlate = draft.licensePlate,
                modelYear = draft.modelYear,
                fuelType = draft.fuelType,
                initialOdometerKm = draft.initialOdometerKm,
                currentOdometerKm = if (DriveFeatureFlags.DRIVE_VEHICLE_LEDGER) {
                    existing.currentOdometerKm
                } else {
                    draft.currentOdometerKm
                },
                notes = draft.notes,
                countryCode = draft.countryCode,
                transmissionType = draft.transmissionType,
                bodyType = draft.bodyType,
                color = draft.color,
                vin = draft.vin,
                engineDisplacementCc = draft.engineDisplacementCc,
                enginePowerKw = draft.enginePowerKw,
                purchaseDate = draft.purchaseDate,
                purchasePriceMinor = draft.purchasePriceMinor,
                currencyCode = draft.currencyCode,
                trimLevel = draft.trimLevel,
                engineCode = draft.engineCode,
                registrationDate = draft.registrationDate,
                inspectionDueDate = draft.inspectionDueDate,
                insuranceDueDate = draft.insuranceDueDate,
                tireSize = draft.tireSize,
                updatedAt = Instant.ofEpochMilli(timestamp),
                syncState = DriveSyncState.LOCAL_PENDING
            )
            val updatedEntity = updated.toEntity()
            vehicleDao.upsert(updatedEntity)
            provenanceDao.upsertAll(
                provenanceEntities(
                    uid,
                    DriveSyncEntityType.VEHICLE.name,
                    vehicleId,
                    changedVehicleFields(existing, updatedEntity),
                    DriveFieldSource.LOCAL,
                    timestamp
                )
            )
            enqueue(uid, vehicleId, DriveSyncOperationType.UPDATE_VEHICLE, timestamp)
            DriveMutationResult.Success(updated)
        }
        scheduleSuccessfulResult(uid, result)
    }

    override suspend fun assignPerson(
        vehicleId: String,
        assignedPersonId: String?
    ): DriveMutationResult<DriveVehicle> {
        val uid = authenticatedUid()?.takeIf(String::isNotBlank)
            ?: return DriveMutationResult.AuthenticationRequired
        val result = if (assignedPersonId.isNullOrBlank()) {
            canonicalAssignmentRepository.remove(vehicleId)
        } else {
            canonicalAssignmentRepository.assign(vehicleId, assignedPersonId)
        }
        val vehicle = withContext(ioDispatcher) {
            vehicleDao.getActiveVehicle(uid, vehicleId)?.toDomain()
        }
        return when (result) {
            is VehicleAssignmentMutationResult.Success ->
                vehicle?.let { DriveMutationResult.Success(it) } ?: DriveMutationResult.NotFound
            is VehicleAssignmentMutationResult.LocalSavedSyncSchedulingFailed ->
                vehicle?.let { DriveMutationResult.LocalSavedSyncSchedulingFailed(it) }
                    ?: DriveMutationResult.NotFound
            is VehicleAssignmentMutationResult.Rejected -> when (result.failure) {
                VehicleAssignmentFailure.AuthenticationChanged -> DriveMutationResult.AuthenticationRequired
                VehicleAssignmentFailure.AccountMismatch -> DriveMutationResult.OwnershipMismatch
                VehicleAssignmentFailure.VehicleNotFound -> DriveMutationResult.NotFound
                VehicleAssignmentFailure.VehicleDeleted -> DriveMutationResult.DeletedRecord
                else -> DriveMutationResult.StorageFailure(DriveStorageFailureCategory.UNKNOWN)
            }
        }
    }

    override suspend fun deleteVehicle(
        vehicleId: String,
        deleteLinkedTrips: Boolean
    ): DriveMutationResult<Unit> = mutate { uid ->
        val timestamp = nextTimestamp()
        val result = database.withTransaction {
            val existing = vehicleDao.getVehicle(uid, vehicleId)
                ?: return@withTransaction DriveMutationResult.NotFound
            if (existing.deletedAt != null) return@withTransaction DriveMutationResult.DeletedRecord
            val tripIds = tripDao.getActiveTripIdsForVehicle(uid, vehicleId)
            if (tripIds.isNotEmpty() && !deleteLinkedTrips) {
                return@withTransaction DriveMutationResult.CascadeConfirmationRequired(tripIds.size)
            }
            if (tripIds.isNotEmpty()) {
                tripDao.markDeletedForVehicle(
                    uid,
                    vehicleId,
                    timestamp,
                    timestamp,
                    DriveSyncState.LOCAL_PENDING.name
                )
                tripIds.forEach { tripId ->
                    enqueue(uid, tripId, DriveSyncOperationType.DELETE_DRIVE_TRIP, timestamp)
                }
            }
            vehicleDao.markDeleted(
                uid,
                vehicleId,
                timestamp,
                timestamp,
                DriveSyncState.LOCAL_PENDING.name
            )
            enqueue(uid, vehicleId, DriveSyncOperationType.DELETE_VEHICLE, timestamp)
            DriveMutationResult.Success(Unit)
        }
        scheduleSuccessfulResult(uid, result)
    }

    override suspend fun bulkDeleteVehicles(
        vehicleIds: Set<String>
    ): DriveMutationResult<Int> = mutate { uid ->
        val normalizedIds = vehicleIds.filter(String::isNotBlank).distinct().sorted()
        if (normalizedIds.isEmpty()) return@mutate DriveMutationResult.Success(0)
        val timestamp = nextTimestamp()
        val result = database.withTransaction {
            val vehicles = normalizedIds.map { vehicleId ->
                vehicleDao.getVehicle(uid, vehicleId)
                    ?: return@withTransaction DriveMutationResult.NotFound
            }
            if (vehicles.any { it.deletedAt != null }) {
                return@withTransaction DriveMutationResult.DeletedRecord
            }
            normalizedIds.forEach { vehicleId ->
                val tripIds = tripDao.getActiveTripIdsForVehicle(uid, vehicleId)
                if (tripIds.isNotEmpty()) {
                    tripDao.markDeletedForVehicle(
                        uid,
                        vehicleId,
                        timestamp,
                        timestamp,
                        DriveSyncState.LOCAL_PENDING.name
                    )
                    tripIds.forEach { tripId ->
                        enqueue(
                            uid,
                            tripId,
                            DriveSyncOperationType.DELETE_DRIVE_TRIP,
                            timestamp
                        )
                    }
                }
                vehicleDao.markDeleted(
                    uid,
                    vehicleId,
                    timestamp,
                    timestamp,
                    DriveSyncState.LOCAL_PENDING.name
                )
                enqueue(uid, vehicleId, DriveSyncOperationType.DELETE_VEHICLE, timestamp)
            }
            DriveMutationResult.Success(normalizedIds.size)
        }
        scheduleSuccessfulResult(uid, result)
    }

    override suspend fun createTrip(
        draft: DriveTripDraft
    ): DriveMutationResult<DriveTrip> = mutate { uid ->
        val timestamp = nextTimestamp()
        val result = database.withTransaction {
            val vehicleExists = draft.vehicleId.isNotBlank() &&
                vehicleDao.getActiveVehicle(uid, draft.vehicleId) != null
            val validation = tripValidator.validate(draft, vehicleExists)
            if (!validation.isValid) {
                return@withTransaction DriveMutationResult.ValidationFailed(validation.issues)
            }
            val trip = DriveTrip(
                id = idGenerator.newId(),
                ownerUid = uid,
                vehicleId = draft.vehicleId,
                startedAt = draft.startedAt,
                endedAt = draft.endedAt,
                startOdometerKm = draft.startOdometerKm,
                endOdometerKm = draft.endOdometerKm,
                distanceKm = requireNotNull(validation.resolvedDistanceKm),
                purpose = draft.purpose,
                startLocationName = draft.startLocationName,
                endLocationName = draft.endLocationName,
                notes = draft.notes,
                entrySource = DriveTripEntrySource.MANUAL,
                createdAt = Instant.ofEpochMilli(timestamp),
                updatedAt = Instant.ofEpochMilli(timestamp),
                syncState = DriveSyncState.LOCAL_PENDING
            )
            tripDao.upsert(trip.toEntity())
            provenanceDao.upsertAll(
                provenanceEntities(
                    uid,
                    DriveSyncEntityType.TRIP.name,
                    trip.id,
                    DriveProvenanceFields.TRIP_FIELDS,
                    DriveFieldSource.LOCAL,
                    timestamp
                )
            )
            enqueue(uid, trip.id, DriveSyncOperationType.CREATE_DRIVE_TRIP, timestamp)
            DriveMutationResult.Success(trip)
        }
        scheduleSuccessfulResult(uid, result)
    }

    override suspend fun updateTrip(
        tripId: String,
        draft: DriveTripDraft
    ): DriveMutationResult<DriveTrip> = mutate { uid ->
        val timestamp = nextTimestamp()
        val result = database.withTransaction {
            val existing = tripDao.getTrip(uid, tripId)
                ?: return@withTransaction DriveMutationResult.NotFound
            if (existing.deletedAt != null) return@withTransaction DriveMutationResult.DeletedRecord
            val vehicleExists = draft.vehicleId.isNotBlank() &&
                vehicleDao.getActiveVehicle(uid, draft.vehicleId) != null
            val validation = tripValidator.validate(draft, vehicleExists)
            if (!validation.isValid) {
                return@withTransaction DriveMutationResult.ValidationFailed(validation.issues)
            }
            val updated = existing.toDomain().copy(
                vehicleId = draft.vehicleId,
                startedAt = draft.startedAt,
                endedAt = draft.endedAt,
                startOdometerKm = draft.startOdometerKm,
                endOdometerKm = draft.endOdometerKm,
                distanceKm = requireNotNull(validation.resolvedDistanceKm),
                purpose = draft.purpose,
                startLocationName = draft.startLocationName,
                endLocationName = draft.endLocationName,
                notes = draft.notes,
                entrySource = DriveTripEntrySource.MANUAL,
                updatedAt = Instant.ofEpochMilli(timestamp),
                syncState = DriveSyncState.LOCAL_PENDING
            )
            val updatedEntity = updated.toEntity()
            tripDao.upsert(updatedEntity)
            provenanceDao.upsertAll(
                provenanceEntities(
                    uid,
                    DriveSyncEntityType.TRIP.name,
                    tripId,
                    changedTripFields(existing, updatedEntity),
                    DriveFieldSource.LOCAL,
                    timestamp
                )
            )
            enqueue(uid, tripId, DriveSyncOperationType.UPDATE_DRIVE_TRIP, timestamp)
            DriveMutationResult.Success(updated)
        }
        scheduleSuccessfulResult(uid, result)
    }

    override suspend fun deleteTrip(tripId: String): DriveMutationResult<Unit> = mutate { uid ->
        val timestamp = nextTimestamp()
        val result = database.withTransaction {
            val existing = tripDao.getTrip(uid, tripId)
                ?: return@withTransaction DriveMutationResult.NotFound
            if (existing.deletedAt != null) return@withTransaction DriveMutationResult.DeletedRecord
            tripDao.markDeleted(
                uid,
                tripId,
                timestamp,
                timestamp,
                DriveSyncState.LOCAL_PENDING.name
            )
            enqueue(uid, tripId, DriveSyncOperationType.DELETE_DRIVE_TRIP, timestamp)
            DriveMutationResult.Success(Unit)
        }
        scheduleSuccessfulResult(uid, result)
    }

    override fun schedulePendingSync() {
        if (!enabled) return
        activeUidOrNull()?.let(syncScheduler::schedule)
    }

    private suspend fun enqueue(
        uid: String,
        recordId: String,
        type: DriveSyncOperationType,
        timestamp: Long
    ) {
        val existing = operationDao.getOperation(uid, type.entityType.name, recordId)
        operationDao.upsert(
            DriveSyncPlanner.plan(
                existing = existing,
                requestedType = type,
                userId = uid,
                recordId = recordId,
                now = timestamp,
                operationId = idGenerator.newId()
            )
        )
    }

    private suspend fun <T> mutate(
        block: suspend (String) -> DriveMutationResult<T>
    ): DriveMutationResult<T> {
        if (!enabled) return DriveMutationResult.StorageFailure(DriveStorageFailureCategory.UNKNOWN)
        val uid = activeUidOrNull() ?: return DriveMutationResult.AuthenticationRequired
        return try {
            withContext(ioDispatcher) { block(uid) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            DriveMutationResult.StorageFailure(DriveStorageFailureCategory.DATABASE)
        }
    }

    private fun activeUidOrNull(): String? = authenticatedUid()?.takeIf(String::isNotBlank)

    private fun Flow<String?>.ownerUidChanges(): Flow<String?> =
        map { it?.takeIf(String::isNotBlank) }.distinctUntilChanged()

    private fun nextTimestamp(): Long {
        while (true) {
            val previous = lastMutationTimestamp.get()
            val candidate = maxOf(nowEpochMillis(), previous + 1L)
            if (lastMutationTimestamp.compareAndSet(previous, candidate)) return candidate
        }
    }

    private fun <T> scheduleResult(uid: String, value: T): DriveMutationResult<T> =
        try {
            syncScheduler.schedule(uid)
            DriveMutationResult.Success(value)
        } catch (_: Exception) {
            DriveMutationResult.LocalSavedSyncSchedulingFailed(value)
        }

    private fun <T> scheduleSuccessfulResult(
        uid: String,
        result: DriveMutationResult<T>
    ): DriveMutationResult<T> = when (result) {
        is DriveMutationResult.Success -> scheduleResult(uid, result.value)
        else -> result
    }

    private fun DriveVehicleWithSummary.toOverviewOrNull(uid: String): DriveVehicleOverview? {
        if (vehicle.userId != uid) return null
        val domainVehicle = vehicle.toDomain()
        return DriveVehicleOverview(
            vehicle = domainVehicle,
            summary = DriveVehicleSummaryCalculator.fromAggregate(
                vehicle = domainVehicle,
                totalDistanceKm = totalDistanceKm,
                tripCount = tripCount,
                lastUsedAt = lastUsedAt?.let(Instant::ofEpochMilli)
            )
        )
    }
}
