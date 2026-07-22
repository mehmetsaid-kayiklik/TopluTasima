package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.dao.DriveSyncReceiptDao
import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import com.example.toplutasima.data.local.entity.DriveSyncReceiptEntity
import com.example.toplutasima.drive.model.DriveSyncReceiptKind
import com.example.toplutasima.drive.model.DriveSyncReceiptStatus

internal interface DriveSyncReceiptStore {
    suspend fun startPull(userId: String, receiptId: String, mode: DrivePullMode, startedAt: Long)
    suspend fun startOutbound(operation: DriveSyncOperationEntity, startedAt: Long)
    suspend fun succeed(userId: String, receiptId: String, finishedAt: Long)
    suspend fun retry(userId: String, receiptId: String, finishedAt: Long, errorCode: String)
    suspend fun fatal(userId: String, receiptId: String, finishedAt: Long, errorCode: String)
    suspend fun superseded(userId: String, receiptId: String, finishedAt: Long)

    companion object {
        val NO_OP = object : DriveSyncReceiptStore {
            override suspend fun startPull(
                userId: String,
                receiptId: String,
                mode: DrivePullMode,
                startedAt: Long
            ) = Unit

            override suspend fun startOutbound(
                operation: DriveSyncOperationEntity,
                startedAt: Long
            ) = Unit

            override suspend fun succeed(userId: String, receiptId: String, finishedAt: Long) = Unit
            override suspend fun retry(
                userId: String,
                receiptId: String,
                finishedAt: Long,
                errorCode: String
            ) = Unit

            override suspend fun fatal(
                userId: String,
                receiptId: String,
                finishedAt: Long,
                errorCode: String
            ) = Unit

            override suspend fun superseded(
                userId: String,
                receiptId: String,
                finishedAt: Long
            ) = Unit
        }
    }
}

internal class RoomDriveSyncReceiptStore(
    private val receiptDao: DriveSyncReceiptDao
) : DriveSyncReceiptStore {
    override suspend fun startPull(
        userId: String,
        receiptId: String,
        mode: DrivePullMode,
        startedAt: Long
    ) {
        receiptDao.upsert(
            DriveSyncReceiptEntity(
                userId = userId,
                receiptId = receiptId,
                kind = when (mode) {
                    DrivePullMode.INITIAL -> DriveSyncReceiptKind.INITIAL_PULL.name
                    DrivePullMode.INCREMENTAL -> DriveSyncReceiptKind.INCREMENTAL_PULL.name
                },
                status = DriveSyncReceiptStatus.STARTED.name,
                startedAt = startedAt,
                attemptCount = 1
            )
        )
    }

    override suspend fun startOutbound(operation: DriveSyncOperationEntity, startedAt: Long) {
        val existing = receiptDao.get(operation.userId, operation.operationId)
        receiptDao.upsert(
            DriveSyncReceiptEntity(
                userId = operation.userId,
                receiptId = operation.operationId,
                kind = DriveSyncReceiptKind.OUTBOUND.name,
                entityType = operation.entityType,
                recordId = operation.recordId,
                operationType = operation.operationType,
                status = DriveSyncReceiptStatus.STARTED.name,
                startedAt = existing?.startedAt ?: startedAt,
                attemptCount = (existing?.attemptCount ?: 0) + 1
            )
        )
    }

    override suspend fun succeed(userId: String, receiptId: String, finishedAt: Long) =
        finish(userId, receiptId, finishedAt, DriveSyncReceiptStatus.SUCCEEDED, null)

    override suspend fun retry(
        userId: String,
        receiptId: String,
        finishedAt: Long,
        errorCode: String
    ) = finish(userId, receiptId, finishedAt, DriveSyncReceiptStatus.RETRY, errorCode)

    override suspend fun fatal(
        userId: String,
        receiptId: String,
        finishedAt: Long,
        errorCode: String
    ) = finish(userId, receiptId, finishedAt, DriveSyncReceiptStatus.FATAL, errorCode)

    override suspend fun superseded(userId: String, receiptId: String, finishedAt: Long) =
        finish(userId, receiptId, finishedAt, DriveSyncReceiptStatus.SUPERSEDED, null)

    private suspend fun finish(
        userId: String,
        receiptId: String,
        finishedAt: Long,
        status: DriveSyncReceiptStatus,
        errorCode: String?
    ) {
        val existing = receiptDao.get(userId, receiptId) ?: return
        receiptDao.upsert(
            existing.copy(
                status = status.name,
                finishedAt = finishedAt,
                errorCode = errorCode
            )
        )
        receiptDao.prune(userId, MAX_RECEIPTS_PER_USER)
    }

    private companion object {
        const val MAX_RECEIPTS_PER_USER = 100
    }
}
