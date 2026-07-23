package com.example.toplutasima.drive.sync

import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.repository.DriveAuthSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Purges Drive-only rows outside the active account and cancels stale Drive work. */
class DriveAccountScopeManager(
    private val databaseProvider: () -> AppDatabase?,
    private val onAuthenticatedUserChanged: (String?) -> Unit,
    private val authChanges: kotlinx.coroutines.flow.Flow<String?> =
        DriveAuthSession.authenticatedUidChanges(),
    private val currentUserId: () -> String?,
    private val onPhotoScopeChanged: suspend (String?) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_CORE
) {
    private val started = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private var job: Job? = null

    fun start() {
        if (!enabled || !started.compareAndSet(false, true)) return
        job = scope.launch {
            authChanges.collectLatest { rawUserId ->
                val userId = rawUserId?.takeIf(String::isNotBlank)
                purgeOutsideActiveAccount(userId)
                if (currentUserId()?.takeIf(String::isNotBlank) == userId) {
                    onAuthenticatedUserChanged(userId)
                }
            }
        }
    }

    internal suspend fun purgeOutsideActiveAccount(userId: String?) {
        val database = databaseProvider()
        try {
            database?.withTransaction {
                if (userId == null) {
                    database.driveLedgerConflictDao().deleteAll()
                    database.driveLedgerSyncReceiptDao().deleteAll()
                    database.driveLedgerSyncMetadataDao().deleteAll()
                    database.driveLedgerOperationDao().deleteAll()
                    database.driveReminderDao().deleteAll()
                    database.driveExpenseDao().deleteAll()
                    database.driveOdometerEntryDao().deleteAll()
                    database.drivePhotoOperationDao().deleteAll()
                    database.drivePhotoSyncReceiptDao().deleteAll()
                    database.drivePhotoSyncMetadataDao().deleteAll()
                    database.driveVehiclePhotoDao().deleteAll()
                    database.driveAssignmentOperationDao().deleteAll()
                    database.driveAssignmentSyncReceiptDao().deleteAll()
                    database.driveAssignmentSyncMetadataDao().deleteAll()
                    database.driveVehicleAssignmentDao().deleteAll()
                    database.driveSyncOperationDao().deleteAll()
                    database.driveSyncReceiptDao().deleteAll()
                    database.driveFieldProvenanceDao().deleteAll()
                    database.driveSyncMetadataDao().deleteAll()
                    database.driveTripDao().deleteAll()
                    database.driveVehicleDao().deleteAll()
                } else {
                    database.driveLedgerConflictDao().deleteAllExceptUser(userId)
                    database.driveLedgerSyncReceiptDao().deleteAllExceptUser(userId)
                    database.driveLedgerSyncMetadataDao().deleteAllExceptUser(userId)
                    database.driveLedgerOperationDao().deleteAllExceptUser(userId)
                    database.driveReminderDao().deleteAllExceptUser(userId)
                    database.driveExpenseDao().deleteAllExceptUser(userId)
                    database.driveOdometerEntryDao().deleteAllExceptUser(userId)
                    database.drivePhotoOperationDao().deleteAllExceptUser(userId)
                    database.drivePhotoSyncReceiptDao().deleteAllExceptUser(userId)
                    database.drivePhotoSyncMetadataDao().deleteAllExceptUser(userId)
                    database.driveVehiclePhotoDao().deleteAllExceptUser(userId)
                    database.driveAssignmentOperationDao().deleteAllExceptUser(userId)
                    database.driveAssignmentSyncReceiptDao().deleteAllExceptUser(userId)
                    database.driveAssignmentSyncMetadataDao().deleteAllExceptUser(userId)
                    database.driveVehicleAssignmentDao().deleteAllExceptUser(userId)
                    database.driveSyncOperationDao().deleteAllExceptUser(userId)
                    database.driveSyncReceiptDao().deleteAllExceptUser(userId)
                    database.driveFieldProvenanceDao().deleteAllExceptUser(userId)
                    database.driveSyncMetadataDao().deleteAllExceptUser(userId)
                    database.driveTripDao().deleteAllExceptUser(userId)
                    database.driveVehicleDao().deleteAllExceptUser(userId)
                }
            }
            onPhotoScopeChanged(userId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }
}
