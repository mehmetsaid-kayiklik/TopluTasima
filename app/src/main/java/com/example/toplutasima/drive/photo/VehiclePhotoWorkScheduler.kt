package com.example.toplutasima.drive.photo

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.worker.VehiclePhotoSyncWorker
import java.util.concurrent.TimeUnit

interface VehiclePhotoSyncScheduler {
    fun enqueue(ownerUid: String, operationId: String, photoId: String, vehicleId: String)
    fun onAuthenticatedUserChanged(ownerUid: String?)
}

class WorkManagerVehiclePhotoSyncScheduler(
    context: Context,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_VEHICLE_PHOTOS
) : VehiclePhotoSyncScheduler {
    private val appContext = context.applicationContext

    override fun enqueue(ownerUid: String, operationId: String, photoId: String, vehicleId: String) {
        if (!enabled || ownerUid.isBlank() || operationId.isBlank() || photoId.isBlank() || vehicleId.isBlank()) return
        if (CurrentUserProvider.currentUserIdOrNull() != ownerUid) return
        val request = OneTimeWorkRequestBuilder<VehiclePhotoSyncWorker>()
            .setInputData(
                workDataOf(
                    VehiclePhotoSyncWorker.INPUT_OWNER_UID to ownerUid,
                    VehiclePhotoSyncWorker.INPUT_OPERATION_ID to operationId,
                    VehiclePhotoSyncWorker.INPUT_PHOTO_ID to photoId,
                    VehiclePhotoSyncWorker.INPUT_VEHICLE_ID to vehicleId
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, MIN_BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .addTag(ownerTag(ownerUid))
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            uniqueName(operationId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    override fun onAuthenticatedUserChanged(ownerUid: String?) {
        WorkManager.getInstance(appContext).cancelAllWorkByTag(WORK_TAG)
    }

    private fun uniqueName(operationId: String): String =
        "$WORK_PREFIX-${VehiclePhotoFileStore.stableScopeHash(operationId)}"

    private fun ownerTag(ownerUid: String): String =
        "$OWNER_TAG_PREFIX-${VehiclePhotoFileStore.stableScopeHash(ownerUid)}"

    internal companion object {
        const val WORK_TAG = "drive_vehicle_photo_sync"
        const val WORK_PREFIX = "drive_vehicle_photo"
        const val OWNER_TAG_PREFIX = "drive_vehicle_photo_owner"
        const val MIN_BACKOFF_SECONDS = 30L
    }
}
