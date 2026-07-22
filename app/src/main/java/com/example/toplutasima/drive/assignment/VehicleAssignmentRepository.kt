package com.example.toplutasima.drive.assignment

import kotlinx.coroutines.flow.Flow

interface VehicleAssignmentRepository {
    fun observeAssignments(): Flow<List<VehicleAssignment>>
    fun observeAssignment(vehicleId: String): Flow<VehicleAssignment?>
    fun observeSelectablePeople(): Flow<List<VehiclePersonDirectoryEntry>>

    suspend fun assign(vehicleId: String, personId: String): VehicleAssignmentMutationResult
    suspend fun remove(vehicleId: String): VehicleAssignmentMutationResult
    suspend fun refreshPersonDirectory(): Result<Unit>
    fun schedulePendingSync()
}
