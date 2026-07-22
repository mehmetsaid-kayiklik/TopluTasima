package com.example.toplutasima.drive.sync

import com.example.toplutasima.data.local.entity.DriveSyncOperationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DriveSyncPlannerTest {
    @Test
    fun `new vehicle create uses uid scoped stable record`() {
        val planned = DriveSyncPlanner.plan(
            existing = null,
            requestedType = DriveSyncOperationType.CREATE_VEHICLE,
            userId = USER_ID,
            recordId = RECORD_ID,
            now = 100L,
            operationId = "operation-1"
        )

        assertEquals(DriveSyncEntityType.VEHICLE.name, planned.entityType)
        assertEquals(DriveSyncOperationType.CREATE_VEHICLE.name, planned.operationType)
        assertEquals(USER_ID, planned.userId)
        assertEquals(RECORD_ID, planned.recordId)
    }

    @Test
    fun `create followed by update stays a single create with a fresh token`() {
        val existing = operation(DriveSyncOperationType.CREATE_VEHICLE, "old-token")

        val planned = DriveSyncPlanner.plan(
            existing = existing,
            requestedType = DriveSyncOperationType.UPDATE_VEHICLE,
            userId = USER_ID,
            recordId = RECORD_ID,
            now = 200L,
            operationId = "new-token"
        )

        assertEquals(DriveSyncOperationType.CREATE_VEHICLE.name, planned.operationType)
        assertEquals("new-token", planned.operationId)
        assertEquals(existing.createdAt, planned.createdAt)
        assertEquals(0, planned.attemptCount)
        assertEquals(null, planned.lastErrorCode)
    }

    @Test
    fun `update followed by delete becomes delete`() {
        val planned = DriveSyncPlanner.plan(
            existing = operation(DriveSyncOperationType.UPDATE_DRIVE_TRIP, "old-token"),
            requestedType = DriveSyncOperationType.DELETE_DRIVE_TRIP,
            userId = USER_ID,
            recordId = RECORD_ID,
            now = 200L,
            operationId = "delete-token"
        )

        assertEquals(DriveSyncOperationType.DELETE_DRIVE_TRIP.name, planned.operationType)
        assertEquals("delete-token", planned.operationId)
    }

    @Test
    fun `delete wins over a later update`() {
        val existing = operation(DriveSyncOperationType.DELETE_VEHICLE, "delete-token")

        val planned = DriveSyncPlanner.plan(
            existing = existing,
            requestedType = DriveSyncOperationType.UPDATE_VEHICLE,
            userId = USER_ID,
            recordId = RECORD_ID,
            now = 300L,
            operationId = "stale-update-token"
        )

        assertSame(existing, planned)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `existing operation from another uid is rejected`() {
        DriveSyncPlanner.plan(
            existing = operation(DriveSyncOperationType.UPDATE_VEHICLE, "old-token"),
            requestedType = DriveSyncOperationType.UPDATE_VEHICLE,
            userId = "different-user",
            recordId = RECORD_ID,
            now = 200L,
            operationId = "new-token"
        )
    }

    private fun operation(
        type: DriveSyncOperationType,
        operationId: String
    ) = DriveSyncOperationEntity(
        operationId = operationId,
        userId = USER_ID,
        entityType = type.entityType.name,
        recordId = RECORD_ID,
        operationType = type.name,
        createdAt = 100L,
        updatedAt = 100L,
        attemptCount = 3,
        lastErrorCode = "NETWORK",
        retryEligible = true,
        nextAttemptAt = 150L
    )

    private companion object {
        const val USER_ID = "user-a"
        const val RECORD_ID = "record-a"
    }
}
