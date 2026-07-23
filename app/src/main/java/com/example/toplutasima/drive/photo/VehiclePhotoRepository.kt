package com.example.toplutasima.drive.photo

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface VehiclePhotoRepository {
    fun observePhotos(vehicleId: String): Flow<List<DriveVehiclePhoto>>
    suspend fun add(vehicleId: String, source: Uri): VehiclePhotoMutationResult
    suspend fun delete(vehicleId: String, photoId: String): VehiclePhotoMutationResult
    suspend fun setPrimary(vehicleId: String, photoId: String): VehiclePhotoMutationResult
    suspend fun move(vehicleId: String, photoId: String, direction: Int): VehiclePhotoMutationResult
    suspend fun retry(vehicleId: String, photoId: String): VehiclePhotoMutationResult
    suspend fun ensureLocalCopies(vehicleId: String)
    suspend fun schedulePendingOperations()
}
