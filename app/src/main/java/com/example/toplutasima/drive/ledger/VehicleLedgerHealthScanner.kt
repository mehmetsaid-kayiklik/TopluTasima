package com.example.toplutasima.drive.ledger

import com.example.toplutasima.data.local.entity.DriveLedgerConflictEntity
import com.example.toplutasima.data.local.entity.DriveLedgerOperationEntity
import com.example.toplutasima.data.local.entity.DriveLedgerSyncReceiptEntity
import shared.vehicleledger.contract.OdometerQuality
import shared.vehicleledger.contract.VehicleLedgerSource
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderStatus

object VehicleLedgerHealthScanner {
    private const val IMPLAUSIBLE_JUMP_METERS = 1_000_000L
    private const val STUCK_OPERATION_MS = 24L * 60L * 60L * 1_000L

    fun scan(
        vehicleId: String,
        vehicleExists: Boolean,
        vehicleDeleted: Boolean,
        mirrorKilometers: Double?,
        odometers: List<VehicleOdometerEntryContract>,
        expenses: List<shared.vehicleledger.contract.VehicleExpenseContract>,
        reminders: List<shared.vehicleledger.contract.VehicleReminderContract>,
        operations: List<DriveLedgerOperationEntity>,
        receipts: List<DriveLedgerSyncReceiptEntity>,
        conflicts: List<DriveLedgerConflictEntity>,
        now: Long,
        todayEpochDay: Long
    ): List<VehicleLedgerHealthIssue> = buildList {
        if (!vehicleExists) {
            add(issue("LEDGER", vehicleId, vehicleId, VehicleLedgerHealthCode.LEDGER_VEHICLE_NOT_FOUND))
        } else if (vehicleDeleted) {
            add(issue("LEDGER", vehicleId, vehicleId, VehicleLedgerHealthCode.LEDGER_VEHICLE_DELETED))
        }

        val active = odometers.filter { it.envelope.deletedAt == null && it.observedAt != null }
        active.groupBy { it.odometerSeriesId }.values.forEach { series ->
            val sorted = series.sortedWith(compareBy<VehicleOdometerEntryContract> { it.observedAt }
                .thenBy { it.odometerEntryId })
            sorted.zipWithNext().forEach { (first, second) ->
                if (second.odometerMeters < first.odometerMeters) {
                    add(issue("ODOMETER", second.odometerEntryId, vehicleId,
                        VehicleLedgerHealthCode.ODOMETER_NON_MONOTONIC))
                } else if (second.odometerMeters - first.odometerMeters > IMPLAUSIBLE_JUMP_METERS) {
                    add(issue("ODOMETER", second.odometerEntryId, vehicleId,
                        VehicleLedgerHealthCode.ODOMETER_IMPLAUSIBLE_JUMP))
                }
            }
        }
        val latestTimestamp = active.maxOfOrNull { requireNotNull(it.observedAt) }
        if (latestTimestamp != null && active.filter { it.observedAt == latestTimestamp }
                .map { it.odometerSeriesId }.distinct().size > 1
        ) {
            add(issue("ODOMETER", vehicleId, vehicleId,
                VehicleLedgerHealthCode.ODOMETER_SERIES_INVALID))
        }
        odometers.filter {
            it.sourceRecordType != null && it.sourceRecordId != null
        }.groupBy {
            listOf(it.sourceRecordType, it.sourceRecordId, it.readingRole.name).joinToString("|")
        }.values.filter { it.size > 1 }.flatten().forEach {
            add(issue("ODOMETER", it.odometerEntryId, vehicleId,
                VehicleLedgerHealthCode.ODOMETER_SOURCE_DUPLICATE))
        }

        val current = CurrentOdometerSelector.select(active)
        val mirrorMeters = VehicleLedgerUnits.legacyKilometersToMeters(mirrorKilometers)
        if (current?.quality == OdometerQuality.CONFIRMED && mirrorMeters != current.odometerMeters) {
            add(issue("VEHICLE", vehicleId, vehicleId, VehicleLedgerHealthCode.ODOMETER_MIRROR_STALE))
        }
        if (mirrorKilometers != null && mirrorMeters == null) {
            add(issue("VEHICLE", vehicleId, vehicleId,
                VehicleLedgerHealthCode.ODOMETER_LEGACY_VALUE_INVALID))
        }
        odometers.filter { it.envelope.source == VehicleLedgerSource.UNKNOWN }.forEach {
            add(issue("ODOMETER", it.odometerEntryId, vehicleId,
                VehicleLedgerHealthCode.LEDGER_UNKNOWN_PROVENANCE))
        }
        expenses.filter { it.envelope.source == VehicleLedgerSource.UNKNOWN }.forEach {
            add(issue("EXPENSE", it.expenseId, vehicleId,
                VehicleLedgerHealthCode.LEDGER_UNKNOWN_PROVENANCE))
        }
        reminders.filter { it.envelope.source == VehicleLedgerSource.UNKNOWN }.forEach {
            add(issue("REMINDER", it.reminderId, vehicleId,
                VehicleLedgerHealthCode.LEDGER_UNKNOWN_PROVENANCE))
        }

        expenses.filter { it.envelope.deletedAt == null }
            .filter { !it.currencyCode.matches(Regex("^[A-Z]{3}$")) || it.currencyExponent !in 0..4 }
            .forEach {
                add(issue("EXPENSE", it.expenseId, vehicleId,
                    VehicleLedgerHealthCode.EXPENSE_INVALID_CURRENCY))
            }
        expenses.filter { it.envelope.deletedAt == null && !it.duplicateFingerprint.isNullOrBlank() }
            .groupBy { it.duplicateFingerprint }
            .values.filter { it.size > 1 }.flatten().forEach {
                add(issue("EXPENSE", it.expenseId, vehicleId,
                    VehicleLedgerHealthCode.EXPENSE_DUPLICATE_SUSPECTED))
            }
        expenses.filter { it.envelope.deletedAt == null }
            .groupBy { it.currencyCode to it.currencyExponent }
            .values.forEach { group ->
                try {
                    group.fold(0L) { total, expense -> Math.addExact(total, expense.amountMinor) }
                } catch (_: ArithmeticException) {
                    group.forEach {
                        add(issue("EXPENSE", it.expenseId, vehicleId,
                            VehicleLedgerHealthCode.EXPENSE_AMOUNT_OVERFLOW))
                    }
                }
            }

        reminders.filter { it.envelope.deletedAt == null }.forEach {
            if (it.dueEpochDay == null && it.dueOdometerMeters == null) {
                add(issue("REMINDER", it.reminderId, vehicleId,
                    VehicleLedgerHealthCode.REMINDER_NO_TRIGGER))
            }
            if (it.recurrenceMonths?.let { months -> months <= 0 } == true ||
                it.recurrenceDistanceMeters?.let { distance -> distance <= 0 } == true
            ) {
                add(issue("REMINDER", it.reminderId, vehicleId,
                    VehicleLedgerHealthCode.REMINDER_RECURRENCE_INVALID))
            }
            if (it.status == VehicleReminderStatus.ACTIVE &&
                it.dueEpochDay?.let { due -> due < todayEpochDay } == true
            ) {
                add(issue("REMINDER", it.reminderId, vehicleId,
                    VehicleLedgerHealthCode.REMINDER_OVERDUE))
            }
            if (it.dueOdometerMeters != null && current?.quality != OdometerQuality.CONFIRMED) {
                add(issue("REMINDER", it.reminderId, vehicleId,
                    VehicleLedgerHealthCode.REMINDER_ODOMETER_STALE))
            }
        }

        operations.filter {
            it.state in setOf("PENDING", "RUNNING", "RETRY") && now - it.createdAt > STUCK_OPERATION_MS
        }.forEach {
            add(issue(it.entityType, it.recordId, it.vehicleId,
                VehicleLedgerHealthCode.LEDGER_OPERATION_STUCK))
        }
        val receiptOperationIds = receipts.map { it.operationId }.toSet()
        operations.filter {
            it.state in setOf("SUCCEEDED", "FATAL", "CONFLICT", "SUPERSEDED") &&
                it.operationId !in receiptOperationIds
        }.forEach {
            add(issue(it.entityType, it.recordId, it.vehicleId,
                VehicleLedgerHealthCode.LEDGER_RECEIPT_MISSING))
        }
        receipts.filter {
            it.safeErrorCode == VehicleLedgerHealthCode.LEDGER_UNSUPPORTED_SCHEMA.name
        }.forEach {
            add(issue(it.entityType, it.recordId, it.vehicleId,
                VehicleLedgerHealthCode.LEDGER_UNSUPPORTED_SCHEMA))
        }
        conflicts.filter { it.resolvedAt == null }.forEach {
            add(issue(it.entityType, it.recordId, it.vehicleId,
                VehicleLedgerHealthCode.LEDGER_CONFLICT_UNRESOLVED))
        }
    }.distinctBy { Triple(it.entityType, it.recordId, it.code) }

    private fun issue(
        entityType: String,
        recordId: String,
        vehicleId: String,
        code: VehicleLedgerHealthCode
    ) = VehicleLedgerHealthIssue(entityType, recordId, vehicleId, code)
}
