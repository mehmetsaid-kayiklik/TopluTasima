package com.example.toplutasima.drive.ledger

import androidx.room.withTransaction
import com.example.toplutasima.data.local.AppDatabase
import com.example.toplutasima.data.local.entity.DriveFieldProvenanceEntity
import com.example.toplutasima.data.local.entity.DriveLedgerConflictEntity
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncReceiptEntity
import com.example.toplutasima.drive.DriveFeatureFlags
import com.example.toplutasima.drive.repository.DriveAuthSession
import com.example.toplutasima.drive.repository.DriveSyncWorkScheduler
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import shared.vehicleledger.contract.OdometerQuality
import shared.vehicleledger.contract.OdometerReadingRole
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseContract
import shared.vehicleledger.contract.VehicleExpenseTransactionKind
import shared.vehicleledger.contract.VehicleLedgerContractSpec
import shared.vehicleledger.contract.VehicleLedgerEntityType
import shared.vehicleledger.contract.VehicleLedgerEnvelope
import shared.vehicleledger.contract.VehicleLedgerOperationKind
import shared.vehicleledger.contract.VehicleLedgerRevision
import shared.vehicleledger.contract.VehicleLedgerSource
import shared.vehicleledger.contract.VehicleLedgerValidator
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract
import shared.vehicleledger.contract.VehicleReminderStatus

@OptIn(ExperimentalCoroutinesApi::class)
class OfflineFirstVehicleLedgerRepository(
    private val database: AppDatabase,
    private val currentUserId: () -> String?,
    private val authenticatedUidChanges: Flow<String?> = DriveAuthSession.authenticatedUidChanges(),
    private val syncScheduler: DriveSyncWorkScheduler,
    private val now: () -> Long = System::currentTimeMillis,
    private val enabled: Boolean = DriveFeatureFlags.DRIVE_VEHICLE_LEDGER
) : VehicleLedgerRepository {

    override fun observeOdometerHistory(vehicleId: String): Flow<List<VehicleOdometerEntryContract>> =
        scoped(emptyList()) { ownerUid ->
            database.driveOdometerEntryDao().observeHistory(ownerUid, vehicleId)
                .mapLedger { rows -> rows.map { it.toContract() } }
        }

    override fun observeCurrentOdometer(vehicleId: String): Flow<CurrentOdometerProjection> =
        scoped(CurrentOdometerProjection(null, null, false, false)) { ownerUid ->
            combine(
                database.driveOdometerEntryDao().observeCurrent(ownerUid, vehicleId),
                database.driveVehicleDao().observeVehicleIncludingDeleted(ownerUid, vehicleId)
            ) { entry, vehicle ->
                val contract = entry?.toContract()
                val mirror = VehicleLedgerUnits.legacyKilometersToMeters(vehicle?.currentOdometerKm)
                CurrentOdometerProjection(
                    entry = contract,
                    meters = contract?.odometerMeters,
                    isEstimated = contract?.quality == OdometerQuality.ESTIMATED,
                    mirrorStale = contract?.quality == OdometerQuality.CONFIRMED &&
                        mirror != contract.odometerMeters
                )
            }
        }

    override suspend fun createOdometer(
        draft: OdometerDraft
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract> = guardedMutation(draft.vehicleId) { ownerUid ->
        val timestamp = now()
        val recordId = UUID.randomUUID().toString()
        val operationId = UUID.randomUUID().toString()
        val batchId = UUID.randomUUID().toString()
        val value = odometerFromDraft(
            ownerUid = ownerUid,
            recordId = recordId,
            draft = draft,
            revision = 1,
            operationId = operationId,
            createdAt = timestamp,
            updatedAt = timestamp,
            deletedAt = null
        )
        val validation = VehicleLedgerValidator.validate(recordId, ownerUid, value)
        if (validation.isNotEmpty()) return@guardedMutation rejectedValidation(validation.map { it.name })
        database.withTransaction {
            database.driveOdometerEntryDao().upsert(value.toEntity(VehicleLedgerSyncState.LOCAL_PENDING))
            insertOperation(value, VehicleLedgerOperationKind.CREATE, batchId, timestamp)
            insertProvenance(value.envelope, VehicleLedgerEntityType.ODOMETER, recordId, ODOMETER_FIELDS)
            enqueueMirror(ownerUid, draft.vehicleId, batchId, timestamp)
        }
        schedule(ownerUid, value)
    }

    override suspend fun updateOdometer(
        odometerEntryId: String,
        draft: OdometerDraft
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract> = guardedMutation(draft.vehicleId) { ownerUid ->
        val current = database.driveOdometerEntryDao().get(ownerUid, odometerEntryId)
            ?: return@guardedMutation rejected(VehicleLedgerFailure.RecordNotFound)
        if (current.deletedAt != null) return@guardedMutation rejected(VehicleLedgerFailure.RecordDeleted)
        if (current.vehicleId != draft.vehicleId) return@guardedMutation rejected(VehicleLedgerFailure.AccountMismatch)
        val revision = nextRevision(ownerUid, VehicleLedgerEntityType.ODOMETER, odometerEntryId, current.revision)
            ?: return@guardedMutation rejected(VehicleLedgerFailure.RevisionOverflow)
        val timestamp = now()
        val batchId = UUID.randomUUID().toString()
        val value = odometerFromDraft(
            ownerUid, odometerEntryId, draft, revision, UUID.randomUUID().toString(),
            current.createdAt, timestamp, null
        )
        val validation = VehicleLedgerValidator.validate(odometerEntryId, ownerUid, value)
        if (validation.isNotEmpty()) return@guardedMutation rejectedValidation(validation.map { it.name })
        database.withTransaction {
            database.driveOdometerEntryDao().upsert(value.toEntity(VehicleLedgerSyncState.LOCAL_PENDING))
            insertOperation(value, VehicleLedgerOperationKind.UPDATE, batchId, timestamp)
            insertProvenance(value.envelope, VehicleLedgerEntityType.ODOMETER, odometerEntryId, ODOMETER_FIELDS)
            enqueueMirror(ownerUid, value.envelope.vehicleId, batchId, timestamp)
        }
        schedule(ownerUid, value)
    }

    override suspend fun tombstoneOdometer(
        odometerEntryId: String
    ): VehicleLedgerMutationResult<Unit> = mutateOdometerDeletion(odometerEntryId, restore = false)

    override suspend fun restoreOdometer(
        odometerEntryId: String
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract> {
        val result = mutateOdometerDeletion(odometerEntryId, restore = true)
        return when (result) {
            is VehicleLedgerMutationResult.Success -> {
                val owner = currentUserId()?.takeIf(String::isNotBlank)
                    ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
                val value = database.driveOdometerEntryDao().get(owner, odometerEntryId)?.toContract()
                    ?: return rejected(VehicleLedgerFailure.RecordNotFound)
                VehicleLedgerMutationResult.Success(value)
            }
            is VehicleLedgerMutationResult.LocalSavedSyncSchedulingFailed -> {
                val owner = currentUserId()?.takeIf(String::isNotBlank)
                    ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
                val value = database.driveOdometerEntryDao().get(owner, odometerEntryId)?.toContract()
                    ?: return rejected(VehicleLedgerFailure.RecordNotFound)
                VehicleLedgerMutationResult.LocalSavedSyncSchedulingFailed(value)
            }
            is VehicleLedgerMutationResult.Rejected -> result
        }
    }

    override suspend fun acceptLegacyMirrorAsMeasurement(
        vehicleId: String,
        observedAt: Long,
        odometerSeriesId: String
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract> {
        val ownerUid = activeOwner() ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
        val vehicle = database.driveVehicleDao().getVehicle(ownerUid, vehicleId)
            ?: return rejected(VehicleLedgerFailure.VehicleNotFound)
        val meters = VehicleLedgerUnits.legacyKilometersToMeters(vehicle.currentOdometerKm)
            ?: return rejectedValidation(setOf("currentOdometerKm"))
        return createOdometer(
            OdometerDraft(
                vehicleId = vehicleId,
                observedAt = observedAt,
                odometerMeters = meters,
                quality = OdometerQuality.CONFIRMED,
                readingRole = OdometerReadingRole.MANUAL,
                odometerSeriesId = odometerSeriesId,
                sourceRecordType = LEGACY_SOURCE_TYPE,
                sourceRecordId = vehicleId
            )
        )
    }

    override suspend fun startOdometerSeries(
        draft: OdometerDraft,
        resetReason: String
    ): VehicleLedgerMutationResult<VehicleOdometerEntryContract> = createOdometer(
        draft.copy(
            odometerSeriesId = UUID.randomUUID().toString(),
            resetReason = resetReason.trim().takeIf(String::isNotEmpty)
        )
    )

    override suspend fun runLegacyOdometerBackfill(): VehicleLedgerMutationResult<Int> {
        if (!enabled) return rejected(VehicleLedgerFailure.FeatureDisabled)
        val ownerUid = activeOwner() ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
        return try {
            var inserted = 0
            val timestamp = now()
            database.driveVehicleDao().getActiveVehiclesSnapshot(ownerUid).forEach { vehicle ->
                val meters = VehicleLedgerUnits.legacyKilometersToMeters(vehicle.currentOdometerKm)
                    ?: return@forEach
                val existing = database.driveOdometerEntryDao().getBySource(
                    ownerUid, LEGACY_SOURCE_TYPE, vehicle.id, OdometerReadingRole.MIGRATED.name
                )
                if (existing != null) return@forEach
                val recordId = stableUuid("legacy-odometer:$ownerUid:${vehicle.id}")
                val operationId = stableUuid("legacy-odometer-operation:$ownerUid:${vehicle.id}")
                val batchId = stableUuid("legacy-odometer-batch:$ownerUid:${vehicle.id}")
                val value = VehicleOdometerEntryContract(
                    odometerEntryId = recordId,
                    observedAt = null,
                    odometerMeters = meters,
                    quality = OdometerQuality.UNKNOWN,
                    readingRole = OdometerReadingRole.MIGRATED,
                    odometerSeriesId = "legacy-${vehicle.id}",
                    sourceRecordType = LEGACY_SOURCE_TYPE,
                    sourceRecordId = vehicle.id,
                    correctionOfEntryId = null,
                    resetReason = null,
                    notes = null,
                    envelope = VehicleLedgerEnvelope(
                        ownerUid, vehicle.id, VehicleLedgerContractSpec.SCHEMA_VERSION, 1,
                        operationId, VehicleLedgerSource.MIGRATED, timestamp, timestamp, null, null
                    )
                )
                if (VehicleLedgerValidator.validate(recordId, ownerUid, value).isNotEmpty()) {
                    return@forEach
                }
                database.withTransaction {
                    if (database.driveOdometerEntryDao().getBySource(
                            ownerUid, LEGACY_SOURCE_TYPE, vehicle.id, OdometerReadingRole.MIGRATED.name
                        ) == null
                    ) {
                        database.driveOdometerEntryDao().upsert(
                            value.toEntity(VehicleLedgerSyncState.LOCAL_PENDING)
                        )
                        insertOperation(value, VehicleLedgerOperationKind.CREATE, batchId, timestamp)
                        insertProvenance(
                            value.envelope, VehicleLedgerEntityType.ODOMETER, recordId, ODOMETER_FIELDS
                        )
                        inserted++
                    }
                }
            }
            if (inserted > 0) schedule(ownerUid, inserted) else VehicleLedgerMutationResult.Success(0)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            rejected(VehicleLedgerFailure.FatalStorage)
        }
    }

    override fun observeExpenses(
        vehicleId: String,
        fromInclusive: Long,
        toExclusive: Long
    ): Flow<List<VehicleExpenseContract>> = scoped(emptyList()) { ownerUid ->
        database.driveExpenseDao().observeByVehicleAndDate(ownerUid, vehicleId, fromInclusive, toExclusive)
            .mapLedger { rows -> rows.map { it.toContract() } }
    }

    override fun observeExpenseSummary(
        vehicleId: String,
        fromInclusive: Long,
        toExclusive: Long
    ): Flow<List<ExpenseSummary>> = scoped(emptyList()) { ownerUid ->
        database.driveExpenseDao().observeSummary(ownerUid, vehicleId, fromInclusive, toExclusive)
            .mapLedger { rows ->
                rows.map {
                    ExpenseSummary(
                        VehicleExpenseCategory.fromWire(it.category), it.currencyCode,
                        it.currencyExponent, it.signedAmountMinor, it.recordCount
                    )
                }
            }
    }

    override suspend fun createExpense(
        draft: ExpenseDraft
    ): VehicleLedgerMutationResult<VehicleExpenseContract> = guardedMutation(draft.vehicleId) { ownerUid ->
        writeExpense(ownerUid, UUID.randomUUID().toString(), null, draft, VehicleLedgerOperationKind.CREATE)
    }

    override suspend fun updateExpense(
        expenseId: String,
        draft: ExpenseDraft
    ): VehicleLedgerMutationResult<VehicleExpenseContract> = guardedMutation(draft.vehicleId) { ownerUid ->
        val current = database.driveExpenseDao().get(ownerUid, expenseId)
            ?: return@guardedMutation rejected(VehicleLedgerFailure.RecordNotFound)
        if (current.deletedAt != null) return@guardedMutation rejected(VehicleLedgerFailure.RecordDeleted)
        if (current.vehicleId != draft.vehicleId) return@guardedMutation rejected(VehicleLedgerFailure.AccountMismatch)
        writeExpense(ownerUid, expenseId, current, draft, VehicleLedgerOperationKind.UPDATE)
    }

    override suspend fun tombstoneExpense(expenseId: String): VehicleLedgerMutationResult<Unit> =
        mutateExpenseDeletion(expenseId, restore = false)

    override suspend fun restoreExpense(
        expenseId: String
    ): VehicleLedgerMutationResult<VehicleExpenseContract> {
        val result = mutateExpenseDeletion(expenseId, restore = true)
        return restoreResult(result) { owner -> database.driveExpenseDao().get(owner, expenseId)?.toContract() }
    }

    override suspend fun duplicateExpenseCandidates(
        draft: ExpenseDraft,
        excludeExpenseId: String
    ): List<VehicleExpenseContract> {
        val ownerUid = activeOwner() ?: return emptyList()
        val fingerprint = draft.duplicateFingerprint ?: VehicleExpenseFingerprint.compute(draft)
        return database.driveExpenseDao().duplicateCandidates(
            ownerUid, draft.vehicleId, fingerprint, excludeExpenseId
        ).map { it.toContract() }
    }

    override fun observeReminders(vehicleId: String): Flow<List<VehicleReminderContract>> =
        scoped(emptyList()) { ownerUid ->
            database.driveReminderDao().observeForVehicle(ownerUid, vehicleId)
                .mapLedger { rows -> rows.map { it.toContract() } }
        }

    override fun observeDueReminders(vehicleId: String): Flow<List<DueReminderProjection>> =
        combine(observeReminders(vehicleId), observeCurrentOdometer(vehicleId)) { reminders, current ->
            reminders.mapNotNull {
                VehicleReminderDuePolicy.evaluate(it, todayEpochDay(), current.meters)
            }
        }

    override suspend fun createReminder(
        draft: ReminderDraft
    ): VehicleLedgerMutationResult<VehicleReminderContract> = guardedMutation(draft.vehicleId) { ownerUid ->
        writeReminder(ownerUid, UUID.randomUUID().toString(), null, draft, VehicleLedgerOperationKind.CREATE)
    }

    override suspend fun updateReminder(
        reminderId: String,
        draft: ReminderDraft
    ): VehicleLedgerMutationResult<VehicleReminderContract> = guardedMutation(draft.vehicleId) { ownerUid ->
        val current = database.driveReminderDao().get(ownerUid, reminderId)
            ?: return@guardedMutation rejected(VehicleLedgerFailure.RecordNotFound)
        if (current.deletedAt != null) return@guardedMutation rejected(VehicleLedgerFailure.RecordDeleted)
        if (current.vehicleId != draft.vehicleId) return@guardedMutation rejected(VehicleLedgerFailure.AccountMismatch)
        writeReminder(ownerUid, reminderId, current, draft, VehicleLedgerOperationKind.UPDATE)
    }

    override suspend fun completeReminder(
        reminderId: String,
        completedAt: Long,
        completedOdometerMeters: Long?
    ): VehicleLedgerMutationResult<VehicleReminderContract> = mutateReminder(reminderId) {
        it.copy(
            status = VehicleReminderStatus.COMPLETED,
            snoozedUntilEpochDay = null,
            lastCompletedAt = completedAt,
            lastCompletedOdometerMeters = completedOdometerMeters
        )
    }

    override suspend fun snoozeReminder(
        reminderId: String,
        untilEpochDay: Long
    ): VehicleLedgerMutationResult<VehicleReminderContract> = mutateReminder(reminderId) {
        it.copy(status = VehicleReminderStatus.SNOOZED, snoozedUntilEpochDay = untilEpochDay)
    }

    override suspend fun disableReminder(
        reminderId: String
    ): VehicleLedgerMutationResult<VehicleReminderContract> = mutateReminder(reminderId) {
        it.copy(status = VehicleReminderStatus.DISABLED, snoozedUntilEpochDay = null)
    }

    override suspend fun tombstoneReminder(reminderId: String): VehicleLedgerMutationResult<Unit> =
        mutateReminderDeletion(reminderId, restore = false)

    override suspend fun restoreReminder(
        reminderId: String
    ): VehicleLedgerMutationResult<VehicleReminderContract> {
        val result = mutateReminderDeletion(reminderId, restore = true)
        return restoreResult(result) { owner -> database.driveReminderDao().get(owner, reminderId)?.toContract() }
    }

    override fun observeDashboard(vehicleId: String): Flow<VehicleLedgerDashboard> {
        val range = currentMonthRange()
        val base = combine(
            observeCurrentOdometer(vehicleId),
            observeDueReminders(vehicleId),
            observeExpenseSummary(vehicleId, range.first, range.last + 1)
        ) { current, due, expenses -> Triple(current, due, expenses) }
        return scoped(VehicleLedgerDashboard(
            CurrentOdometerProjection(null, null, false, false), 0, emptyList(), 0, 0
        )) { ownerUid ->
            combine(
                base,
                database.driveLedgerOperationDao().observePendingCount(ownerUid),
                database.driveLedgerConflictDao().observeUnresolved(ownerUid)
            ) { values, pending, conflicts ->
                VehicleLedgerDashboard(
                    values.first, values.second.size, values.third, pending,
                    conflicts.count { it.vehicleId == vehicleId }
                )
            }
        }
    }

    override fun observeHealth(vehicleId: String): Flow<List<VehicleLedgerHealthIssue>> =
        scoped(emptyList()) { ownerUid ->
            val records = combine(
                database.driveVehicleDao().observeVehicleIncludingDeleted(ownerUid, vehicleId),
                database.driveOdometerEntryDao().observeHistory(ownerUid, vehicleId),
                database.driveExpenseDao().observeAll(ownerUid),
                database.driveReminderDao().observeForVehicle(ownerUid, vehicleId)
            ) { vehicle, odometers, expenses, reminders ->
                HealthRecords(vehicle, odometers.map { it.toContract() },
                    expenses.filter { it.vehicleId == vehicleId }.map { it.toContract() },
                    reminders.map { it.toContract() })
            }
            combine(
                records,
                database.driveLedgerOperationDao().observeAll(ownerUid),
                database.driveLedgerSyncReceiptDao().observeAll(ownerUid),
                database.driveLedgerConflictDao().observeUnresolved(ownerUid)
            ) { value, operations, receipts, conflicts ->
                VehicleLedgerHealthScanner.scan(
                    vehicleId, value.vehicle != null, value.vehicle?.deletedAt != null,
                    value.vehicle?.currentOdometerKm, value.odometers, value.expenses, value.reminders,
                    operations.filter { it.vehicleId == vehicleId },
                    receipts.filter { it.vehicleId == vehicleId },
                    conflicts.filter { it.vehicleId == vehicleId }, now(), todayEpochDay()
                )
            }
        }

    override fun observeReceipts(): Flow<List<DriveLedgerSyncReceiptEntity>> =
        scoped(emptyList()) { ownerUid -> database.driveLedgerSyncReceiptDao().observeRecent(ownerUid, 100) }

    override fun observeConflicts(): Flow<List<DriveLedgerConflictEntity>> =
        scoped(emptyList()) { ownerUid -> database.driveLedgerConflictDao().observeUnresolved(ownerUid) }

    override fun schedulePendingSync() {
        if (!enabled) return
        activeOwner()?.let(syncScheduler::schedule)
    }

    private suspend fun writeExpense(
        ownerUid: String,
        expenseId: String,
        current: com.example.toplutasima.data.local.entity.DriveExpenseEntity?,
        draft: ExpenseDraft,
        kind: VehicleLedgerOperationKind
    ): VehicleLedgerMutationResult<VehicleExpenseContract> {
        val timestamp = now()
        val revision = nextRevision(
            ownerUid, VehicleLedgerEntityType.EXPENSE, expenseId, current?.revision ?: 0
        ) ?: return rejected(VehicleLedgerFailure.RevisionOverflow)
        val normalized = draft.copy(
            currencyCode = draft.currencyCode.trim().uppercase(Locale.ROOT),
            duplicateFingerprint = draft.duplicateFingerprint ?: VehicleExpenseFingerprint.compute(draft)
        )
        val value = VehicleExpenseContract(
            expenseId, normalized.occurredAt, normalized.category, normalized.transactionKind,
            normalized.amountMinor, normalized.currencyCode, normalized.currencyExponent,
            normalized.vendorName?.trim()?.takeIf(String::isNotEmpty),
            normalized.notes?.trim()?.takeIf(String::isNotEmpty),
            normalized.referenceNumber?.trim()?.takeIf(String::isNotEmpty),
            normalized.periodStartEpochDay, normalized.periodEndEpochDay, normalized.dueEpochDay,
            normalized.odometerEntryId, normalized.odometerMetersSnapshot, normalized.splitGroupId,
            normalized.duplicateFingerprint, normalized.relatedExpenseId,
            VehicleLedgerEnvelope(
                ownerUid, normalized.vehicleId, VehicleLedgerContractSpec.SCHEMA_VERSION, revision,
                UUID.randomUUID().toString(), normalized.source,
                current?.createdAt ?: timestamp, timestamp, null, null
            )
        )
        val validation = VehicleLedgerValidator.validate(expenseId, ownerUid, value)
        if (validation.isNotEmpty()) return rejectedValidation(validation.map { it.name })
        val batchId = UUID.randomUUID().toString()
        database.withTransaction {
            database.driveExpenseDao().upsert(value.toEntity(VehicleLedgerSyncState.LOCAL_PENDING))
            insertOperation(value, kind, batchId, timestamp)
            insertProvenance(value.envelope, VehicleLedgerEntityType.EXPENSE, expenseId, EXPENSE_FIELDS)
        }
        return schedule(ownerUid, value)
    }

    private suspend fun writeReminder(
        ownerUid: String,
        reminderId: String,
        current: com.example.toplutasima.data.local.entity.DriveReminderEntity?,
        draft: ReminderDraft,
        kind: VehicleLedgerOperationKind
    ): VehicleLedgerMutationResult<VehicleReminderContract> {
        val timestamp = now()
        val revision = nextRevision(
            ownerUid, VehicleLedgerEntityType.REMINDER, reminderId, current?.revision ?: 0
        ) ?: return rejected(VehicleLedgerFailure.RevisionOverflow)
        val value = VehicleReminderContract(
            reminderId = reminderId,
            title = draft.title.trim(),
            reminderType = draft.reminderType,
            status = draft.status,
            dueEpochDay = draft.dueEpochDay,
            dueOdometerMeters = draft.dueOdometerMeters,
            recurrenceMonths = draft.recurrenceMonths,
            recurrenceDistanceMeters = draft.recurrenceDistanceMeters,
            recurrenceAnchor = draft.recurrenceAnchor,
            leadDays = draft.leadDays,
            leadDistanceMeters = draft.leadDistanceMeters,
            snoozedUntilEpochDay = draft.snoozedUntilEpochDay,
            linkedServiceRecordId = draft.linkedServiceRecordId,
            lastCompletedServiceRecordId = draft.lastCompletedServiceRecordId,
            lastCompletedAt = draft.lastCompletedAt,
            lastCompletedOdometerMeters = draft.lastCompletedOdometerMeters,
            notes = draft.notes?.trim()?.takeIf(String::isNotEmpty),
            envelope = VehicleLedgerEnvelope(
                ownerUid, draft.vehicleId, VehicleLedgerContractSpec.SCHEMA_VERSION, revision,
                UUID.randomUUID().toString(), draft.source,
                current?.createdAt ?: timestamp, timestamp, null, null
            )
        )
        val validation = VehicleLedgerValidator.validate(reminderId, ownerUid, value)
        if (validation.isNotEmpty()) return rejectedValidation(validation.map { it.name })
        val batchId = UUID.randomUUID().toString()
        database.withTransaction {
            database.driveReminderDao().upsert(value.toEntity(VehicleLedgerSyncState.LOCAL_PENDING))
            insertOperation(value, kind, batchId, timestamp)
            insertProvenance(value.envelope, VehicleLedgerEntityType.REMINDER, reminderId, REMINDER_FIELDS)
        }
        return schedule(ownerUid, value)
    }

    private suspend fun mutateReminder(
        reminderId: String,
        transform: (ReminderDraft) -> ReminderDraft
    ): VehicleLedgerMutationResult<VehicleReminderContract> {
        if (!enabled) return rejected(VehicleLedgerFailure.FeatureDisabled)
        val ownerUid = activeOwner() ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
        val current = database.driveReminderDao().get(ownerUid, reminderId)
            ?: return rejected(VehicleLedgerFailure.RecordNotFound)
        if (current.deletedAt != null) return rejected(VehicleLedgerFailure.RecordDeleted)
        return guardedMutation(current.vehicleId) {
            writeReminder(ownerUid, reminderId, current, transform(current.toReminderDraft()),
                VehicleLedgerOperationKind.UPDATE)
        }
    }

    private suspend fun mutateOdometerDeletion(
        recordId: String,
        restore: Boolean
    ): VehicleLedgerMutationResult<Unit> = mutateDeletion(
        VehicleLedgerEntityType.ODOMETER,
        recordId,
        get = { owner -> database.driveOdometerEntryDao().get(owner, recordId) },
        vehicleId = { it.vehicleId }, revision = { it.revision }, createdAt = { it.createdAt },
        deletedAt = { it.deletedAt },
        write = { owner, current, envelope, state ->
            database.driveOdometerEntryDao().upsert(
                current.toContract().copy(envelope = envelope).toEntity(state)
            )
        },
        restore = restore
    )

    private suspend fun mutateExpenseDeletion(
        recordId: String,
        restore: Boolean
    ): VehicleLedgerMutationResult<Unit> = mutateDeletion(
        VehicleLedgerEntityType.EXPENSE,
        recordId,
        get = { owner -> database.driveExpenseDao().get(owner, recordId) },
        vehicleId = { it.vehicleId }, revision = { it.revision }, createdAt = { it.createdAt },
        deletedAt = { it.deletedAt },
        write = { _, current, envelope, state ->
            database.driveExpenseDao().upsert(current.toContract().copy(envelope = envelope).toEntity(state))
        },
        restore = restore
    )

    private suspend fun mutateReminderDeletion(
        recordId: String,
        restore: Boolean
    ): VehicleLedgerMutationResult<Unit> = mutateDeletion(
        VehicleLedgerEntityType.REMINDER,
        recordId,
        get = { owner -> database.driveReminderDao().get(owner, recordId) },
        vehicleId = { it.vehicleId }, revision = { it.revision }, createdAt = { it.createdAt },
        deletedAt = { it.deletedAt },
        write = { _, current, envelope, state ->
            database.driveReminderDao().upsert(current.toContract().copy(envelope = envelope).toEntity(state))
        },
        restore = restore
    )

    private suspend fun <T> mutateDeletion(
        entityType: VehicleLedgerEntityType,
        recordId: String,
        get: suspend (String) -> T?,
        vehicleId: (T) -> String,
        revision: (T) -> Long,
        createdAt: (T) -> Long,
        deletedAt: (T) -> Long?,
        write: suspend (String, T, VehicleLedgerEnvelope, VehicleLedgerSyncState) -> Unit,
        restore: Boolean
    ): VehicleLedgerMutationResult<Unit> {
        if (!enabled) return rejected(VehicleLedgerFailure.FeatureDisabled)
        val ownerUid = activeOwner() ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
        val current = get(ownerUid) ?: return rejected(VehicleLedgerFailure.RecordNotFound)
        if (restore == (deletedAt(current) == null)) return VehicleLedgerMutationResult.Success(Unit)
        val vehicle = database.driveVehicleDao().getVehicle(ownerUid, vehicleId(current))
            ?: return rejected(VehicleLedgerFailure.VehicleNotFound)
        if (restore && vehicle.deletedAt != null) return rejected(VehicleLedgerFailure.VehicleDeleted)
        val next = nextRevision(ownerUid, entityType, recordId, revision(current))
            ?: return rejected(VehicleLedgerFailure.RevisionOverflow)
        val timestamp = now()
        val operationId = UUID.randomUUID().toString()
        val batchId = UUID.randomUUID().toString()
        val envelope = VehicleLedgerEnvelope(
            ownerUid, vehicleId(current), VehicleLedgerContractSpec.SCHEMA_VERSION, next,
            operationId, VehicleLedgerSource.MANUAL, createdAt(current), timestamp, null,
            if (restore) null else timestamp
        )
        try {
            database.withTransaction {
                write(ownerUid, current, envelope, VehicleLedgerSyncState.LOCAL_PENDING)
                insertOperation(envelope, entityType, recordId,
                    if (restore) VehicleLedgerOperationKind.RESTORE else VehicleLedgerOperationKind.TOMBSTONE,
                    batchId, timestamp)
                if (entityType == VehicleLedgerEntityType.ODOMETER) {
                    enqueueMirror(ownerUid, vehicleId(current), batchId, timestamp)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return rejected(VehicleLedgerFailure.FatalStorage)
        }
        return schedule(ownerUid, Unit)
    }

    private suspend fun <T> guardedMutation(
        vehicleId: String,
        block: suspend (String) -> VehicleLedgerMutationResult<T>
    ): VehicleLedgerMutationResult<T> {
        if (!enabled) return rejected(VehicleLedgerFailure.FeatureDisabled)
        val ownerUid = activeOwner() ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
        return try {
            val vehicle = database.driveVehicleDao().getVehicle(ownerUid, vehicleId)
                ?: return rejected(VehicleLedgerFailure.VehicleNotFound)
            if (vehicle.deletedAt != null) return rejected(VehicleLedgerFailure.VehicleDeleted)
            block(ownerUid)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            rejected(VehicleLedgerFailure.FatalStorage)
        }
    }

    private fun odometerFromDraft(
        ownerUid: String,
        recordId: String,
        draft: OdometerDraft,
        revision: Long,
        operationId: String,
        createdAt: Long,
        updatedAt: Long,
        deletedAt: Long?
    ) = VehicleOdometerEntryContract(
        recordId, draft.observedAt, draft.odometerMeters, draft.quality, draft.readingRole,
        draft.odometerSeriesId, draft.sourceRecordType, draft.sourceRecordId,
        draft.correctionOfEntryId, draft.resetReason?.trim()?.takeIf(String::isNotEmpty),
        draft.notes?.trim()?.takeIf(String::isNotEmpty),
        VehicleLedgerEnvelope(
            ownerUid, draft.vehicleId, VehicleLedgerContractSpec.SCHEMA_VERSION, revision,
            operationId, draft.source, createdAt, updatedAt, null, deletedAt
        )
    )

    private suspend fun nextRevision(
        ownerUid: String,
        entityType: VehicleLedgerEntityType,
        recordId: String,
        current: Long
    ): Long? {
        val highest = maxOf(current, database.driveLedgerOperationDao().highestTargetRevision(
            ownerUid, entityType.name, recordId
        ))
        return try {
            VehicleLedgerRevision.next(highest)
        } catch (_: ArithmeticException) {
            null
        }
    }

    private suspend fun insertOperation(
        value: VehicleOdometerEntryContract,
        kind: VehicleLedgerOperationKind,
        batchId: String,
        timestamp: Long
    ) = insertOperation(value.envelope, VehicleLedgerEntityType.ODOMETER,
        value.odometerEntryId, kind, batchId, timestamp)

    private suspend fun insertOperation(
        value: VehicleExpenseContract,
        kind: VehicleLedgerOperationKind,
        batchId: String,
        timestamp: Long
    ) = insertOperation(value.envelope, VehicleLedgerEntityType.EXPENSE,
        value.expenseId, kind, batchId, timestamp)

    private suspend fun insertOperation(
        value: VehicleReminderContract,
        kind: VehicleLedgerOperationKind,
        batchId: String,
        timestamp: Long
    ) = insertOperation(value.envelope, VehicleLedgerEntityType.REMINDER,
        value.reminderId, kind, batchId, timestamp)

    private suspend fun insertOperation(
        envelope: VehicleLedgerEnvelope,
        entityType: VehicleLedgerEntityType,
        recordId: String,
        kind: VehicleLedgerOperationKind,
        batchId: String,
        timestamp: Long
    ) {
        database.driveLedgerOperationDao().upsert(
            DriveLedgerOperationEntity(
                envelope.ownerUid, envelope.operationId, batchId, entityType.name, recordId,
                envelope.vehicleId, kind.name, envelope.revision,
                VehicleLedgerOperationState.PENDING.name, 0, timestamp,
                null, null, null, timestamp, timestamp
            )
        )
    }

    private suspend fun enqueueMirror(
        ownerUid: String,
        vehicleId: String,
        batchId: String,
        timestamp: Long
    ) {
        val entityType = VehicleLedgerEntityType.ODOMETER_MIRROR
        val highest = database.driveLedgerOperationDao().highestTargetRevision(
            ownerUid, entityType.name, vehicleId
        )
        val revision = try {
            VehicleLedgerRevision.next(maxOf(highest, timestamp - 1))
        } catch (_: ArithmeticException) {
            return
        }
        database.driveLedgerOperationDao().upsert(
            DriveLedgerOperationEntity(
                ownerUid, UUID.randomUUID().toString(), batchId, entityType.name, vehicleId,
                vehicleId, VehicleLedgerOperationKind.RECONCILE_MIRROR.name, revision,
                VehicleLedgerOperationState.PENDING.name, 0, timestamp,
                null, null, null, timestamp, timestamp
            )
        )
    }

    private suspend fun insertProvenance(
        envelope: VehicleLedgerEnvelope,
        entityType: VehicleLedgerEntityType,
        recordId: String,
        fields: Set<String>
    ) {
        database.driveFieldProvenanceDao().upsertAll(fields.map {
            DriveFieldProvenanceEntity(
                envelope.ownerUid, entityType.name, recordId, it, envelope.source.name,
                envelope.clientUpdatedAt
            )
        })
    }

    private fun activeOwner(): String? = currentUserId()?.takeIf(String::isNotBlank)

    private fun <T> scoped(empty: T, source: (String) -> Flow<T>): Flow<T> {
        if (!enabled) return flowOf(empty)
        return authenticatedUidChanges.distinctUntilChanged().flatMapLatest { ownerUid ->
            ownerUid?.takeIf(String::isNotBlank)?.let { activeOwner ->
                flow {
                    // Clear the previous account synchronously before the new Room query emits.
                    emit(empty)
                    emitAll(source(activeOwner))
                }
            } ?: flowOf(empty)
        }
    }

    private fun <T, R> Flow<T>.mapLedger(transform: suspend (T) -> R): Flow<R> =
        map(transform)

    private fun <T> schedule(ownerUid: String, value: T): VehicleLedgerMutationResult<T> = try {
        if (activeOwner() != ownerUid) {
            VehicleLedgerMutationResult.Rejected(VehicleLedgerFailure.AuthenticationChanged)
        } else {
            syncScheduler.schedule(ownerUid)
            VehicleLedgerMutationResult.Success(value)
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        VehicleLedgerMutationResult.LocalSavedSyncSchedulingFailed(value)
    }

    private suspend fun <T> restoreResult(
        result: VehicleLedgerMutationResult<Unit>,
        lookup: suspend (String) -> T?
    ): VehicleLedgerMutationResult<T> = when (result) {
        is VehicleLedgerMutationResult.Success -> {
            val owner = activeOwner() ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
            lookup(owner)?.let { VehicleLedgerMutationResult.Success(it) }
                ?: rejected(VehicleLedgerFailure.RecordNotFound)
        }
        is VehicleLedgerMutationResult.LocalSavedSyncSchedulingFailed -> {
            val owner = activeOwner() ?: return rejected(VehicleLedgerFailure.AuthenticationChanged)
            lookup(owner)?.let { VehicleLedgerMutationResult.LocalSavedSyncSchedulingFailed(it) }
                ?: rejected(VehicleLedgerFailure.RecordNotFound)
        }
        is VehicleLedgerMutationResult.Rejected -> result
    }

    private fun com.example.toplutasima.data.local.entity.DriveReminderEntity.toReminderDraft() =
        ReminderDraft(
            vehicleId, title, shared.vehicleledger.contract.VehicleReminderType.fromWire(reminderType),
            VehicleReminderStatus.fromWire(status), dueEpochDay, dueOdometerMeters,
            recurrenceMonths, recurrenceDistanceMeters,
            shared.vehicleledger.contract.VehicleReminderRecurrenceAnchor.fromWire(recurrenceAnchor),
            leadDays, leadDistanceMeters, snoozedUntilEpochDay, linkedServiceRecordId,
            lastCompletedServiceRecordId, lastCompletedAt, lastCompletedOdometerMeters, notes,
            VehicleLedgerSource.fromWire(source)
        )

    private fun stableUuid(value: String): String = UUID.nameUUIDFromBytes(
        value.toByteArray(StandardCharsets.UTF_8)
    ).toString()

    private data class HealthRecords(
        val vehicle: com.example.toplutasima.data.local.entity.DriveVehicleEntity?,
        val odometers: List<VehicleOdometerEntryContract>,
        val expenses: List<VehicleExpenseContract>,
        val reminders: List<VehicleReminderContract>
    )

    private companion object {
        const val LEGACY_SOURCE_TYPE = "LEGACY_VEHICLE_CURRENT"
        val ODOMETER_FIELDS = setOf(
            "observedAt", "odometerMeters", "quality", "readingRole", "odometerSeriesId",
            "sourceRecordType", "sourceRecordId", "correctionOfEntryId", "resetReason", "notes"
        )
        val EXPENSE_FIELDS = setOf(
            "occurredAt", "category", "transactionKind", "amountMinor", "currencyCode",
            "currencyExponent", "vendorName", "notes", "referenceNumber", "periodStartEpochDay",
            "periodEndEpochDay", "dueEpochDay", "odometerEntryId", "odometerMetersSnapshot",
            "splitGroupId", "duplicateFingerprint", "relatedExpenseId"
        )
        val REMINDER_FIELDS = setOf(
            "title", "reminderType", "status", "dueEpochDay", "dueOdometerMeters",
            "recurrenceMonths", "recurrenceDistanceMeters", "recurrenceAnchor", "leadDays",
            "leadDistanceMeters", "snoozedUntilEpochDay", "linkedServiceRecordId",
            "lastCompletedServiceRecordId", "lastCompletedAt", "lastCompletedOdometerMeters", "notes"
        )
    }
}

private fun <T> rejected(failure: VehicleLedgerFailure): VehicleLedgerMutationResult<T> =
    VehicleLedgerMutationResult.Rejected(failure)

private fun <T> rejectedValidation(fields: Collection<String>): VehicleLedgerMutationResult<T> =
    rejected(VehicleLedgerFailure.Validation(fields.toSet()))
