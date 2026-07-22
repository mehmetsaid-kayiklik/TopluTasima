package com.example.toplutasima.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.toplutasima.auth.CurrentUserProvider
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.repository.DriveSyncRunResult
import com.example.toplutasima.drive.repository.DriveSyncRepository
import kotlinx.coroutines.CancellationException
import org.koin.core.context.GlobalContext

internal interface DriveSyncWorkerOperations {
    fun currentUserIdOrNull(): String?
    suspend fun synchronize(ownerUid: String): DriveSyncRunResult
}

private class DefaultDriveSyncWorkerOperations : DriveSyncWorkerOperations {
    private val repository: DriveSyncRepository by lazy {
        GlobalContext.get().get()
    }

    override fun currentUserIdOrNull(): String? = CurrentUserProvider.currentUserIdOrNull()

    override suspend fun synchronize(ownerUid: String): DriveSyncRunResult =
        repository.synchronize(ownerUid)
}

class DriveSyncWorker internal constructor(
    appContext: Context,
    workerParams: WorkerParameters,
    private val operations: DriveSyncWorkerOperations,
    private val enabled: Boolean
) : CoroutineWorker(appContext, workerParams) {
    constructor(
        appContext: Context,
        workerParams: WorkerParameters
    ) : this(
        appContext = appContext,
        workerParams = workerParams,
        operations = DefaultDriveSyncWorkerOperations(),
        enabled = DriveFeatureFlags.DRIVE_CORE
    )

    override suspend fun doWork(): Result {
        if (!enabled) return Result.success()
        val requestedOwnerUid = inputData.getString(INPUT_OWNER_UID)
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()
        if (operations.currentUserIdOrNull() != requestedOwnerUid) return Result.success()
        return try {
            val result = operations.synchronize(requestedOwnerUid)
            when {
                operations.currentUserIdOrNull() != requestedOwnerUid -> Result.success()
                result.retryRequired -> Result.retry()
                result.permanentFailureCount > 0 -> Result.failure()
                else -> Result.success()
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Result.retry()
        }
    }

    internal companion object {
        const val INPUT_OWNER_UID = "drive_sync_owner_uid"
    }
}
