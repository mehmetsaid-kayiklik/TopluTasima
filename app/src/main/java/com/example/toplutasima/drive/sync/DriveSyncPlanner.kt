package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity

object DriveSyncPlanner {
    fun plan(
        existing: DriveSyncOperationEntity?,
        requestedType: DriveSyncOperationType,
        userId: String,
        recordId: String,
        now: Long,
        operationId: String
    ): DriveSyncOperationEntity {
        require(userId.isNotBlank()) { "Drive sync owner must not be blank" }
        require(recordId.isNotBlank()) { "Drive sync record ID must not be blank" }
        require(operationId.isNotBlank()) { "Drive sync operation ID must not be blank" }

        if (existing != null) {
            require(existing.userId == userId) { "Drive sync owner mismatch" }
            require(existing.recordId == recordId) { "Drive sync record mismatch" }
            require(existing.entityType == requestedType.entityType.name) {
                "Drive sync entity type mismatch"
            }
        }

        val existingType = existing?.let {
            requireNotNull(DriveSyncOperationType.fromStorage(it.operationType)) {
                "Unknown drive sync operation type"
            }
        }
        if (existingType?.isDelete == true) {
            return existing
        }

        val effectiveType = when {
            requestedType.isDelete -> requestedType
            existingType == DriveSyncOperationType.CREATE_VEHICLE &&
                requestedType == DriveSyncOperationType.UPDATE_VEHICLE -> {
                DriveSyncOperationType.CREATE_VEHICLE
            }
            existingType == DriveSyncOperationType.CREATE_DRIVE_TRIP &&
                requestedType == DriveSyncOperationType.UPDATE_DRIVE_TRIP -> {
                DriveSyncOperationType.CREATE_DRIVE_TRIP
            }
            else -> requestedType
        }

        return DriveSyncOperationEntity(
            operationId = operationId,
            userId = userId,
            entityType = effectiveType.entityType.name,
            recordId = recordId,
            operationType = effectiveType.name,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
            attemptCount = 0,
            lastErrorCode = null,
            retryEligible = true,
            nextAttemptAt = null
        )
    }
}
