package com.example.toplutasima.drive.repository

import com.example.toplutasima.drive.model.DriveTrip
import com.example.toplutasima.drive.model.DriveTripDraft
import com.example.toplutasima.drive.model.DriveVehicle
import com.example.toplutasima.drive.model.DriveVehicleDraft
import com.example.toplutasima.drive.model.DriveVehicleOverview
import com.example.toplutasima.drive.model.DriveVehicleSummary
import com.example.toplutasima.drive.model.DriveFieldProvenance
import com.example.toplutasima.drive.model.DriveHealthIssue
import com.example.toplutasima.drive.model.DriveSyncReceipt
import com.example.toplutasima.drive.validation.DriveValidationIssue
import kotlinx.coroutines.flow.Flow

sealed interface DriveMutationResult<out T> {
    data class Success<T>(val value: T) : DriveMutationResult<T>
    data class LocalSavedSyncSchedulingFailed<T>(val value: T) : DriveMutationResult<T>
    data class ValidationFailed(val issues: List<DriveValidationIssue>) : DriveMutationResult<Nothing>
    data object AuthenticationRequired : DriveMutationResult<Nothing>
    data object OwnershipMismatch : DriveMutationResult<Nothing>
    data object NotFound : DriveMutationResult<Nothing>
    data object DeletedRecord : DriveMutationResult<Nothing>
    data class CascadeConfirmationRequired(val activeTripCount: Int) : DriveMutationResult<Nothing>
    data class StorageFailure(val category: DriveStorageFailureCategory) : DriveMutationResult<Nothing>
}

enum class DriveStorageFailureCategory {
    DATABASE,
    SCHEDULING,
    UNKNOWN
}

interface DriveVehicleRepository {
    fun observeVehicles(): Flow<List<DriveVehicle>>
    fun observeVehicleOverviews(): Flow<List<DriveVehicleOverview>>
    fun observeVehicle(vehicleId: String): Flow<DriveVehicle?>
    fun observeSummary(vehicleId: String): Flow<DriveVehicleSummary?>

    suspend fun createVehicle(draft: DriveVehicleDraft): DriveMutationResult<DriveVehicle>
    suspend fun updateVehicle(
        vehicleId: String,
        draft: DriveVehicleDraft
    ): DriveMutationResult<DriveVehicle>

    suspend fun assignPerson(
        vehicleId: String,
        assignedPersonId: String?
    ): DriveMutationResult<DriveVehicle>

    suspend fun deleteVehicle(
        vehicleId: String,
        deleteLinkedTrips: Boolean
    ): DriveMutationResult<Unit>

    fun schedulePendingSync()
}

interface DriveTripRepository {
    fun observeTrips(vehicleId: String): Flow<List<DriveTrip>>
    suspend fun createTrip(draft: DriveTripDraft): DriveMutationResult<DriveTrip>
    suspend fun updateTrip(
        tripId: String,
        draft: DriveTripDraft
    ): DriveMutationResult<DriveTrip>

    suspend fun deleteTrip(tripId: String): DriveMutationResult<Unit>
}

interface DriveSyncRepository {
    suspend fun synchronize(ownerUid: String): DriveSyncRunResult
}

interface DriveAdvancedRepository {
    fun observeHealth(): Flow<List<DriveHealthIssue>>
    fun observeSyncReceipts(): Flow<List<DriveSyncReceipt>>
    fun observeProvenance(entityType: String, recordId: String): Flow<List<DriveFieldProvenance>>
    suspend fun bulkDeleteVehicles(vehicleIds: Set<String>): DriveMutationResult<Int>

    companion object {
        val Disabled: DriveAdvancedRepository = object : DriveAdvancedRepository {
            override fun observeHealth(): Flow<List<DriveHealthIssue>> =
                kotlinx.coroutines.flow.flowOf(emptyList())

            override fun observeSyncReceipts(): Flow<List<DriveSyncReceipt>> =
                kotlinx.coroutines.flow.flowOf(emptyList())

            override fun observeProvenance(
                entityType: String,
                recordId: String
            ): Flow<List<DriveFieldProvenance>> = kotlinx.coroutines.flow.flowOf(emptyList())

            override suspend fun bulkDeleteVehicles(
                vehicleIds: Set<String>
            ): DriveMutationResult<Int> =
                DriveMutationResult.StorageFailure(DriveStorageFailureCategory.UNKNOWN)
        }
    }
}

fun interface DriveSyncWorkScheduler {
    fun schedule(ownerUid: String)
}

data class DriveSyncRunResult(
    val processedCount: Int,
    val retryRequired: Boolean,
    val permanentFailureCount: Int,
    val pulledCount: Int = 0
)
