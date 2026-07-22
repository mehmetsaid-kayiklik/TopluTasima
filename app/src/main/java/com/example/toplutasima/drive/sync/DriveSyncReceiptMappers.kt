package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.entity.DriveSyncReceiptEntity
import com.example.toplutasima.drive.model.DriveSyncReceipt
import com.example.toplutasima.drive.model.DriveSyncReceiptKind
import com.example.toplutasima.drive.model.DriveSyncReceiptStatus
import java.time.Instant

fun DriveSyncReceiptEntity.toDomain(): DriveSyncReceipt = DriveSyncReceipt(
    receiptId = receiptId,
    kind = DriveSyncReceiptKind.fromStorage(kind),
    entityType = entityType,
    recordId = recordId,
    operationType = operationType,
    status = DriveSyncReceiptStatus.fromStorage(status),
    startedAt = Instant.ofEpochMilli(startedAt),
    finishedAt = finishedAt?.let(Instant::ofEpochMilli),
    attemptCount = attemptCount,
    errorCode = errorCode
)
