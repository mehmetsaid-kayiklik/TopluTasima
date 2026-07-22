package com.example.toplutasima.drive.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import com.example.toplutasima.worker.DriveSyncWorker

class WorkManagerDriveSyncScheduler(
    context: Context,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_CORE
) : DriveSyncWorkScheduler {
    private val appContext = context.applicationContext

    override fun schedule(ownerUid: String) {
        if (!enabled || ownerUid.isBlank()) return
        if (CurrentUserProvider.currentUserIdOrNull() != ownerUid) return
        val request = OneTimeWorkRequestBuilder<DriveSyncWorker>()
            .setInputData(workDataOf(DriveSyncWorker.INPUT_OWNER_UID to ownerUid))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(WORK_TAG)
            .build()
        WorkManager.getInstance(appContext).enqueueUniqueWork(
            UNIQUE_WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    fun onAuthenticatedUserChanged(ownerUid: String?) {
        WorkManager.getInstance(appContext).cancelUniqueWork(UNIQUE_WORK_NAME)
        if (!ownerUid.isNullOrBlank()) schedule(ownerUid)
    }

    internal companion object {
        const val UNIQUE_WORK_NAME = "drive_core_sync"
        const val WORK_TAG = "drive_core_sync"
    }
}
