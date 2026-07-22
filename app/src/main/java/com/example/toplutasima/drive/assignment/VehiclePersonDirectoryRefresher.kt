package com.example.toplutasima.drive.assignment

import androidx.room.withTransaction
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.repository.ProfileSyncRepository
import com.example.toplutasima.network.firestore.FirestorePersonService
import kotlinx.coroutines.CancellationException

/**
 * İsimleri yalnız consent sorgusundan cache'ler; paylaşılmayan/silinmiş kişiler için yalnız health
 * sinyali tutulur. Assignment veya araç kaydı otomatik düzeltilmez.
 */
class VehiclePersonDirectoryRefresher(
    private val database: AppDatabase,
    private val profileSyncRepository: ProfileSyncRepository,
    private val currentUserId: () -> String? = CurrentUserProvider::currentUserIdOrNull,
    private val fetchShareStates: suspend () -> List<FirestorePersonService.PersonShareState> =
        FirestorePersonService::fetchShareStates,
    private val fetchPersonTombstones: suspend () -> List<FirestorePersonService.PersonTombstone> =
        FirestorePersonService::fetchPersonTombstones
) {
    suspend fun refresh() {
        val ownerUid = currentUserId()?.takeIf(String::isNotBlank)
            ?: throw VehicleAssignmentTypedException(VehicleAssignmentFailure.AuthenticationChanged)
        profileSyncRepository.refreshSharedProfiles()
        assertOwner(ownerUid)
        val states = fetchShareStates()
        assertOwner(ownerUid)
        val deletedPersonIds = fetchPersonTombstones().mapTo(hashSetOf()) { it.personId }
        assertOwner(ownerUid)

        val statesByDocumentId = states.associateBy { it.documentId }
        database.withTransaction {
            database.driveVehicleAssignmentDao().getAll(ownerUid)
                .filter { it.deletedAt == null }
                .forEach { assignment ->
                    val vehicle = database.driveVehicleDao().getVehicle(ownerUid, assignment.vehicleId)
                    val state = assignment.personId?.let(statesByDocumentId::get)
                    val health = when {
                        vehicle == null || vehicle.deletedAt != null ->
                            VehicleAssignmentHealthCode.ASSIGNMENT_VEHICLE_NOT_FOUND
                        assignment.personId == null ->
                            VehicleAssignmentHealthCode.ASSIGNED_PERSON_NOT_FOUND
                        assignment.personId in deletedPersonIds ->
                            VehicleAssignmentHealthCode.ASSIGNED_PERSON_DELETED
                        state == null ->
                            VehicleAssignmentHealthCode.ASSIGNED_PERSON_NOT_FOUND
                        !state.identityValid ->
                            VehicleAssignmentHealthCode.PERSON_ID_DOCUMENT_ID_MISMATCH
                        state.archived || !state.sharedWithTransit ->
                            VehicleAssignmentHealthCode.ASSIGNED_PERSON_NOT_SHARED
                        else -> null
                    }
                    database.driveVehicleAssignmentDao().setHealth(
                        ownerUid = ownerUid,
                        vehicleId = assignment.vehicleId,
                        healthCode = health?.name
                    )
                }
        }
        assertOwner(ownerUid)
    }

    private fun assertOwner(expected: String) {
        if (currentUserId() != expected) {
            throw VehicleAssignmentTypedException(VehicleAssignmentFailure.AuthenticationChanged)
        }
    }
}

class VehicleAssignmentTypedException(
    val failure: VehicleAssignmentFailure,
    cause: Throwable? = null
) : Exception(failure::class.simpleName, cause)
