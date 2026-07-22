package com.example.toplutasima.drive.assignment

import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveAssignmentOperationEntity
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.repository.DriveIdGenerator
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import shared.vehicleassignment.contract.AssignmentRevision
import shared.vehicleassignment.contract.VehicleAssignmentContract
import shared.vehicleassignment.contract.VehicleAssignmentContractSpec
import shared.vehicleassignment.contract.VehicleAssignmentSource

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OfflineFirstVehicleAssignmentRepository(
    private val database: AppDatabase,
    private val currentUserId: () -> String?,
    private val authenticatedUidChanges: Flow<String?>,
    private val syncScheduler: DriveSyncWorkScheduler,
    private val directoryRefresher: suspend () -> Unit,
    private val idGenerator: DriveIdGenerator = DriveIdGenerator.UUID,
    private val now: () -> Long = System::currentTimeMillis,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_PERSON_DIRECTORY
) : VehicleAssignmentRepository {
    private val assignmentDao get() = database.driveVehicleAssignmentDao()
    private val operationDao get() = database.driveAssignmentOperationDao()
    private val vehicleDao get() = database.driveVehicleDao()
    private val profileDao get() = database.profileDao()

    override fun observeAssignments(): Flow<List<VehicleAssignment>> {
        if (!enabled) return flowOf(emptyList())
        return authenticatedUidChanges.flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(emptyList())
            else assignmentDao.observeAll(uid).map { rows -> rows.map { it.toDomain() } }
        }
    }

    override fun observeAssignment(vehicleId: String): Flow<VehicleAssignment?> {
        if (!enabled || vehicleId.isBlank()) return flowOf(null)
        return authenticatedUidChanges.flatMapLatest { uid ->
            if (uid.isNullOrBlank()) flowOf(null)
            else assignmentDao.observe(uid, vehicleId).map { it?.toDomain() }
        }
    }

    override fun observeSelectablePeople(): Flow<List<VehiclePersonDirectoryEntry>> {
        if (!enabled) return flowOf(emptyList())
        return authenticatedUidChanges.flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                profileDao.observeSharedWithTransitProfiles(uid).map { profiles ->
                    profiles.map { profile ->
                        VehiclePersonDirectoryEntry(
                            personId = profile.id,
                            displayName = profile.displayName,
                            sharedWithTransit = profile.sharedWithTransit,
                            archived = profile.archived,
                            deleted = false,
                            identityValid = true
                        )
                    }
                }
            }
        }
    }

    override suspend fun assign(
        vehicleId: String,
        personId: String
    ): VehicleAssignmentMutationResult = mutate(vehicleId, personId, tombstone = false)

    override suspend fun remove(vehicleId: String): VehicleAssignmentMutationResult =
        mutate(vehicleId, requestedPersonId = null, tombstone = true)

    override suspend fun refreshPersonDirectory(): Result<Unit> {
        if (!enabled) return Result.success(Unit)
        return try {
            withContext(ioDispatcher) { directoryRefresher() }
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override fun schedulePendingSync() {
        if (!enabled) return
        currentUserId()?.takeIf(String::isNotBlank)?.let(syncScheduler::schedule)
    }

    private suspend fun mutate(
        rawVehicleId: String,
        requestedPersonId: String?,
        tombstone: Boolean
    ): VehicleAssignmentMutationResult {
        if (!enabled) {
            return VehicleAssignmentMutationResult.Rejected(
                VehicleAssignmentFailure.FatalRemoteFailure
            )
        }
        val ownerUid = currentUserId()?.takeIf(String::isNotBlank)
            ?: return VehicleAssignmentMutationResult.Rejected(
                VehicleAssignmentFailure.AuthenticationChanged
            )
        val vehicleId = rawVehicleId.trim()
        val personId = requestedPersonId?.trim()?.takeIf(String::isNotEmpty)
        if (vehicleId.isBlank()) {
            return VehicleAssignmentMutationResult.Rejected(VehicleAssignmentFailure.VehicleNotFound)
        }

        val localResult: VehicleAssignmentMutationResult = try {
            withContext(ioDispatcher) {
                database.withTransaction<VehicleAssignmentMutationResult> {
                    applyLocalMutation(ownerUid, vehicleId, personId, tombstone)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return VehicleAssignmentMutationResult.Rejected(VehicleAssignmentFailure.LocalStorageFailure)
        }

        if (localResult !is VehicleAssignmentMutationResult.Success) return localResult
        if (currentUserId() != ownerUid) {
            return VehicleAssignmentMutationResult.Rejected(
                VehicleAssignmentFailure.AuthenticationChanged
            )
        }
        return try {
            syncScheduler.schedule(ownerUid)
            localResult
        } catch (_: Exception) {
            VehicleAssignmentMutationResult.LocalSavedSyncSchedulingFailed(localResult.assignment)
        }
    }

    private suspend fun applyLocalMutation(
        ownerUid: String,
        vehicleId: String,
        personId: String?,
        tombstone: Boolean
    ): VehicleAssignmentMutationResult {
        if (currentUserId() != ownerUid) {
            return VehicleAssignmentMutationResult.Rejected(
                VehicleAssignmentFailure.AuthenticationChanged
            )
        }
        val vehicle = vehicleDao.getVehicle(ownerUid, vehicleId)
            ?: return VehicleAssignmentMutationResult.Rejected(
                VehicleAssignmentFailure.VehicleNotFound
            )
        if (vehicle.deletedAt != null) {
            return VehicleAssignmentMutationResult.Rejected(VehicleAssignmentFailure.VehicleDeleted)
        }
        if (!tombstone) {
            val profile = personId?.let { profileDao.getProfileById(ownerUid, it) }
                ?: return VehicleAssignmentMutationResult.Rejected(
                    VehicleAssignmentFailure.PersonNotFound
                )
            if (profile.id != personId) {
                return VehicleAssignmentMutationResult.Rejected(
                    VehicleAssignmentFailure.InvalidPersonIdentity
                )
            }
            if (profile.archived) {
                return VehicleAssignmentMutationResult.Rejected(VehicleAssignmentFailure.PersonDeleted)
            }
            if (!profile.sharedWithTransit) {
                return VehicleAssignmentMutationResult.Rejected(
                    VehicleAssignmentFailure.PersonNotShared
                )
            }
        }

        val current = assignmentDao.get(ownerUid, vehicleId)
        if (
            !tombstone && current != null && current.deletedAt == null && current.personId == personId
        ) {
            return VehicleAssignmentMutationResult.Success(current.toDomain())
        }
        if (tombstone && (current == null || current.deletedAt != null)) {
            return current?.let { VehicleAssignmentMutationResult.Success(it.toDomain()) }
                ?: VehicleAssignmentMutationResult.Rejected(
                    VehicleAssignmentFailure.AssignmentConflict
                )
        }

        val highestRevision = maxOf(
            current?.revision ?: 0L,
            operationDao.highestTargetRevision(ownerUid, vehicleId) ?: 0L
        )
        val revision = AssignmentRevision.next(highestRevision)
        val timestamp = now()
        val operationId = idGenerator.newId()
        val retainedPersonId = if (tombstone) current?.personId else personId
        val contract = VehicleAssignmentContract(
            vehicleId = vehicleId,
            personId = retainedPersonId,
            schemaVersion = VehicleAssignmentContractSpec.CURRENT_SCHEMA_VERSION,
            revision = revision,
            operationId = operationId,
            source = VehicleAssignmentSource.TOPLU_TASIMA,
            clientUpdatedAt = timestamp,
            deletedAt = timestamp.takeIf { tombstone }
        )
        if (contract.validate(vehicleId).isNotEmpty()) {
            return VehicleAssignmentMutationResult.Rejected(
                VehicleAssignmentFailure.InvalidPersonIdentity
            )
        }
        val entity = contract.toEntity(
            ownerUid = ownerUid,
            serverUpdatedAt = null,
            syncState = VehicleAssignmentSyncState.PENDING
        )
        assignmentDao.upsert(entity)
        operationDao.insertIfAbsent(
            DriveAssignmentOperationEntity(
                ownerUid = ownerUid,
                operationId = operationId,
                vehicleId = vehicleId,
                personId = retainedPersonId,
                schemaVersion = contract.schemaVersion,
                targetRevision = revision,
                source = contract.source.wireValue,
                clientUpdatedAt = timestamp,
                deletedAt = contract.deletedAt,
                state = "PENDING",
                createdAt = timestamp,
                updatedAt = timestamp
            )
        )
        vehicleDao.setAssignmentMirror(
            ownerUid,
            vehicleId,
            retainedPersonId.takeIf { !tombstone }
        )
        return VehicleAssignmentMutationResult.Success(entity.toDomain())
    }
}
