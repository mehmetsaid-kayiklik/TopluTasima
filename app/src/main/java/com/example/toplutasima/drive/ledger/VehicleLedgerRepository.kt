package com.example.toplutasima.drive.ledger

import com.example.toplutasima.data.local.entity.DriveLedgerConflictEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncReceiptEntity
import kotlinx.coroutines.flow.Flow
import shared.vehicleledger.contract.VehicleExpenseContract
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract

/** UID-scoped, local-first API for the versioned vehicle-ledger domain. */
interface VehicleLedgerRepository {
    fun observeOdometerHistory(vehicleId: String): Flow<List<VehicleOdometerEntryContract>>
    fun observeCurrentOdometer(vehicleId: String): Flow<CurrentOdometerProjection>
    suspend fun createOdometer(draft: OdometerDraft): VehicleLedgerMutationResult<VehicleOdometerEntryContract>
    suspend fun updateOdometer(
        odometerEntryId: String,
        draft: OdometerDraft
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract>
    suspend fun tombstoneOdometer(odometerEntryId: String): VehicleLedgerMutationResult<Unit>
    suspend fun restoreOdometer(odometerEntryId: String): VehicleLedgerMutationResult<VehicleOdometerEntryContract>
    suspend fun acceptLegacyMirrorAsMeasurement(
        vehicleId: String,
        observedAt: Long,
        odometerSeriesId: String
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract>
    suspend fun startOdometerSeries(
        draft: OdometerDraft,
        resetReason: String
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract>
    suspend fun runLegacyOdometerBackfill(): VehicleLedgerMutationResult<Int>

    fun observeExpenses(
        vehicleId: String,
        fromInclusive: Long,
        toExclusive: Long
    ): Flow<List<VehicleExpenseContract>>
    fun observeExpenseSummary(
        vehicleId: String,
        fromInclusive: Long,
        toExclusive: Long
    ): Flow<List<ExpenseSummary>>
    suspend fun createExpense(draft: ExpenseDraft): VehicleLedgerMutationResult<VehicleExpenseContract>
    suspend fun updateExpense(
        expenseId: String,
        draft: ExpenseDraft
    ): VehicleLedgerMutationResult<VehicleExpenseContract>
    suspend fun tombstoneExpense(expenseId: String): VehicleLedgerMutationResult<Unit>
    suspend fun restoreExpense(expenseId: String): VehicleLedgerMutationResult<VehicleExpenseContract>
    suspend fun duplicateExpenseCandidates(
        draft: ExpenseDraft,
        excludeExpenseId: String = ""
    ): List<VehicleExpenseContract>

    fun observeReminders(vehicleId: String): Flow<List<VehicleReminderContract>>
    fun observeDueReminders(vehicleId: String): Flow<List<DueReminderProjection>>
    suspend fun createReminder(draft: ReminderDraft): VehicleLedgerMutationResult<VehicleReminderContract>
    suspend fun updateReminder(
        reminderId: String,
        draft: ReminderDraft
    ): VehicleLedgerMutationResult<VehicleReminderContract>
    suspend fun completeReminder(
        reminderId: String,
        completedAt: Long,
        completedOdometerMeters: Long?
    ): VehicleLedgerMutationResult<VehicleReminderContract>
    suspend fun snoozeReminder(
        reminderId: String,
        untilEpochDay: Long
    ): VehicleLedgerMutationResult<VehicleReminderContract>
    suspend fun disableReminder(reminderId: String): VehicleLedgerMutationResult<VehicleReminderContract>
    suspend fun tombstoneReminder(reminderId: String): VehicleLedgerMutationResult<Unit>
    suspend fun restoreReminder(reminderId: String): VehicleLedgerMutationResult<VehicleReminderContract>

    fun observeDashboard(vehicleId: String): Flow<VehicleLedgerDashboard>
    fun observeHealth(vehicleId: String): Flow<List<VehicleLedgerHealthIssue>>
    fun observeReceipts(): Flow<List<DriveLedgerSyncReceiptEntity>>
    fun observeConflicts(): Flow<List<DriveLedgerConflictEntity>>
    fun schedulePendingSync()
}
