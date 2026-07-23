package com.example.toplutasima.drive.ledger

import com.example.toplutasima.data.local.entity.DriveExpenseEntity
import com.example.toplutasima.data.local.entity.DriveOdometerEntryEntity
import com.example.toplutasima.data.local.entity.DriveReminderEntity
import shared.vehicleledger.contract.LedgerServerTimestamp
import shared.vehicleledger.contract.OdometerQuality
import shared.vehicleledger.contract.OdometerReadingRole
import shared.vehicleledger.contract.VehicleExpenseCategory
import shared.vehicleledger.contract.VehicleExpenseContract
import shared.vehicleledger.contract.VehicleExpenseTransactionKind
import shared.vehicleledger.contract.VehicleLedgerEnvelope
import shared.vehicleledger.contract.VehicleLedgerSource
import shared.vehicleledger.contract.VehicleOdometerEntryContract
import shared.vehicleledger.contract.VehicleReminderContract
import shared.vehicleledger.contract.VehicleReminderRecurrenceAnchor
import shared.vehicleledger.contract.VehicleReminderStatus
import shared.vehicleledger.contract.VehicleReminderType

internal fun DriveOdometerEntryEntity.toContract() = VehicleOdometerEntryContract(
    odometerEntryId = odometerEntryId,
    observedAt = observedAt,
    odometerMeters = odometerMeters,
    quality = OdometerQuality.fromWire(quality),
    readingRole = OdometerReadingRole.fromWire(readingRole),
    odometerSeriesId = odometerSeriesId,
    sourceRecordType = sourceRecordType,
    sourceRecordId = sourceRecordId,
    correctionOfEntryId = correctionOfEntryId,
    resetReason = resetReason,
    notes = notes,
    envelope = envelope()
)

internal fun VehicleOdometerEntryContract.toEntity(
    syncState: VehicleLedgerSyncState,
    healthCode: VehicleLedgerHealthCode? = null
) = DriveOdometerEntryEntity(
    ownerUid = envelope.ownerUid,
    odometerEntryId = odometerEntryId,
    vehicleId = envelope.vehicleId,
    observedAt = observedAt,
    odometerMeters = odometerMeters,
    quality = quality.name,
    readingRole = readingRole.name,
    odometerSeriesId = odometerSeriesId,
    sourceRecordType = sourceRecordType,
    sourceRecordId = sourceRecordId,
    correctionOfEntryId = correctionOfEntryId,
    resetReason = resetReason,
    notes = notes,
    schemaVersion = envelope.schemaVersion,
    revision = envelope.revision,
    operationId = envelope.operationId,
    source = envelope.source.name,
    createdAt = envelope.createdAt,
    clientUpdatedAt = envelope.clientUpdatedAt,
    serverUpdatedAtSeconds = envelope.serverUpdatedAt?.seconds,
    serverUpdatedAtNanos = envelope.serverUpdatedAt?.nanoseconds,
    deletedAt = envelope.deletedAt,
    syncState = syncState.name,
    healthCode = healthCode?.name
)

internal fun DriveExpenseEntity.toContract() = VehicleExpenseContract(
    expenseId = expenseId,
    occurredAt = occurredAt,
    category = VehicleExpenseCategory.fromWire(category),
    transactionKind = VehicleExpenseTransactionKind.fromWire(transactionKind),
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    currencyExponent = currencyExponent,
    vendorName = vendorName,
    notes = notes,
    referenceNumber = referenceNumber,
    periodStartEpochDay = periodStartEpochDay,
    periodEndEpochDay = periodEndEpochDay,
    dueEpochDay = dueEpochDay,
    odometerEntryId = odometerEntryId,
    odometerMetersSnapshot = odometerMetersSnapshot,
    splitGroupId = splitGroupId,
    duplicateFingerprint = duplicateFingerprint,
    relatedExpenseId = relatedExpenseId,
    envelope = envelope()
)

internal fun VehicleExpenseContract.toEntity(
    syncState: VehicleLedgerSyncState,
    healthCode: VehicleLedgerHealthCode? = null
) = DriveExpenseEntity(
    ownerUid = envelope.ownerUid,
    expenseId = expenseId,
    vehicleId = envelope.vehicleId,
    occurredAt = occurredAt,
    category = category.name,
    transactionKind = transactionKind.name,
    amountMinor = amountMinor,
    currencyCode = currencyCode,
    currencyExponent = currencyExponent,
    vendorName = vendorName,
    notes = notes,
    referenceNumber = referenceNumber,
    periodStartEpochDay = periodStartEpochDay,
    periodEndEpochDay = periodEndEpochDay,
    dueEpochDay = dueEpochDay,
    odometerEntryId = odometerEntryId,
    odometerMetersSnapshot = odometerMetersSnapshot,
    splitGroupId = splitGroupId,
    duplicateFingerprint = duplicateFingerprint,
    relatedExpenseId = relatedExpenseId,
    schemaVersion = envelope.schemaVersion,
    revision = envelope.revision,
    operationId = envelope.operationId,
    source = envelope.source.name,
    createdAt = envelope.createdAt,
    clientUpdatedAt = envelope.clientUpdatedAt,
    serverUpdatedAtSeconds = envelope.serverUpdatedAt?.seconds,
    serverUpdatedAtNanos = envelope.serverUpdatedAt?.nanoseconds,
    deletedAt = envelope.deletedAt,
    syncState = syncState.name,
    healthCode = healthCode?.name
)

internal fun DriveReminderEntity.toContract() = VehicleReminderContract(
    reminderId = reminderId,
    title = title,
    reminderType = VehicleReminderType.fromWire(reminderType),
    status = VehicleReminderStatus.fromWire(status),
    dueEpochDay = dueEpochDay,
    dueOdometerMeters = dueOdometerMeters,
    recurrenceMonths = recurrenceMonths,
    recurrenceDistanceMeters = recurrenceDistanceMeters,
    recurrenceAnchor = VehicleReminderRecurrenceAnchor.fromWire(recurrenceAnchor),
    leadDays = leadDays,
    leadDistanceMeters = leadDistanceMeters,
    snoozedUntilEpochDay = snoozedUntilEpochDay,
    linkedServiceRecordId = linkedServiceRecordId,
    lastCompletedServiceRecordId = lastCompletedServiceRecordId,
    lastCompletedAt = lastCompletedAt,
    lastCompletedOdometerMeters = lastCompletedOdometerMeters,
    notes = notes,
    envelope = envelope()
)

internal fun VehicleReminderContract.toEntity(
    syncState: VehicleLedgerSyncState,
    healthCode: VehicleLedgerHealthCode? = null
) = DriveReminderEntity(
    ownerUid = envelope.ownerUid,
    reminderId = reminderId,
    vehicleId = envelope.vehicleId,
    title = title,
    reminderType = reminderType.name,
    status = status.name,
    dueEpochDay = dueEpochDay,
    dueOdometerMeters = dueOdometerMeters,
    recurrenceMonths = recurrenceMonths,
    recurrenceDistanceMeters = recurrenceDistanceMeters,
    recurrenceAnchor = recurrenceAnchor.name,
    leadDays = leadDays,
    leadDistanceMeters = leadDistanceMeters,
    snoozedUntilEpochDay = snoozedUntilEpochDay,
    linkedServiceRecordId = linkedServiceRecordId,
    lastCompletedServiceRecordId = lastCompletedServiceRecordId,
    lastCompletedAt = lastCompletedAt,
    lastCompletedOdometerMeters = lastCompletedOdometerMeters,
    notes = notes,
    schemaVersion = envelope.schemaVersion,
    revision = envelope.revision,
    operationId = envelope.operationId,
    source = envelope.source.name,
    createdAt = envelope.createdAt,
    clientUpdatedAt = envelope.clientUpdatedAt,
    serverUpdatedAtSeconds = envelope.serverUpdatedAt?.seconds,
    serverUpdatedAtNanos = envelope.serverUpdatedAt?.nanoseconds,
    deletedAt = envelope.deletedAt,
    syncState = syncState.name,
    healthCode = healthCode?.name
)

private fun DriveOdometerEntryEntity.envelope() = VehicleLedgerEnvelope(
    ownerUid, vehicleId, schemaVersion, revision, operationId,
    VehicleLedgerSource.fromWire(source), createdAt, clientUpdatedAt,
    timestamp(serverUpdatedAtSeconds, serverUpdatedAtNanos), deletedAt
)

private fun DriveExpenseEntity.envelope() = VehicleLedgerEnvelope(
    ownerUid, vehicleId, schemaVersion, revision, operationId,
    VehicleLedgerSource.fromWire(source), createdAt, clientUpdatedAt,
    timestamp(serverUpdatedAtSeconds, serverUpdatedAtNanos), deletedAt
)

private fun DriveReminderEntity.envelope() = VehicleLedgerEnvelope(
    ownerUid, vehicleId, schemaVersion, revision, operationId,
    VehicleLedgerSource.fromWire(source), createdAt, clientUpdatedAt,
    timestamp(serverUpdatedAtSeconds, serverUpdatedAtNanos), deletedAt
)

private fun timestamp(seconds: Long?, nanos: Int?): LedgerServerTimestamp? =
    if (seconds != null && nanos != null) LedgerServerTimestamp(seconds, nanos) else null
