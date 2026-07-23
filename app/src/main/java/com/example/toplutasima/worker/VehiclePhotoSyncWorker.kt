package com.example.toplutasima.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.photo.RoomVehiclePhotoSyncCoordinator
import com.example.toplutasima.drive.photo.VehiclePhotoSyncResult
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

internal interface VehiclePhotoWorkerOperations {
    fun currentUserIdOrNull(): String?
    suspend fun process(ownerUid: String, operationId: String, photoId: String, vehicleId: String): VehiclePhotoSyncResult
}

private class DefaultVehiclePhotoWorkerOperations : VehiclePhotoWorkerOperations {
    private val coordinator: RoomVehiclePhotoSyncCoordinator by lazy { GlobalContext.get().get() }
    override fun currentUserIdOrNull(): String? = CurrentUserProvider.currentUserIdOrNull()
    override suspend fun process(
        ownerUid: String,
        operationId: String,
        photoId: String,
        vehicleId: String
    ): VehiclePhotoSyncResult = coordinator.process(ownerUid, operationId, photoId, vehicleId)
}

class VehiclePhotoSyncWorker internal constructor(
    appContext: Context,
    params: WorkerParameters,
    private val operations: VehiclePhotoWorkerOperations,
    private val enabled: Boolean
) : CoroutineWorker(appContext, params) {
    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext,
        params,
        DefaultVehiclePhotoWorkerOperations(),
        DriveFeatureFlags.DRIVE_VEHICLE_PHOTOS
    )

    override suspend fun doWork(): Result {
        if (!enabled) return Result.success()
        val ownerUid = inputData.getString(INPUT_OWNER_UID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val operationId = inputData.getString(INPUT_OPERATION_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val photoId = inputData.getString(INPUT_PHOTO_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        val vehicleId = inputData.getString(INPUT_VEHICLE_ID)?.takeIf(String::isNotBlank)
            ?: return Result.failure()
        if (operations.currentUserIdOrNull() != ownerUid) return Result.success()
        return try {
            val result = operations.process(ownerUid, operationId, photoId, vehicleId)
            when {
                operations.currentUserIdOrNull() != ownerUid -> Result.success()
                result.retryRequired -> Result.retry()
                result.fatal -> Result.failure()
                else -> Result.success()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: com.example.toplutasima.drive.photo.VehiclePhotoFailure.AuthenticationChanged) {
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < MAX_UNKNOWN_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val INPUT_OWNER_UID = "vehicle_photo_owner_uid"
        const val INPUT_OPERATION_ID = "vehicle_photo_operation_id"
        const val INPUT_PHOTO_ID = "vehicle_photo_id"
        const val INPUT_VEHICLE_ID = "vehicle_photo_vehicle_id"
        private const val MAX_UNKNOWN_ATTEMPTS = 5
    }
}
